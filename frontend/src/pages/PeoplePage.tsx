import { useState } from 'react';
import { useArchivePerson, useCreatePerson, usePeople, useRenamePerson } from '../hooks/queries';

/** BRD FR13. Mostly self-maintaining - names arrive here by being used on a transaction. */
export function PeoplePage() {
  const { data: people = [], isLoading, error } = usePeople();
  const createPerson = useCreatePerson();
  const renamePerson = useRenamePerson();
  const archivePerson = useArchivePerson();

  const [draft, setDraft] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');

  return (
    <div className="page">
      <h1>People</h1>
      <p className="subtle">Anyone you split with. Names are saved automatically as you use them.</p>

      <section className="card">
        <div className="inline-add">
          <input
            type="text"
            value={draft}
            placeholder="Add someone"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && draft.trim()) {
                createPerson.mutate(draft.trim());
                setDraft('');
              }
            }}
          />
          <button
            type="button"
            className="add-button"
            disabled={!draft.trim() || createPerson.isPending}
            onClick={() => {
              createPerson.mutate(draft.trim());
              setDraft('');
            }}
          >
            Add
          </button>
        </div>
      </section>

      {isLoading && <p className="status-message">Loading…</p>}
      {error && <p className="status-message error">{(error as Error).message}</p>}

      <ul className="person-list">
        {people.map((p) => (
          <li key={p.personId} className="person-row">
            {editingId === p.personId ? (
              <>
                <input
                  type="text"
                  value={editingName}
                  onChange={(e) => setEditingName(e.target.value)}
                  autoFocus
                />
                <button
                  type="button"
                  className="add-button"
                  onClick={() => {
                    renamePerson.mutate({ id: p.personId, displayName: editingName });
                    setEditingId(null);
                  }}
                >
                  Save
                </button>
                <button type="button" className="icon-button" onClick={() => setEditingId(null)}>
                  &times;
                </button>
              </>
            ) : (
              <>
                <span className="person-name">{p.displayName}</span>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    setEditingId(p.personId);
                    setEditingName(p.displayName);
                  }}
                >
                  Rename
                </button>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => archivePerson.mutate(p.personId)}
                >
                  Remove
                </button>
              </>
            )}
          </li>
        ))}
      </ul>

      <p className="hint">
        Removing someone hides them from the picker. Past transactions keep their name.
      </p>
    </div>
  );
}
