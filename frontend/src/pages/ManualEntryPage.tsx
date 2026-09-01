import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCreateTransaction } from '../hooks/queries';
import type { TransactionType } from '../types';

/** Today, in the yyyy-MM-dd form a date input and the API both expect. */
function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Add a charge by hand — cash, or anything with no receipt photo and no statement row
 * behind it yet.
 *
 * <p>The deliberate escape hatch, not the main path: photographing a receipt or importing
 * a statement is the point of the app, and typing amounts in is the friction it exists to
 * remove. So this stays four fields and does not try to collect line items — a split
 * entered here is an EQUAL-or-weighted split over a total, and the split editor takes it
 * from there.
 */
export function ManualEntryPage() {
  const navigate = useNavigate();
  const create = useCreateTransaction();

  const [type, setType] = useState<TransactionType>('SPLIT');
  const [merchant, setMerchant] = useState('');
  const [transactionDate, setTransactionDate] = useState(today());
  const [total, setTotal] = useState('');

  const amount = Number(total);
  const valid = merchant.trim().length > 0 && Number.isFinite(amount) && amount > 0;

  const submit = () => {
    if (!valid) return;
    create.mutate(
      { type, merchant: merchant.trim(), transactionDate, total: amount },
      {
        onSuccess: (transaction) => {
          // A split still needs participants and a mode, so hand it to the editor. A
          // claim is already complete and opens straight away, so show it as it stands.
          navigate(
            transaction.type === 'SPLIT'
              ? `/split/${transaction.transactionId}`
              : `/transactions/${transaction.transactionId}`,
          );
        },
      },
    );
  };

  return (
    <div className="page">
      <Link to="/" className="back-link">
        &larr; Ledger
      </Link>
      <h1>Add by hand</h1>
      <p className="subtle">
        For cash, or anything you have no receipt photo or statement row for. If you do have
        a receipt, <Link to="/capture">photograph it instead</Link> — you will get the line
        items and an itemized split.
      </p>

      <section className="card">
        <div className="field">
          <label>What is it?</label>
          <div className="chip-row">
            {(['SPLIT', 'REIMBURSEMENT'] as TransactionType[]).map((option) => (
              <button
                key={option}
                type="button"
                className={type === option ? 'chip chip-on' : 'chip'}
                onClick={() => setType(option)}
              >
                {option === 'SPLIT' ? 'Split with someone' : 'Claim from work'}
              </button>
            ))}
          </div>
        </div>

        <div className="field">
          <label htmlFor="merchant">Merchant</label>
          <input
            id="merchant"
            type="text"
            value={merchant}
            placeholder="Where was it?"
            autoFocus
            onChange={(e) => setMerchant(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="date">Date</label>
          <input
            id="date"
            type="date"
            value={transactionDate}
            onChange={(e) => setTransactionDate(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="total">Total</label>
          <input
            id="total"
            type="number"
            step="0.01"
            min="0"
            inputMode="decimal"
            value={total}
            placeholder="0.00"
            onChange={(e) => setTotal(e.target.value)}
          />
        </div>
      </section>

      {create.error && <p className="status-message error">{(create.error as Error).message}</p>}

      <button type="button" className="primary-button" disabled={!valid || create.isPending} onClick={submit}>
        {create.isPending ? 'Adding…' : type === 'SPLIT' ? 'Add & split' : 'Add claim'}
      </button>
    </div>
  );
}
