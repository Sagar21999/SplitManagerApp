import * as cdk from 'aws-cdk-lib';
import * as pipelines from 'aws-cdk-lib/pipelines';
import { Construct } from 'constructs';
import { AppStage } from './app-stage';

// Set after completing the CodeStarConnections GitHub OAuth handshake in the
// AWS Console (Developer Tools > Settings > Connections) — see docs/loe.md
// Phase 1. Passed via CDK context: `cdk deploy -c codeStarConnectionArn=...`.
const GITHUB_OWNER_REPO = 'Sagar21999/SplitManagerApp';
const GITHUB_BRANCH = 'main';

export class PipelineStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    const connectionArn = this.node.tryGetContext('codeStarConnectionArn');
    if (!connectionArn) {
      throw new Error(
        'Missing required context value "codeStarConnectionArn". ' +
          'Complete the CodeStarConnections GitHub handshake in the AWS console, then pass ' +
          '-c codeStarConnectionArn=<arn> to cdk commands.',
      );
    }

    const pipeline = this.buildPipeline(connectionArn);

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
