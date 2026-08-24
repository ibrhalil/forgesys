import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AssignSection } from '../components/detail/AssignSection';
import { useLocaleStore } from '../store/localeStore';

/**
 * Async mode: server-side q-search over reference data — the current selection
 * renders from the id→label seed, picks merge in, Save reports the id set.
 */
describe('AssignSection (async mode)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });

  const OPTIONS = [
    { value: 'r-1', label: 'Developer' },
    { value: 'r-2', label: 'Ops' },
  ];

  it('seeds the current selection as chips, merges a searched pick and saves the id set', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(
      <AssignSection
        title="Roles"
        loadOptions={(input) =>
          Promise.resolve(input === '' ? OPTIONS : OPTIONS.filter((o) => o.label.toLowerCase().includes(input.toLowerCase())))
        }
        selectedValues={['r-1']}
        selectedOptions={[{ value: 'r-1', label: 'Developer' }]}
        onSave={onSave}
      />,
    );

    // Seed chip renders without any search.
    expect(screen.getByText('Developer')).toBeInTheDocument();

    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    // Menu first open loads the empty-input page instantly (no debounce wait).
    await user.click(await screen.findByRole('option', { name: 'Ops' }));

    expect(screen.getByText('Ops')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSave).toHaveBeenCalledWith(['r-1', 'r-2']);
  });

  it('stays dirty-checked: an unchanged selection cannot save', () => {
    render(
      <AssignSection
        title="Roles"
        loadOptions={() => Promise.resolve(OPTIONS)}
        selectedValues={['r-1']}
        selectedOptions={[{ value: 'r-1', label: 'Developer' }]}
        onSave={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
