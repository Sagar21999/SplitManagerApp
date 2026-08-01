import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';

export interface LambdaStackProps extends cdk.StackProps {
  envName: string;
}

// Placeholder construct — no resources defined until the lambdas/ package is
// actually needed (see HLD/BRD: not built for P0).
export class LambdaStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: LambdaStackProps) {
    super(scope, id, props);
  }
}
