import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LuUsers } from 'react-icons/lu';
import { DataTable, type Column } from '../components/ui/DataTable';
import type { SortState } from '../types';
import { useLocaleStore } from '../store/localeStore';

/**
 * Unit tests for the DataTable primitive (K-39 first tests): sortable headers
 * (aria-sort + onSortChange with the column's sortKey) and the footer pager
 * (prev/next bounds, rows-per-page segments).
 */

interface Row {
  id: string;
  name: string;
}

const columns: Column<Row>[] = [
  { key: 'name', header: 'Name', sortKey: 'name' },
  { key: 'note', header: 'Note' }, // not sortable on purpose
];

const rows: Row[] = [
  { id: '1', name: 'Ada' },
  { id: '2', name: 'Linus' },
];

function renderTable(overrides: Partial<Parameters<typeof DataTable<Row>>[0]> = {}) {
  const props = {
    columns,
    data: rows,
    rowKey: (r: Row) => r.id,
    page: 0,
    pageSize: 10,
    totalElements: 25,
    totalPages: 3,
    onPageChange: vi.fn(),
    sort: { field: 'name', dir: 'asc' } satisfies SortState,
    onSortChange: vi.fn(),
    pageSizeOptions: [10, 25, 50],
    onPageSizeChange: vi.fn(),
    ...overrides,
  };
  const result = render(<DataTable<Row> {...props} />);
  return { ...props, container: result.container };
}

