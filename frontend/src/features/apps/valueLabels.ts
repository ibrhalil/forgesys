import { useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import { useUserLabels } from '../users/hooks';
import { appsApi } from './api';
import { cellDisplay, shortenId } from './cellValue';
import type { PageParams } from '../../types';
import type { AppDetail, AppProperty, AppRecord } from './types';

/**
 * Picker-aware cell labels for USER (id → email) and RELATION (id → target record
 * title) values — plain `cellDisplay` elsewhere. USER ids resolve at any scale via
 * {@link useUserLabels} (directory page + per-id detail fallback). RELATION stays
 * best-effort by design: records have no single-record GET endpoint, so resolution
 * rides the target app's bounded records page — ids beyond it (or deleted
 * records/users) fall back to the shortened raw id, exactly like the pre-picker
 * display.
 */

const TARGET_RECORDS_PARAMS: PageParams = { page: 0, size: 100, sorts: [{ field: 'createdDate', dir: 'desc' }] };

export type ValueResolver = (prop: AppProperty, record: AppRecord) => string;

/**
 * Resolve display labels for every USER/RELATION property of `app` in one hook:
 * `records` scopes which USER ids need resolving (all rows actually rendered),
 * plus one detail+records query pair per distinct RELATION target. All queries
 * ride the standard ['users', …] / ['apps', …] cache keys, so they share data
 * with the pickers and other views of the same data.
 */
export function useValueResolvers(app: AppDetail, records: AppRecord[]): ValueResolver {
  const userIds = useMemo(
    () =>
      Array.from(
        new Set(
          app.properties
            .filter((p) => p.type === 'USER')
            .flatMap((p) => records.map((r) => r.values[p.id]))
            .filter((v): v is string | number => v !== undefined && v !== null && v !== '')
            .map(String),
        ),
      ),
    [app.properties, records],
  );
  const usersById = useUserLabels(userIds);

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
