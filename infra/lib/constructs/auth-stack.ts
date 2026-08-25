import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import { Construct } from 'constructs';

export interface AuthStackProps extends cdk.StackProps {
  envName: string;
}

/**
 * Cognito user pool for the single account that owns this ledger.
 *
 * Deliberately holds the pool and hosted-UI domain but NOT the app client. The
 * client needs OAuth callback URLs, which are the CloudFront distribution's
 * domain — and that distribution lives in FrontendStack, which itself depends on
 * ApiStack's load balancer. Creating the client here would close the cycle
 * Auth -> Api -> Frontend -> Auth. FrontendStack creates the client instead,
 * where the domain it needs is a local value.
 */
export class AuthStack extends cdk.Stack {
  public readonly userPool: cognito.UserPool;
  public readonly userPoolDomain: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props: AuthStackProps) {
    super(scope, id, props);

    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: `split-manager-${props.envName}-users`,
      // Exactly one account exists, provisioned by hand. Anyone able to sign
      // themselves up would get their own empty ledger on our bill.
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      autoVerify: { email: true },
      passwordPolicy: {
        minLength: 12,
        requireLowercase: true,
        requireUppercase: true,
        requireDigits: true,
        requireSymbols: true,
      },
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { sms: false, otp: true },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      // The ledger is the durable asset, not the pool — but destroying the pool
      // would strand every transaction behind a `sub` that no longer exists.
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    this.userPoolDomain = this.userPool.addDomain('UserPoolDomain', {
      cognitoDomain: {
        // Must be globally unique across all of Cognito, hence the account suffix.
        domainPrefix: `split-manager-${props.envName}-${this.account}`,
      },
    });

    new cdk.CfnOutput(this, 'UserPoolId', { value: this.userPool.userPoolId });
    new cdk.CfnOutput(this, 'UserPoolIssuerUrl', {
      value: `https://cognito-idp.${this.region}.amazonaws.com/${this.userPool.userPoolId}`,
    });
    new cdk.CfnOutput(this, 'HostedUiDomain', {
      value: this.userPoolDomain.baseUrl(),
    });
  }
}
