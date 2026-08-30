import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { AuthStack } from './constructs/auth-stack';
import { DataStack } from './constructs/data-stack';
import { ApiStack } from './constructs/api-stack';
import { FrontendStack } from './constructs/frontend-stack';
import { LambdaStack } from './constructs/lambda-stack';

export interface AppStageProps extends cdk.StageProps {
  envName: 'beta' | 'prod';
}

export class AppStage extends cdk.Stage {
  public readonly authStack: AuthStack;
  public readonly dataStack: DataStack;
  public readonly apiStack: ApiStack;
  public readonly frontendStack: FrontendStack;
  public readonly lambdaStack: LambdaStack;
  public readonly apiUrlOutput: cdk.CfnOutput;

  constructor(scope: Construct, id: string, props: AppStageProps) {
    super(scope, id, props);

    // Order matters and is load-bearing: Auth -> Data -> Api -> Frontend.
    // FrontendStack owns the user pool client because it is the only stack that
    // knows the CloudFront domain the OAuth callback has to point at. See the
    // note in auth-stack.ts.
    this.authStack = new AuthStack(this, 'AuthStack', { envName: props.envName });

    this.dataStack = new DataStack(this, 'DataStack', { envName: props.envName });

    this.apiStack = new ApiStack(this, 'ApiStack', {
      envName: props.envName,
      table: this.dataStack.table,
      imagesBucket: this.dataStack.imagesBucket,
      statementsBucket: this.dataStack.statementsBucket,
      userPool: this.authStack.userPool,
    });

    this.apiUrlOutput = new cdk.CfnOutput(this.apiStack, 'ApiUrl', {
      value: `http://${this.apiStack.loadBalancer.loadBalancerDnsName}`,
    });

    this.frontendStack = new FrontendStack(this, 'FrontendStack', {
      envName: props.envName,
      loadBalancer: this.apiStack.loadBalancer,
      userPool: this.authStack.userPool,
      userPoolDomain: this.authStack.userPoolDomain,
    });

    this.lambdaStack = new LambdaStack(this, 'LambdaStack', { envName: props.envName });
  }
}
