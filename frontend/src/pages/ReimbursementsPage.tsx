import { useState } from 'react';
import { Link } from 'react-router-dom';
import { reimbursements } from '../apiClient';
import { ShareTextPanel } from '../components/ShareTextPanel';
import { useReimbursements } from '../hooks/queries';
import { STATUS_LABELS } from '../types';

/** BRD FR21-23. A filtered view over the same ledger, plus a claim-ready export. */
export function ReimbursementsPage() {
  const { data = [], isLoading, error } = useReimbursements();
  const [summary, setSummary] = useState<string | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  return (
    <div className="page">
      <h1>Reimbursements</h1>
      <p className="subtle">Work expenses to claim — Uber, transit, anything your employer owes you.</p>

      {isLoading && <p className="status-message">Loading…</p>}
      {error && <p className="status-message error">{(error as Error).message}</p>}
      {!isLoading && data.length === 0 && (
        <p className="status-message">
          Nothing to claim. Import a statement and confirm the travel charges to fill this in.
        </p>
      )}

      <ul className="txn-list">
        {data.map((t) => (
          <li key={t.transactionId}>
            <Link className="txn-row" to={`/transactions/${t.transactionId}`}>
              <div className="txn-main">
                <span className="txn-merchant">{t.merchant || 'Untitled'}</span>
                <span className="txn-date">{t.transactionDate}</span>
              </div>
              <div className="txn-side">
                <span className="txn-total">${t.total.toFixed(2)}</span>
                <span className="status-pill" data-status={t.status}>
                  {STATUS_LABELS[t.status]}
                </span>
              </div>
            </Link>
          </li>
        ))}
      </ul>

      {data.length > 0 && (
        <button
          type="button"
          className="add-button"
          onClick={() => {
            setSummaryError(null);
            reimbursements
              .summary()
              .then((r) => setSummary(r.summaryText))
              .catch((e: Error) => setSummaryError(e.message));
          }}
        >
          Build claim summary
        </button>
      )}

      {summaryError && <p className="status-message error">{summaryError}</p>}
      {summary && <ShareTextPanel shareText={summary} />}
    </div>
  );
}
