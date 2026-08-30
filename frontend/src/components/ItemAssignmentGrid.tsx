import { SELF } from '../types';
import type { LineItem, Person } from '../types';

interface ItemAssignmentGridProps {
  items: LineItem[];
  people: Person[];
  participantIds: string[];
  assignments: Record<string, string[]>;
  onAssignmentChange: (itemId: string, participantIds: string[]) => void;
}

export function ItemAssignmentGrid({
  items,
  people,
  participantIds,
  assignments,
  onAssignmentChange,
}: ItemAssignmentGridProps) {
  const label = (id: string) =>
    id === SELF ? 'You' : (people.find((p) => p.personId === id)?.displayName ?? id);

  const toggle = (itemId: string, participantId: string) => {
    const current = assignments[itemId] ?? [];
    onAssignmentChange(
      itemId,
      current.includes(participantId)
        ? current.filter((id) => id !== participantId)
        : [...current, participantId],
    );
  };

  return (
    <section className="card">
      <h2>Who had what?</h2>
      {participantIds.length === 0 ? (
        <p className="hint">Pick who was involved first.</p>
      ) : (
        items.map((item) => (
          <div className="assignment-row" key={item.id}>
            <div className="assignment-item-name">
              {item.name || 'Item'}{' '}
              <span className="assignment-item-price">${item.price.toFixed(2)}</span>
            </div>
            <div className="chip-row">
              {participantIds.map((id) => (
                <button
                  key={id}
                  type="button"
                  className={`chip chip-sm ${(assignments[item.id] ?? []).includes(id) ? 'chip-on' : ''}`}
                  onClick={() => toggle(item.id, id)}
                >
                  {label(id)}
                </button>
              ))}
            </div>
          </div>
        ))
      )}
    </section>
  );
}
