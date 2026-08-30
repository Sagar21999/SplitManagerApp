import { useState } from 'react';
import { SELF } from '../types';
import type { Person } from '../types';

interface PersonPickerProps {
  people: Person[];
  selectedIds: string[];
  onSelectionChange: (ids: string[]) => void;
  /** Names typed here are created by the API on finalize (BRD FR13). */
  pendingNames: string[];
  onPendingNamesChange: (names: string[]) => void;
}

/**
 * Who was involved. The saved directory is the primary affordance - the whole point of
 * FR13 is that a regular participant is one tap rather than retyping a name every time.
 * Free text is still there for someone new, and that name joins the directory on finalize.
 */
export function PersonPicker({
  people,
  selectedIds,
  onSelectionChange,
  pendingNames,
  onPendingNamesChange,
}: PersonPickerProps) {
  const [draft, setDraft] = useState('');

  const toggle = (id: string) =>
    onSelectionChange(
      selectedIds.includes(id) ? selectedIds.filter((x) => x !== id) : [...selectedIds, id],
    );

  const addName = () => {
    const name = draft.trim();
    if (!name) return;
    // Selecting an existing person by typing their name should not create a duplicate.
    const existing = people.find((p) => p.displayName.toLowerCase() === name.toLowerCase());
    if (existing) {
      if (!selectedIds.includes(existing.personId)) toggle(existing.personId);
    } else if (!pendingNames.some((n) => n.toLowerCase() === name.toLowerCase())) {
      onPendingNamesChange([...pendingNames, name]);
    }
    setDraft('');
  };

  return (
    <section className="card">
      <h2>Who was involved?</h2>

      <div className="chip-row">
        <button
          type="button"
          className={`chip ${selectedIds.includes(SELF) ? 'chip-on' : ''}`}
          onClick={() => toggle(SELF)}
        >
          You
        </button>
        {people.map((p) => (
          <button
            key={p.personId}
            type="button"
            className={`chip ${selectedIds.includes(p.personId) ? 'chip-on' : ''}`}
            onClick={() => toggle(p.personId)}
          >
            {p.displayName}
          </button>
        ))}
        {pendingNames.map((name) => (
          <button
            key={`pending-${name}`}
            type="button"
            className="chip chip-on chip-pending"
            onClick={() => onPendingNamesChange(pendingNames.filter((n) => n !== name))}
            title="New - will be saved when you finalize"
          >
            {name} +
          </button>
        ))}
      </div>

      <div className="inline-add">
        <input
          type="text"
          value={draft}
          placeholder="Add someone new"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              addName();
            }
          }}
        />
        <button type="button" className="add-button" onClick={addName}>
          Add
        </button>
      </div>
    </section>
  );
}
