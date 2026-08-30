import type { Balances } from '../types';

/** BRD FR12 - the answer to "who owes me what right now?". */
export function BalanceSummaryBar({ data }: { data: Balances | undefined }) {
  if (!data || data.balances.length === 0) {
    return (
      <section className="balance-bar">
        <p className="hint">Nothing outstanding.</p>
      </section>
    );
  }

  return (
    <section className="balance-bar">
      <div className="balance-total">
        <span>Owed to you</span>
        <strong>${data.totalOwedToUser.toFixed(2)}</strong>
      </div>
      <div className="chip-row">
        {data.balances.map((b) => (
          <span key={b.personId} className={`balance-chip ${b.netAmount < 0 ? 'balance-neg' : ''}`}>
            {b.displayName}{' '}
            <strong>
              {b.netAmount < 0 ? '-' : ''}${Math.abs(b.netAmount).toFixed(2)}
            </strong>
          </span>
        ))}
      </div>
    </section>
  );
}
