/**
 * Pure month/week grid math for the CALENDAR view renderer. All helpers work on
 * plain `{ year, month, day }` triples and `YYYY-MM-DD` strings — no Date object
 * crossing month boundaries, so no timezone/UTC pitfalls (the formatDate lesson).
 */

export interface YearMonthDay {
  year: number;
  /** 0-based month, mirroring JS Date. */
  month: number;
  day: number;
}

export const DAY_MS = 86_400_000;

/** Days in a month (month is 0-based). */
export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
}

export function toIso(d: YearMonthDay): string {
  const mm = String(d.month + 1).padStart(2, '0');
  const dd = String(d.day).padStart(2, '0');
  return `${d.year}-${mm}-${dd}`;
}

export function fromIso(iso: string): YearMonthDay | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]) - 1;
  const day = Number(m[3]);
  if (month < 0 || month > 11 || day < 1 || day > daysInMonth(year, month)) return null;
  return { year, month, day };
}

/** 0=Monday … 6=Sunday (the calendar grid runs Monday-first). */
export function mondayIndex(iso: string): number {
  const d = new Date(`${iso}T00:00:00`);
  return (d.getDay() + 6) % 7;
}

/** Local today as YYYY-MM-DD (kept off Date-timezone traps: built from local parts). */
export function todayIso(): string {
  const now = new Date();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${mm}-${dd}`;
}

/**
 * The 6×7 day cells covering a month: Monday-first weeks, leading/trailing days
 * of the neighbouring months included (rendered muted). Each cell carries its
 * own `{year, month}` so month arithmetic stays correct across year borders.
 */
export function monthMatrix(year: number, month: number): YearMonthDay[][] {
  const first = new Date(Date.UTC(year, month, 1));
  // Date.UTC months are 0-based like ours; shift to the Monday on/before day 1.
  const lead = (first.getUTCDay() + 6) % 7;
  const start = new Date(first.getTime() - lead * DAY_MS);

  const weeks: YearMonthDay[][] = [];
  let cursor = start;
  for (let w = 0; w < 6; w++) {
    const week: YearMonthDay[] = [];
    for (let i = 0; i < 7; i++) {
      week.push({ year: cursor.getUTCFullYear(), month: cursor.getUTCMonth(), day: cursor.getUTCDate() });
      cursor = new Date(cursor.getTime() + DAY_MS);
    }
    weeks.push(week);
  }
  return weeks;
}

/** The 7 days of the week containing `anchorIso`, Monday-first. */
export function weekDays(anchorIso: string): YearMonthDay[] {
  const lead = mondayIndex(anchorIso);
  const anchor = new Date(`${anchorIso}T00:00:00Z`);
  const start = new Date(anchor.getTime() - lead * DAY_MS);
  const days: YearMonthDay[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(start.getTime() + i * DAY_MS);
    days.push({ year: d.getUTCFullYear(), month: d.getUTCMonth(), day: d.getUTCDate() });
  }
  return days;
}

export function addMonths(year: number, month: number, delta: number): { year: number; month: number } {
  const zero = year * 12 + month + delta;
  return { year: Math.floor(zero / 12), month: ((zero % 12) + 12) % 12 };
}

/** Localized month labels resolved per-locale by the renderer via Intl. */
export function monthLabel(year: number, month: number, locale: string): string {
  const d = new Date(Date.UTC(year, month, 1));
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(d);
}

/** Short weekday labels (Mon…Sun), locale-aware. */
export function weekdayLabels(locale: string): string[] {
  // 2023-01-02 is a Monday — format its week for Mon-first labels.
  const labels: string[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(Date.UTC(2023, 0, 2 + i));
    labels.push(new Intl.DateTimeFormat(locale, { weekday: 'short', timeZone: 'UTC' }).format(d));
  }
  return labels;
}
