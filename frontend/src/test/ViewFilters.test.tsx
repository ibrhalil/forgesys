import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ViewFilters } from '../features/custom-apps/components/ViewFilters';
import type { CustomAppDetail, CustomAppValueFilter, CustomAppValueSort } from '../features/custom-apps/types';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '66666666-6666-6666-6666-666666666666';

const APP: CustomAppDetail = {
  id: APP_ID,
  projectId: 'proj-1',
  projectName: 'Genel',
  name: 'Orders',
  description: null,
  icon: null,
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  properties: [
    { id: 'p-title', customAppId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
    { id: 'p-count', customAppId: APP_ID, name: 'Count', type: 'NUMBER', config: null, required: false, position: 1 },
    { id: 'p-formula', customAppId: APP_ID, name: 'Magic', type: 'FORMULA', config: null, required: false, position: 2 },
  ],
  views: [],
};

const onApply = vi.fn();
const onSaveToView = vi.fn();
const onClear = vi.fn();

function renderFilters(seedFilters: CustomAppValueFilter[] = [], seedSorts: CustomAppValueSort[] = []) {
  return render(
    <ViewFilters
      customApp={APP}
      seedFilters={seedFilters}
      seedSorts={seedSorts}
      canSave
      onApply={onApply}
      onSaveToView={onSaveToView}
      onClear={onClear}
    />,
  );
}

async function pick(user: ReturnType<typeof userEvent.setup>, combobox: HTMLElement, option: string) {
  await user.click(combobox);
  await user.click(await screen.findByRole('option', { name: option }));
}

/** combobox input rendered by SelectInput for the given explicit id. */
function comboboxById(id: string): HTMLElement {
  const el = document.getElementById(id);
  if (!el) throw new Error(`no element #${id}`);
  return el;
}

describe('ViewFilters', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    onApply.mockClear();
    onSaveToView.mockClear();
    onClear.mockClear();
  });

  it('derives operator options from the property type and excludes FORMULA', async () => {
    const user = userEvent.setup();
    renderFilters();

    await user.click(screen.getByRole('button', { name: 'Add filter' }));
    // First row defaults to the first queryable property (Title, TEXT).
    await user.click(comboboxById('filter-property-0'));
    // FORMULA is not offered; NUMBER is.
    expect(screen.queryByRole('option', { name: 'Magic' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('option', { name: 'Count' }));

    await user.click(comboboxById('filter-operator-0'));
    // NUMBER ops: comparators present, CONTAINS absent.
    expect(screen.getByRole('option', { name: '≥ greater or equal' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'contains' })).not.toBeInTheDocument();
  });

  it('disables Apply while a value operator row has no value, then applies the wire filter', async () => {
    const user = userEvent.setup();
    renderFilters();

    await user.click(screen.getByRole('button', { name: 'Add filter' }));
    const apply = screen.getByRole('button', { name: 'Apply' });
    expect(apply).toBeDisabled();

    await user.type(screen.getByLabelText('Value'), 'urgent');
    expect(apply).toBeEnabled();
    await user.click(apply);

    expect(onApply).toHaveBeenCalledWith(
      [{ propertyId: 'p-title', operator: 'EQ', value: 'urgent' }],
      [],
    );
  });

  it('hides the value control for IS_EMPTY and omits the value key on apply', async () => {
    const user = userEvent.setup();
    renderFilters();

    await user.click(screen.getByRole('button', { name: 'Add filter' }));
    await pick(user, comboboxById('filter-operator-0'), 'is empty');

    expect(screen.queryByLabelText('Value')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onApply).toHaveBeenCalledWith([{ propertyId: 'p-title', operator: 'IS_EMPTY' }], []);
  });

  it('adds sort rows and saves the rows into the view config', async () => {
    const user = userEvent.setup();
    renderFilters();

    await user.click(screen.getByRole('button', { name: 'Add sort' }));
    await pick(user, comboboxById('sort-property-0'), 'Count');
    await pick(user, comboboxById('sort-direction-0'), 'Ascending');

    await user.click(screen.getByRole('button', { name: 'Save to view' }));
    expect(onSaveToView).toHaveBeenCalledWith([], [{ propertyId: 'p-count', direction: 'asc' }]);
  });

  it('seeds rows from the view config and clears via the Clear action', async () => {
    const user = userEvent.setup();
    renderFilters([{ propertyId: 'p-title', operator: 'CONTAINS', value: 'ship' }]);

    expect(screen.getByLabelText('Value')).toHaveValue('ship');
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    expect(onClear).toHaveBeenCalled();
  });
});
