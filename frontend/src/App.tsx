import { BrowserRouter, Link, NavLink, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, RequireAuth, useAuth } from './auth/AuthProvider';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { LedgerPage } from './pages/LedgerPage';
import { PeoplePage } from './pages/PeoplePage';
import { ReceiptCapturePage } from './pages/ReceiptCapturePage';
import { ReimbursementsPage } from './pages/ReimbursementsPage';
import { SplitEditorPage } from './pages/SplitEditorPage';
import { TransactionDetailPage } from './pages/TransactionDetailPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The ledger is single-user and changes only through this app, so aggressive
      // refetching buys nothing. A short stale window keeps navigation snappy.
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // A 401 triggers re-login inside apiClient; retrying would just race that.
      retry: 1,
    },
  },
});

function AppShell({ children }: { children: React.ReactNode }) {
  const { logout } = useAuth();
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          Split Manager
        </Link>
        <nav className="app-nav">
          <NavLink to="/" end>
            Ledger
          </NavLink>
          <NavLink to="/capture">Add</NavLink>
          <NavLink to="/reimbursements">Claims</NavLink>
          <NavLink to="/people">People</NavLink>
        </nav>
        <button type="button" className="sign-out" onClick={() => void logout()}>
          Sign out
        </button>
      </header>
      <main>{children}</main>
    </div>
  );
}

function ProtectedRoutes() {
  return (
    <RequireAuth>
      <AppShell>
        <Routes>
          <Route path="/" element={<LedgerPage />} />
          <Route path="/capture" element={<ReceiptCapturePage />} />
          <Route path="/split/:id" element={<SplitEditorPage />} />
          <Route path="/transactions/:id" element={<TransactionDetailPage />} />
          <Route path="/reimbursements" element={<ReimbursementsPage />} />
          <Route path="/people" element={<PeoplePage />} />
          <Route path="*" element={<p className="status-message">Page not found.</p>} />
        </Routes>
      </AppShell>
    </RequireAuth>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <QueryClientProvider client={queryClient}>
          <Routes>
            {/*
              The callback route sits OUTSIDE RequireAuth: the user is by definition not
              yet authenticated when they land here, and gating it would bounce them back
              to the hosted UI in a loop.
            */}
            <Route path="/auth/callback" element={<AuthCallbackPage />} />
            <Route path="*" element={<ProtectedRoutes />} />
          </Routes>
        </QueryClientProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
