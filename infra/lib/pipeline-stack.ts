import * as cdk from 'aws-cdk-lib';
import * as codepipeline from 'aws-cdk-lib/aws-codepipeline';
import * as pipelines from 'aws-cdk-lib/pipelines';
import { Construct } from 'constructs';
import { AppStage } from './app-stage';

// Set once, after completing the CodeStarConnections GitHub OAuth handshake in
// the AWS Console (CodePipeline > Settings > Connections).
// Not a secret (an ARN grants nothing without the AWS account's own
// IAM permissions), so it's safe to commit rather than pass as CLI context —
// CodePipeline's own self-mutating synth step has no way to supply `-c` flags.
const GITHUB_CONNECTION_ARN =
  'arn:aws:codeconnections:us-east-1:548171705631:connection/cbf0c0f7-4149-41b3-baa7-c04319d264a7';
const GITHUB_OWNER_REPO = 'Sagar21999/SplitManagerApp';
const GITHUB_BRANCH = 'main';

export class PipelineStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    const pipeline = this.buildPipeline(GITHUB_CONNECTION_ARN);

    // Beta is the working environment — all verification happens there, manually
    // (see docs/lld.md §11). The IntegTests gate that used to sit here was removed
    // along with the integ-tests/ package, so Prod promotion is now ungated: an
    // accepted risk while Prod carries no users. Reinstate the gate before Prod
    // becomes load-bearing.
    pipeline.addStage(new AppStage(this, 'Beta', { envName: 'beta' }));
    pipeline.addStage(new AppStage(this, 'Prod', { envName: 'prod' }));
  }

  private buildPipeline(connectionArn: string): pipelines.CodePipeline {
    return new pipelines.CodePipeline(this, 'Pipeline', {
      pipelineName: 'split-manager-pipeline',
      // V1: flat monthly pricing rather than V2's per-minute pricing — cheaper
      // for a personal project with infrequent pipeline runs.
      pipelineType: codepipeline.PipelineType.V1,
      synth: new pipelines.ShellStep('Synth', {
        input: pipelines.CodePipelineSource.connection(GITHUB_OWNER_REPO, GITHUB_BRANCH, {
          connectionArn,
          // Explicit, even though AWS documents DetectChanges as defaulting to true.
          // Left unset, CDK omits the property from the template entirely, and in this
          // account pushes were reaching GitHub without ever starting an execution —
          // two separate pushes, zero runs, both needing a manual start. Emitting
          // DetectChanges: true forces CodePipeline to (re)register the webhook.
          triggerOnPush: true,
        }),
        commands: ['cd infra', 'npm ci', 'npx cdk synth'],
        primaryOutputDirectory: 'infra/cdk.out',
      }),
    });
  }
}
