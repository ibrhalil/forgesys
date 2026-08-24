import { projectsApi } from '../../features/projects/api';
import { ReferencePicker, type ReferencePickerProps } from './ReferencePicker';

/** Async typeahead over NOTES-type project containers (labels are names). */
export function ProjectPicker(props: ReferencePickerProps) {
  return (
    <ReferencePicker
      {...props}
      search={(input) =>
        projectsApi
          .list({ q: input, size: 20, sorts: [{ field: 'name', dir: 'asc' }], type: 'NOTES' })
          .then((page) => page.items.map((p) => ({ value: p.id, label: p.name })))
          .catch(() => [])
      }
    />
  );
}
