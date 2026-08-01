import * as cdk from 'aws-cdk-lib';
import * as pipelines from 'aws-cdk-lib/pipelines';
import { Construct } from 'constructs';
import { AppStage } from './app-stage';

// Set once, after completing the CodeStarConnections GitHub OAuth handshake in
// the AWS Console (CodePipeline > Settings > Connections) — see docs/loe.md
// Phase 1. Not a secret (an ARN grants nothing without the AWS account's own
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

    const betaAppStage = new AppStage(this, 'Beta', { envName: 'beta' });
    const betaStage = pipeline.addStage(betaAppStage);
    betaStage.addPost(
      new pipelines.CodeBuildStep('IntegTests', {
        commands: ['cd integ-tests', 'mvn test -Dbeta.api.url=$BETA_API_URL'],
        envFromCfnOutputs: { BETA_API_URL: betaAppStage.apiUrlOutput },
      }),
    );

    // No manual approval — promotion is automatic once IntegTests passes.
    pipeline.addStage(new AppStage(this, 'Prod', { envName: 'prod' }));
  }

  private buildPipeline(connectionArn: string): pipelines.CodePipeline {
    return new pipelines.CodePipeline(this, 'Pipeline', {
      pipelineName: 'split-manager-pipeline',
      synth: new pipelines.ShellStep('Synth', {
        input: pipelines.CodePipelineSource.connection(GITHUB_OWNER_REPO, GITHUB_BRANCH, {
          connectionArn,
        }),
        commands: ['cd infra', 'npm ci', 'npx cdk synth'],
        primaryOutputDirectory: 'infra/cdk.out',
      }),
    });
  }
}
