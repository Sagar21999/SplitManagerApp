import { getConfig } from './config';
import type { FinalizeSplitResponse, SessionResponse, SubmitExpenseRequest } from './types';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const { apiUrl } = await getConfig();
  const res = await fetch(`${apiUrl}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`${init?.method ?? 'GET'} ${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
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
