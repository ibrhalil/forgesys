import { useState } from 'react';
import { LuCalendarDays, LuChevronLeft, LuChevronRight } from 'react-icons/lu';
import { Button } from '../../../components/ui/Button';
import { EmptyState } from '../../../components/ui/EmptyState';
import { useT } from '../../../lib/i18n';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { formatDate } from '../../../lib/format';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { ValueResolver } from '../valueLabels';
import type { CustomAppDetail, CustomAppRecord, CustomAppView } from '../types';
import {
  DAY_MS,
  addMonths,
  fromIso,
  monthLabel,
  monthMatrix,
  toIso,
  todayIso,
  weekDays,
  weekdayLabels,
} from '../calendarUtils';

/** Month cells cap their chips; week cells get more room. */
const MONTH_CHIPS = 3;
const WEEK_CHIPS = 8;

/**
 * CALENDAR view renderer — records placed on a minimal month/week grid by the
 * configured dateProperty (ISO yyyy-mm-dd values). Records without a date value
 * are not placed (filtering by IS_EMPTY shows them in another view).
 */
export function RecordCalendar({
  customApp,
  view,
  records,
  isLoading,
  resolve,
  onRequestEdit,
}: {
  customApp: CustomAppDetail;
  view: CustomAppView;
  records: CustomAppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  /** Opens the record form on a chip click — only wired for apps:record:write holders. */
  onRequestEdit?: (record: CustomAppRecord) => void;
}) {
  const { t, locale } = useT();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_RECORD_WRITE));
  const [mode, setMode] = useState<'month' | 'week'>('month');
  const [anchor, setAnchor] = useState(() => todayIso());

  const dateProp = customApp.properties.find((p) => p.id === view.config?.dateProperty);
  if (!dateProp || dateProp.type !== 'DATE') {
    return <EmptyState message={t('customApps.calendarMissingDateProp')} icon={LuCalendarDays} />;
  }

  const titleProp = firstTextProperty(customApp.properties);
  const today = todayIso();
  const anchorYmd = fromIso(anchor)!;
  const byDay = new Map<string, CustomAppRecord[]>();
  for (const r of records) {
    const iso = r.values[dateProp.id];
    if (iso === undefined || iso === null || iso === '') continue;
    const key = String(iso);
    const list = byDay.get(key);
    if (list) list.push(r);
    else byDay.set(key, [r]);
  }

  const shiftMonth = (delta: number) => {
    const next = addMonths(anchorYmd.year, anchorYmd.month, delta);
    setAnchor(toIso({ ...next, day: 1 }));
  };
  const shiftWeek = (delta: number) => {
    const base = new Date(`${anchor}T00:00:00Z`).getTime();
    const next = new Date(base + delta * 7 * DAY_MS);
    setAnchor(next.toISOString().slice(0, 10));
  };

  const week = weekDays(anchor);
  const weeks = monthMatrix(anchorYmd.year, anchorYmd.month);
  const label =
    mode === 'month'
      ? monthLabel(anchorYmd.year, anchorYmd.month, locale)
      : `${formatDate(toIso(week[0]))} – ${formatDate(toIso(week[6]))}`;

  const chip = (r: CustomAppRecord) => {
    const title = recordTitle(r, titleProp, resolve);
    // Chips open the record form for writers (same affordance as sibling renderers'
    // edit actions); read-only viewers get the same look as a plain span.
    if (canWrite && onRequestEdit) {
      return (
        <button
          key={r.id}
          type="button"
          title={title}
          onClick={() => onRequestEdit(r)}
          className="block w-full truncate rounded bg-accent/15 px-1.5 py-0.5 text-left text-xs text-accent transition-colors hover:bg-accent/25 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
        >
          {title}
        </button>
      );
    }
    return (
      <span
        key={r.id}
        title={title}
        className="block truncate rounded bg-accent/15 px-1.5 py-0.5 text-xs text-accent"
      >
        {title}
      </span>
    );
  };

  const cell = (iso: string, muted: boolean, tall: boolean) => {
    const dayRecords = byDay.get(iso) ?? [];
    const cap = tall ? WEEK_CHIPS : MONTH_CHIPS;
    const isToday = iso === today;
    return (
      <div
        key={iso}
        className={`flex min-h-[5rem] flex-col gap-1 rounded-lg border p-1.5 ${
          muted ? 'border-glass/50 bg-bg/20' : 'border-glass bg-surface/60'
        } ${isToday ? 'ring-1 ring-accent/50' : ''}`}
      >
        <span className={`text-xs font-semibold ${isToday ? 'text-accent' : muted ? 'text-muted/50' : 'text-muted'}`}>
          {Number(iso.slice(8, 10))}
        </span>
        {dayRecords.slice(0, cap).map(chip)}
        {dayRecords.length > cap && (
          <span className="text-[10px] text-muted/70">+{dayRecords.length - cap}</span>
        )}
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-3">
      {/* Header: nav + label + month/week toggle + today */}
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          aria-label={t('customApps.calendarPrev')}
          onClick={() => (mode === 'month' ? shiftMonth(-1) : shiftWeek(-1))}
        >
          <LuChevronLeft aria-hidden className="h-4 w-4" />
        </Button>
        <Button
          variant="secondary"
          size="sm"
          aria-label={t('customApps.calendarNext')}
          onClick={() => (mode === 'month' ? shiftMonth(1) : shiftWeek(1))}
        >
          <LuChevronRight aria-hidden className="h-4 w-4" />
        </Button>
        <span className="min-w-44 text-sm font-semibold text-main">{label}</span>
        <Button variant="ghost" size="sm" onClick={() => setAnchor(today)}>
          {t('customApps.calendarToday')}
        </Button>
        <div role="group" aria-label={t('customApps.calendarMode')} className="ml-auto flex items-center gap-0.5">
          {(['month', 'week'] as const).map((m) => (
            <Button
              key={m}
              variant="ghost"
              size="sm"
              aria-pressed={mode === m}
              className={mode === m ? 'font-semibold text-accent' : 'text-muted'}
              onClick={() => setMode(m)}
            >
              {t(`customApps.calendarMode.${m}`)}
            </Button>
          ))}
        </div>
      </div>

      {/* Weekday header */}
      <div className="grid grid-cols-7 gap-1">
        {weekdayLabels(locale).map((d) => (
          <span key={d} className="px-1 text-center text-xs font-semibold uppercase tracking-wide text-muted">
            {d}
          </span>
        ))}
      </div>

      {isLoading ? (
        <div className="py-16 text-center text-xs text-muted">{t('common.loading')}</div>
      ) : mode === 'month' ? (
        weeks.map((w, i) => (
          <div key={i} className="grid grid-cols-7 gap-1">
            {w.map((d) => cell(toIso(d), d.month !== anchorYmd.month, false))}
          </div>
        ))
      ) : (
        <div className="grid grid-cols-1 gap-1 sm:grid-cols-2 lg:grid-cols-7">
          {week.map((d) => cell(toIso(d), false, true))}
        </div>
      )}
    </div>
  );
}
