import { getConfig } from './config';
import { getAccessToken } from './auth/tokenStore';
import { beginLogin } from './auth/authFlow';
import type {
  Balances,
  ConfirmCandidateRequest,
  CreateTransactionRequest,
  FinalizeRequest,
  IssuerProfile,
  Person,
  ReceiptDraft,
  StatementCandidate,
  StatementImport,
  Transaction,
  TransactionDetail,
  TransactionStatus,
  TransactionType,
  UpdateTransactionRequest,
} from './types';

/**
 * Every call carries the Cognito access token. `getAccessToken()` refreshes
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
    // Rejected despite being unexpired — revoked, or the pool changed. Nothing to
    // retry locally; send the user back through sign-in.
    await beginLogin();
    throw new Error('Session expired.');
  }
  if (!res.ok) {
    // The API returns { "error": "..." } for handled failures; surfacing that message
    // is the difference between "400" and "Percentages must sum to 100, got 60".
    let message = `${method} ${path} failed: ${res.status}`;
    try {
      const body = (await res.json()) as { error?: string };
      if (body?.error) message = body.error;
    } catch {
      /* non-JSON body — keep the status-code message */
    }
    throw new Error(message);
  }
  if (res.status === 204) {
    return undefined as T;
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

function qs(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') search.set(k, String(v));
  });
  const s = search.toString();
  return s ? `?${s}` : '';
}

export const transactions = {
  async createFromReceipt(image: File): Promise<ReceiptDraft> {
    const { apiUrl } = await getConfig();
    const auth = await authHeaders();
    const formData = new FormData();
    formData.append('image', image);
    // No Content-Type header here — fetch sets the multipart boundary itself when given
    // a FormData body; setting it manually (like request()'s JSON default) breaks it.
    const res = await fetch(`${apiUrl}/transactions/from-receipt`, {
      method: 'POST',
      body: formData,
      headers: auth,
    });
    return handle<ReceiptDraft>(res, 'POST', '/transactions/from-receipt');
  },

  /** Hand entry, for a charge with no receipt photo and no statement row behind it. */
  create(body: CreateTransactionRequest) {
    return request<Transaction>('/transactions', { method: 'POST', body: JSON.stringify(body) });
  },

  list(filters: { status?: TransactionStatus; type?: TransactionType; limit?: number } = {}) {
    return request<Transaction[]>(`/transactions${qs(filters)}`);
  },

  get(id: string) {
    return request<TransactionDetail>(`/transactions/${id}`);
  },

  update(id: string, body: UpdateTransactionRequest) {
    return request<Transaction>(`/transactions/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    });
  },

  finalize(id: string, body: FinalizeRequest) {
    return request<TransactionDetail>(`/transactions/${id}/finalize`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  updateStatus(id: string, status: TransactionStatus) {
    return request<Transaction>(`/transactions/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  },

  remove(id: string) {
    return request<void>(`/transactions/${id}`, { method: 'DELETE' });
  },
};

export const people = {
  list() {
    return request<Person[]>('/people');
  },
  create(displayName: string) {
    return request<Person>('/people', { method: 'POST', body: JSON.stringify({ displayName }) });
  },
  rename(id: string, displayName: string) {
    return request<Person>(`/people/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ displayName }),
    });
  },
  archive(id: string) {
    return request<void>(`/people/${id}`, { method: 'DELETE' });
  },
};

export const statements = {
  issuerProfiles() {
    return request<IssuerProfile[]>('/statements/issuer-profiles');
  },

  async upload(file: File, issuerProfile?: string): Promise<StatementImport> {
    const { apiUrl } = await getConfig();
    const auth = await authHeaders();
    const formData = new FormData();
    formData.append('file', file);
    if (issuerProfile) formData.append('issuerProfile', issuerProfile);
    // As with the receipt upload: no Content-Type, so fetch sets the multipart boundary.
    const res = await fetch(`${apiUrl}/statements`, { method: 'POST', body: formData, headers: auth });
    return handle<StatementImport>(res, 'POST', '/statements');
  },

  get(id: string) {
    return request<StatementImport>(`/statements/${id}`);
  },

  getCandidates(id: string) {
    return request<StatementCandidate[]>(`/statements/${id}/candidates`);
  },

  confirmCandidate(id: string, candidateId: string, edits: ConfirmCandidateRequest = {}) {
    return request<Transaction>(`/statements/${id}/candidates/${candidateId}/confirm`, {
      method: 'POST',
      body: JSON.stringify(edits),
    });
  },

  dismissCandidate(id: string, candidateId: string) {
    return request<void>(`/statements/${id}/candidates/${candidateId}/dismiss`, { method: 'POST' });
  },
};

export const balances = {
  get() {
    return request<Balances>('/balances');
  },
};

export const reimbursements = {
  list(limit = 200) {
    return request<Transaction[]>(`/reimbursements${qs({ limit })}`);
  },
  summary(limit = 200) {
    return request<{ summaryText: string }>(`/reimbursements/summary${qs({ limit })}`);
  },
};
