import type { ReceiptItem } from '../types';

interface ReceiptItemRowProps {
  item: ReceiptItem;
  onChange: (updated: ReceiptItem) => void;
  onRemove: (id: string) => void;
}

function ReceiptItemRow({ item, onChange, onRemove }: ReceiptItemRowProps) {
  return (
    <div className="item-row">
      <input
        className="item-name"
        type="text"
        value={item.name}
        onChange={(e) => onChange({ ...item, name: e.target.value })}
        aria-label="Item name"
      />
      <input
        className="item-price"
        type="number"
        step="0.01"
        min="0"
        value={item.price}
        onChange={(e) => onChange({ ...item, price: Number(e.target.value) })}
        aria-label="Item price"
      />
      <button type="button" className="icon-button" onClick={() => onRemove(item.id)} aria-label="Remove item">
        &times;
      </button>
    </div>
  );
}

interface AddItemButtonProps {
  onAdd: () => void;
}

function AddItemButton({ onAdd }: AddItemButtonProps) {
  return (
    <button type="button" className="add-button" onClick={onAdd}>
      + Add item
    </button>
  );
}

interface ReceiptReviewSectionProps {
  merchant: string | null;
  items: ReceiptItem[];
  onItemsChange: (items: ReceiptItem[]) => void;
}

export function ReceiptReviewSection({ merchant, items, onItemsChange }: ReceiptReviewSectionProps) {
  const updateItem = (updated: ReceiptItem) => {
    onItemsChange(items.map((item) => (item.id === updated.id ? updated : item)));
  };

  const removeItem = (id: string) => {
    onItemsChange(items.filter((item) => item.id !== id));
  };

  const addItem = () => {
    onItemsChange([...items, { id: crypto.randomUUID(), name: '', price: 0 }]);
  };

  return (
    <section className="card">
      <h2>{merchant ?? 'Receipt'}</h2>
      <div className="item-list">
        {items.map((item) => (
          <ReceiptItemRow key={item.id} item={item} onChange={updateItem} onRemove={removeItem} />
        ))}
      </div>
      <AddItemButton onAdd={addItem} />
    </section>
  );
}
