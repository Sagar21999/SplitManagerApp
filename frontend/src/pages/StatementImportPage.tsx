import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useIssuerProfiles, useUploadStatement } from '../hooks/queries';

/**
 * Upload a card statement (BRD FR17). CSV today; the file input already accepts PDF so
 * the Phase 5 parser needs no UI change.
 *
 * <p>The file is never stored: the API deletes it as soon as it has extracted the rows.
 * Saying so here matters, because handing over a full statement is a bigger ask than
 * handing over a receipt photo.
 */
export function StatementImportPage() {
  const navigate = useNavigate();
  const upload = useUploadStatement();
  const { data: profiles = [] } = useIssuerProfiles();

  const [file, setFile] = useState<File | null>(null);
  const [issuerProfile, setIssuerProfile] = useState('');

  const submit = () => {
    if (!file) return;
    upload.mutate(
      { file, issuerProfile: issuerProfile || undefined },
      { onSuccess: (imported) => navigate(`/statements/${imported.importId}/review`) },
    );
  };

  return (
    <div className="page">
      <h1>Import a statement</h1>
      <p className="subtle">
        Upload a CSV export from your card issuer. Charges you might want to split or claim
        are pulled out for review — nothing is added to the ledger until you say so.
      </p>

      <section className="card">
        <div className="field">
          <label htmlFor="statement-file">Statement file</label>
          <input
            id="statement-file"
            type="file"
            accept=".csv,.pdf,text/csv,application/pdf"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </div>

        <div className="field">
          <label htmlFor="issuer-profile">Card issuer</label>
          <select
            id="issuer-profile"
            value={issuerProfile}
            onChange={(e) => setIssuerProfile(e.target.value)}
          >
            <option value="">Detect automatically</option>
            {profiles.map((profile) => (
              <option key={profile.id} value={profile.id}>
                {profile.label}
              </option>
            ))}
          </select>
          <p className="hint">
            Automatic detection reads the column headers. Pick your issuer if the amounts
            come out backwards — the sign convention is the one thing headers cannot say.
          </p>
        </div>
      </section>

      {upload.error && <p className="status-message error">{(upload.error as Error).message}</p>}

      <button
        type="button"
        className="primary-button"
        disabled={!file || upload.isPending}
        onClick={submit}
      >
        {upload.isPending ? 'Reading statement…' : 'Upload & review'}
      </button>

      <p className="hint">
        The file itself is deleted as soon as it has been read. Only the charges you confirm
        are kept.
      </p>
    </div>
  );
}
