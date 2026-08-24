import { useState } from 'react';
import { Modal } from '../../../../components/ui/Modal';
import { Button } from '../../../../components/ui/Button';
import { TextField } from '../../../../components/ui/Field';
import { TextAreaField } from '../../../../components/ui/TextArea';
import { SelectInput } from '../../../../components/ui/SelectInput';
import { Toggle } from '../../../../components/ui/Toggle';
import { CheckboxList, type CheckboxItem } from '../../../../components/ui/CheckboxList';
import { DemoSection } from '../../components/DemoSection';
import type { SelectOption } from '../../../../lib/select';
import { LuPlus, LuCheck } from 'react-icons/lu';

const PROJECT_TYPES: SelectOption<string>[] = [
  { value: 'software', label: 'Software Development (Kanban + Tasks)' },
  { value: 'custom_apps', label: 'Dynamic Database App (App Builder)' },
  { value: 'knowledge', label: 'Team Documentation & Notes' },
];

const MODULE_TAGS: CheckboxItem[] = [
  { id: 'mod_tasks', label: 'Enable Task Sprints', description: 'Interactive Kanban board and deadline tracker' },
  { id: 'mod_notes', label: 'Enable Markdown Wiki', description: 'Shared team knowledge base and document editor' },
  { id: 'mod_apps', label: 'Enable Custom Records', description: 'Airtable/Notion-style flexible database tables' },
];

function LiveFormModal() {
  const [open, setOpen] = useState(false);
  const [createdProject, setCreatedProject] = useState<{
    name: string;
    key: string;
    type: string;
    publicAccess: boolean;
    modules: string[];
  } | null>(null);

  // Form states
  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [type, setType] = useState<SelectOption<string> | null>(PROJECT_TYPES[0]);
  const [description, setDescription] = useState('');
  const [isPublic, setIsPublic] = useState(false);
  const [selectedModules, setSelectedModules] = useState<string[]>(['mod_tasks', 'mod_notes']);
  const [loading, setLoading] = useState(false);

  // Field validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Project title is required';
    if (!key.trim()) errs.key = 'Project key identifier is required';
    else if (!/^[A-Z0-9_-]{2,10}$/.test(key.trim().toUpperCase())) {
      errs.key = 'Key must be 2-10 alphanumeric characters (e.g. SF, CORE)';
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleNameChange = (val: string) => {
    setName(val);
    if (!key || key.length <= 4) {
      // Auto generate uppercase acronym key
      const autoKey = val
        .split(' ')
        .filter(Boolean)
        .map((w) => w[0])
        .join('')
        .toUpperCase()
        .slice(0, 5);
      if (autoKey) setKey(autoKey);
    }
  };

  const handleSubmit = () => {
    if (!validate()) return;
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setCreatedProject({
        name,
        key: key.toUpperCase(),
        type: type?.label || 'Software',
        publicAccess: isPublic,
        modules: selectedModules,
      });
      setOpen(false);
      // Reset form
      setName('');
      setKey('');
      setDescription('');
      setErrors({});
    }, 1000);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="primary" onClick={() => setOpen(true)}>
          <LuPlus className="h-4 w-4" />
          <span>Create New Project</span>
        </Button>
      </div>

      {createdProject && (
        <div className="rounded-xl border border-accent-green/30 bg-accent-green/5 p-4 space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-accent-green">
            <LuCheck className="h-4 w-4" />
            <span>Project Created Successfully!</span>
          </div>
          <div className="text-xs text-main grid grid-cols-2 gap-2 mt-2">
            <p><span className="text-muted">Name:</span> {createdProject.name} ({createdProject.key})</p>
            <p><span className="text-muted">Type:</span> {createdProject.type}</p>
            <p><span className="text-muted">Visibility:</span> {createdProject.publicAccess ? 'Public' : 'Tenant Private'}</p>
            <p><span className="text-muted">Enabled Modules:</span> {createdProject.modules.length} selected</p>
          </div>
        </div>
      )}

      <Modal
        open={open}
        size="lg"
        title="Create Project Container"
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={loading}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleSubmit} loading={loading}>
              Create Project
            </Button>
          </>
        }
      >
        <div className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-2">
              <TextField
                label="Project Title"
                placeholder="e.g. Core Banking Platform"
                value={name}
                onChange={(e) => handleNameChange(e.target.value)}
                error={errors.name}
                hint="Human-readable project name"
              />
            </div>
            <div>
              <TextField
                label="Key (Prefix)"
                placeholder="CBP"
                value={key}
                onChange={(e) => setKey(e.target.value.toUpperCase())}
                error={errors.key}
                hint="Used as task prefix (e.g. CBP-12)"
              />
            </div>
          </div>

          <SelectInput
            label="Project Template / Container Type"
            options={PROJECT_TYPES}
            value={type}
            onChange={(v) => setType(v as SelectOption<string> | null)}
            hint="Determines default workspace view modes"
          />

          <TextAreaField
            label="Project Description (Optional)"
            placeholder="High level objectives and scope for this workspace..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <div className="rounded-xl border border-glass bg-main/[0.02] p-4 space-y-4">
            <Toggle
              label="Tenant-Wide Access (All members can view)"
              checked={isPublic}
              onChange={setIsPublic}
            />

            <div className="border-t border-glass pt-3">
              <span className="block text-xs font-semibold uppercase tracking-wider text-muted mb-2">
                Active Modules in this Container
              </span>
              <CheckboxList
                items={MODULE_TAGS}
                selectedIds={selectedModules}
                onChange={setSelectedModules}
              />
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
}

const FORM_MODAL_CODE = `import { Modal } from 'components/ui/Modal';
import { Button } from 'components/ui/Button';
import { TextField } from 'components/ui/Field';
import { SelectInput } from 'components/ui/SelectInput';
import { Toggle } from 'components/ui/Toggle';

export function CreateProjectModal({ open, onClose, onSuccess }: Props) {
  const [name, setName] = useState('');
  const [type, setType] = useState<SelectOption | null>(null);
  const [isPublic, setIsPublic] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!name) return setError('Title is required');
    setLoading(true);
    try {
      await api.createProject({ name, type: type?.value, isPublic });
      onSuccess();
      onClose();
    } catch (err) {
      // Global toast catches API errors or extractFieldErrors() maps inline
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      size="lg"
      title="Create New Project"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={loading}>
            Save Project
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <TextField
          label="Project Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={error}
        />
        <SelectInput
          label="Container Type"
          options={typeOptions}
          value={type}
          onChange={setType}
        />
        <Toggle
          label="Public Visibility"
          checked={isPublic}
          onChange={setIsPublic}
        />
      </div>
    </Modal>
  );
}`;

export function FormModalPatternDemo() {
  return (
    <div className="space-y-10">
      <div>
        <div className="inline-flex items-center gap-1.5 rounded-md bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent mb-2">
          Form Pattern
        </div>
        <h1 className="text-2xl font-bold text-main">Entity Form & Modal Pattern</h1>
        <p className="mt-1 text-sm text-muted">
          Standard creation and update dialog pattern. Integrates validation rules, synchronized input states,
          SelectInputs, Toggles, and asynchronous submit buttons with loading state.
        </p>
      </div>

      <DemoSection
        title="Live Interactive Form Dialog"
        description="Try filling the form, triggering validation errors by clearing inputs, and creating a project container."
        code={FORM_MODAL_CODE}
      >
        <LiveFormModal />
      </DemoSection>
    </div>
  );
}
