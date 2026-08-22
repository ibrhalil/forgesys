import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
  render(<DataTable<Row> {...props} />);
  return props;
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
});
