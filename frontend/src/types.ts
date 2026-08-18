export interface ReceiptItem {
  id: string;
  name: string;
  price: number;
}

export type SessionStatus = 'PARSING' | 'PARSED' | 'PARSE_FAILED' | 'FINALIZED';

export interface ParseReceiptResponse {
  sessionId: string;
  merchant: string | null;
  items: ReceiptItem[];
  tax: number;
  tip: number | null;
  total: number;
  url: string;
}

export interface SessionResponse {
  sessionId: string;
  status: SessionStatus;
  merchant: string | null;
  items: ReceiptItem[];
  tax: number;
  tip: number | null;
  total: number;
}

export type SplitMode = 'EQUAL' | 'BY_ITEM';

export interface FinalizedSplit {
  mode: SplitMode;
  participantShares: Record<string, number>;
  payerId: string;
}

export interface SubmitExpenseRequest {
  sessionId: string;
  split: FinalizedSplit;
}

export interface SplitSummaryDto {
  amountOwedByParticipant: Record<string, number>;
  shareText: string;
}

export interface FinalizeSplitResponse {
  success: boolean;
  summary: SplitSummaryDto | null;
  error: string | null;
}

/** Frontend-only concept — the API has no participant model, just free-text names. */
export interface Participant {
  id: string;
  name: string;
}
