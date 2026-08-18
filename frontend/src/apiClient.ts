import { getConfig } from './config';
import type {
  FinalizeSplitResponse,
  ParseReceiptResponse,
  SessionResponse,
  SubmitExpenseRequest,
} from './types';

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

export async function parseReceipt(image: File): Promise<ParseReceiptResponse> {
  const { apiUrl } = await getConfig();
  const formData = new FormData();
  formData.append('image', image);
  // No Content-Type header here — fetch sets the multipart boundary itself when given
  // a FormData body; setting it manually (like request()'s JSON default) breaks it.
  const res = await fetch(`${apiUrl}/parse-receipt`, { method: 'POST', body: formData });
  if (!res.ok) {
    throw new Error(`POST /parse-receipt failed: ${res.status}`);
  }
  return res.json() as Promise<ParseReceiptResponse>;
}
