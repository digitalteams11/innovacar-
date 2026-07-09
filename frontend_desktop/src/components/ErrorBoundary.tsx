import React from 'react';

interface ErrorBoundaryState {
  hasError: boolean;
}

export default class ErrorBoundary extends React.Component<React.PropsWithChildren, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: unknown) {
    console.error('Route render failed', error);
  }

  private retry = () => {
    this.setState({ hasError: false });
  };

  private goDashboard = () => {
    window.location.hash = '#/';
    this.setState({ hasError: false });
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="min-h-screen bg-[#f7f7f4] px-4 py-10 text-[#242722] dark:bg-[#101418] dark:text-white">
        <div className="mx-auto max-w-lg rounded-2xl border border-rose-100 bg-white p-6 text-center shadow-soft dark:border-white/10 dark:bg-[#1a2332]">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600">
            !
          </div>
          <h1 className="text-xl font-bold">Unable to load this page.</h1>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-300">
            Something went wrong while loading this page. You can retry or return to the dashboard.
          </p>
          <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <button
              type="button"
              onClick={this.retry}
              className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700"
            >
              Retry
            </button>
            <button
              type="button"
              onClick={this.goDashboard}
              className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50 dark:border-white/10 dark:text-white dark:hover:bg-white/5"
            >
              Go to Dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }
}
