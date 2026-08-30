import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ItemAssignmentGrid } from '../components/ItemAssignmentGrid';
import { PayerSelector } from '../components/PayerSelector';
import { PersonPicker } from '../components/PersonPicker';
import { ReceiptReviewSection } from '../components/ReceiptReviewSection';
import { SplitModeToggle } from '../components/SplitModeToggle';
import { SplitSummary } from '../components/SplitSummary';
import { TipEntrySection } from '../components/TipEntrySection';
import { WeightEntryGrid } from '../components/WeightEntryGrid';
import { useFinalizeSplit, usePeople, useTransaction } from '../hooks/queries';
import { computeSplitPreview } from '../splitCalculation';
import { SELF } from '../types';
import type { LineItem, SplitDefinition, SplitMode } from '../types';

/**
 * Review a parsed receipt and finalize its split.
 *
 * The working draft is local state and is only written on finalize — v1's "nothing
 * persists mid-edit" property, deliberately preserved. Every number shown while editing
 * comes from the client-side mirror in splitCalculation.ts; the server recomputes on
 * submit and its answer wins.
 */
export function SplitEditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, isLoading, error } = useTransaction(id);
  const { data: people = [] } = usePeople();
  const finalize = useFinalizeSplit(id ?? '');

  const [merchant, setMerchant] = useState('');
  const [items, setItems] = useState<LineItem[]>([]);
  const [tip, setTip] = useState(0);
  const [mode, setMode] = useState<SplitMode>('EQUAL');
  const [payerId, setPayerId] = useState<string>(SELF);
  const [participantIds, setParticipantIds] = useState<string[]>([SELF]);
  const [pendingNames, setPendingNames] = useState<string[]>([]);
  const [weights, setWeights] = useState<Record<string, number>>({});
  const [assignments, setAssignments] = useState<Record<string, string[]>>({});
  const [seeded, setSeeded] = useState(false);

  // Seed once from the server draft. Re-seeding on every refetch would stomp on edits
  // in progress — a background invalidation would silently discard the user's work.
  useEffect(() => {
    if (!data || seeded) return;
    const t = data.transaction;
    setMerchant(t.merchant ?? '');
    setItems(t.items ?? []);
    setTip(t.tip ?? 0);
    if (t.splitDefinition) {
      setMode(t.splitDefinition.mode);
      setPayerId(t.splitDefinition.payerId);
      setParticipantIds(t.splitDefinition.participantIds);
      setWeights(t.splitDefinition.weights ?? {});
      setAssignments(t.splitDefinition.itemAssignments ?? {});
    }
    setSeeded(true);
  }, [data, seeded]);

  const subtotal = useMemo(
    () => items.reduce((acc, i) => acc + (Number(i.price) || 0), 0),
    [items],
  );
  const tax = data?.transaction.tax ?? 0;
  const total = useMemo(() => subtotal + (tax ?? 0) + (tip ?? 0), [subtotal, tax, tip]);

  const definition: SplitDefinition = useMemo(
    () => ({
      mode,
      payerId,
      participantIds,
      weights: mode === 'SHARES' || mode === 'PERCENTAGE' || mode === 'EXACT' ? weights : null,
      itemAssignments: mode === 'BY_ITEM' ? assignments : null,
    }),
    [mode, payerId, participantIds, weights, assignments],
  );

  const preview = useMemo(
    () => computeSplitPreview(definition, total, items),
    [definition, total, items],
  );

  if (isLoading) return <p className="status-message">Loading…</p>;
  if (error) return <p className="status-message error">{(error as Error).message}</p>;
  if (!data) return <p className="status-message">Not found.</p>;

  // Newly-typed names have no id yet, so they cannot be in participantIds. The API
  // creates them on finalize and substitutes the ids server-side.
  const canFinalize = preview.error === null || pendingNames.length > 0;

  const submit = () => {
    finalize.mutate(
      { split: definition, tip, newPersonNames: pendingNames },
      { onSuccess: () => navigate(`/transactions/${id}`) },
    );
  };

  return (
    <div className="page">
      <h1>Review &amp; split</h1>

      <ReceiptReviewSection
        merchant={merchant}
        items={items}
        onMerchantChange={setMerchant}
        onItemsChange={setItems}
      />

      <TipEntrySection subtotal={subtotal} tip={tip} onTipChange={setTip} />

      <section className="card totals-card">
        <div className="total-row">
          <span>Subtotal</span>
          <span>${subtotal.toFixed(2)}</span>
        </div>
        <div className="total-row">
          <span>Tax</span>
          <span>${(tax ?? 0).toFixed(2)}</span>
        </div>
        <div className="total-row">
          <span>Tip</span>
          <span>${tip.toFixed(2)}</span>
        </div>
        <div className="total-row total-row-strong">
          <span>Total</span>
          <span>${total.toFixed(2)}</span>
        </div>
      </section>

      <PersonPicker
        people={people}
        selectedIds={participantIds}
        onSelectionChange={setParticipantIds}
        pendingNames={pendingNames}
        onPendingNamesChange={setPendingNames}
      />

      <PayerSelector
        people={people}
        participantIds={participantIds}
        pendingNames={pendingNames}
        payerId={payerId}
        onPayerChange={setPayerId}
      />

      <SplitModeToggle mode={mode} onModeChange={setMode} itemsAvailable={items.length > 0} />

      {(mode === 'SHARES' || mode === 'PERCENTAGE' || mode === 'EXACT') && (
        <WeightEntryGrid
          mode={mode}
          people={people}
          participantIds={participantIds}
          weights={weights}
          total={total}
          onWeightsChange={setWeights}
        />
      )}

      {mode === 'BY_ITEM' && (
        <ItemAssignmentGrid
          items={items}
          people={people}
          participantIds={participantIds}
          assignments={assignments}
          onAssignmentChange={(itemId, ids) =>
            setAssignments((prev) => ({ ...prev, [itemId]: ids }))
          }
        />
      )}

      <SplitSummary
        people={people}
        shares={preview.shares}
        payerId={payerId}
        error={pendingNames.length > 0 ? null : preview.error}
      />

      {pendingNames.length > 0 && (
        <p className="hint">
          {pendingNames.join(', ')} will be added to your saved people when you finalize.
        </p>
      )}

      {finalize.error && <p className="status-message error">{(finalize.error as Error).message}</p>}

      <button
        type="button"
        className="primary-button"
        disabled={!canFinalize || finalize.isPending}
        onClick={submit}
      >
        {finalize.isPending ? 'Finalizing…' : 'Finalize split'}
      </button>
    </div>
  );
}
