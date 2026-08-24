import { describe, expect, it } from 'vitest';
import {
  addMonths,
  daysInMonth,
  fromIso,
  monthMatrix,
  toIso,
  todayIso,
  weekDays,
  weekdayLabels,
} from '../features/apps/calendarUtils';

describe('daysInMonth', () => {
  it('handles non-leap and leap Februaries', () => {
    expect(daysInMonth(2026, 1)).toBe(28);
    expect(daysInMonth(2024, 1)).toBe(29);
    expect(daysInMonth(2026, 0)).toBe(31);
    expect(daysInMonth(2026, 3)).toBe(30);
  });
});

describe('fromIso/toIso', () => {
  it('round-trips valid dates', () => {
    expect(toIso({ year: 2026, month: 7, day: 5 })).toBe('2026-08-05');
    expect(fromIso('2026-08-05')).toEqual({ year: 2026, month: 7, day: 5 });
  });

  it('rejects malformed and impossible dates', () => {
    expect(fromIso('not-a-date')).toBeNull();
    expect(fromIso('2026-13-01')).toBeNull();
    expect(fromIso('2026-02-30')).toBeNull();
    expect(fromIso('2026-2-3')).toBeNull();
  });
});

describe('monthMatrix', () => {
  it('builds a Monday-first 6×7 grid covering August 2026', () => {
    // Aug 1 2026 is a Saturday → 5 leading July days; grid runs Mon Jul 27 – Sun Sep 6.
    const weeks = monthMatrix(2026, 7);
    expect(weeks).toHaveLength(6);
    weeks.forEach((w) => expect(w).toHaveLength(7));
    expect(weeks[0][0]).toEqual({ year: 2026, month: 6, day: 27 });
    expect(weeks[5][6]).toEqual({ year: 2026, month: 8, day: 6 });
  });

  it('crosses the year border for January (leading December days)', () => {
    // Jan 1 2026 is a Thursday → 3 leading days: Mon Dec 29 2025.
    const weeks = monthMatrix(2026, 0);
    expect(weeks[0][0]).toEqual({ year: 2025, month: 11, day: 29 });
    expect(weeks[0][6]).toEqual({ year: 2026, month: 0, day: 4 });
  });

  it('produces consecutive days across the whole grid', () => {
    const flat = monthMatrix(2026, 7).flat();
    for (let i = 1; i < flat.length; i++) {
      const prev = new Date(Date.UTC(flat[i - 1].year, flat[i - 1].month, flat[i - 1].day)).getTime();
      const curr = new Date(Date.UTC(flat[i].year, flat[i].month, flat[i].day)).getTime();
      expect(curr - prev).toBe(86_400_000);
    }
  });

  it('crops trailing all-overflow weeks (February 2026 → 5 weeks)', () => {
    // Feb 1 2026 is a Sunday → 6 leading January days; the raw 6th week would be
    // entirely March — it must be cropped, the mixed Feb 23–Mar 1 week stays.
    const weeks = monthMatrix(2026, 1);
    expect(weeks).toHaveLength(5);
    expect(weeks[4][0]).toEqual({ year: 2026, month: 1, day: 23 });
    expect(weeks[4][6]).toEqual({ year: 2026, month: 2, day: 1 });
  });

  it('crops down to exactly 4 weeks for a Monday-start February (2021)', () => {
    // Feb 1 2021 is a Monday → no lead; weeks 5 and 6 are pure March → both cropped.
    const weeks = monthMatrix(2021, 1);
    expect(weeks).toHaveLength(4);
    expect(weeks[0][0]).toEqual({ year: 2021, month: 1, day: 1 });
    expect(weeks[3][6]).toEqual({ year: 2021, month: 1, day: 28 });
  });
});

describe('weekDays', () => {
  it('returns the Monday-first week containing the anchor', () => {
    // 2026-08-19 is a Wednesday.
    const days = weekDays('2026-08-19');
    expect(days).toHaveLength(7);
    expect(days[0]).toEqual({ year: 2026, month: 7, day: 17 });
    expect(days[6]).toEqual({ year: 2026, month: 7, day: 23 });
  });
});

describe('addMonths', () => {
  it('rolls over both year borders', () => {
    expect(addMonths(2026, 11, 1)).toEqual({ year: 2027, month: 0 });
    expect(addMonths(2026, 0, -1)).toEqual({ year: 2025, month: 11 });
    expect(addMonths(2026, 6, 6)).toEqual({ year: 2027, month: 0 });
  });
});

describe('todayIso', () => {
  it('formats the local today as YYYY-MM-DD', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('weekdayLabels', () => {
  it('starts with Monday and is locale-aware', () => {
    expect(weekdayLabels('en')[0]).toMatch(/^Mon/i);
    expect(weekdayLabels('tr')[0]).toMatch(/^Pzt/i);
    expect(weekdayLabels('en')).toHaveLength(7);
  });
});
