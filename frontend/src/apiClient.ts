import { getConfig } from './config';
import { getAccessToken } from './auth/tokenStore';
import { beginLogin } from './auth/authFlow';
import type {
  FinalizeSplitResponse,
  ParseReceiptResponse,
  SessionResponse,
  SubmitExpenseRequest,
} from './types';

/**
 * Every API call carries the Cognito access token. `getAccessToken()` refreshes
 * transparently when the current one is stale, so callers never deal with expiry.
 */
async function authHeaders(): Promise<Record<string, string>> {
  const token = await getAccessToken();
  if (!token) {
    // No usable token and no way to mint one — the refresh token is gone or revoked.
    await beginLogin();
    throw new Error('Not signed in.');
  }
  return { Authorization: `Bearer ${token}` };
}

async function handle<T>(res: Response, method: string, path: string): Promise<T> {
  if (res.status === 401) {
    // The token was rejected despite being unexpired — revoked, or the pool changed.
    // Nothing to retry locally; send the user back through sign-in.
    await beginLogin();
    throw new Error('Session expired.');
  }
  if (!res.ok) {
    throw new Error(`${method} ${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const { apiUrl } = await getConfig();
  const auth = await authHeaders();
  const res = await fetch(`${apiUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...auth, ...(init?.headers ?? {}) },
  });
  return handle<T>(res, init?.method ?? 'GET', path);
}

export function getSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/session/${sessionId}`);
}

export function finalizeSplit(payload: SubmitExpenseRequest): Promise<FinalizeSplitResponse> {
  return request<FinalizeSplitResponse>('/finalize-split', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function parseReceipt(image: File): Promise<ParseReceiptResponse> {
  const { apiUrl } = await getConfig();
  const auth = await authHeaders();
  const formData = new FormData();
  formData.append('image', image);
  // No Content-Type header here — fetch sets the multipart boundary itself when given
  // a FormData body; setting it manually (like request()'s JSON default) breaks it.
  const res = await fetch(`${apiUrl}/parse-receipt`, {
    method: 'POST',
    body: formData,
    headers: auth,
  });
  return handle<ParseReceiptResponse>(res, 'POST', '/parse-receipt');
}
