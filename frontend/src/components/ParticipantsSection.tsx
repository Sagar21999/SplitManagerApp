import { useState } from 'react';
import type { Participant } from '../types';

interface ParticipantNameEntryProps {
  onAdd: (name: string) => void;
}

function ParticipantNameEntry({ onAdd }: ParticipantNameEntryProps) {
  const [name, setName] = useState('');

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    onAdd(trimmed);
    setName('');
  };

  return (
    <div className="participant-entry">
      <input
        type="text"
        placeholder="Add participant"
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') submit();
        }}
      />
      <button type="button" className="add-button" onClick={submit}>
        Add
      </button>
    </div>
  );
}

interface ParticipantsSectionProps {
  participants: Participant[];
  payerId: string | null;
  onParticipantsChange: (participants: Participant[]) => void;
  onPayerChange: (payerId: string) => void;
}

export function ParticipantsSection({
  participants,
  payerId,
  onParticipantsChange,
  onPayerChange,
}: ParticipantsSectionProps) {
  const addParticipant = (name: string) => {
    const participant: Participant = { id: crypto.randomUUID(), name };
    const next = [...participants, participant];
    onParticipantsChange(next);
    if (!payerId) onPayerChange(participant.id);
  };

  const removeParticipant = (id: string) => {
    onParticipantsChange(participants.filter((p) => p.id !== id));
    if (payerId === id) {
      const remaining = participants.filter((p) => p.id !== id);
      onPayerChange(remaining[0]?.id ?? '');
    }
  };

  return (
    <section className="card">
      <h2>Participants</h2>
      <ul className="participant-list">
        {participants.map((p) => (
          <li key={p.id} className="participant-row">
            <label className="payer-radio">
              <input
                type="radio"
                name="payer"
                checked={payerId === p.id}
                onChange={() => onPayerChange(p.id)}
              />
              paid
            </label>
            <span className="participant-name">{p.name}</span>
            <button
              type="button"
              className="icon-button"
              onClick={() => removeParticipant(p.id)}
              aria-label={`Remove ${p.name}`}
            >
              &times;
            </button>
          </li>
        ))}
      </ul>
      <ParticipantNameEntry onAdd={addParticipant} />
    </section>
  );
}
