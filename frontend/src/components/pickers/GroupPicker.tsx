import { groupsApi } from '../../features/groups/api';
import { ReferencePicker, type ReferencePickerProps } from './ReferencePicker';

/** Async typeahead over the groups catalog (labels are names). */
export function GroupPicker(props: ReferencePickerProps) {
  return (
    <ReferencePicker
      {...props}
      search={(input) =>
        groupsApi
          .list({ q: input, size: 20, sorts: [{ field: 'name', dir: 'asc' }] })
          .then((page) => page.items.map((g) => ({ value: g.id, label: g.name })))
          .catch(() => [])
      }
    />
  );
}
