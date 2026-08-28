import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Drawer } from '../components/ui/Drawer';
import { useLocaleStore } from '../store/localeStore';

/**
 * Unit tests for the Drawer primitive (K-55 F3): dialog semantics, Escape +
 * backdrop close, focus restore — the shared useDialogPanel contract.
 */

function DrawerHost() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>opener</button>
      <Drawer open={open} title="Details" onClose={() => setOpen(false)}>
        <p>row detail body</p>
      </Drawer>
    </div>
  );
}

describe('Drawer', () => {
  it('renders a dialog only while open', () => {
    const { rerender } = render(
      <Drawer open={false} title="T" onClose={vi.fn()}>
        body
      </Drawer>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    rerender(
      <Drawer open title="T" onClose={vi.fn()}>
        body
      </Drawer>,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('opens focused on the panel surface, closes on Escape and restores the opener', async () => {
    const user = userEvent.setup();
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    render(<DrawerHost />);
    await user.click(screen.getByRole('button', { name: 'opener' }));

    const dialog = screen.getByRole('dialog', { name: 'Details' });
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveFocus();
    expect(screen.getByText('row detail body')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'opener' })).toHaveFocus();
  });

  it('closes on backdrop mousedown but not on panel content mousedown', async () => {
    const user = userEvent.setup();
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    render(<DrawerHost />);
    await user.click(screen.getByRole('button', { name: 'opener' }));

    // Clicking inside the panel body does not close.
    await user.click(screen.getByText('row detail body'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    // Clicking the overlay (outside the panel) closes.
    await user.click(screen.getByRole('dialog').parentElement!);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
