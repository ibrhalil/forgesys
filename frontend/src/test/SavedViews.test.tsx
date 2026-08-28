import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { SavedViewsMenu } from '../components/ui/SavedViewsMenu';
import { deleteSavedView, listSavedViews, saveSavedView } from '../lib/savedViews';
import type { SearchQueryState } from '../lib/searchQuery';
import { useLocaleStore } from '../store/localeStore';

/** Unit tests for saved views (K-55 F7, localStorage v1): store + menu wiring. */

const STATE: SearchQueryState = {
  v: 1,
  page: 0,
  size: 10,
  sorts: [{ field: 'createdDate', dir: 'desc' }],
  q: 'errors',
};

describe('savedViews store', () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(() => window.localStorage.clear());

  it('round-trips save → list → delete', () => {
    expect(listSavedViews('t')).toEqual([]);

    const saved = saveSavedView('t', 'My View', STATE);
    expect(saved).not.toBeNull();
    expect(listSavedViews('t')).toHaveLength(1);
    expect(listSavedViews('t')[0].state).toEqual(STATE);

    // Same name (case-insensitive) replaces, never duplicates.
    saveSavedView('t', 'my view', { ...STATE, q: 'other' });
    expect(listSavedViews('t')).toHaveLength(1);
    expect(listSavedViews('t')[0].state.q).toBe('other');

    deleteSavedView('t', listSavedViews('t')[0].id);
    expect(listSavedViews('t')).toEqual([]);
  });

  it('rejects blank names and tolerates corrupt storage', () => {
    expect(saveSavedView('t', '   ', STATE)).toBeNull();
    window.localStorage.setItem('sf_table_views_t', '{not json');
    expect(listSavedViews('t')).toEqual([]);
  });
});

describe('SavedViewsMenu', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });
  afterEach(() => window.localStorage.clear());

  it('saves the current state under a name and applies it back', async () => {
    const onApply = vi.fn();
    render(<SavedViewsMenu storageKey="menu-t" state={STATE} onApply={onApply} />);

    fireEvent.click(screen.getByRole('button', { name: /views/i }));
    fireEvent.change(screen.getByLabelText('View name'), { target: { value: 'Errors only' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(screen.getByText('Errors only')).toBeInTheDocument());
    expect(listSavedViews('menu-t')).toHaveLength(1);

    fireEvent.click(screen.getByText('Errors only'));
    expect(onApply).toHaveBeenCalledWith(STATE);
  });

  it('deletes a view from the menu', async () => {
    saveSavedView('menu-t', 'Stale', STATE);
    render(<SavedViewsMenu storageKey="menu-t" state={STATE} onApply={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /views/i }));
    fireEvent.click(screen.getByRole('button', { name: /delete: stale/i }));

    await waitFor(() => expect(screen.getByText('No saved views')).toBeInTheDocument());
    expect(listSavedViews('menu-t')).toEqual([]);
  });
});
