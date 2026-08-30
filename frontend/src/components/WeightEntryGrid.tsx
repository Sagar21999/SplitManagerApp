import { SELF } from '../types';
import type { Person, SplitMode } from '../types';

interface WeightEntryGridProps {
  mode: Extract<SplitMode, 'SHARES' | 'PERCENTAGE' | 'EXACT'>;
  people: Person[];
  participantIds: string[];
  weights: Record<string, number>;
  total: number;
  onWeightsChange: (weights: Record<string, number>) => void;
}

const HELP: Record<string, string> = {
  SHARES: 'Whole numbers. Someone on 2 pays twice what someone on 1 pays.',
  PERCENTAGE: 'Must add up to 100.',
  EXACT: 'Type each amount directly. Must add up to the total.',
};

export function WeightEntryGrid({
  mode,
  people,
  participantIds,
  weights,
  total,
  onWeightsChange,
}: WeightEntryGridProps) {
  const label = (id: string) =>
    id === SELF ? 'You' : (people.find((p) => p.personId === id)?.displayName ?? id);

  const sum = participantIds.reduce((acc, id) => acc + (Number(weights[id]) || 0), 0);
  const target = mode === 'PERCENTAGE' ? 100 : mode === 'EXACT' ? total : null;
  const offTarget = target !== null && Math.abs(sum - target) > (mode === 'EXACT' ? 0.005 : 0.001);

  return (
    <section className="card">
      <h2>{mode === 'SHARES' ? 'Shares' : mode === 'PERCENTAGE' ? 'Percentages' : 'Amounts'}</h2>
      <p className="hint">{HELP[mode]}</p>
      {participantIds.map((id) => (
        <label key={id} className="weight-row">
          <span>{label(id)}</span>
          <input
            type="number"
            step={mode === 'SHARES' ? '1' : '0.01'}
            min="0"
            value={weights[id] ?? ''}
            onChange={(e) => onWeightsChange({ ...weights, [id]: Number(e.target.value) })}
          />
        </label>
      ))}
      {target !== null && (
        <p className={`running-total ${offTarget ? 'running-total-bad' : ''}`}>
          {sum.toFixed(2)} / {target.toFixed(2)}
        </p>
      )}
    </section>
  );
}
