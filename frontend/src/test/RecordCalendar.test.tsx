import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RecordCalendar } from '../features/apps/components/RecordCalendar';
import { cellDisplay } from '../features/apps/cellValue';
import { todayIso } from '../features/apps/calendarUtils';
import type { AppDetail, AppRecord, AppView } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';
import { useAuthStore } from '../store/authStore';

const APP_ID = '33333333-3333-3333-3333-333333333333';

const APP: AppDetail = {
  id: APP_ID,
  projectId: 'proj-1',
  projectName: 'Genel',
  name: 'Events',
  description: null,
  icon: null,
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  properties: [
    { id: 'p-title', appId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
    { id: 'p-date', appId: APP_ID, name: 'Due', type: 'DATE', config: null, required: false, position: 1 },
  ],
  views: [],
};

const VIEW: AppView = { id: 'v-cal', appId: APP_ID, name: 'Calendar', type: 'CALENDAR', config: { dateProperty: 'p-date' }, position: 0 };

function isoPlusDays(iso: string, days: number): string {
  return new Date(new Date(`${iso}T00:00:00Z`).getTime() + days * 86_400_000).toISOString().slice(0, 10);
}

const TODAY = todayIso();

const RECORDS: AppRecord[] = [
  { id: 'r-today', appId: APP_ID, values: { 'p-title': 'Standup', 'p-date': TODAY }, createdDate: '2026-08-01T00:00:00Z', updatedAt: '', createdBy: 'u' },
  { id: 'r-later', appId: APP_ID, values: { 'p-title': 'Retrospective', 'p-date': isoPlusDays(TODAY, 3) }, createdDate: '2026-08-01T00:00:00Z', updatedAt: '', createdBy: 'u' },
  { id: 'r-nodate', appId: APP_ID, values: { 'p-title': 'Undated' }, createdDate: '2026-08-01T00:00:00Z', updatedAt: '', createdBy: 'u' },
];

function renderCalendar(view: AppView = VIEW, onRequestEdit?: (r: AppRecord) => void) {
  return render(
    <RecordCalendar
      app={APP}
      view={view}
      records={RECORDS}
      isLoading={false}
      resolve={cellDisplay}
      onRequestEdit={onRequestEdit}
    />,
  );
}

function monthLabelOf(date: Date): string {
  return new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(date);
}

describe('RecordCalendar', () => {
  beforeEach(() => useLocaleStore.setState({ locale: 'en' }));

  it('places dated records as chips and skips undated ones', () => {
    renderCalendar();

    expect(screen.getAllByText('Standup').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Retrospective').length).toBeGreaterThan(0);
    expect(screen.queryByText('Undated')).not.toBeInTheDocument();
    // Month header shows the anchor (today) month.
    expect(screen.getByText(monthLabelOf(new Date(`${TODAY}T00:00:00Z`)))).toBeInTheDocument();
  });

  it('toggles between month and week mode', async () => {
    const user = userEvent.setup();
    renderCalendar();

    await user.click(screen.getByRole('button', { name: 'Week' }));
    // Week header label = "d MMM yyyy – d MMM yyyy" range.
    const first = new Date(`${TODAY}T00:00:00Z`);
    expect(screen.getByText(/–/)).toBeInTheDocument();
    expect(screen.queryByText(monthLabelOf(first))).not.toBeInTheDocument();
    // Back to month.
    await user.click(screen.getByRole('button', { name: 'Month' }));
    expect(screen.getByText(monthLabelOf(first))).toBeInTheDocument();
  });

  it('navigates to the previous month and back to today', async () => {
    const user = userEvent.setup();
    renderCalendar();

    const now = new Date(`${TODAY}T00:00:00Z`);
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    const prevMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
    expect(screen.getByText(monthLabelOf(prevMonth))).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Today' }));
    expect(screen.getByText(monthLabelOf(new Date(`${TODAY}T00:00:00Z`)))).toBeInTheDocument();
  });

  it('shows an empty state when the date property is gone', () => {
    renderCalendar({ ...VIEW, config: { dateProperty: 'p-missing' } });
    expect(screen.getByText(/date property is missing/i)).toBeInTheDocument();
  });

  it('opens the record form through a chip click for apps:record:write holders', async () => {
    useAuthStore.setState({ hasAuthority: () => true });
    const onRequestEdit = vi.fn();
    const user = userEvent.setup();
    renderCalendar(VIEW, onRequestEdit);

    await user.click(screen.getByRole('button', { name: 'Standup' }));
    expect(onRequestEdit).toHaveBeenCalledWith(RECORDS[0]);
  });

  it('keeps chips non-interactive without apps:record:write', () => {
    useAuthStore.setState({ hasAuthority: (a: string) => a !== 'apps:record:write' });
    renderCalendar(VIEW, vi.fn());

    expect(screen.queryByRole('button', { name: 'Standup' })).not.toBeInTheDocument();
    expect(screen.getByText('Standup')).toBeInTheDocument();
  });
});
