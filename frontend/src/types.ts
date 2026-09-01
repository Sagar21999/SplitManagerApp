export interface LineItem {
  id: string;
  name: string;
  price: number;
}

export type TransactionType = 'SPLIT' | 'REIMBURSEMENT';

export type TransactionStatus = 'DRAFT' | 'OPEN' | 'EXTERNALLY_ADDED' | 'SETTLED';

export type SplitMode = 'EQUAL' | 'SHARES' | 'PERCENTAGE' | 'EXACT' | 'BY_ITEM';

/** The ledger owner's own id in participant lists. Mirrors Participants.SELF in the API. */
export const SELF = 'SELF';

export interface SplitDefinition {
  mode: SplitMode;
  payerId: string;
  participantIds: string[];
  /** SHARES / PERCENTAGE / EXACT only. */
  weights?: Record<string, number> | null;
  /** BY_ITEM only: lineItemId -> participantIds. */
  itemAssignments?: Record<string, string[]> | null;
}

export interface FinalizedSplit {
  mode: SplitMode;
  participantShares: Record<string, number>;
  payerId: string;
  computedAt: string;
}

export interface Transaction {
  transactionId: string;
  type: TransactionType;
  status: TransactionStatus;
  merchant: string | null;
  transactionDate: string;
  subtotal: number | null;
  tax: number | null;
  tip: number | null;
  total: number;
  items: LineItem[];
  splitDefinition: SplitDefinition | null;
  finalizedSplit: FinalizedSplit | null;
  sourceStatementImportId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SplitSummary {
  amountOwedByParticipant: Record<string, number>;
  shareText: string;
}

export interface TransactionDetail {
  transaction: Transaction;
  summary: SplitSummary;
}

export interface Person {
  personId: string;
  displayName: string;
  lastUsedAt: string | null;
}

export interface PersonBalance {
  personId: string;
  displayName: string;
  /** Positive means they owe you. */
  netAmount: number;
  openTransactionCount: number;
}

export interface Balances {
  balances: PersonBalance[];
  totalOwedToUser: number;
}

export interface FinalizeRequest {
  split: SplitDefinition;
  tip?: number | null;
  /** Free-text names not yet in the directory; the API creates and substitutes ids. */
  newPersonNames?: string[];
}

export interface CreateTransactionRequest {
  type: TransactionType;
  merchant: string;
  transactionDate: string;
  total: number;
}

export interface UpdateTransactionRequest {
  merchant?: string | null;
  transactionDate?: string | null;
  items?: LineItem[] | null;
  subtotal?: number | null;
  tax?: number | null;
  tip?: number | null;
  total?: number | null;
  notes?: string | null;
}

export type CandidateClassification = 'LIKELY_SPLIT' | 'LIKELY_REIMBURSEMENT' | 'UNLIKELY';

export type CandidateStatus = 'PENDING' | 'CONFIRMED' | 'DISMISSED';

export type StatementImportStatus = 'PARSING' | 'READY' | 'FAILED';

/** An existing transaction that may already record the same charge (BRD FR19). */
export interface DuplicateMatch {
  transactionId: string;
  /** MERCHANT_DATE_AMOUNT is a strong match; DATE_AMOUNT is weak but still shown. */
  matchStrategy: 'MERCHANT_DATE_AMOUNT' | 'DATE_AMOUNT';
  score: number;
  merchant: string | null;
  transactionDate: string;
}

export interface StatementCandidate {
  candidateId: string;
  importId: string;
  sequence: number;
  date: string;
  rawDescription: string;
  normalizedMerchant: string;
  amount: number;
  classification: CandidateClassification;
  classificationConfidence: number;
  duplicateMatches: DuplicateMatch[];
  status: CandidateStatus;
  resultingTransactionId: string | null;
}

export interface StatementImport {
  importId: string;
  fileName: string | null;
  issuerProfile: string | null;
  uploadedAt: string;
  rowCount: number;
  candidateCount: number;
  status: StatementImportStatus;
  /** Also set on a READY import when some rows were dropped - a partial-parse warning. */
  failureReason: string | null;
  candidates: StatementCandidate[];
}

export interface IssuerProfile {
  id: string;
  label: string;
}

export interface ConfirmCandidateRequest {
  type?: TransactionType | null;
  merchant?: string | null;
  date?: string | null;
  amount?: number | null;
}

/** What the receipt upload returns: the draft, plus anything that looks like it. */
export interface ReceiptDraft {
  transaction: Transaction;
  duplicateWarnings: DuplicateMatch[];
}

export const CLASSIFICATION_LABELS: Record<CandidateClassification, string> = {
  LIKELY_SPLIT: 'Likely split',
  LIKELY_REIMBURSEMENT: 'Likely claim',
  UNLIKELY: 'Unclassified',
};

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  DRAFT: 'Draft',
  OPEN: 'Open',
  EXTERNALLY_ADDED: 'Externally added',
  SETTLED: 'Settled',
};

export const SPLIT_MODE_LABELS: Record<SplitMode, string> = {
  EQUAL: 'Equally',
  SHARES: 'Shares',
  PERCENTAGE: 'Percent',
  EXACT: 'Exact',
  BY_ITEM: 'By item',
};
