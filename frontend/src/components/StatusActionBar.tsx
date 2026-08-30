import { STATUS_LABELS } from '../types';
import type { TransactionStatus } from '../types';

interface StatusActionBarProps {
  status: TransactionStatus;
  onStatusChange: (next: TransactionStatus) => void;
  busy: boolean;
}

/** Only transitions the API allows are offered - mirrors TransactionStatus in Java. */
const NEXT: Record<TransactionStatus, TransactionStatus[]> = {
  DRAFT: [],
  OPEN: ['EXTERNALLY_ADDED', 'SETTLED'],
  EXTERNALLY_ADDED: ['SETTLED', 'OPEN'],
  SETTLED: ['OPEN', 'EXTERNALLY_ADDED'],
};

const ACTION_LABELS: Partial<Record<TransactionStatus, string>> = {
  EXTERNALLY_ADDED: 'Mark as externally added',
  SETTLED: 'Mark as settled',
  OPEN: 'Reopen',
};

export function StatusActionBar({ status, onStatusChange, busy }: StatusActionBarProps) {
  const options = NEXT[status];
  return (
    <section className="card">
      <h2>Status</h2>
      <p className="status-pill" data-status={status}>
        {STATUS_LABELS[status]}
      </p>
      {status === 'EXTERNALLY_ADDED' && (
        <p className="hint">
          Added to Splitwise by hand. Still counts toward balances until it is settled.
        </p>
      )}
      <div className="chip-row">
        {options.map((next) => (
          <button
            key={next}
            type="button"
            className="add-button"
            disabled={busy}
            onClick={() => onStatusChange(next)}
          >
            {ACTION_LABELS[next] ?? STATUS_LABELS[next]}
          </button>
        ))}
      </div>
    </section>
  );
}
