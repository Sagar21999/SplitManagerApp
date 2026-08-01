import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { DataStack } from './constructs/data-stack';
import { ApiStack } from './constructs/api-stack';
import { FrontendStack } from './constructs/frontend-stack';
import { LambdaStack } from './constructs/lambda-stack';

export interface AppStageProps extends cdk.StageProps {
  envName: 'beta' | 'prod';
}

export class AppStage extends cdk.Stage {
  public readonly dataStack: DataStack;
  public readonly apiStack: ApiStack;
  public readonly frontendStack: FrontendStack;
  public readonly lambdaStack: LambdaStack;
  public readonly apiUrlOutput: cdk.CfnOutput;

  constructor(scope: Construct, id: string, props: AppStageProps) {
    super(scope, id, props);

    this.dataStack = new DataStack(this, 'DataStack', { envName: props.envName });

    this.apiStack = new ApiStack(this, 'ApiStack', {
      envName: props.envName,
      table: this.dataStack.table,
      imagesBucket: this.dataStack.imagesBucket,
    });

    this.apiUrlOutput = new cdk.CfnOutput(this.apiStack, 'ApiUrl', {
      value: `http://${this.apiStack.loadBalancer.loadBalancerDnsName}`,
    });

    this.frontendStack = new FrontendStack(this, 'FrontendStack', {
      envName: props.envName,
      apiUrl: this.apiStack.loadBalancer.loadBalancerDnsName,
    });

    this.lambdaStack = new LambdaStack(this, 'LambdaStack', { envName: props.envName });
  }
}
