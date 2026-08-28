import { useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import { useUserLabels } from '../users/hooks';
import { customAppsApi } from './api';
import { cellDisplay, shortenId } from './cellValue';
import type { PageParams } from '../../types';
import type { CustomAppDetail, CustomAppProperty, CustomAppRecord } from './types';

/**
 * Picker-aware cell labels for USER (id → email) and RELATION (id → target record
 * title) values — plain `cellDisplay` elsewhere. USER ids resolve at any scale via
 * {@link useUserLabels} (directory page + per-id detail fallback). RELATION stays
 * best-effort by design: records have no single-record GET endpoint, so resolution
 * rides the target customApp's bounded records page — ids beyond it (or deleted
 * records/users) fall back to the shortened raw id, exactly like the pre-picker
 * display.
 */

const TARGET_RECORDS_PARAMS: PageParams = { page: 0, size: 100, sorts: [{ field: 'createdDate', direction: 'desc' }] };

export type ValueResolver = (prop: CustomAppProperty, record: CustomAppRecord) => string;

/**
 * Resolve display labels for every USER/RELATION property of `customApp` in one hook:
 * `records` scopes which USER ids need resolving (all rows actually rendered),
 * plus one detail+records query pair per distinct RELATION target. All queries
 * ride the standard ['users', …] / ['customApps', …] cache keys, so they share data
 * with the pickers and other views of the same data.
 */
export function useValueResolvers(customApp: CustomAppDetail, records: CustomAppRecord[]): ValueResolver {
  const userIds = useMemo(
    () =>
      Array.from(
        new Set(
          customApp.properties
            .filter((p) => p.type === 'USER')
            .flatMap((p) => records.map((r) => r.values[p.id]))
            .filter((v): v is string | number => v !== undefined && v !== null && v !== '')
            .map(String),
        ),
      ),
    [customApp.properties, records],
  );
  const usersById = useUserLabels(userIds);

  // Distinct RELATION target customApps (stable while the customApp definition is).
  const targetIds = useMemo(
    () =>
      Array.from(
        new Set(
          customApp.properties
            .filter((p) => p.type === 'RELATION' && p.config?.targetCustomAppId)
            .map((p) => p.config!.targetCustomAppId!),
        ),
      ),
    [customApp.properties],
  );

  const detailQueries = useQueries({
    queries: targetIds.map((targetCustomAppId) => ({
      queryKey: ['customApps', targetCustomAppId],
      queryFn: () => customAppsApi.get(targetCustomAppId),
    })),
  });
  const recordQueries = useQueries({
    queries: targetIds.map((targetCustomAppId) => ({
      queryKey: ['customApps', targetCustomAppId, 'records', TARGET_RECORDS_PARAMS],
      queryFn: () => customAppsApi.listRecords(targetCustomAppId, TARGET_RECORDS_PARAMS),
    })),
  });

  return useMemo(() => {
    const relationMaps = new Map<string, Map<string, string>>();
    targetIds.forEach((targetCustomAppId, i) => {
      const titleProp = detailQueries[i].data?.properties.find((p) => p.type === 'TEXT');
      const m = new Map<string, string>();
      for (const r of recordQueries[i].data?.items ?? []) {
        m.set(r.id, (titleProp && String(r.values[titleProp.id] ?? '')) || `#${shortenId(r.id)}`);
      }
      relationMaps.set(targetCustomAppId, m);
    });

    return (prop: CustomAppProperty, record: CustomAppRecord) => {
      const value = record.values[prop.id];
      if (value === undefined || value === null || value === '') return '';
      if (prop.type === 'USER') {
        const id = String(value);
        return usersById.get(id) ?? shortenId(id);
      }
      if (prop.type === 'RELATION') {
        const id = String(value);
        return relationMaps.get(prop.config?.targetCustomAppId ?? '')?.get(id) ?? shortenId(id);
      }
      return cellDisplay(prop, record);
    };
  }, [targetIds, usersById, detailQueries, recordQueries]);
}
