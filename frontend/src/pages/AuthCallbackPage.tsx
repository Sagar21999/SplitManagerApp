import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { completeLogin } from '../auth/authFlow';
import { useAuth } from '../auth/AuthProvider';

/**
 * Lands the OAuth redirect: exchanges the code for tokens, then replaces this URL so the
 * one-time code never survives in the back button or a copied link.
 */
export function AuthCallbackPage() {
  const { markAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    completeLogin()
      .then(({ postLoginPath }) => {
        if (cancelled) return;
        markAuthenticated();
        // replace, not push — going "back" to a spent authorization code is a dead end.
        navigate(postLoginPath || '/', { replace: true });
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Sign-in failed.');
      });

    return () => {
      cancelled = true;
    };
  }, [markAuthenticated, navigate]);

  if (error) {
    return (
      <div className="status-message error">
        <p>{error}</p>
        <button type="button" className="add-button" onClick={() => window.location.assign('/')}>
          Try again
        </button>
      </div>
    );
  }

  return <p className="status-message">Signing you in…</p>;
}
