import { Link } from 'react-router-dom';
import type { DuplicateMatch } from '../types';

/**
 * BRD FR19. A warning, never a merge.
 *
 * <p>The same coffee shop for the same amount two mornings running is a real second
 * charge. Hiding it would be a worse failure than showing something the user dismisses,
 * so this only ever points at what it found and lets them decide.
 */
export function DuplicateWarningBanner({ matches }: { matches: DuplicateMatch[] }) {
  if (matches.length === 0) return null;

  const strong = matches.filter((m) => m.matchStrategy === 'MERCHANT_DATE_AMOUNT');
  const shown = strong.length > 0 ? strong : matches;

  return (
    <div className="duplicate-banner">
      <strong>
        {strong.length > 0
          ? 'This may already be in your ledger'
          : 'Something else matches this amount and date'}
      </strong>
      <ul>
        {shown.map((match) => (
          <li key={match.transactionId}>
            <Link to={`/transactions/${match.transactionId}`}>
              {match.merchant ?? 'Untitled'} · {match.transactionDate}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
