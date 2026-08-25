/**
 * Minimal PKCE helpers for the Cognito hosted-UI authorization-code flow.
 *
 * Hand-rolled rather than pulling in oidc-client-ts: the flow we need is one redirect,
 * one code exchange, and one refresh call, all against a single known provider. The
 * library's value is breadth we don't use.
 */

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Random, URL-safe, 43+ chars — the `code_verifier` per RFC 7636. */
export function generateCodeVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

/** S256 challenge derived from the verifier. */
export async function deriveCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

/** Opaque value round-tripped through the provider to detect CSRF on the callback. */
export function generateState(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}
