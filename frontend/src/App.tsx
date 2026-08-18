import { SplitPage } from './pages/SplitPage';
import { UploadPage } from './pages/UploadPage';

/**
 * Two routes: /upload (and / as an alias) for capturing a new receipt, and
 * /split/:sessionId for reviewing/finalizing an already-parsed one.
 */
function parseSessionId(pathname: string): string | null {
  const match = pathname.match(/^\/split\/([^/]+)\/?$/);
  return match ? match[1] : null;
}

export default function App() {
  const { pathname } = window.location;
  const sessionId = parseSessionId(pathname);

  if (sessionId) {
    return <SplitPage sessionId={sessionId} />;
  }

  if (pathname === '/' || pathname === '/upload') {
    return <UploadPage />;
  }

  return <p className="status-message">Page not found.</p>;
}
