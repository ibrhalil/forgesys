import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SelectInput } from '../components/ui/SelectInput';
import type { SelectOption } from '../lib/select';
import { useLocaleStore } from '../store/localeStore';

const OPTIONS: SelectOption<string>[] = [
  { value: 'active', label: 'Active' },
  { value: 'suspended', label: 'Suspended' },
];

/** The control wrapper: nearest ancestor of the combobox input carrying the md-control radius. */
function controlEl(): HTMLElement {
  const control = screen.getByRole('combobox').closest('.rounded-md');
  if (!control) throw new Error('control element not found');
  return control as HTMLElement;
}

describe('SelectInput (K-54 contract)', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
  });

  it('renders the control with contract classes (unstyled: visuals fully from Tailwind)', () => {
    render(<SelectInput id="sel-test" options={OPTIONS} value={OPTIONS[0]} onChange={() => undefined} />);
    const control = controlEl();
    expect(control).toHaveClass('bg-main/5', 'border-glass', 'rounded-md');
    // No focus ring before focus; the control is the interaction surface (cursor-text).
    expect(control).toHaveClass('cursor-text');
  });

  it('opens the contract menu (body portal, popover recipe) and marks the selected option', async () => {
    const user = userEvent.setup();
    render(<SelectInput id="sel-menu" options={OPTIONS} value={OPTIONS[0]} onChange={() => undefined} />);
    await user.click(screen.getByRole('combobox'));
    const option = await screen.findByRole('option', { name: 'Active' });
    expect(option).toHaveClass('bg-accent/15', 'text-accent');
    const menu = option.closest('.bg-surface');
    expect(menu).not.toBeNull();
    expect(menu).toHaveClass('rounded-lg', 'shadow-lg');
    // Menu renders through a portal attached to document.body (escapes overflow containers).
    expect(menu?.parentElement?.parentElement).toBe(document.body);
  });

  it('renders multi tags with the squared accent tag recipe', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SelectInput id="sel-multi" options={OPTIONS} isMulti onChange={onChange} />);
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Active' }));
    const tag = await screen.findByText('Active');
    const tagBox = tag.closest('[class*="bg-accent/15"]');
    expect(tagBox).not.toBeNull();
    expect(tagBox).toHaveClass('rounded');
  });

  it('uses the Lucide chevron indicator instead of the react-select default icon', () => {
    render(<SelectInput id="sel-icon" options={OPTIONS} onChange={() => undefined} />);
    const path = controlEl().querySelector('svg path');
    // Lucide chevron-down path — react-select's default DownChevron has a different path.
    expect(path?.getAttribute('d')).toContain('m6 9 6 6 6-6');
  });
});
