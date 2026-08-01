import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

export interface DataStackProps extends cdk.StackProps {
  envName: string;
}

export class DataStack extends cdk.Stack {
  public readonly table: dynamodb.TableV2;
  public readonly imagesBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    this.table = new dynamodb.TableV2(this, 'ReceiptSessionsTable', {
      tableName: `split-manager-${props.envName}-receipt-sessions`,
      partitionKey: { name: 'sessionId', type: dynamodb.AttributeType.STRING },
      timeToLiveAttribute: 'expiresAt',
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.imagesBucket = new s3.Bucket(this, 'ReceiptImagesBucket', {
      bucketName: `split-manager-${props.envName}-receipt-images-${this.account}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      lifecycleRules: [
        {
          expiration: cdk.Duration.days(1),
        },
      ],
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
  }
}
