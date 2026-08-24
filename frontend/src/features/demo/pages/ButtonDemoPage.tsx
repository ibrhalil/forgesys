import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Spinner } from '../../../components/ui/Spinner';
import { DemoSection } from '../components/DemoSection';
import { LuPlus, LuDownload, LuTrash2, LuArrowRight, LuCheck } from 'react-icons/lu';

export function ButtonDemoPage() {
  const [loadingBtn, setLoadingBtn] = useState(false);
  const [asyncActionSuccess, setAsyncActionSuccess] = useState(false);

  const simulateAction = () => {
    setLoadingBtn(true);
    setAsyncActionSuccess(false);
    setTimeout(() => {
      setLoadingBtn(false);
      setAsyncActionSuccess(true);
      setTimeout(() => setAsyncActionSuccess(false), 3000);
    }, 1500);
  };

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-main">Button & Spinner</h1>
        <p className="mt-1 text-sm text-muted">
          Interactive trigger and loading indicator primitives. Built with theme tokens and accessible focus states.
        </p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {[
            'components/ui/Button.tsx',
            'components/ui/Spinner.tsx',
            'Variant = "primary" | "secondary" | "danger" | "ghost"',
            'Size = "sm" | "md"',
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

      {/* 1. Button Variants */}
      <DemoSection
        title="1. Button Variants"
        description="Four standard variants for hierarchy and intent."
        code={`<Button variant="primary">Primary Action</Button>
<Button variant="secondary">Secondary (Default)</Button>
<Button variant="danger">Destructive Action</Button>
<Button variant="ghost">Ghost / Subtle</Button>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">Primary Action</Button>
          <Button variant="secondary">Secondary (Default)</Button>
          <Button variant="danger">Destructive Action</Button>
          <Button variant="ghost">Ghost / Subtle</Button>
        </div>
      </DemoSection>

      {/* 2. Button Sizes */}
      <DemoSection
        title="2. Button Sizes"
        description="Standard md (default 40px rhythm) and compact sm (inline controls, table headers)."
        code={`<Button size="md" variant="primary">Medium (Default)</Button>
<Button size="sm" variant="primary">Small (sm)</Button>

<Button size="md" variant="secondary">Medium</Button>
<Button size="sm" variant="secondary">Small</Button>`}
      >
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-3">
            <Button size="md" variant="primary">Medium (Default)</Button>
            <Button size="sm" variant="primary">Small (sm)</Button>
          </div>
          <div className="flex items-center gap-3">
            <Button size="md" variant="secondary">Secondary md</Button>
            <Button size="sm" variant="secondary">Secondary sm</Button>
          </div>
        </div>
      </DemoSection>

      {/* 3. With Icons */}
      <DemoSection
        title="3. Buttons with Icons"
        description="Buttons compose seamlessly with Lucide icons (react-icons/lu)."
        code={`import { LuPlus, LuDownload, LuTrash2, LuArrowRight } from 'react-icons/lu';

<Button variant="primary">
  <LuPlus className="h-4 w-4" />
  <span>Create Project</span>
</Button>

<Button variant="secondary">
  <LuDownload className="h-4 w-4" />
  <span>Export Report</span>
</Button>

<Button variant="danger">
  <LuTrash2 className="h-4 w-4" />
  <span>Delete</span>
</Button>

<Button variant="ghost">
  <span>View All</span>
  <LuArrowRight className="h-4 w-4" />
</Button>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">
            <LuPlus className="h-4 w-4" />
            <span>Create Project</span>
          </Button>
          <Button variant="secondary">
            <LuDownload className="h-4 w-4" />
            <span>Export Report</span>
          </Button>
          <Button variant="danger">
            <LuTrash2 className="h-4 w-4" />
            <span>Delete</span>
          </Button>
          <Button variant="ghost">
            <span>View All</span>
            <LuArrowRight className="h-4 w-4" />
          </Button>
        </div>
      </DemoSection>

      {/* 4. Interactive Loading State */}
      <DemoSection
        title="4. Loading State"
        description="Setting loading={true} swaps content with a compact Spinner and disables the button."
        code={`const [loading, setLoading] = useState(false);

const handleSave = async () => {
  setLoading(true);
  await api.save();
  setLoading(false);
};

<Button variant="primary" loading={loading} onClick={handleSave}>
  Save Changes
</Button>`}
      >
        <div className="flex items-center gap-4">
          <Button
            variant="primary"
            loading={loadingBtn}
            onClick={simulateAction}
          >
            {loadingBtn ? 'Saving…' : 'Click to Trigger Async Save'}
          </Button>

          <Button
            variant="secondary"
            loading={true}
          >
            Always Loading
          </Button>

          {asyncActionSuccess && (
            <span className="flex items-center gap-1.5 text-xs font-semibold text-accent-green animate-in fade-in">
              <LuCheck className="h-4 w-4" />
              <span>Saved successfully!</span>
            </span>
          )}
        </div>
      </DemoSection>

      {/* 5. Disabled State */}
      <DemoSection
        title="5. Disabled State"
        description="Disabled buttons prevent pointer events and drop opacity."
        code={`<Button variant="primary" disabled>Disabled Primary</Button>
<Button variant="secondary" disabled>Disabled Secondary</Button>
<Button variant="danger" disabled>Disabled Danger</Button>
<Button variant="ghost" disabled>Disabled Ghost</Button>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary" disabled>Disabled Primary</Button>
          <Button variant="secondary" disabled>Disabled Secondary</Button>
          <Button variant="danger" disabled>Disabled Danger</Button>
          <Button variant="ghost" disabled>Disabled Ghost</Button>
        </div>
      </DemoSection>

      {/* 6. Spinner Standalone */}
      <DemoSection
        title="6. Spinner Primitives"
        description="Single source for animated spinners across ForgeSys."
        code={`import { Spinner } from 'components/ui/Spinner';

// Sizes: sm (16px), md (24px, default), lg (32px)
<Spinner size="sm" />
<Spinner size="md" />
<Spinner size="lg" />

// Custom tone styling via className:
<Spinner className="border-muted/40 border-t-accent" />
<Spinner className="border-accent-green/30 border-t-accent-green" />`}
      >
        <div className="space-y-6">
          <div>
            <span className="mb-2 block text-xs font-semibold text-muted uppercase tracking-wider">Sizes</span>
            <div className="flex items-center gap-6">
              <div className="flex items-center gap-2 text-xs text-muted">
                <Spinner size="sm" className="border-muted/30 border-t-accent" />
                <span>sm (16px)</span>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted">
                <Spinner size="md" className="border-muted/30 border-t-accent" />
                <span>md (24px - default)</span>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted">
                <Spinner size="lg" className="border-muted/30 border-t-accent" />
                <span>lg (32px)</span>
              </div>
            </div>
          </div>

          <div>
            <span className="mb-2 block text-xs font-semibold text-muted uppercase tracking-wider">In-Card Loading Placeholder</span>
            <div className="rounded-xl border border-glass bg-surface p-12 flex flex-col items-center justify-center gap-3">
              <Spinner size="lg" className="border-glass border-t-accent" />
              <p className="text-xs text-muted">Loading tenant data and schema migrations…</p>
            </div>
          </div>
        </div>
      </DemoSection>
    </div>
  );
}
