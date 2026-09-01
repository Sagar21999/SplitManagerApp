import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { DuplicateWarningBanner } from '../components/DuplicateWarningBanner';
import { useCreateFromReceipt } from '../hooks/queries';
import type { DuplicateMatch } from '../types';

/** Absorbs v1's UploadPage. capture="environment" opens the camera on a phone. */
export function ReceiptCapturePage() {
  const navigate = useNavigate();
  const create = useCreateFromReceipt();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [draft, setDraft] = useState<{ id: string; duplicates: DuplicateMatch[] } | null>(null);

  const submit = () => {
    if (!file) return;
    create.mutate(file, {
      onSuccess: ({ transaction, duplicateWarnings }) => {
        // A clean upload goes straight on to the split editor. When something in the
        // ledger looks like the same charge - typically already imported from a
        // statement - stop here and let the user look before adding a second copy.
        if (duplicateWarnings.length === 0) {
          navigate(`/split/${transaction.transactionId}`);
          return;
        }
        setDraft({ id: transaction.transactionId, duplicates: duplicateWarnings });
      },
    });
  };

  return (
    <div className="page">
      <h1>Add a receipt</h1>
      <p className="subtle">Take a photo of the receipt, or pick one from your library.</p>

      <section className="card">
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        {file && <p className="hint">{file.name}</p>}
      </section>

      {create.error && <p className="status-message error">{(create.error as Error).message}</p>}

      {draft ? (
        <>
          <DuplicateWarningBanner matches={draft.duplicates} />
          <button
            type="button"
            className="primary-button"
            onClick={() => navigate(`/split/${draft.id}`)}
          >
            Split it anyway
          </button>
        </>
      ) : (
        <button
          type="button"
          className="primary-button"
          disabled={!file || create.isPending}
          onClick={submit}
        >
          {create.isPending ? 'Reading receipt…' : 'Upload & split'}
        </button>
      )}

      <p className="hint">
        Not a receipt? <Link to="/transactions/new">Add it by hand</Link> instead.
      </p>
    </div>
  );
}
