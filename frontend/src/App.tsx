import { SplitPage } from './pages/SplitPage';

/** LLD §6: single route (/split/:sessionId), the Shortcut always deep-links here directly. */
function parseSessionId(pathname: string): string | null {
  const match = pathname.match(/^\/split\/([^/]+)\/?$/);
  return match ? match[1] : null;
}

export default function App() {
  const sessionId = parseSessionId(window.location.pathname);

  if (!sessionId) {
    return <p className="status-message">No receipt session specified.</p>;
  }

  return <SplitPage sessionId={sessionId} />;
}
