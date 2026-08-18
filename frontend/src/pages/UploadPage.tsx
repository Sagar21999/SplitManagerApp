import { useState } from 'react';
import { parseReceipt } from '../apiClient';

export function UploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!file) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await parseReceipt(file);
      window.location.href = `/split/${response.sessionId}`;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to parse receipt');
      setSubmitting(false);
    }
  };

  return (
    <div className="split-page">
      <section className="card">
        <h2>Split a receipt</h2>
        <p className="hint">Take a photo of the receipt, or pick one from your library.</p>
        <input
          type="file"
          accept="image/*"
          capture="environment"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        {file && <p className="hint">{file.name}</p>}
        {error && <p className="status-message error">{error}</p>}
        <button
          type="button"
          className="confirm-button"
          disabled={!file || submitting}
          onClick={handleSubmit}
        >
          {submitting ? 'Uploading…' : 'Upload & Split'}
        </button>
      </section>
    </div>
  );
}