describe('DataTable', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });

  it('renders rows and marks the active sort column via aria-sort', () => {
    renderTable({ sort: { field: 'name', dir: 'desc' } });

    expect(screen.getByText('Ada')).toBeInTheDocument();
    expect(screen.getByText('Linus')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /name/i })).toHaveAttribute(
      'aria-sort',
      'descending',
    );
    // Non-sortable column: no aria-sort at all.
    expect(screen.getByRole('columnheader', { name: /note/i })).not.toHaveAttribute('aria-sort');
  });

  it('clicking a sortable header reports the sortKey', async () => {
    const user = userEvent.setup();
    const props = renderTable();

    await user.click(screen.getByRole('button', { name: /name/i }));

    expect(props.onSortChange).toHaveBeenCalledWith('name');
  });

  it('does not offer sorting for a column without sortKey', () => {
    renderTable();

    expect(screen.queryByRole('button', { name: /note/i })).not.toBeInTheDocument();
  });

  it('disables prev on the first page and reports next-page clicks', async () => {
    const user = userEvent.setup();
    const props = renderTable();

    expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /next/i }));
    expect(props.onPageChange).toHaveBeenCalledWith(1);
  });

  it('disables next on the last page', () => {
    renderTable({ page: 2 });

    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /prev/i })).toBeEnabled();
  });

  it('rows-per-page segments report the chosen size', async () => {
    const user = userEvent.setup();
    const props = renderTable();

    expect(screen.getByRole('button', { name: '10' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: '25' }));
    expect(props.onPageSizeChange).toHaveBeenCalledWith(25);
  });

  it('renders table settings button when storageKey is provided', async () => {
    renderTable({ storageKey: 'users' });
    expect(screen.getByRole('button', { name: /table settings/i })).toBeInTheDocument();
  });

  it('toggles column visibility and persists to localStorage', async () => {
    const user = userEvent.setup();
    renderTable({ storageKey: 'users' });

    // Open settings menu (defaults to Columns tab)
    await user.click(screen.getByRole('button', { name: /table settings/i }));
    expect(screen.getByText('Customize columns')).toBeInTheDocument();

    // Hide the "Note" column
    const noteCheckbox = screen.getByRole('checkbox', { name: /note/i });
    expect(noteCheckbox).toBeChecked();
    await user.click(noteCheckbox);

    // Note column header is now hidden
    expect(screen.queryByRole('columnheader', { name: /note/i })).not.toBeInTheDocument();

    // Check localStorage persistence
    const stored = JSON.parse(window.localStorage.getItem('sf_table_prefs_users') ?? '{}');
    expect(stored.hiddenColumns).toEqual(['note']);
  });

  it('changes density and persists to localStorage', async () => {
    const user = userEvent.setup();
    renderTable({ storageKey: 'users' });

    // Open settings menu
    await user.click(screen.getByRole('button', { name: /table settings/i }));
    // Switch to Density tab
    await user.click(screen.getByRole('button', { name: /density/i }));

    // Select Compact
    await user.click(screen.getByRole('button', { name: /compact/i }));

    const stored = JSON.parse(window.localStorage.getItem('sf_table_prefs_users') ?? '{}');
    expect(stored.density).toBe('compact');
  });

  it('shows passive export options with Coming soon badge when onExport is not provided', async () => {
    const user = userEvent.setup();
    renderTable({ storageKey: 'users' });

    // Open settings menu
    await user.click(screen.getByRole('button', { name: /table settings/i }));
    // Switch to Export tab
    await user.click(screen.getByRole('button', { name: /export/i }));

    // CSV button is disabled and shows Coming soon
    const csvBtn = screen.getByRole('button', { name: /export csv/i });
    expect(csvBtn).toBeDisabled();
    expect(screen.getAllByText('Coming soon')[0]).toBeInTheDocument();
  });

  it('triggers onExport when provided', async () => {
    const user = userEvent.setup();
    const onExport = vi.fn();
    renderTable({ storageKey: 'users', onExport });

    // Open settings menu
    await user.click(screen.getByRole('button', { name: /table settings/i }));
    // Switch to Export tab
    await user.click(screen.getByRole('button', { name: /export/i }));

    // Click CSV
    const csvBtn = screen.getByRole('button', { name: /export csv/i });
    expect(csvBtn).toBeEnabled();
    await user.click(csvBtn);
    expect(onExport).toHaveBeenCalledWith('csv');
  });

  it('initializes with hidden columns from localStorage', () => {
    window.localStorage.setItem('sf_table_prefs_users', JSON.stringify({ hiddenColumns: ['note'] }));
    renderTable({ storageKey: 'users' });

    expect(screen.queryByRole('columnheader', { name: /note/i })).not.toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /name/i })).toBeInTheDocument();
  });

  it('resets column preferences back to defaults', async () => {
    const user = userEvent.setup();
    window.localStorage.setItem('sf_table_prefs_users', JSON.stringify({ hiddenColumns: ['note'] }));
    renderTable({ storageKey: 'users' });

    // Open menu
    await user.click(screen.getByRole('button', { name: /table settings/i }));
    // Click Reset
    await user.click(screen.getByRole('button', { name: /reset to default/i }));

    // Note column is visible again
    expect(screen.getByRole('columnheader', { name: /note/i })).toBeInTheDocument();
    const stored = JSON.parse(window.localStorage.getItem('sf_table_prefs_users') ?? '{}');
    expect(stored.hiddenColumns).toEqual([]);
  });

  it('renders the context empty icon when the table has no rows', () => {
    // react-icons renders no name class — compare the svg's first path instead.
    const { container: iconRef } = render(<LuUsers />);
    const expectedPath = iconRef.querySelector('path')?.getAttribute('d');
    const { container } = renderTable({ data: [], totalElements: 0, totalPages: 0, emptyIcon: LuUsers });

    const emptyIcon = container.querySelector('svg');
    expect(emptyIcon?.querySelector('path')?.getAttribute('d')).toBe(expectedPath);
  });

  it('labels non-hideable columns with the localized Primary badge in the settings menu', async () => {
    const user = userEvent.setup();
    renderTable({
      storageKey: 'users',
      columns: [
        { key: 'name', header: 'Name', sortKey: 'name', hideable: false },
        { key: 'note', header: 'Note' },
      ],
    });

    await user.click(screen.getByRole('button', { name: /table settings/i }));
    expect(screen.getByText('Primary')).toBeInTheDocument();

    // The open menu re-renders with the Turkish label after a locale switch.
    useLocaleStore.setState({ locale: 'tr' });
    await waitFor(() => expect(screen.getByText('Birincil')).toBeInTheDocument());
  });

  /* ── K-49 column filters ── */

  const filterColumns: Column<Row>[] = [
    { key: 'name', header: 'Name', sortKey: 'name' },
    {
      key: 'note',
      header: 'Note',
      filter: { field: 'note', control: 'text' },
    },
  ];

  it('renders no filter trigger when the table has no filter wiring', () => {
    renderTable({ columns: filterColumns });

    expect(screen.queryByRole('button', { name: /filter note/i })).not.toBeInTheDocument();
  });

  it('applies a structured clause from the column filter popover (K-49)', async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderTable({ columns: filterColumns, filters: [], onFiltersChange });

    // Open the Note column's filter popover
    await user.click(screen.getByRole('button', { name: /filter note/i }));
    // Operator defaults to CONTAINS for text — type a value and apply
    await user.type(await screen.findByLabelText(/value/i), 'urgent');
    await user.click(screen.getByRole('button', { name: /apply/i }));

    expect(onFiltersChange).toHaveBeenCalledWith([
      { field: 'note', operator: 'CONTAINS', values: ['urgent'] },
    ]);
  });

  it('clears an active clause via the popover clear link', async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    renderTable({
      columns: filterColumns,
      filters: [{ field: 'note', operator: 'CONTAINS', values: ['urgent'] }],
      onFiltersChange,
    });

    // Active state: the trigger is accent-colored; opening shows Clear
    await user.click(screen.getByRole('button', { name: /filter note/i }));
    await user.click(screen.getByRole('button', { name: /clear/i }));

    expect(onFiltersChange).toHaveBeenCalledWith([]);
  });

  it('renders the filter popover as a fixed-position body portal (short tables)', async () => {
    const user = userEvent.setup();
    renderTable({ columns: filterColumns, filters: [], onFiltersChange: vi.fn() });

    await user.click(screen.getByRole('button', { name: /filter note/i }));

    // Portaled to body so the table's overflow-x-auto wrapper can never clip it.
    const panel = screen.getByRole('dialog', { name: /filter note/i });
    expect(panel.parentElement).toBe(document.body);
    expect(panel.style.position).toBe('fixed');
  });

  it('flips the popover above the trigger near the viewport bottom', async () => {
    const user = userEvent.setup();
    renderTable({ columns: filterColumns, filters: [], onFiltersChange: vi.fn() });

    const trigger = screen.getByRole('button', { name: /filter note/i });
    vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue({
      top: 740,
      bottom: 750,
      left: 40,
      right: 52,
      width: 12,
      height: 10,
      x: 40,
      y: 740,
      toJSON: () => ({}),
    } as DOMRect);

    await user.click(trigger);

    const panel = screen.getByRole('dialog', { name: /filter note/i });
    expect(parseFloat(panel.style.top)).toBeLessThan(740); // opened upward
  });
});
