import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { beginLogin, logout as endSession } from './authFlow';
import { getAccessToken, hasRefreshToken } from './tokenStore';

type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  status: AuthStatus;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  /** Called by the callback page once tokens are stored. */
  markAuthenticated: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('checking');

  // On load the in-memory access token is always gone (a reload clears it), so the only
  // question is whether the sessionStorage refresh token can still mint a new one.
  useEffect(() => {
    let cancelled = false;

    async function restore() {
      if (!hasRefreshToken()) {
        if (!cancelled) setStatus('unauthenticated');
        return;
      }
      const token = await getAccessToken();
      if (!cancelled) setStatus(token ? 'authenticated' : 'unauthenticated');
    }

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  const markAuthenticated = useCallback(() => setStatus('authenticated'), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      login: beginLogin,
      logout: endSession,
      markAuthenticated,
    }),
    [status, markAuthenticated],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}

/**
 * Gates a subtree behind a signed-in session, redirecting to the hosted UI when there
 * isn't one. Renders nothing while the refresh-token check is still in flight, so a
 * logged-in user reloading the page never sees a flash of the login screen.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, login } = useAuth();

  useEffect(() => {
    if (status === 'unauthenticated') {
      void login();
    }
  }, [status, login]);

  if (status === 'authenticated') {
    return <>{children}</>;
  }
  return (
    <p className="status-message">
      {status === 'checking' ? 'Checking your session…' : 'Redirecting to sign in…'}
    </p>
  );
}
