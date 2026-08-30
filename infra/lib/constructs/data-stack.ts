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
  public readonly statementsBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    // Single table holding transactions, the people directory, and (from Phase 4)
    // statement imports — distinguished by key prefix. See LLD 4.2.
    //
    // Replaces v1's `-receipt-sessions` table rather than altering it: the partition key
    // changes from `sessionId` to a generic `pk`/`sk` pair, which DynamoDB cannot do in
    // place. The old table held only transient, TTL-expiring session data, so there is
    // nothing to migrate — it is left behind by the rename and can be deleted by hand.
    this.table = new dynamodb.TableV2(this, 'LedgerTable', {
      tableName: `split-manager-${props.envName}-ledger`,
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },

      // No timeToLiveAttribute. v1 expired sessions after 24h; v2's ledger is permanent,
      // and the balances, statuses, and duplicate detection all depend on rows surviving.

      globalSecondaryIndexes: [
        {
          // Chronological listing. gsi1sk is `{ISO date}#{transactionId}`, so lexical
          // order is date order and a reverse query gives newest-first directly.
          indexName: 'GSI1',
          partitionKey: { name: 'gsi1pk', type: dynamodb.AttributeType.STRING },
          sortKey: { name: 'gsi1sk', type: dynamodb.AttributeType.STRING },
          projectionType: dynamodb.ProjectionType.ALL,
        },
        {
          // Duplicate detection (BRD FR19). Partitioned by amount+date so one query
          // returns the candidate set; merchant similarity is scored in the API.
          indexName: 'GSI2',
          partitionKey: { name: 'gsi2pk', type: dynamodb.AttributeType.STRING },
          sortKey: { name: 'gsi2sk', type: dynamodb.AttributeType.STRING },
          projectionType: dynamodb.ProjectionType.ALL,
        },
      ],

      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },

      // RETAIN, unlike every other resource here: this is the user's permanent financial
      // record. A stack teardown that silently took the ledger with it would be
      // unrecoverable, and an orphaned table is a much cheaper mistake to fix.
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // Receipt images are RETAINED — a transaction references its image for as long as it
    // exists. v1 expired these after a day, which was correct when sessions themselves
    // expired and wrong now that they do not.
    this.imagesBucket = new s3.Bucket(this, 'ReceiptImagesBucket', {
      bucketName: `split-manager-${props.envName}-receipt-images-${this.account}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: false,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // Statement uploads are the opposite: deleted as soon as they are parsed (BRD FR20).
    // The API deletes each object explicitly; this rule is the backstop for a crash
    // between upload and delete. Keeping raw bank statements around would make an S3
    // compromise far worse than it needs to be — the extracted rows are all we need.
    this.statementsBucket = new s3.Bucket(this, 'StatementsBucket', {
      bucketName: `split-manager-${props.envName}-statements-${this.account}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      lifecycleRules: [{ expiration: cdk.Duration.days(1) }],
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
  }
}
