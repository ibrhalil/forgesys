import { useState } from 'react';
import { Field, TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { Toggle } from '../../../components/ui/Toggle';
import { CheckboxList, type CheckboxItem } from '../../../components/ui/CheckboxList';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { DemoSection } from '../components/DemoSection';

const STATIC_ROLE_OPTIONS: SelectOption<string>[] = [
  { value: 'admin', label: 'Admin (Full Access)' },
  { value: 'editor', label: 'Editor (Write & Publish)' },
  { value: 'viewer', label: 'Viewer (Read Only)' },
  { value: 'billing', label: 'Billing Manager' },
  { value: 'legacy_role', label: 'Legacy Role (Deprecated)' },
];

const CHECKBOX_ROLES: CheckboxItem[] = [
  { id: 'role_admin', label: 'iam:admin', description: 'Complete system administration and tenant management.' },
  { id: 'role_project_lead', label: 'project:lead', description: 'Create and assign tasks, manage sprints and custom apps.' },
  { id: 'role_developer', label: 'dev:contributor', description: 'Push code, edit notes, view project task boards.' },
  { id: 'role_auditor', label: 'audit:viewer', description: 'Read-only access to audit trail and authentication logs.' },
];

export function FormDemoPage() {
  // TextField states
  const [nameVal, setNameVal] = useState('Acme Corp');
  const [emailVal, setEmailVal] = useState('');
  const [errorInputVal, setErrorInputVal] = useState('invalid-domain');

  // TextArea states
  const [descVal, setDescVal] = useState('SystemForge workspace for engineering and product teams.');

  // Toggle states
  const [enabledToggle, setEnabledToggle] = useState(true);
  const [notificationsToggle, setNotificationsToggle] = useState(false);
  const [allPermsToggle, setAllPermsToggle] = useState(false);

  // CheckboxList state
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>(['role_project_lead', 'role_developer']);

  // SelectInput states
  const [singleSelectVal, setSingleSelectVal] = useState<SelectOption<string> | null>(STATIC_ROLE_OPTIONS[0]);
  const [multiSelectVal, setMultiSelectVal] = useState<SelectOption<string>[] | null>([STATIC_ROLE_OPTIONS[1], STATIC_ROLE_OPTIONS[2]]);
  const [tagSelectVal, setTagSelectVal] = useState<SelectOption<string>[] | null>([
    { value: 'frontend', label: 'frontend' },
    { value: 'react19', label: 'react19' },
  ]);
  const [asyncSelectVal, setAsyncSelectVal] = useState<SelectOption<string> | null>(null);
  const [compactSelectVal, setCompactSelectVal] = useState<SelectOption<string> | null>(STATIC_ROLE_OPTIONS[0]);

  // Simulated async load
  const loadUsersAsync = (query: string): Promise<SelectOption<string>[]> => {
    return new Promise((resolve) => {
      setTimeout(() => {
        const users = [
          { value: 'u1', label: 'alice@example.com (Alice Johnson)' },
          { value: 'u2', label: 'bob@example.com (Bob Smith)' },
          { value: 'u3', label: 'carol@example.com (Carol Williams)' },
          { value: 'u4', label: 'dave@example.com (Dave Brown)' },
        ];
        resolve(
          users.filter((u) => u.label.toLowerCase().includes(query.toLowerCase())),
        );
      }, 400);
    });
  };

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-main">Form Controls</h1>
        <p className="mt-1 text-sm text-muted">
          Design system form inputs including TextFields, TextAreas, Toggles, CheckboxLists, and searchable SelectInputs.
        </p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {[
            'components/ui/Field.tsx',
            'components/ui/TextArea.tsx',
            'components/ui/Toggle.tsx',
            'components/ui/CheckboxList.tsx',
            'components/ui/SelectInput.tsx',
          ].map((s) => (
            <code
              key={s}
              className="rounded-md border border-glass bg-main/[0.03] px-2 py-0.5 font-mono text-muted"
            >
              {s}
            </code>
          ))}
        </div>
      </div>

      {/* 1. TextField States */}
      <DemoSection
        title="1. TextField & Field Wrapper"
        description="Standard single-line inputs and generic Field label/error wrappers for custom elements."
        code={`import { TextField, Field } from 'components/ui/Field';

<TextField
  label="Workspace Name"
  value={name}
  onChange={(e) => setName(e.target.value)}
  hint="Public display name for this workspace"
/>

<TextField
  label="Email Address"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  error="A valid company email address is required"
/>

// Generic Field wrapper for custom child elements:
<Field label="Custom Input with Addon" hint="Composite element wrapped with standard label & hint">
  <div className="flex rounded-lg border border-glass bg-main/5 overflow-hidden">
    <span className="px-3 py-2 text-xs bg-main/10 text-muted font-mono">https://</span>
    <input className="flex-1 bg-transparent px-3 py-2 text-sm text-main focus:outline-none" />
  </div>
</Field>`}
      >
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <TextField
              label="Workspace Name"
              value={nameVal}
              onChange={(e) => setNameVal(e.target.value)}
              hint="Public display name for this workspace"
              placeholder="e.g. Acme Corp"
            />

            <TextField
              label="Company Email"
              value={emailVal}
              onChange={(e) => setEmailVal(e.target.value)}
              placeholder="admin@acme.internal"
              hint="Notifications and invoices will be sent here"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <TextField
              label="Workspace Domain"
              value={errorInputVal}
              onChange={(e) => setErrorInputVal(e.target.value)}
              error={errorInputVal.includes('.') ? null : 'Must include a valid domain suffix (e.g. .internal)'}
              placeholder="acme.internal"
            />

            <Field label="Custom Input with Addon" hint="Field can wrap any custom composite input component">
              <div className="flex rounded-lg border border-glass bg-main/5 overflow-hidden">
                <span className="px-3 py-2 text-xs bg-main/10 text-muted font-mono flex items-center">https://</span>
                <input
                  type="text"
                  placeholder="acme.forgesys.io"
                  className="flex-1 bg-transparent px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none"
                />
              </div>
            </Field>
          </div>
        </div>
      </DemoSection>

      {/* 2. TextAreaField */}
      <DemoSection
        title="2. TextAreaField"
        description="Multi-line textarea with automatic minimum height and resize support."
        code={`import { TextAreaField } from 'components/ui/TextArea';

<TextAreaField
  label="Project Description"
  value={desc}
  onChange={(e) => setDesc(e.target.value)}
  hint="Provide an overview of deliverables and goals"
/>`}
      >
        <div className="max-w-xl">
          <TextAreaField
            label="Project Description"
            value={descVal}
            onChange={(e) => setDescVal(e.target.value)}
            hint="Markdown format supported. Maximum 500 characters."
            placeholder="Write project summary..."
          />
        </div>
      </DemoSection>

      {/* 3. Toggle Component */}
      <DemoSection
        title="3. Toggle (Boolean Settings)"
        description="Accessible switch (role='switch') designed strictly for single on/off settings. Note: for multi-select lists, use CheckboxList instead."
        code={`import { Toggle } from 'components/ui/Toggle';

<Toggle
  label="Tenant Account Enabled"
  checked={enabled}
  onChange={setEnabled}
/>`}
      >
        <div className="space-y-4 rounded-xl border border-glass bg-surface p-5 max-w-md">
          <div className="flex items-center justify-between">
            <Toggle
              label="Account Active"
              checked={enabledToggle}
              onChange={setEnabledToggle}
            />
            <span className="text-xs text-muted font-mono">{enabledToggle ? 'true' : 'false'}</span>
          </div>

          <div className="flex items-center justify-between border-t border-glass pt-3">
            <Toggle
              label="Email Notifications"
              checked={notificationsToggle}
              onChange={setNotificationsToggle}
            />
            <span className="text-xs text-muted font-mono">{notificationsToggle ? 'true' : 'false'}</span>
          </div>

          <div className="flex items-center justify-between border-t border-glass pt-3">
            <Toggle
              label="All Permissions Superuser (K-40)"
              checked={allPermsToggle}
              onChange={setAllPermsToggle}
            />
            <span className="text-xs text-muted font-mono">{allPermsToggle ? 'true' : 'false'}</span>
          </div>

          <div className="flex items-center justify-between border-t border-glass pt-3 opacity-60">
            <Toggle
              label="MFA Enforced (Read-Only Policy)"
              checked={true}
              disabled={true}
              onChange={() => undefined}
            />
            <span className="text-xs text-muted">locked</span>
          </div>
        </div>
      </DemoSection>

      {/* 4. CheckboxList Component */}
      <DemoSection
        title="4. CheckboxList (Multi-Select Pickers)"
        description="Scrollable checkbox list with titles and optional description lines. Ideal for IAM role, group, and permission assignments."
        code={`import { CheckboxList, type CheckboxItem } from 'components/ui/CheckboxList';

const items: CheckboxItem[] = [
  { id: '1', label: 'iam:admin', description: 'Complete system administration' },
  { id: '2', label: 'project:lead', description: 'Sprint and task board management' },
];

<CheckboxList
  items={items}
  selectedIds={selectedIds}
  onChange={setSelectedIds}
/>`}
      >
        <div className="max-w-lg rounded-xl border border-glass bg-surface p-4">
          <div className="mb-3 flex items-center justify-between border-b border-glass pb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted">Assign Security Roles</span>
            <span className="text-xs text-accent font-medium">{selectedRoleIds.length} roles selected</span>
          </div>
          <CheckboxList
            items={CHECKBOX_ROLES}
            selectedIds={selectedRoleIds}
            onChange={setSelectedRoleIds}
          />
        </div>
      </DemoSection>

      {/* 5. SelectInput - Single, Multi & Creatable */}
      <DemoSection
        title="5. SelectInput (Single, Multi & Creatable Tags)"
        description="Searchable, portal-enabled dropdown select built on react-select with ForgeSys light corporate styling."
        code={`import { SelectInput } from 'components/ui/SelectInput';

// 1. Single Select with Clear
<SelectInput
  label="User Primary Role"
  options={options}
  value={singleVal}
  onChange={setSingleVal}
  isClearable
/>

// 2. Multi-Select
<SelectInput
  label="Assigned Roles"
  options={options}
  value={multiVal}
  onChange={setMultiVal}
  isMulti
/>

// 3. Creatable Tag Select
<SelectInput
  label="Project Tags"
  value={tagVal}
  onChange={setTagVal}
  isMulti
  creatable
  placeholder="Type and press Enter to create tags..."
/>`}
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <SelectInput
            label="Single Select"
            options={STATIC_ROLE_OPTIONS}
            value={singleSelectVal}
            onChange={(val) => setSingleSelectVal(val as SelectOption<string> | null)}
            isClearable
            hint="Supports keyboard search & clear"
          />

          <SelectInput
            label="Multi-Select"
            options={STATIC_ROLE_OPTIONS}
            value={multiSelectVal}
            onChange={(val) => setMultiSelectVal(val as SelectOption<string>[] | null)}
            isMulti
            hint="Multiple tags with remove buttons"
          />

          <SelectInput
            label="Creatable Tags"
            value={tagSelectVal}
            onChange={(val) => setTagSelectVal(val as SelectOption<string>[] | null)}
            isMulti
            creatable
            placeholder="Add new tags..."
            hint="Type custom text and press enter"
          />
        </div>
      </DemoSection>

      {/* 6. SelectInput - Async Typeahead & Compact Size */}
      <DemoSection
        title="6. SelectInput (Async Typeahead & Compact size='sm')"
        description="Supports async query loading for large datasets and size='sm' for inline table/card controls."
        code={`// Async Typeahead with Promise
<SelectInput
  label="Assign Owner (Async Search)"
  loadOptions={loadUsersAsync}
  value={asyncVal}
  onChange={setAsyncVal}
  placeholder="Search users by email..."
/>

// Compact size="sm" (32px inline controls)
<SelectInput
  size="sm"
  options={options}
  value={val}
  onChange={setVal}
/>`}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <SelectInput
            label="Assignee (Async Typeahead)"
            loadOptions={loadUsersAsync}
            value={asyncSelectVal}
            onChange={(val) => setAsyncSelectVal(val as SelectOption<string> | null)}
            placeholder="Type 'alice' or 'bob'..."
            hint="Simulates server-side query with 400ms delay"
          />

          <div>
            <span className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-muted">
              Inline Status Mover (size=&quot;sm&quot;)
            </span>
            <div className="flex items-center gap-3 rounded-lg border border-glass bg-surface p-3">
              <span className="text-xs text-muted font-medium">Card Status:</span>
              <div className="w-48">
                <SelectInput
                  size="sm"
                  options={STATIC_ROLE_OPTIONS}
                  value={compactSelectVal}
                  onChange={(val) => setCompactSelectVal(val as SelectOption<string> | null)}
                />
              </div>
            </div>
          </div>
        </div>
      </DemoSection>
    </div>
  );
}
