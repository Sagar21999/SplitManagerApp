import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import { Construct } from 'constructs';
import * as path from 'path';
import * as fs from 'fs';
import { execFileSync } from 'child_process';

export interface FrontendStackProps extends cdk.StackProps {
  envName: string;
  apiUrl: string;
}

export class FrontendStack extends cdk.Stack {
  public readonly bucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: FrontendStackProps) {
    super(scope, id, props);

    this.bucket = new s3.Bucket(this, 'FrontendBucket', {
      bucketName: `split-manager-${props.envName}-frontend-${this.account}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    this.distribution = new cloudfront.Distribution(this, 'FrontendDistribution', {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.bucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      },
      defaultRootObject: 'index.html',
      errorResponses: [
        // SPA client-side routing: unknown paths fall back to index.html.
        { httpStatus: 403, responseHttpStatus: 200, responsePagePath: '/index.html' },
        { httpStatus: 404, responseHttpStatus: 200, responsePagePath: '/index.html' },
      ],
    });

    const frontendDir = path.join(__dirname, '../../../frontend');

    new s3deploy.BucketDeployment(this, 'DeployFrontend', {
      sources: [
        s3deploy.Source.asset(frontendDir, {
          bundling: {
            image: cdk.DockerImage.fromRegistry('public.ecr.aws/docker/library/node:20'),
            local: {
              // Runs on the synth host (already has Node, since infra/'s own synth step
              // does `npm ci`) rather than spinning up Docker; falls back to the image
              // above only if Node/npm aren't available.
              tryBundle(outputDir: string): boolean {
                try {
                  execFileSync('npm', ['ci'], { cwd: frontendDir, stdio: 'inherit', shell: true });
                  execFileSync('npm', ['run', 'build'], { cwd: frontendDir, stdio: 'inherit', shell: true });
                } catch {
                  return false;
                }
                fs.cpSync(path.join(frontendDir, 'dist'), outputDir, { recursive: true });
                return true;
              },
            },
          },
        }),
        // The API's ALB URL isn't known until deploy time, so it can't be baked into the
        // JS bundle at build/bundling time — ship it as a small runtime config the app
        // fetches on load instead (see frontend/src/config.ts).
        s3deploy.Source.jsonData('config.json', { apiUrl: `http://${props.apiUrl}` }),
      ],
      destinationBucket: this.bucket,
      distribution: this.distribution,
      distributionPaths: ['/*'],
    });

    new cdk.CfnOutput(this, 'FrontendUrl', {
      value: `https://${this.distribution.distributionDomainName}`,
    });
  }
}
