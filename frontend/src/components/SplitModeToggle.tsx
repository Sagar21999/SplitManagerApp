import type { SplitMode } from '../types';

interface SplitModeToggleProps {
  mode: SplitMode;
  onModeChange: (mode: SplitMode) => void;
}

export function SplitModeToggle({ mode, onModeChange }: SplitModeToggleProps) {
  return (
    <div className="split-mode-toggle">
      <button
        type="button"
        className={mode === 'EQUAL' ? 'active' : ''}
        onClick={() => onModeChange('EQUAL')}
      >
        Equal
      </button>
      <button
        type="button"
        className={mode === 'BY_ITEM' ? 'active' : ''}
        onClick={() => onModeChange('BY_ITEM')}
      >
        By Item
      </button>
    </div>
  );
}
