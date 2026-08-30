import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BalanceSummaryBar } from '../components/BalanceSummaryBar';
import { useBalances, useTransactions } from '../hooks/queries';
import { STATUS_LABELS } from '../types';
import type { TransactionStatus } from '../types';

const FILTERS: { label: string; value: TransactionStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Externally added', value: 'EXTERNALLY_ADDED' },
  { label: 'Settled', value: 'SETTLED' },
];

export function LedgerPage() {
  const [status, setStatus] = useState<TransactionStatus | undefined>(undefined);
  const { data: transactions = [], isLoading, error } = useTransactions({ status, limit: 100 });
  const { data: balances } = useBalances();

  return (
    <div className="page">
      <h1>Ledger</h1>

      <BalanceSummaryBar data={balances} />

      <div className="chip-row filter-row">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            className={`chip ${status === f.value ? 'chip-on' : ''}`}
            onClick={() => setStatus(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading && <p className="status-message">Loading…</p>}
      {error && <p className="status-message error">{(error as Error).message}</p>}

      {!isLoading && transactions.length === 0 && (
        <p className="status-message">
          Nothing here yet. <Link to="/capture">Add a receipt</Link>.
        </p>
      )}

      <ul className="txn-list">
        {transactions.map((t) => (
          <li key={t.transactionId}>
            <Link
              className="txn-row"
              to={t.status === 'DRAFT' ? `/split/${t.transactionId}` : `/transactions/${t.transactionId}`}
            >
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
    </div>
  );
}
