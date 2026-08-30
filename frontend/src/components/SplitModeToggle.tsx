import { SPLIT_MODE_LABELS } from '../types';
import type { SplitMode } from '../types';

const MODES: SplitMode[] = ['EQUAL', 'SHARES', 'PERCENTAGE', 'EXACT', 'BY_ITEM'];

interface SplitModeToggleProps {
  mode: SplitMode;
  onModeChange: (mode: SplitMode) => void;
  /** By-item needs line items; hide it when there are none rather than fail on submit. */
  itemsAvailable: boolean;
}

export function SplitModeToggle({ mode, onModeChange, itemsAvailable }: SplitModeToggleProps) {
  const available = MODES.filter((m) => m !== 'BY_ITEM' || itemsAvailable);
  return (
    <section className="card">
      <h2>How to split</h2>
      <div className="chip-row">
        {available.map((m) => (
          <button
            key={m}
            type="button"
            className={`chip ${mode === m ? 'chip-on' : ''}`}
            onClick={() => onModeChange(m)}
          >
            {SPLIT_MODE_LABELS[m]}
          </button>
        ))}
      </div>
    </section>
  );
}
