import { useEffect, useMemo, useState } from 'react';
import { useReceiptSession } from '../hooks/useReceiptSession';
import { finalizeSplit } from '../apiClient';
import { computeEqualSplit, computeItemSplit } from '../splitCalculation';
import { ReceiptReviewSection } from '../components/ReceiptReviewSection';
import { TipEntrySection } from '../components/TipEntrySection';
import { ParticipantsSection } from '../components/ParticipantsSection';
import { SplitModeToggle } from '../components/SplitModeToggle';
import { ItemAssignmentGrid } from '../components/ItemAssignmentGrid';
import { SplitSummary } from '../components/SplitSummary';
import { ConfirmButton } from '../components/ConfirmButton';
import { ConfirmationModal } from '../components/ConfirmationModal';
import type { Participant, ReceiptItem, SplitMode } from '../types';

interface SplitPageProps {
  sessionId: string;
}

export function SplitPage({ sessionId }: SplitPageProps) {
  const { session, loading, error } = useReceiptSession(sessionId);

  const [items, setItems] = useState<ReceiptItem[]>([]);
  const [tip, setTip] = useState(0);
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [payerId, setPayerId] = useState<string | null>(null);
  const [mode, setMode] = useState<SplitMode>('EQUAL');
  const [assignments, setAssignments] = useState<Record<string, string[]>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [shareText, setShareText] = useState<string | null>(null);

  useEffect(() => {
    if (session) {
      setItems(session.items);
      setTip(session.tip ?? 0);
    }
  }, [session]);

  const subtotal = useMemo(() => items.reduce((sum, item) => sum + item.price, 0), [items]);
  const tax = session?.tax ?? 0;
  const total = subtotal + tax + tip;

  const shares = useMemo(() => {
    if (participants.length === 0) return {};
    const participantIds = participants.map((p) => p.id);
    if (mode === 'EQUAL') {
      return computeEqualSplit(total, participantIds);
    }
    return computeItemSplit(items, assignments, tax, tip, payerId ?? participantIds[0]);
  }, [mode, participants, items, assignments, tax, tip, total, payerId]);

  const canConfirm = participants.length > 0 && payerId !== null && !submitting;

  const handleAssignmentChange = (itemId: string, participantIds: string[]) => {
    setAssignments((prev) => ({ ...prev, [itemId]: participantIds }));
  };

  const handleConfirm = async () => {
    if (!payerId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await finalizeSplit({
        sessionId,
        split: { mode, participantShares: shares, payerId },
      });
      if (response.success && response.summary) {
        setShareText(response.summary.shareText);
      } else {
        setSubmitError(response.error ?? 'Failed to finalize split');
      }
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to finalize split');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <p className="status-message">Loading receipt…</p>;
  if (error || !session) return <p className="status-message error">{error ?? 'Session not found'}</p>;

  return (
    <div className="split-page">
      <ReceiptReviewSection merchant={session.merchant} items={items} onItemsChange={setItems} />
      <TipEntrySection subtotal={subtotal} tip={tip} onTipChange={setTip} />
      <ParticipantsSection
        participants={participants}
        payerId={payerId}
        onParticipantsChange={setParticipants}
        onPayerChange={setPayerId}
      />
      <SplitModeToggle mode={mode} onModeChange={setMode} />
      {mode === 'BY_ITEM' && (
        <ItemAssignmentGrid
          items={items}
          participants={participants}
          assignments={assignments}
          onAssignmentChange={handleAssignmentChange}
        />
      )}
      <SplitSummary participants={participants} shares={shares} />
      {submitError && <p className="status-message error">{submitError}</p>}
      <ConfirmButton disabled={!canConfirm} submitting={submitting} onConfirm={handleConfirm} />
      {shareText && <ConfirmationModal shareText={shareText} onClose={() => setShareText(null)} />}
    </div>
  );
}
