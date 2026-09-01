import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { balances, people, reimbursements, statements, transactions } from '../apiClient';
import type {
  ConfirmCandidateRequest,
  CreateTransactionRequest,
  FinalizeRequest,
  TransactionStatus,
  TransactionType,
  UpdateTransactionRequest,
} from '../types';

/**
 * Server state lives in TanStack Query rather than component state.
 *
 * v1 kept everything local, which worked when the app was one page driven by one session.
 * The ledger changes that: a status change on the detail page has to move the balance bar
 * and the list row, and those live on other screens. Query keys plus invalidation express
 * that far more simply than lifting state.
 */

export const keys = {
  transactions: (filters?: Record<string, unknown>) => ['transactions', filters ?? {}] as const,
  transaction: (id: string) => ['transaction', id] as const,
  people: () => ['people'] as const,
  balances: () => ['balances'] as const,
  reimbursements: () => ['reimbursements'] as const,
  reimbursementSummary: () => ['reimbursements', 'summary'] as const,
  statementImport: (id: string) => ['statementImport', id] as const,
  issuerProfiles: () => ['issuerProfiles'] as const,
};

export function useTransactions(filters: {
  status?: TransactionStatus;
  type?: TransactionType;
  limit?: number;
}) {
  return useQuery({
    queryKey: keys.transactions(filters),
    queryFn: () => transactions.list(filters),
  });
}

export function useTransaction(id: string | undefined) {
  return useQuery({
    queryKey: keys.transaction(id ?? ''),
    queryFn: () => transactions.get(id as string),
    enabled: Boolean(id),
  });
}

export function usePeople() {
  return useQuery({ queryKey: keys.people(), queryFn: people.list });
}

export function useBalances() {
  return useQuery({ queryKey: keys.balances(), queryFn: balances.get });
}

export function useReimbursements() {
  return useQuery({ queryKey: keys.reimbursements(), queryFn: () => reimbursements.list() });
}

/** Everything a write can move. Cheaper to over-invalidate than to reason about overlap. */
function invalidateLedger(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ['transactions'] });
  void qc.invalidateQueries({ queryKey: ['balances'] });
  void qc.invalidateQueries({ queryKey: ['reimbursements'] });
}

export function useCreateFromReceipt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (image: File) => transactions.createFromReceipt(image),
    onSuccess: () => invalidateLedger(qc),
  });
}

export function useCreateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTransactionRequest) => transactions.create(body),
    onSuccess: () => invalidateLedger(qc),
  });
}

export function useUpdateTransaction(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateTransactionRequest) => transactions.update(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.transaction(id) });
      invalidateLedger(qc);
    },
  });
}

export function useFinalizeSplit(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: FinalizeRequest) => transactions.finalize(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.transaction(id) });
      // Finalizing can create people (FR13), so the directory is stale too.
      void qc.invalidateQueries({ queryKey: keys.people() });
      invalidateLedger(qc);
    },
  });
}

export function useUpdateStatus(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (status: TransactionStatus) => transactions.updateStatus(id, status),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.transaction(id) });
      invalidateLedger(qc);
    },
  });
}

export function useDeleteTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => transactions.remove(id),
    onSuccess: () => invalidateLedger(qc),
  });
}

export function useCreatePerson() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (displayName: string) => people.create(displayName),
    onSuccess: () => void qc.invalidateQueries({ queryKey: keys.people() }),
  });
}

export function useRenamePerson() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, displayName }: { id: string; displayName: string }) =>
      people.rename(id, displayName),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.people() });
      // Names are denormalized into balances and summaries.
      invalidateLedger(qc);
    },
  });
}

export function useArchivePerson() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => people.archive(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: keys.people() }),
  });
}

export function useIssuerProfiles() {
  return useQuery({
    queryKey: keys.issuerProfiles(),
    queryFn: statements.issuerProfiles,
    // Reference data shipped with the API - it only changes on deploy.
    staleTime: Infinity,
  });
}

export function useStatementImport(id: string | undefined) {
  return useQuery({
    queryKey: keys.statementImport(id ?? ''),
    queryFn: () => statements.get(id as string),
    enabled: Boolean(id),
  });
}

export function useUploadStatement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, issuerProfile }: { file: File; issuerProfile?: string }) =>
      statements.upload(file, issuerProfile),
    // Seed the cache so the review page renders immediately rather than refetching
    // everything the upload response already contained.
    onSuccess: (imported) => qc.setQueryData(keys.statementImport(imported.importId), imported),
  });
}

export function useConfirmCandidate(importId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      candidateId,
      edits,
    }: {
      candidateId: string;
      edits?: ConfirmCandidateRequest;
    }) => statements.confirmCandidate(importId, candidateId, edits ?? {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.statementImport(importId) });
      invalidateLedger(qc);
    },
  });
}

export function useDismissCandidate(importId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (candidateId: string) => statements.dismissCandidate(importId, candidateId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: keys.statementImport(importId) }),
  });
}
