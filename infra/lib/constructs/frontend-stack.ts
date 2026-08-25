import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';
import * as path from 'path';
import * as fs from 'fs';
import { execFileSync } from 'child_process';

export interface FrontendStackProps extends cdk.StackProps {
  envName: string;
  loadBalancer: elbv2.IApplicationLoadBalancer;
  userPool: cognito.IUserPool;
  // Concrete type, not IUserPoolDomain: baseUrl() is only on the concrete class.
  userPoolDomain: cognito.UserPoolDomain;
}

export class FrontendStack extends cdk.Stack {
  public readonly bucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;
  public readonly userPoolClient: cognito.UserPoolClient;

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
      additionalBehaviors: {
        // The API is served through this same distribution rather than hit directly
        // on its ALB DNS name. Two reasons, both hard requirements:
        //
        //  1. HTTPS. The ALB listener is plain HTTP with no certificate (adding one
        //     needs a custom domain we don't have). A page served over HTTPS cannot
        //     fetch() an http:// URL — the browser blocks it as mixed content. Every
        //     API call from the deployed SPA would fail. CloudFront terminates TLS
        //     with its own *.cloudfront.net certificate, so no domain is needed.
        //  2. Same origin. With the API under /api/* on the SPA's own origin there is
        //     no cross-origin request at all, so CORS stops being a concern rather
        //     than something to configure correctly.
        //
        // CloudFront -> ALB is still plain HTTP. That hop is AWS-internal; closing it
        // requires the same custom domain and certificate as option (1).
        '/api/*': {
          origin: new origins.LoadBalancerV2Origin(props.loadBalancer, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
            httpPort: 80,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          // Never cache API responses — the ledger is read-your-writes.
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          // Forwards the Authorization header (and query strings/cookies) to the
          // origin. Without this CloudFront strips it and every request reaches the
          // API unauthenticated, so all of them 401.
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
        },
      },
      defaultRootObject: 'index.html',
      errorResponses: [
        // SPA client-side routing: unknown paths fall back to index.html.
        { httpStatus: 403, responseHttpStatus: 200, responsePagePath: '/index.html' },
        { httpStatus: 404, responseHttpStatus: 200, responsePagePath: '/index.html' },
      ],
    });

    const appUrl = `https://${this.distribution.distributionDomainName}`;

    // Created here rather than in AuthStack because the OAuth callback URLs are this
    // distribution's domain — see the note in auth-stack.ts about the dependency cycle.
    //
    // Must be `new UserPoolClient(this, ...)` and NOT `props.userPool.addClient(...)`:
    // addClient() parents the client under the pool, which lives in AuthStack, so the
    // callback URL below would make AuthStack depend on FrontendStack. Combined with
    // ApiStack -> AuthStack and FrontendStack -> ApiStack that closes a cycle and synth
    // fails outright. Scoping the client to this stack keeps the reference local.
    this.userPoolClient = new cognito.UserPoolClient(this, 'WebClient', {
      userPool: props.userPool,
      userPoolClientName: `split-manager-${props.envName}-web`,
      // Public client: a browser cannot keep a secret, so PKCE is what protects the
      // authorization code instead.
      generateSecret: false,
      authFlows: { userSrp: true },
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL, cognito.OAuthScope.PROFILE],
        callbackUrls: [
          `${appUrl}/auth/callback`,
          // Local dev against the deployed pool. Cognito allows http only for localhost.
          ...(props.envName === 'beta' ? ['http://localhost:5173/auth/callback'] : []),
        ],
        logoutUrls: [appUrl, ...(props.envName === 'beta' ? ['http://localhost:5173'] : [])],
      },
      accessTokenValidity: cdk.Duration.hours(1),
      idTokenValidity: cdk.Duration.hours(1),
      refreshTokenValidity: cdk.Duration.days(30),
      preventUserExistenceErrors: true,
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
        // Cognito IDs and the hosted-UI domain aren't known until deploy time, so they
        // can't be baked into the JS bundle — shipped as a small runtime config the app
        // fetches on load instead (see frontend/src/config.ts).
        s3deploy.Source.jsonData('config.json', {
          // Relative: the API is same-origin under /api/* via the behavior above.
          apiUrl: '/api',
          auth: {
            region: this.region,
            userPoolId: props.userPool.userPoolId,
            userPoolClientId: this.userPoolClient.userPoolClientId,
            hostedUiDomain: props.userPoolDomain.baseUrl(),
            redirectUri: `${appUrl}/auth/callback`,
            logoutUri: appUrl,
          },
        }),
      ],
      destinationBucket: this.bucket,
      distribution: this.distribution,
      distributionPaths: ['/*'],
    });

    new cdk.CfnOutput(this, 'FrontendUrl', { value: appUrl });
    new cdk.CfnOutput(this, 'UserPoolClientId', {
      value: this.userPoolClient.userPoolClientId,
    });
  }
}
