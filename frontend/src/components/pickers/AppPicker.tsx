import { appsApi } from '../../features/apps/api';
import { ReferencePicker, type ReferencePickerProps } from './ReferencePicker';

/**
 * Async typeahead over the tenant's custom apps (labels are names). Supports
 * `excludeIds` so a RELATION target picker can hide the app being configured
 * (no self-reference).
 */
export function AppPicker(props: ReferencePickerProps) {
  return (
    <ReferencePicker
      {...props}
      search={(input) =>
        appsApi
          .list({ q: input, size: 20, sorts: [{ field: 'name', dir: 'asc' }] })
          .then((page) => page.items.map((a) => ({ value: a.id, label: a.name })))
          .catch(() => [])
      }
    />
  );
}
