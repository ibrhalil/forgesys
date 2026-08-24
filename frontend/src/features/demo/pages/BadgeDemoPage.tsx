import { Badge } from '../../../components/ui/Badge';
import { DemoSection } from '../components/DemoSection';
import { LuCheck, LuX, LuShield, LuTriangleAlert, LuClock } from 'react-icons/lu';

export function BadgeDemoPage() {
  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-main">Badge</h1>
        <p className="mt-1 text-sm text-muted">
          Compact status and category label component. Used for visual indicators such as statuses, roles, counts, and tags.
        </p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {['components/ui/Badge.tsx', 'Tone = "accent" | "blue" | "green" | "danger" | "warning" | "muted"'].map((s) => (
            <code
              key={s}
              className="rounded-md border border-glass bg-main/[0.03] px-2 py-0.5 font-mono text-muted"
            >
              {s}
            </code>
          ))}
        </div>
      </div>

      {/* 1. All Tones */}
      <DemoSection
        title="1. All Tones"
        description="Available color tones defined by design system tokens."
        code={`<Badge tone="accent">Accent</Badge>
<Badge tone="blue">Blue</Badge>
<Badge tone="green">Green</Badge>
<Badge tone="warning">Warning</Badge>
<Badge tone="danger">Danger</Badge>
<Badge tone="muted">Muted (Default)</Badge>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone="accent">Accent</Badge>
          <Badge tone="blue">Blue</Badge>
          <Badge tone="green">Green</Badge>
          <Badge tone="warning">Warning</Badge>
          <Badge tone="danger">Danger</Badge>
          <Badge tone="muted">Muted (Default)</Badge>
        </div>
      </DemoSection>

      {/* 2. Semantic Usage in ForgeSys */}
      <DemoSection
        title="2. Semantic Usage"
        description="Standard color-to-meaning mapping across the ForgeSys application."
        code={`// Account / Entity Status
<Badge tone="green">Active</Badge>
<Badge tone="muted">Disabled</Badge>
<Badge tone="warning">Locked</Badge>

// Verification & Security
<Badge tone="blue">Verified</Badge>
<Badge tone="accent">Super Admin</Badge>
<Badge tone="danger">Revoked</Badge>`}
      >
        <div className="space-y-4">
          <div>
            <span className="mb-2 block text-xs font-semibold text-muted uppercase tracking-wider">Account / Entity Status</span>
            <div className="flex flex-wrap items-center gap-3">
              <Badge tone="green">Active</Badge>
              <Badge tone="muted">Disabled</Badge>
              <Badge tone="warning">Locked</Badge>
              <Badge tone="danger">Expired</Badge>
            </div>
          </div>
          <div>
            <span className="mb-2 block text-xs font-semibold text-muted uppercase tracking-wider">Verification & Security</span>
            <div className="flex flex-wrap items-center gap-3">
              <Badge tone="blue">Verified</Badge>
              <Badge tone="accent">Admin</Badge>
              <Badge tone="muted">Viewer</Badge>
              <Badge tone="danger">Access Denied</Badge>
            </div>
          </div>
        </div>
      </DemoSection>

      {/* 3. With Icons */}
      <DemoSection
        title="3. With Icons"
        description="Badges easily compose with Lucide icons inside children."
        code={`import { LuCheck, LuX, LuShield, LuAlertTriangle, LuClock } from 'react-icons/lu';

<Badge tone="green">
  <LuCheck className="h-3.5 w-3.5" />
  <span>Operational</span>
</Badge>

<Badge tone="danger">
  <LuX className="h-3.5 w-3.5" />
  <span>Failed</span>
</Badge>

<Badge tone="accent">
  <LuShield className="h-3.5 w-3.5" />
  <span>Protected</span>
</Badge>

<Badge tone="warning">
  <LuTriangleAlert className="h-3.5 w-3.5" />
  <span>Attention Required</span>
</Badge>

<Badge tone="muted">
  <LuClock className="h-3.5 w-3.5" />
  <span>Pending Review</span>
</Badge>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone="green">
            <LuCheck className="h-3.5 w-3.5" />
            <span>Operational</span>
          </Badge>
          <Badge tone="danger">
            <LuX className="h-3.5 w-3.5" />
            <span>Failed</span>
          </Badge>
          <Badge tone="accent">
            <LuShield className="h-3.5 w-3.5" />
            <span>Protected</span>
          </Badge>
          <Badge tone="warning">
            <LuTriangleAlert className="h-3.5 w-3.5" />
            <span>Attention Required</span>
          </Badge>
          <Badge tone="muted">
            <LuClock className="h-3.5 w-3.5" />
            <span>Pending Review</span>
          </Badge>
        </div>
      </DemoSection>

      {/* 4. Inside Data Cells & Headers */}
      <DemoSection
        title="4. Contextual Placement"
        description="How Badges integrate into list rows and heading components."
        code={`<div className="flex items-center justify-between p-4 border border-glass rounded-lg bg-surface">
  <div className="flex items-center gap-2.5">
    <span className="font-semibold text-main">Production Cluster</span>
    <Badge tone="green">v2.4.0</Badge>
  </div>
  <Badge tone="blue">US-East</Badge>
</div>`}
      >
        <div className="rounded-lg border border-glass bg-surface p-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span className="font-semibold text-main text-sm">Enterprise Tenant Database</span>
            <Badge tone="green">Connected</Badge>
            <Badge tone="accent">Primary</Badge>
          </div>
          <Badge tone="blue">PostgreSQL 16</Badge>
        </div>
      </DemoSection>
    </div>
  );
}
