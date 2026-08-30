import { SELF } from '../types';
import type { Person } from '../types';

interface PayerSelectorProps {
  people: Person[];
  participantIds: string[];
  pendingNames: string[];
  payerId: string;
  onPayerChange: (payerId: string) => void;
}

/**
 * Who fronted the bill. New in v2 - without it the split is a breakdown rather than a
 * debt, and the balances have no direction. The payer also absorbs the rounding remainder.
 */
export function PayerSelector({
  people,
  participantIds,
  pendingNames,
  payerId,
  onPayerChange,
}: PayerSelectorProps) {
  const named = people.filter((p) => participantIds.includes(p.personId));

  return (
    <section className="card">
      <h2>Who paid?</h2>
      <div className="chip-row">
        <button
          type="button"
          className={`chip ${payerId === SELF ? 'chip-on' : ''}`}
          onClick={() => onPayerChange(SELF)}
        >
          You
        </button>
        {named.map((p) => (
          <button
            key={p.personId}
            type="button"
            className={`chip ${payerId === p.personId ? 'chip-on' : ''}`}
            onClick={() => onPayerChange(p.personId)}
          >
            {p.displayName}
          </button>
        ))}
      </div>
      {pendingNames.length > 0 && (
        <p className="hint">
          Newly added people can be picked as the payer after you finalize once.
        </p>
      )}
    </section>
  );
}
