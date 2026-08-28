import { rolesApi } from '../../features/roles/api';
import { ReferencePicker, type ReferencePickerProps } from './ReferencePicker';

/** Async typeahead over the roles catalog (labels are names). */
export function RolePicker(props: ReferencePickerProps) {
  return (
    <ReferencePicker
      {...props}
      search={(input) =>
        rolesApi
          .list({ q: input, size: 20, sorts: [{ field: 'name', direction: 'asc' }] })
          .then((page) => page.items.map((r) => ({ value: r.id, label: r.name })))
          .catch(() => [])
      }
    />
  );
}
