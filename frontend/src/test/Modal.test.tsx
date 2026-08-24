import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Modal } from '../components/ui/Modal';
import { useLocaleStore } from '../store/localeStore';

/**
 * Unit tests for the Modal primitive (K-39 first tests): dialog semantics, focus
 * management on open/close and the Tab/Shift+Tab focus trap.
 */

function ModalHost() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>opener</button>
      <Modal
        open={open}
        title="Confirm"
        onClose={() => setOpen(false)}
        footer={
          <>
            <button type="button">Cancel</button>
            <button type="button">OK</button>
          </>
        }
      >
        <input aria-label="Field" />
      </Modal>
    </div>
  );
}

describe('Modal', () => {
  it('renders a dialog only while open', () => {
    const { rerender } = render(
      <Modal open={false} title="T" onClose={vi.fn()}>
        body
      </Modal>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    rerender(
      <Modal open title="T" onClose={vi.fn()}>
        body
      </Modal>,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('opens focused on the dialog surface (not the close button) and traps Tab/Shift+Tab inside', async () => {
    const user = userEvent.setup();
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    render(<ModalHost />);
    await user.click(screen.getByRole('button', { name: 'opener' }));

    const dialog = screen.getByRole('dialog', { name: 'Confirm' });
    expect(dialog).toBeInTheDocument();
    // Opening lands on the dialog container itself — the X button must not grab focus.
    expect(dialog).toHaveFocus();

    // DOM order of focusables: Close -> Field -> Cancel -> OK.
    // Tab from the surface enters at Close; from OK wraps to the first;
    // Shift+Tab from Close wraps to the last.
    await user.tab();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText('Field')).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'OK' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'OK' })).toHaveFocus();
  });

  it('closes on Escape and restores focus to the opener', async () => {
    const user = userEvent.setup();
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    render(<ModalHost />);
    await user.click(screen.getByRole('button', { name: 'opener' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'opener' })).toHaveFocus();
  });
});
