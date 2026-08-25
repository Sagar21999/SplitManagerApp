import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr_assets from 'aws-cdk-lib/aws-ecr-assets';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import { Construct } from 'constructs';
import * as path from 'path';
import * as fs from 'fs';

export interface ApiStackProps extends cdk.StackProps {
  envName: string;
  table: dynamodb.ITable;
  imagesBucket: s3.IBucket;
  userPool: cognito.IUserPool;
}

export class ApiStack extends cdk.Stack {
  public readonly service: ecs.FargateService;
  public readonly loadBalancer: elbv2.ApplicationLoadBalancer;
  public readonly taskRole: iam.Role;

  constructor(scope: Construct, id: string, props: ApiStackProps) {
    super(scope, id, props);

    // No NAT gateways: Fargate tasks run in public subnets with a public IP.
    // Cheaper for a low-traffic personal tool; revisit if this ever needs to be private.
    const vpc = new ec2.Vpc(this, 'ApiVpc', {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
      ],
    });

    const cluster = new ecs.Cluster(this, 'ApiCluster', {
      vpc,
      clusterName: `split-manager-${props.envName}-cluster`,
    });

    this.taskRole = new iam.Role(this, 'ApiTaskRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });
    props.table.grantReadWriteData(this.taskRole);
    props.imagesBucket.grantReadWrite(this.taskRole);
    this.taskRole.addToPolicy(
      new iam.PolicyStatement({
        actions: ['textract:AnalyzeExpense'],
        // Textract does not support resource-level ARN scoping for this action.
        resources: ['*'],
      }),
    );

    // 256/512 was CPU-starving Spring Boot's startup (~50-60s to first response) badly
    // enough that ALB health checks occasionally timed out even after the app was up,
    // repeatedly tripping the unhealthy threshold and cycling the task. Bumped to give
    // the JVM enough headroom to start and serve health checks reliably.
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'ApiTaskDefinition', {
      cpu: 512,
      memoryLimitMiB: 1024,
      taskRole: this.taskRole,
    });

    // Falls back to a public placeholder image until api/Dockerfile exists (Phase 2),
    // so infra/'s pipeline can be scaffolded and verified independently of api/'s progress.
    const apiDockerfilePath = path.join(__dirname, '../../../api/Dockerfile');
    const hasApiDockerfile = fs.existsSync(apiDockerfilePath);
    const containerPort = hasApiDockerfile ? 8080 : 80;

    taskDefinition.addContainer('ApiContainer', {
      image: hasApiDockerfile
        ? ecs.ContainerImage.fromAsset(path.join(__dirname, '../../../api'), {
            platform: ecr_assets.Platform.LINUX_AMD64,
          })
        : ecs.ContainerImage.fromRegistry('public.ecr.aws/nginx/nginx:latest'),
      portMappings: [{ containerPort }],
      environment: {
        ENV_NAME: props.envName,
        TABLE_NAME: props.table.tableName,
        IMAGES_BUCKET_NAME: props.imagesBucket.bucketName,
        // Spring Security fetches this pool's JWKS from {issuer}/.well-known/jwks.json
        // and validates every incoming bearer token's signature, issuer, and expiry
        // against it. No shared secret is involved.
        COGNITO_ISSUER_URI: `https://cognito-idp.${this.region}.amazonaws.com/${props.userPool.userPoolId}`,
      },
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'api',
        logRetention: logs.RetentionDays.ONE_WEEK,
      }),
    });

    this.service = new ecs.FargateService(this, 'ApiService', {
      cluster,
      taskDefinition,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      // Spring Boot + eager AWS SDK client init takes ~60s to come up. The target group's
      // healthyThresholdCount below needs 2 consecutive passes (30s) once the app responds,
      // so 180s leaves comfortable margin over the ~90s worst case — without this, the ALB
      // marks the task unhealthy before it's ready (or on any transient startup blip after
      // grace period ends) and ECS cycles it forever.
      healthCheckGracePeriod: cdk.Duration.seconds(180),
    });

    this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'ApiLoadBalancer', {
      vpc,
      internetFacing: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    const listener = this.loadBalancer.addListener('ApiListener', { port: 80, open: true });
    listener.addTargets('ApiTargets', {
      port: containerPort,
      targets: [this.service],
      healthCheck: {
        // Matches server.servlet.context-path=/api in application.yml. CloudFront
        // forwards the /api/* prefix to this origin rather than stripping it, so the
        // app serves everything under /api — including actuator.
        path: hasApiDockerfile ? '/api/actuator/health' : '/',
        interval: cdk.Duration.seconds(15),
        timeout: cdk.Duration.seconds(10),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
    });
  }
}
