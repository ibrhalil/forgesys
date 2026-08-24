import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { useLocaleStore } from '../store/localeStore';

/** Default footer labels must follow the UI locale (no hardcoded 'Confirm'/'Cancel'). */
describe('ConfirmDialog', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('renders locale-default buttons in English', () => {
    useLocaleStore.setState({ locale: 'en' });
    render(<ConfirmDialog open title="T" message="M" onConfirm={vi.fn()} onClose={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('renders locale-default buttons in Turkish', () => {
    useLocaleStore.setState({ locale: 'tr' });
    render(<ConfirmDialog open title="T" message="M" onConfirm={vi.fn()} onClose={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Onayla' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'İptal' })).toBeInTheDocument();
  });

  it('prefers explicit confirm/cancel props over the locale defaults', () => {
    useLocaleStore.setState({ locale: 'en' });
    render(
      <ConfirmDialog
        open
        title="T"
        message="M"
        confirmText="Delete"
        cancelText="Dismiss"
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument();
  });
});
