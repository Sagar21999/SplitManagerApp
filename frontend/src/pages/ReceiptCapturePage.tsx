import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateFromReceipt } from '../hooks/queries';

/** Absorbs v1's UploadPage. capture="environment" opens the camera on a phone. */
export function ReceiptCapturePage() {
  const navigate = useNavigate();
  const create = useCreateFromReceipt();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);

  const submit = () => {
    if (!file) return;
    create.mutate(file, {
      onSuccess: (transaction) => navigate(`/split/${transaction.transactionId}`),
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

      <button
        type="button"
        className="primary-button"
        disabled={!file || create.isPending}
        onClick={submit}
      >
        {create.isPending ? 'Reading receipt…' : 'Upload & split'}
      </button>

      <p className="hint">
        Not a receipt? You can also add a transaction by hand from the ledger later.
      </p>
    </div>
  );
}
