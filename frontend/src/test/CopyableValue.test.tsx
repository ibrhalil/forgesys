import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CopyableValue } from '../components/ui/CopyableValue';
import { useLocaleStore } from '../store/localeStore';

/** Unit tests for the CopyableValue primitive (K-55 F3). */

/** userEvent.setup() installs its own clipboard stub — ours must land AFTER it. */
function stubClipboard(writeText: ReturnType<typeof vi.fn>) {
  Object.defineProperty(window.navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  });
}

describe('CopyableValue', () => {
  let writeText: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    writeText = vi.fn(async () => undefined);
  });
  afterEach(() => {
    delete (window.navigator as { clipboard?: unknown }).clipboard;
    vi.restoreAllMocks();
  });

  it('renders the mono value and copies it on click', async () => {
    const user = userEvent.setup();
    stubClipboard(writeText);
    render(<CopyableValue value="a1b2c3d4-1111" label="trace id" />);

    expect(screen.getByText('a1b2c3d4-1111')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /copy: trace id/i }));
    expect(writeText).toHaveBeenCalledWith('a1b2c3d4-1111');
  });

  it('flashes the confirmation check after a successful copy', async () => {
    vi.useFakeTimers();
    try {
      userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      stubClipboard(writeText);
      render(<CopyableValue value="xyz" />);
      const button = screen.getByRole('button', { name: 'Copy' });

      const iconBefore = button.querySelector('svg')?.innerHTML;
      fireEvent.click(button);
      // The async copy continuation lands on a microtask.
      await act(async () => {});
      expect(button.querySelector('svg')?.innerHTML).not.toBe(iconBefore); // copy icon swapped for the check

      act(() => vi.advanceTimersByTime(1600));
      expect(button.querySelector('svg')?.innerHTML).toBe(iconBefore); // reverted
    } finally {
      vi.useRealTimers();
    }
  });

  it('survives clipboard failures without crashing', () => {
    userEvent.setup();
    stubClipboard(vi.fn(async () => { throw new Error('denied'); }));
    render(<CopyableValue value="xyz" />);
    fireEvent.click(screen.getByRole('button', { name: 'Copy' }));
    expect(screen.getByText('xyz')).toBeInTheDocument();
  });
});
