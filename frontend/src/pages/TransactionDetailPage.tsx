import { Link, useNavigate, useParams } from 'react-router-dom';
import { ShareTextPanel } from '../components/ShareTextPanel';
import { StatusActionBar } from '../components/StatusActionBar';
import { useDeleteTransaction, usePeople, useTransaction, useUpdateStatus } from '../hooks/queries';
import { SELF, STATUS_LABELS } from '../types';

export function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, isLoading, error } = useTransaction(id);
  const { data: people = [] } = usePeople();
  const updateStatus = useUpdateStatus(id ?? '');
  const remove = useDeleteTransaction();

  if (isLoading) return <p className="status-message">Loading…</p>;
  if (error) return <p className="status-message error">{(error as Error).message}</p>;
  if (!data) return <p className="status-message">Not found.</p>;

  const t = data.transaction;
  const label = (pid: string) =>
    pid === SELF ? 'You' : (people.find((p) => p.personId === pid)?.displayName ?? pid);

  return (
    <div className="page">
      <Link className="back-link" to="/">
        &larr; Ledger
      </Link>

      <h1>{t.merchant || 'Untitled'}</h1>
      <p className="subtle">
        {t.transactionDate} · ${t.total.toFixed(2)} · {STATUS_LABELS[t.status]}
      </p>

      {t.status === 'DRAFT' && (
        <p className="status-message">
          This one is still a draft. <Link to={`/split/${t.transactionId}`}>Finish the split</Link>.
        </p>
      )}

      {t.items.length > 0 && (
        <section className="card">
          <h2>Items</h2>
          <ul className="summary-list">
            {t.items.map((i) => (
              <li key={i.id}>
                <span>{i.name || 'Item'}</span>
                <span>${i.price.toFixed(2)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {t.finalizedSplit && (
        <section className="card">
          <h2>Split</h2>
          <ul className="summary-list">
            {Object.entries(t.finalizedSplit.participantShares).map(([pid, amount]) => (
              <li key={pid} className={pid === t.finalizedSplit?.payerId ? 'summary-payer' : ''}>
                <span>
                  {label(pid)}
                  {pid === t.finalizedSplit?.payerId ? ' (paid)' : ''}
                </span>
                <span>${amount.toFixed(2)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <ShareTextPanel shareText={data.summary.shareText} />

      {t.status !== 'DRAFT' && (
        <StatusActionBar
          status={t.status}
          busy={updateStatus.isPending}
          onStatusChange={(next) => updateStatus.mutate(next)}
        />
      )}

      {updateStatus.error && (
        <p className="status-message error">{(updateStatus.error as Error).message}</p>
      )}

      <button
        type="button"
        className="danger-button"
        disabled={remove.isPending}
        onClick={() => {
          // No window.confirm: a modal dialog blocks the page, and the ledger is
          // recoverable enough that a two-step button is the better trade.
          remove.mutate(t.transactionId, { onSuccess: () => navigate('/') });
        }}
      >
        {remove.isPending ? 'Deleting…' : 'Delete transaction'}
      </button>
    </div>
  );
}
