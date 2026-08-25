import { getConfig } from '../config';
import { deriveCodeChallenge, generateCodeVerifier, generateState } from './pkce';
import { clearTokens, pkceStore, setTokens } from './tokenStore';

/**
 * Sends the browser to the Cognito hosted UI to sign in.
 *
 * The current path is stashed so the callback can return the user where they were rather
 * than dumping them on the ledger root.
 */
export async function beginLogin(): Promise<void> {
  const { auth } = await getConfig();

  const verifier = generateCodeVerifier();
  const challenge = await deriveCodeChallenge(verifier);
  const state = generateState();

  pkceStore.save(verifier, state, window.location.pathname + window.location.search);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: auth.userPoolClientId,
    redirect_uri: auth.redirectUri,
    scope: 'openid email profile',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });

  window.location.assign(`${auth.hostedUiDomain}/oauth2/authorize?${params}`);
}

/**
 * Handles the /auth/callback redirect: validates state, exchanges the authorization code
 * for tokens, and reports where to navigate next.
 */
export async function completeLogin(): Promise<{ postLoginPath: string }> {
  const { auth } = await getConfig();
  const params = new URLSearchParams(window.location.search);

  const error = params.get('error');
  if (error) {
    throw new Error(`Sign-in failed: ${params.get('error_description') ?? error}`);
  }

  const code = params.get('code');
  const returnedState = params.get('state');
  const { verifier, state, postLoginPath } = pkceStore.take();

  if (!code || !verifier) {
    throw new Error('Sign-in callback is missing its authorization code.');
  }
  // Mismatched state means this callback did not originate from our redirect.
  if (!returnedState || returnedState !== state) {
    throw new Error('Sign-in state mismatch — the request may have been tampered with.');
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: auth.userPoolClientId,
    code,
    redirect_uri: auth.redirectUri,
    code_verifier: verifier,
  });

  const res = await fetch(`${auth.hostedUiDomain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  if (!res.ok) {
    throw new Error(`Token exchange failed: ${res.status}`);
  }

  const data = (await res.json()) as {
    access_token: string;
    refresh_token: string;
    expires_in: number;
  };
  setTokens(data.access_token, data.expires_in, data.refresh_token);

  return { postLoginPath };
}

/** Clears local tokens and ends the Cognito session, so the next login really prompts. */
export async function logout(): Promise<void> {
  const { auth } = await getConfig();
  clearTokens();
  const params = new URLSearchParams({
    client_id: auth.userPoolClientId,
    logout_uri: auth.logoutUri,
  });
  window.location.assign(`${auth.hostedUiDomain}/logout?${params}`);
}
