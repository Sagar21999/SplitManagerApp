import type { Participant, ReceiptItem } from '../types';

interface ItemAssignmentRowProps {
  item: ReceiptItem;
  participants: Participant[];
  assignedIds: string[];
  onToggle: (participantId: string) => void;
}

function ItemAssignmentRow({ item, participants, assignedIds, onToggle }: ItemAssignmentRowProps) {
  return (
    <div className="assignment-row">
      <div className="assignment-item-name">
        {item.name} <span className="assignment-item-price">${item.price.toFixed(2)}</span>
      </div>
      <div className="assignment-checkboxes">
        {participants.map((p) => (
          <label key={p.id} className="assignment-checkbox">
            <input
              type="checkbox"
              checked={assignedIds.includes(p.id)}
              onChange={() => onToggle(p.id)}
            />
            {p.name}
          </label>
        ))}
      </div>
    </div>
  );
}

interface ItemAssignmentGridProps {
  items: ReceiptItem[];
  participants: Participant[];
  assignments: Record<string, string[]>;
  onAssignmentChange: (itemId: string, participantIds: string[]) => void;
}

export function ItemAssignmentGrid({
  items,
  participants,
  assignments,
  onAssignmentChange,
}: ItemAssignmentGridProps) {
  const toggle = (itemId: string, participantId: string) => {
    const current = assignments[itemId] ?? [];
    const next = current.includes(participantId)
      ? current.filter((id) => id !== participantId)
      : [...current, participantId];
    onAssignmentChange(itemId, next);
  };

  return (
    <section className="card">
      <h2>Who had what?</h2>
      {participants.length === 0 ? (
        <p className="hint">Add participants first.</p>
      ) : (
        items.map((item) => (
          <ItemAssignmentRow
            key={item.id}
            item={item}
            participants={participants}
            assignedIds={assignments[item.id] ?? []}
            onToggle={(participantId) => toggle(item.id, participantId)}
          />
        ))
      )}
    </section>
  );
}
