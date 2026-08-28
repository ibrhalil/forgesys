import { usersApi } from '../../features/users/api';
import { ReferencePicker, type ReferencePickerProps } from './ReferencePicker';

/**
 * Async typeahead over the user directory (labels are emails) — the shared
 * reference-picker core over `q`-searched list endpoints. The backend
 * visibility scope applies to whatever the caller may see.
 */
export function UserPicker(props: ReferencePickerProps) {
  return (
    <ReferencePicker
      {...props}
      search={(input) =>
        usersApi
          .list({ q: input, size: 20, sorts: [{ field: 'email', direction: 'asc' }] })
          .then((page) => page.items.map((u) => ({ value: u.id, label: u.email })))
          .catch(() => [])
      }
    />
  );
}
