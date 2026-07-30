import { Component, type ErrorInfo, type ReactNode } from 'react';
import { LuTriangleAlert } from 'react-icons/lu';
import { t } from '../lib/i18n';

interface Props {
  children: ReactNode;
}
interface State {
  hasError: boolean;
}

/**
 * Top-level render crash guard. Without it an uncaught render error white-screens the
 * whole app; this shows a minimal fallback with a reload action instead. Network/data
 * errors are NOT handled here — those flow through React Query + the toast layer.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Keep a trace in the dev console; the toast layer handles user-facing errors.
    // eslint-disable-next-line no-console
    console.error('Unhandled render error:', error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-danger/15 text-danger">
            <LuTriangleAlert className="h-7 w-7" strokeWidth={2} />
          </div>
          <h1 className="m-0 text-2xl font-semibold text-main">{t('error.title')}</h1>
          <p className="m-0 max-w-sm text-sm text-muted">
            {t('error.message')}
          </p>
          <button
            onClick={() => window.location.reload()}
            className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-bg transition hover:brightness-110"
          >
            {t('error.reload')}
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
