import { useEffect, useState } from 'react';
import { getSession } from '../apiClient';
import type { SessionResponse } from '../types';

export function useReceiptSession(sessionId: string): {
  session: SessionResponse | null;
  loading: boolean;
  error: string | null;
} {
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    getSession(sessionId)
      .then((result) => {
        if (!cancelled) setSession(result);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load session');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  return { session, loading, error };
}
