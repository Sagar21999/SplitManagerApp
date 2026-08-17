import type { Participant } from '../types';

interface SplitSummaryProps {
  participants: Participant[];
  shares: Record<string, number>;
}

export function SplitSummary({ participants, shares }: SplitSummaryProps) {
  return (
    <section className="card">
      <h2>Summary</h2>
      <ul className="summary-list">
        {participants.map((p) => (
          <li key={p.id} className="summary-row">
            <span>{p.name}</span>
            <span>${(shares[p.id] ?? 0).toFixed(2)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
