import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr_assets from 'aws-cdk-lib/aws-ecr-assets';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';
import * as path from 'path';
import * as fs from 'fs';

export interface ApiStackProps extends cdk.StackProps {
  envName: string;
  table: dynamodb.ITable;
  imagesBucket: s3.IBucket;
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

    const splitwiseApiKeySecretName = `split-manager/${props.envName}/splitwise-api-key`;
    this.taskRole.addToPolicy(
      new iam.PolicyStatement({
        actions: ['secretsmanager:GetSecretValue'],
        resources: [
          cdk.Stack.of(this).formatArn({
            service: 'secretsmanager',
            resource: 'secret',
            resourceName: `${splitwiseApiKeySecretName}-??????`,
            arnFormat: cdk.ArnFormat.COLON_RESOURCE_NAME,
          }),
        ],
      }),
    );

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'ApiTaskDefinition', {
      cpu: 256,
      memoryLimitMiB: 512,
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
        SPLITWISE_API_KEY_SECRET_NAME: splitwiseApiKeySecretName,
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
      healthCheck: { path: hasApiDockerfile ? '/actuator/health' : '/' },
    });
  }
}
