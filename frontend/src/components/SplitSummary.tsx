import { SELF } from '../types';
import type { Person } from '../types';

interface SplitSummaryProps {
  people: Person[];
  shares: Record<string, number>;
  payerId: string;
  error: string | null;
}

/** Live preview. The server recomputes on finalize and remains the source of truth. */
export function SplitSummary({ people, shares, payerId, error }: SplitSummaryProps) {
  const label = (id: string) =>
    id === SELF ? 'You' : (people.find((p) => p.personId === id)?.displayName ?? id);

  const entries = Object.entries(shares);

  return (
    <section className="card">
      <h2>Preview</h2>
      {error ? (
        <p className="status-message error">{error}</p>
      ) : entries.length === 0 ? (
        <p className="hint">Pick participants to see the split.</p>
      ) : (
        <ul className="summary-list">
          {entries.map(([id, amount]) => (
            <li key={id} className={id === payerId ? 'summary-payer' : ''}>
              <span>
                {label(id)}
                {id === payerId ? ' (paid)' : ''}
              </span>
              <span>${amount.toFixed(2)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
