export interface AuthConfig {
  region: string;
  userPoolId: string;
  userPoolClientId: string;
  /** Cognito hosted-UI base URL, e.g. https://split-manager-beta-123.auth.us-east-1.amazoncognito.com */
  hostedUiDomain: string;
  redirectUri: string;
  logoutUri: string;
}

export interface AppConfig {
  /** Same-origin path prefix — the API is proxied at /api/* by CloudFront. */
  apiUrl: string;
  auth: AuthConfig;
}

let cached: Promise<AppConfig> | null = null;

/**
 * The API path and the Cognito pool/client IDs aren't known until deploy time, so they
 * can't be baked into the JS bundle at build time — they're written to config.json by the
 * CDK BucketDeployment alongside the built assets, and fetched once here at runtime.
 */
export function getConfig(): Promise<AppConfig> {
  if (!cached) {
    cached = fetch('/config.json').then((res) => {
      if (!res.ok) {
        throw new Error(`Failed to load /config.json: ${res.status}`);
      }
      return res.json() as Promise<AppConfig>;
    });
  }
  return cached;
}
