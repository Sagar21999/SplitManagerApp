import { getConfig } from '../config';

/**
 * Token storage and refresh.
 *
 * The access token lives in a module-level variable and never touches persistent storage.
 * The refresh token goes to sessionStorage — cleared when the tab closes, and not shared
 * with other tabs. Neither goes to localStorage: this app holds a permanent record of the
 * user's finances, and localStorage would leave a long-lived credential readable by any
 * XSS that ever lands on the origin.
 *
 * The cost is that a full page reload drops the access token. That's fine — the refresh
 * token in sessionStorage silently mints a new one.
 */

const REFRESH_TOKEN_KEY = 'sm.refreshToken';
const PKCE_VERIFIER_KEY = 'sm.pkceVerifier';
const PKCE_STATE_KEY = 'sm.pkceState';
const POST_LOGIN_PATH_KEY = 'sm.postLoginPath';

let accessToken: string | null = null;
let accessTokenExpiresAt = 0;
let inFlightRefresh: Promise<string | null> | null = null;

export function setTokens(token: string, expiresInSeconds: number, refreshToken?: string): void {
  accessToken = token;
  // Renew a minute early so a token doesn't expire mid-request.
  accessTokenExpiresAt = Date.now() + (expiresInSeconds - 60) * 1000;
  if (refreshToken) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function clearTokens(): void {
  accessToken = null;
  accessTokenExpiresAt = 0;
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(PKCE_VERIFIER_KEY);
  sessionStorage.removeItem(PKCE_STATE_KEY);
}

export function hasRefreshToken(): boolean {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY) !== null;
}

/** PKCE transaction state, held only between the redirect out and the callback back. */
export const pkceStore = {
  save(verifier: string, state: string, postLoginPath: string): void {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
    sessionStorage.setItem(PKCE_STATE_KEY, state);
    sessionStorage.setItem(POST_LOGIN_PATH_KEY, postLoginPath);
  },
  take(): { verifier: string | null; state: string | null; postLoginPath: string } {
    const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
    const state = sessionStorage.getItem(PKCE_STATE_KEY);
    const postLoginPath = sessionStorage.getItem(POST_LOGIN_PATH_KEY) ?? '/';
    sessionStorage.removeItem(PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(PKCE_STATE_KEY);
    sessionStorage.removeItem(POST_LOGIN_PATH_KEY);
    return { verifier, state, postLoginPath };
  },
};

/**
 * Returns a usable access token, refreshing first if the current one is missing or about
 * to expire. Concurrent callers share one refresh rather than each firing their own.
 */
export async function getAccessToken(): Promise<string | null> {
  if (accessToken && Date.now() < accessTokenExpiresAt) {
    return accessToken;
  }
  if (!inFlightRefresh) {
    inFlightRefresh = refreshAccessToken().finally(() => {
      inFlightRefresh = null;
    });
  }
  return inFlightRefresh;
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) {
    return null;
  }

  const { auth } = await getConfig();
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: auth.userPoolClientId,
    refresh_token: refreshToken,
  });

  const res = await fetch(`${auth.hostedUiDomain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  if (!res.ok) {
    // Refresh token expired or revoked — the only way forward is a fresh login.
    clearTokens();
    return null;
  }

  const data = (await res.json()) as { access_token: string; expires_in: number };
  // Cognito does not return a new refresh token on this grant; the existing one stands.
  setTokens(data.access_token, data.expires_in);
  return data.access_token;
}
