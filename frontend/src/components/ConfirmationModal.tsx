import { useState } from 'react';

interface ConfirmationModalProps {
  shareText: string;
  onClose: () => void;
}

export function ConfirmationModal({ shareText, onClose }: ConfirmationModalProps) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    await navigator.clipboard.writeText(shareText);
    setCopied(true);
  };

  return (
    <div className="modal-backdrop">
      <div className="modal">
        <h2>Split confirmed</h2>
        <pre className="share-text">{shareText}</pre>
        <div className="modal-actions">
          <button type="button" className="add-button" onClick={copy}>
            {copied ? 'Copied!' : 'Copy'}
          </button>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
