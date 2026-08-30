const TIP_PRESETS = [0.18, 0.2, 0.25];

interface TipEntrySectionProps {
  subtotal: number;
  tip: number;
  onTipChange: (tip: number) => void;
}

export function TipEntrySection({ subtotal, tip, onTipChange }: TipEntrySectionProps) {
  return (
    <section className="card">
      <h2>Tip</h2>
      <div className="tip-presets">
        {TIP_PRESETS.map((pct) => (
          <button
            key={pct}
            type="button"
            className={`preset-button ${Math.abs(tip - subtotal * pct) < 0.005 ? 'active' : ''}`}
            onClick={() => onTipChange(Math.round(subtotal * pct * 100) / 100)}
          >
            {Math.round(pct * 100)}%
          </button>
        ))}
        <button
          type="button"
          className={`preset-button ${tip === 0 ? 'active' : ''}`}
          onClick={() => onTipChange(0)}
        >
          None
        </button>
      </div>
      <label className="field">
        Custom amount
        <input
          type="number"
          step="0.01"
          min="0"
          value={tip}
          onChange={(e) => onTipChange(Number(e.target.value))}
        />
      </label>
    </section>
  );
}
