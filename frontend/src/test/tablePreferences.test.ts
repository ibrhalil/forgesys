import { beforeEach, describe, expect, it } from 'vitest';
import {
  getTableStorageKey,
  loadTablePreferences,
  resetTablePreferences,
  saveTablePreferences,
} from '../lib/tablePreferences';

describe('tablePreferences', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('generates prefixed storage key', () => {
    expect(getTableStorageKey('users')).toBe('sf_table_prefs_users');
  });

  it('returns empty object when no preferences exist or key is undefined', () => {
    expect(loadTablePreferences()).toEqual({});
    expect(loadTablePreferences('unknown_table')).toEqual({});
  });

  it('saves and loads preferences properly', () => {
    saveTablePreferences('users', { pageSize: 50, hiddenColumns: ['note'] });
    const loaded = loadTablePreferences('users');
    expect(loaded).toEqual({ pageSize: 50, hiddenColumns: ['note'] });
  });

  it('merges partial preference updates without overwriting existing fields', () => {
    saveTablePreferences('roles', { pageSize: 25 });
    saveTablePreferences('roles', { hiddenColumns: ['description'] });

    const loaded = loadTablePreferences('roles');
    expect(loaded).toEqual({ pageSize: 25, hiddenColumns: ['description'] });
  });

  it('resets preferences properly', () => {
    saveTablePreferences('projects', { pageSize: 100 });
    expect(loadTablePreferences('projects')).toEqual({ pageSize: 100 });

    resetTablePreferences('projects');
    expect(loadTablePreferences('projects')).toEqual({});
  });
});
