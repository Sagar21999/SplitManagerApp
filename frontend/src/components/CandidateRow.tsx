import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CLASSIFICATION_LABELS } from '../types';
import type { ConfirmCandidateRequest, StatementCandidate, TransactionType } from '../types';
import { DuplicateWarningBanner } from './DuplicateWarningBanner';

interface CandidateRowProps {
  candidate: StatementCandidate;
  busy: boolean;
  onConfirm: (edits: ConfirmCandidateRequest) => void;
  onDismiss: () => void;
}

/**
 * One statement row, with the classification the API guessed and any duplicate warning.
 *
 * <p>Editing is inline and optional: the descriptor on a statement ("TST* JOES PIZZA 041")
 * is rarely what you want the ledger to say, but correcting it should not need a second
 * screen.
 */
export function CandidateRow({ candidate, busy, onConfirm, onDismiss }: CandidateRowProps) {
  const [editing, setEditing] = useState(false);
  const [merchant, setMerchant] = useState(candidate.rawDescription);
  const [amount, setAmount] = useState(String(candidate.amount));
  const [date, setDate] = useState(candidate.date);
  const [type, setType] = useState<TransactionType>(
    candidate.classification === 'LIKELY_REIMBURSEMENT' ? 'REIMBURSEMENT' : 'SPLIT',
  );

  if (candidate.status !== 'PENDING') {
    return (
      <li className="candidate-row candidate-done">
        <div className="candidate-main">
          <span className="candidate-merchant">{candidate.rawDescription}</span>
          <span className="txn-date">{candidate.date}</span>
        </div>
        <span className="candidate-resolved">
          {candidate.status === 'CONFIRMED' && candidate.resultingTransactionId ? (
            <Link to={`/transactions/${candidate.resultingTransactionId}`}>Added</Link>
          ) : (
            'Dismissed'
          )}
        </span>
      </li>
    );
  }

  const confirm = () => {
    const parsed = Number(amount);
    onConfirm({
      type,
      merchant: merchant.trim() || candidate.rawDescription,
      date,
      amount: Number.isFinite(parsed) && parsed > 0 ? parsed : candidate.amount,
    });
  };

  return (
    <li className="candidate-row">
      <div className="candidate-main">
        {editing ? (
          <div className="candidate-edit">
            <input
              type="text"
              value={merchant}
              aria-label="Merchant"
              onChange={(e) => setMerchant(e.target.value)}
            />
            <input
              type="date"
              value={date}
              aria-label="Date"
              onChange={(e) => setDate(e.target.value)}
            />
            <input
              type="number"
              step="0.01"
              min="0"
              value={amount}
              aria-label="Amount"
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
        ) : (
          <>
            <span className="candidate-merchant">{candidate.rawDescription}</span>
            <span className="txn-date">{candidate.date}</span>
          </>
        )}
      </div>

      <div className="candidate-side">
        <span className="txn-total">${candidate.amount.toFixed(2)}</span>
        <span className="status-pill" data-classification={candidate.classification}>
          {CLASSIFICATION_LABELS[candidate.classification]}
        </span>
      </div>

      <DuplicateWarningBanner matches={candidate.duplicateMatches} />

      <div className="candidate-actions">
        <div className="chip-row">
          {(['SPLIT', 'REIMBURSEMENT'] as TransactionType[]).map((option) => (
            <button
              key={option}
              type="button"
              className={type === option ? 'chip chip-sm chip-on' : 'chip chip-sm'}
              onClick={() => setType(option)}
            >
              {option === 'SPLIT' ? 'Split' : 'Claim'}
            </button>
          ))}
        </div>
        <button type="button" className="link-button" onClick={() => setEditing((v) => !v)}>
          {editing ? 'Done editing' : 'Edit'}
        </button>
        <button type="button" className="add-button" disabled={busy} onClick={confirm}>
          Add
        </button>
        <button type="button" className="link-button" disabled={busy} onClick={onDismiss}>
          Dismiss
        </button>
      </div>
    </li>
  );
}
