import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CandidateRow } from '../components/CandidateRow';
import {
  useConfirmCandidate,
  useDismissCandidate,
  useStatementImport,
} from '../hooks/queries';
import type { ConfirmCandidateRequest } from '../types';

/**
 * Review what the import found (BRD FR18).
 *
 * <p>Unclassified rows are collapsed by default. A statement is mostly things the user
 * bought alone, and a list where the interesting rows are buried stops being read at all.
 */
export function CandidateReviewPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { data: statementImport, isLoading, error } = useStatementImport(id);
  const confirm = useConfirmCandidate(id);
  const dismiss = useDismissCandidate(id);

  const [showAll, setShowAll] = useState(false);

  const candidates = useMemo(() => statementImport?.candidates ?? [], [statementImport]);
  const likely = candidates.filter((c) => c.classification !== 'UNLIKELY');
  const unlikely = candidates.filter((c) => c.classification === 'UNLIKELY');
  const pendingCount = candidates.filter((c) => c.status === 'PENDING').length;

  const onConfirm = (candidateId: string) => (edits: ConfirmCandidateRequest) =>
    confirm.mutate(
      { candidateId, edits },
      {
        onSuccess: (transaction) => {
          // A split still needs participants and a mode, so go straight to the editor.
          // A claim is already complete and stays here so the user can keep reviewing.
          if (transaction.type === 'SPLIT') navigate(`/split/${transaction.transactionId}`);
        },
      },
    );

  const busy = confirm.isPending || dismiss.isPending;
  const shown = showAll ? [...likely, ...unlikely] : likely;

  return (
    <div className="page">
      <Link to="/statements" className="back-link">
        &larr; Import another
      </Link>
      <h1>Review charges</h1>

      {isLoading && <p className="status-message">Loading…</p>}
      {error && <p className="status-message error">{(error as Error).message}</p>}

      {statementImport && (
        <>
          <p className="subtle">
            {statementImport.fileName ?? 'Statement'} · {statementImport.rowCount} rows read ·{' '}
            {pendingCount} still to review
          </p>

          {statementImport.failureReason && (
            <p className="status-message error">{statementImport.failureReason}</p>
          )}

          {candidates.length === 0 && (
            <p className="status-message">
              No charges to review — every row was a payment, a refund, or unreadable.
            </p>
          )}

          <ul className="candidate-list">
            {shown.map((candidate) => (
              <CandidateRow
                key={candidate.candidateId}
                candidate={candidate}
                busy={busy}
                onConfirm={onConfirm(candidate.candidateId)}
                onDismiss={() => dismiss.mutate(candidate.candidateId)}
              />
            ))}
          </ul>

          {unlikely.length > 0 && (
            <button type="button" className="link-button" onClick={() => setShowAll((v) => !v)}>
              {showAll
                ? `Hide ${unlikely.length} unclassified`
                : `Show ${unlikely.length} unclassified`}
            </button>
          )}

          {(confirm.error || dismiss.error) && (
            <p className="status-message error">
              {((confirm.error ?? dismiss.error) as Error).message}
            </p>
          )}
        </>
      )}
    </div>
  );
}
