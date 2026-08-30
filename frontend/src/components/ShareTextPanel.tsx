import { useState } from 'react';

/** The manual Splitwise handoff (BRD "Relationship to Splitwise"). */
export function ShareTextPanel({ shareText }: { shareText: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(shareText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  if (!shareText) return null;

  return (
    <section className="card">
      <h2>Share</h2>
      <pre className="share-text">{shareText}</pre>
      <button type="button" className="add-button" onClick={() => void copy()}>
        {copied ? 'Copied' : 'Copy'}
      </button>
    </section>
  );
}
