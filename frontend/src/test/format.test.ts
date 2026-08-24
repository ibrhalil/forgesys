import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { formatDate, formatDateTime, relativeTime } from '../lib/format';
import { useLocaleStore } from '../store/localeStore';

/**
 * Locale-aware formatting (Phase 3): outputs follow `sf_locale` — en keeps the
 * existing en-GB day-first alignment, tr renders Turkish month names.
 */

describe('formatDate / formatDateTime', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
  });

  it('formats dates in English (en-GB day-first)', () => {
    expect(formatDate('2026-07-30')).toBe('30 Jul 2026');
  });

  it('formats dates in Turkish once the locale switches', () => {
    useLocaleStore.setState({ locale: 'tr' });
    expect(formatDate('2026-07-30')).toBe('30 Tem 2026');
  });

  it('formats datetimes per locale', () => {
    expect(formatDateTime('2026-07-30T14:05:00')).toMatch(/30 Jul 2026/);
    useLocaleStore.setState({ locale: 'tr' });
    expect(formatDateTime('2026-07-30T14:05:00')).toMatch(/30 Tem 2026/);
  });

  it('falls back to the raw input on parse failure and — on null', () => {
    expect(formatDate('not-a-date')).toBe('not-a-date');
    expect(formatDate(null)).toBe('—');
    expect(formatDateTime(undefined)).toBe('—');
  });
});

describe('relativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-24T12:00:00Z'));
    useLocaleStore.setState({ locale: 'en' });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('follows the UI locale', () => {
    expect(relativeTime('2026-08-24T10:00:00Z')).toBe('2 hours ago');
    useLocaleStore.setState({ locale: 'tr' });
    expect(relativeTime('2026-08-24T10:00:00Z')).toBe('2 saat önce');
  });

  it('keeps coarse buckets (days, months)', () => {
    expect(relativeTime('2026-08-22T12:00:00Z')).toBe('2 days ago');
    expect(relativeTime('2026-06-24T12:00:00Z')).toBe('2 months ago');
  });

  it('returns — for null and the raw string for unparseable input', () => {
    expect(relativeTime(null)).toBe('—');
    expect(relativeTime('garbage')).toBe('garbage');
  });
});
