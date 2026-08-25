import { useEffect, useState } from 'react';
import { AuthProvider, RequireAuth, useAuth } from './auth/AuthProvider';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { SplitPage } from './pages/SplitPage';
import { UploadPage } from './pages/UploadPage';

/**
 * Routing is still the v1 hand-rolled `window.location` match — Phase 3 replaces this
 * with a real router when the ledger adds enough pages to justify one.
 */
function parseSessionId(pathname: string): string | null {
  const match = pathname.match(/^\/split\/([^/]+)\/?$/);
  return match ? match[1] : null;
}

/** Re-renders on history changes, which AuthCallbackPage triggers after sign-in. */
function usePathname(): string {
  const [pathname, setPathname] = useState(window.location.pathname);
  useEffect(() => {
    const onNavigate = () => setPathname(window.location.pathname);
    window.addEventListener('popstate', onNavigate);
    return () => window.removeEventListener('popstate', onNavigate);
  }, []);
  return pathname;
}

function SignOutButton() {
  const { logout } = useAuth();
  return (
    <button type="button" className="sign-out" onClick={() => void logout()}>
      Sign out
    </button>
  );
}

function Routes() {
  const pathname = usePathname();

  // The callback route must sit OUTSIDE RequireAuth: the user is by definition not yet
  // authenticated when they land here, and gating it would bounce them back to the
  // hosted UI in a loop.
  if (pathname === '/auth/callback') {
    return <AuthCallbackPage />;
  }

  const sessionId = parseSessionId(pathname);

  return (
    <RequireAuth>
      <SignOutButton />
      {sessionId ? (
        <SplitPage sessionId={sessionId} />
      ) : pathname === '/' || pathname === '/upload' ? (
        <UploadPage />
      ) : (
        <p className="status-message">Page not found.</p>
      )}
    </RequireAuth>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Routes />
    </AuthProvider>
  );
}
