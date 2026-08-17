interface AppConfig {
  apiUrl: string;
}

let cached: Promise<AppConfig> | null = null;

/**
 * The API's ALB URL isn't known until deploy time, so it can't be baked into the JS
 * bundle at build time — it's written to config.json by the CDK BucketDeployment
 * alongside the built assets, and fetched once here at runtime instead.
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
