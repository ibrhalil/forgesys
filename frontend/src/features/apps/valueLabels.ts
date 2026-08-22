import { useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import { useUsers } from '../users/hooks';
import { appsApi } from './api';
import { cellDisplay, shortenId } from './cellValue';
import type { PageParams } from '../../types';
import type { AppDetail, AppProperty, AppRecord } from './types';

/**
 * Picker-aware cell labels for USER (id → email) and RELATION (id → target record
 * title) values — plain `cellDisplay` elsewhere. Resolution is best-effort: ids
 * outside the first fetched page (or deleted users/records) fall back to the
 * shortened raw id, exactly like the pre-picker display.
 */

const TARGET_RECORDS_PARAMS: PageParams = { page: 0, size: 100, sorts: [{ field: 'createdDate', dir: 'desc' }] };

export type ValueResolver = (prop: AppProperty, record: AppRecord) => string;

/**
 * Resolve display labels for every USER/RELATION property of `app` in one hook:
 * one users page + one detail+records query pair per distinct RELATION target.
 * All queries ride the standard ['apps', ...] cache keys, so they share data with
 * the pickers and other views of the same apps.
 */
export function useValueResolvers(app: AppDetail): ValueResolver {
  const { data: usersPage } = useUsers({ size: 100 });

  // Distinct RELATION target apps (stable while the app definition is).
  const targetIds = useMemo(
    () =>
      Array.from(
        new Set(
          app.properties
            .filter((p) => p.type === 'RELATION' && p.config?.targetAppId)
            .map((p) => p.config!.targetAppId!),
        ),
      ),
    [app.properties],
  );

  const usersById = useMemo(() => {
    const m = new Map<string, string>();
    for (const u of usersPage?.items ?? []) m.set(u.id, u.email);
    return m;
  }, [usersPage]);

  const detailQueries = useQueries({
    queries: targetIds.map((targetAppId) => ({
      queryKey: ['apps', targetAppId],
      queryFn: () => appsApi.get(targetAppId),
    })),
  });
  const recordQueries = useQueries({
    queries: targetIds.map((targetAppId) => ({
      queryKey: ['apps', targetAppId, 'records', TARGET_RECORDS_PARAMS],
      queryFn: () => appsApi.listRecords(targetAppId, TARGET_RECORDS_PARAMS),
    })),
  });

  return useMemo(() => {
    const relationMaps = new Map<string, Map<string, string>>();
    targetIds.forEach((targetAppId, i) => {
      const titleProp = detailQueries[i].data?.properties.find((p) => p.type === 'TEXT');
      const m = new Map<string, string>();
      for (const r of recordQueries[i].data?.items ?? []) {
        m.set(r.id, (titleProp && String(r.values[titleProp.id] ?? '')) || `#${shortenId(r.id)}`);
      }
      relationMaps.set(targetAppId, m);
    });

    return (prop: AppProperty, record: AppRecord) => {
      const value = record.values[prop.id];
      if (value === undefined || value === null || value === '') return '';
      if (prop.type === 'USER') {
        const id = String(value);
        return usersById.get(id) ?? shortenId(id);
      }
      if (prop.type === 'RELATION') {
        const id = String(value);
        return relationMaps.get(prop.config?.targetAppId ?? '')?.get(id) ?? shortenId(id);
      }
      return cellDisplay(prop, record);
    };
  }, [targetIds, usersById, detailQueries, recordQueries]);
}
