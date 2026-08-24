import { useState } from 'react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { SearchInput } from '../../../components/ui/SearchInput';
import { RowMenu } from '../../../components/ui/RowMenu';
import { Toggle } from '../../../components/ui/Toggle';
import { DemoSection } from '../components/DemoSection';
import {
  LuPalette,
  LuCheck,
  LuCopy,
  LuRotateCcw,
  LuSparkles,
  LuArrowUpRight,
  LuEye,
  LuTrash2,
  LuLayers,
} from 'react-icons/lu';

export interface ThemePreset {
  id: string;
  name: string;
  category: string;
  description: string;
  tokens: {
    accent: string;
    accentBlue: string;
    accentGreen: string;
    danger: string;
    warning: string;
    bg: string;
    surface: string;
    sidebar: string;
    main: string;
    muted: string;
    glass: string;
  };
}

export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'indigo-modern',
    name: 'Modern Indigo & Slate',
    category: 'High-Tech & SaaS (Linear / Stripe style)',
    description: 'Clean, high-contrast indigo accent on crisp neutral slate surfaces. Recommended for modern developer & SaaS tools.',
    tokens: {
      accent: '#4f46e5',        // Indigo 600
      accentBlue: '#0284c7',    // Sky 600
      accentGreen: '#16a34a',   // Green 600
      danger: '#dc2626',        // Red 600
      warning: '#d97706',       // Amber 600
      bg: '#f8fafc',            // Slate 50
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#0f172a',          // Slate 900
      muted: '#64748b',         // Slate 500
      glass: 'rgba(15, 23, 42, 0.08)',
    },
  },
  {
    id: 'emerald-fintech',
    name: 'Emerald Tech & Teal',
    category: 'Fintech, ERP & Operations',
    description: 'Fresh, confident emerald green palette communicating security, balance, and financial precision.',
    tokens: {
      accent: '#059669',        // Emerald 600
      accentBlue: '#0284c7',    // Sky 600
      accentGreen: '#047857',   // Emerald 700
      danger: '#e11d48',        // Rose 600
      warning: '#d97706',       // Amber 600
      bg: '#f0fdf4',            // Emerald 50
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#064e3b',          // Emerald 950
      muted: '#475569',         // Slate 600
      glass: 'rgba(6, 78, 59, 0.08)',
    },
  },
  {
    id: 'deep-navy',
    name: 'Deep Navy & Cyan',
    category: 'Enterprise Cloud & Data Platform',
    description: 'Authoritative deep navy and vibrant ocean blues. Highly legible for dense tables, dashboards, and enterprise telemetry.',
    tokens: {
      accent: '#0284c7',        // Sky 600
      accentBlue: '#2563eb',    // Blue 600
      accentGreen: '#059669',   // Emerald 600
      danger: '#dc2626',        // Red 600
      warning: '#b45309',       // Amber 700
      bg: '#f1f5f9',            // Slate 100
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#0f172a',          // Slate 900
      muted: '#64748b',         // Slate 500
      glass: 'rgba(15, 23, 42, 0.1)',
    },
  },
  {
    id: 'royal-violet',
    name: 'Royal Violet & AI Purple',
    category: 'Modern Workspace & Creative Apps',
    description: 'Vibrant violet accent providing premium character and visual distinction for dynamic app builders and note modules.',
    tokens: {
      accent: '#7c3aed',        // Violet 600
      accentBlue: '#3b82f6',    // Blue 500
      accentGreen: '#10b981',   // Emerald 500
      danger: '#ef4444',        // Red 500
      warning: '#f59e0b',       // Amber 500
      bg: '#faf5ff',            // Purple 50
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#1e1b4b',          // Indigo 950
      muted: '#6b7280',         // Gray 500
      glass: 'rgba(30, 27, 75, 0.08)',
    },
  },
  {
    id: 'warm-amber',
    name: 'Warm Amber & Stone',
    category: 'Executive & Editorial Workspace',
    description: 'Refined warm stone surfaces with amber accents. Less clinical than blue, warmer and editorial for documents and projects.',
    tokens: {
      accent: '#d97706',        // Amber 600
      accentBlue: '#0369a1',    // Sky 700
      accentGreen: '#15803d',   // Green 700
      danger: '#b91c1c',        // Red 700
      warning: '#b45309',       // Amber 700
      bg: '#fafaf9',            // Stone 50
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#1c1917',          // Stone 900
      muted: '#78716c',         // Stone 500
      glass: 'rgba(28, 25, 23, 0.08)',
    },
  },
  {
    id: 'raspberry-original',
    name: 'Raspberry Ruby (Current Default)',
    category: 'Original Brand Palette',
    description: 'The baseline ForgeSys corporate light theme with pale-sky backdrop and raspberry accent.',
    tokens: {
      accent: '#c2185b',
      accentBlue: '#0369a1',
      accentGreen: '#047857',
      danger: '#b91c1c',
      warning: '#b45309',
      bg: '#e0f2fe',
      surface: '#ffffff',
      sidebar: '#ffffff',
      main: '#1e293b',
      muted: '#64748b',
      glass: 'rgba(15, 23, 42, 0.1)',
    },
  },
];

export function ThemeDemoPage() {
  const [activePresetId, setActivePresetId] = useState<string>('indigo-modern');
  const [copiedCode, setCopiedCode] = useState(false);
  const [customAccent, setCustomAccent] = useState('#4f46e5');
  const [customBg, setCustomBg] = useState('#f8fafc');
  const [customMain, setCustomMain] = useState('#0f172a');
  const [isCustomMode, setIsCustomMode] = useState(false);

  const activePreset = THEME_PRESETS.find((p) => p.id === activePresetId) || THEME_PRESETS[0];

  const applyTokensToDom = (tokens: ThemePreset['tokens']) => {
    const root = document.documentElement;
    root.style.setProperty('--color-accent', tokens.accent);
    root.style.setProperty('--color-accent-blue', tokens.accentBlue);
    root.style.setProperty('--color-accent-green', tokens.accentGreen);
    root.style.setProperty('--color-danger', tokens.danger);
    root.style.setProperty('--color-warning', tokens.warning);
    root.style.setProperty('--color-bg', tokens.bg);
    root.style.setProperty('--color-surface', tokens.surface);
    root.style.setProperty('--color-sidebar', tokens.sidebar);
    root.style.setProperty('--color-main', tokens.main);
    root.style.setProperty('--color-muted', tokens.muted);
    root.style.setProperty('--color-glass', tokens.glass);
  };

  const handleSelectPreset = (preset: ThemePreset) => {
    setActivePresetId(preset.id);
    setIsCustomMode(false);
    setCustomAccent(preset.tokens.accent);
    setCustomBg(preset.tokens.bg);
    setCustomMain(preset.tokens.main);
    applyTokensToDom(preset.tokens);
  };

  const handleCustomApply = () => {
    setIsCustomMode(true);
    const customTokens: ThemePreset['tokens'] = {
      ...activePreset.tokens,
      accent: customAccent,
      bg: customBg,
      main: customMain,
    };
    applyTokensToDom(customTokens);
  };

  const handleResetToDefault = () => {
    const defaultPreset = THEME_PRESETS.find((p) => p.id === 'raspberry-original') || THEME_PRESETS[5];
    handleSelectPreset(defaultPreset);
  };

  // Generate CSS tokens code block for index.css
  const currentTokens = isCustomMode
    ? { ...activePreset.tokens, accent: customAccent, bg: customBg, main: customMain }
    : activePreset.tokens;

  const generatedCssTokens = `@theme {
  --color-bg: ${currentTokens.bg};
  --color-surface: ${currentTokens.surface};
  --color-sidebar: ${currentTokens.sidebar};
  --color-glass: ${currentTokens.glass};

  --color-main: ${currentTokens.main};
  --color-muted: ${currentTokens.muted};

  --color-accent: ${currentTokens.accent};
  --color-accent-blue: ${currentTokens.accentBlue};
  --color-accent-green: ${currentTokens.accentGreen};
  --color-danger: ${currentTokens.danger};
  --color-warning: ${currentTokens.warning};

  --font-sans: "Outfit", "Inter", -apple-system, system-ui, sans-serif;
}`;

  const copyTokens = async () => {
    await navigator.clipboard.writeText(generatedCssTokens);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  return (
    <div className="space-y-10">
      <div>
        <div className="inline-flex items-center gap-1.5 rounded-md bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent mb-2">
          <LuPalette className="h-3.5 w-3.5" />
          <span>Brand Identity & Design Tokens</span>
        </div>
        <h1 className="text-2xl font-bold text-main">Theme & Color Palette Explorer</h1>
        <p className="mt-1 text-sm text-muted">
          Compare carefully curated corporate color palettes designed to give ForgeSys more visual depth,
          contrast, and character. Selecting any preset instantly updates the live application in real-time.
        </p>
      </div>

      {/* 1. Presets Selection Grid */}
      <DemoSection
        title="1. Curated Corporate Presets (Click to Test Live)"
        description="Choose a preset to test its impact across all buttons, badges, tables, sidebars, and highlights in real time."
        code={`// Selected: ${activePreset.name}
// Click 'Copy @theme Tokens' below to apply to src/index.css`}
      >
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {THEME_PRESETS.map((preset) => {
              const isSelected = activePresetId === preset.id && !isCustomMode;
              return (
                <button
                  key={preset.id}
                  type="button"
                  onClick={() => handleSelectPreset(preset)}
                  className={`text-left p-4 rounded-xl border transition-all relative flex flex-col justify-between ${
                    isSelected
                      ? 'border-accent bg-accent/5 ring-2 ring-accent/30 shadow-md'
                      : 'border-glass bg-surface hover:border-accent/40 hover:bg-main/[0.02]'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-bold uppercase tracking-wider text-muted">
                        {preset.category}
                      </span>
                      {isSelected && (
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-white">
                          <LuCheck className="h-3 w-3" />
                        </span>
                      )}
                    </div>
                    <h3 className="text-sm font-bold text-main mt-1">{preset.name}</h3>
                    <p className="text-xs text-muted mt-1 leading-relaxed line-clamp-2">
                      {preset.description}
                    </p>
                  </div>

                  {/* Swatches Bar */}
                  <div className="mt-4 pt-3 border-t border-glass flex items-center gap-1.5">
                    <div
                      className="h-5 w-5 rounded-md shadow-xs border border-black/10"
                      style={{ backgroundColor: preset.tokens.accent }}
                      title={`Accent: ${preset.tokens.accent}`}
                    />
                    <div
                      className="h-5 w-5 rounded-md shadow-xs border border-black/10"
                      style={{ backgroundColor: preset.tokens.accentBlue }}
                      title={`Blue: ${preset.tokens.accentBlue}`}
                    />
                    <div
                      className="h-5 w-5 rounded-md shadow-xs border border-black/10"
                      style={{ backgroundColor: preset.tokens.accentGreen }}
                      title={`Green: ${preset.tokens.accentGreen}`}
                    />
                    <div
                      className="h-5 w-5 rounded-md shadow-xs border border-black/10"
                      style={{ backgroundColor: preset.tokens.bg }}
                      title={`Page Background: ${preset.tokens.bg}`}
                    />
                    <div
                      className="h-5 w-5 rounded-md shadow-xs border border-black/10"
                      style={{ backgroundColor: preset.tokens.main }}
                      title={`Text Main: ${preset.tokens.main}`}
                    />
                  </div>
                </button>
              );
            })}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
            <Button variant="secondary" size="sm" onClick={handleResetToDefault}>
              <LuRotateCcw className="h-3.5 w-3.5" />
              <span>Reset to Original (Raspberry)</span>
            </Button>

            <Button variant="primary" size="sm" onClick={copyTokens}>
              {copiedCode ? <LuCheck className="h-3.5 w-3.5" /> : <LuCopy className="h-3.5 w-3.5" />}
              <span>{copiedCode ? 'Copied to Clipboard!' : 'Copy @theme CSS Tokens'}</span>
            </Button>
          </div>
        </div>
      </DemoSection>

      {/* 2. Live UI Component Benchmark Sandbox */}
      <DemoSection
        title="2. Live Component Benchmark Sandbox"
        description="See how the active palette looks on real components (Buttons, Badges, Metrics, Table Rows, Inputs) with proper contrast and focus states."
        code={generatedCssTokens}
      >
        <div className="rounded-xl border border-glass bg-bg/60 p-6 space-y-6">
          {/* Top Info Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-glass pb-4">
            <div className="flex items-center gap-2.5">
              <div
                className="h-4 w-4 rounded-full shadow-xs ring-2 ring-surface"
                style={{ backgroundColor: currentTokens.accent }}
              />
              <span className="font-bold text-sm text-main">
                Active Palette: <span className="text-accent">{isCustomMode ? 'Custom Tuning' : activePreset.name}</span>
              </span>
            </div>
            <Badge tone="accent">Theme Applied to Document Root</Badge>
          </div>

          {/* Grid of UI Elements */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: KPI Stat & Buttons */}
            <div className="lg:col-span-5 space-y-4">
              {/* Stat Card */}
              <div className="rounded-xl border border-glass bg-surface p-4 shadow-sm">
                <div className="flex items-center justify-between text-xs text-muted uppercase tracking-wider font-semibold">
                  <span>Monthly Active Tenants</span>
                  <LuSparkles className="h-4 w-4 text-accent" />
                </div>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-2xl font-bold text-main">1,482</span>
                  <span className="inline-flex items-center text-xs font-semibold text-accent-green">
                    <LuArrowUpRight className="h-3.5 w-3.5" /> +18.4%
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-muted">PostgreSQL isolated schemas</p>
              </div>

              {/* Button Groups */}
              <div className="rounded-xl border border-glass bg-surface p-4 space-y-3 shadow-sm">
                <span className="text-xs font-semibold text-muted uppercase tracking-wider block">Action Buttons</span>
                <div className="flex flex-wrap items-center gap-2">
                  <Button variant="primary" size="sm">Primary</Button>
                  <Button variant="secondary" size="sm">Secondary</Button>
                  <Button variant="danger" size="sm">Danger</Button>
                  <Button variant="ghost" size="sm">Ghost</Button>
                </div>
              </div>

              {/* Badges Cluster */}
              <div className="rounded-xl border border-glass bg-surface p-4 space-y-3 shadow-sm">
                <span className="text-xs font-semibold text-muted uppercase tracking-wider block">Status Badges</span>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone="accent">Accent / Admin</Badge>
                  <Badge tone="green">Active Status</Badge>
                  <Badge tone="blue">Verified</Badge>
                  <Badge tone="warning">Locked</Badge>
                  <Badge tone="danger">Revoked</Badge>
                </div>
              </div>
            </div>

            {/* Right Column: Mini Table Row & Form Preview */}
            <div className="lg:col-span-7 space-y-4">
              <div className="rounded-xl border border-glass bg-surface p-5 space-y-4 shadow-sm">
                <div className="flex items-center justify-between border-b border-glass pb-3">
                  <span className="text-xs font-semibold uppercase tracking-wider text-muted">Data Row Sample</span>
                  <SearchInput placeholder="Filter entries..." value="" onChange={() => undefined} />
                </div>

                <div className="rounded-lg border border-glass overflow-hidden">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-glass bg-bg/50 text-left text-muted font-semibold uppercase tracking-wider">
                        <th className="p-2.5">User</th>
                        <th className="p-2.5">Role</th>
                        <th className="p-2.5">Status</th>
                        <th className="p-2.5 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-glass">
                      <tr className="bg-accent/[0.04]">
                        <td className="p-2.5 font-semibold text-main">alice@forgesys.internal</td>
                        <td className="p-2.5"><Badge tone="accent">iam:admin</Badge></td>
                        <td className="p-2.5"><Badge tone="green">Active</Badge></td>
                        <td className="p-2.5 text-right">
                          <RowMenu
                            ariaLabel="Actions"
                            items={[
                              { label: 'View', onClick: () => undefined, icon: LuEye },
                              { label: 'Delete', onClick: () => undefined, icon: LuTrash2, danger: true },
                            ]}
                          />
                        </td>
                      </tr>
                      <tr>
                        <td className="p-2.5 text-main">bob.smith@forgesys.internal</td>
                        <td className="p-2.5"><Badge tone="blue">project:lead</Badge></td>
                        <td className="p-2.5"><Badge tone="muted">Offline</Badge></td>
                        <td className="p-2.5 text-right">
                          <RowMenu
                            ariaLabel="Actions"
                            items={[
                              { label: 'View', onClick: () => undefined, icon: LuEye },
                            ]}
                          />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div className="pt-2 border-t border-glass flex items-center justify-between">
                  <Toggle label="Enable Multi-Factor Auth" checked={true} onChange={() => undefined} />
                  <Button variant="primary" size="sm">
                    <LuLayers className="h-3.5 w-3.5" />
                    <span>Save Preferences</span>
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </DemoSection>

      {/* 3. Custom Color Fine-Tuner */}
      <DemoSection
        title="3. Custom Color Fine-Tuner"
        description="Pick your own custom brand hex colors to dynamically evaluate brand identity on the fly."
        code={`// Custom CSS tokens dynamically applied to document.documentElement
--color-accent: ${customAccent};
--color-bg: ${customBg};
--color-main: ${customMain};`}
      >
        <div className="rounded-xl border border-glass bg-surface p-5 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted uppercase tracking-wider block">
                Brand Accent Color
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={customAccent}
                  onChange={(e) => setCustomAccent(e.target.value)}
                  className="h-9 w-12 rounded cursor-pointer border border-glass bg-transparent p-0.5"
                />
                <input
                  type="text"
                  value={customAccent}
                  onChange={(e) => setCustomAccent(e.target.value)}
                  className="flex-1 rounded-lg border border-glass bg-main/5 px-3 py-2 text-xs font-mono text-main uppercase"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted uppercase tracking-wider block">
                Page Background Tint
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={customBg}
                  onChange={(e) => setCustomBg(e.target.value)}
                  className="h-9 w-12 rounded cursor-pointer border border-glass bg-transparent p-0.5"
                />
                <input
                  type="text"
                  value={customBg}
                  onChange={(e) => setCustomBg(e.target.value)}
                  className="flex-1 rounded-lg border border-glass bg-main/5 px-3 py-2 text-xs font-mono text-main uppercase"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted uppercase tracking-wider block">
                Main Text Heading
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={customMain}
                  onChange={(e) => setCustomMain(e.target.value)}
                  className="h-9 w-12 rounded cursor-pointer border border-glass bg-transparent p-0.5"
                />
                <input
                  type="text"
                  value={customMain}
                  onChange={(e) => setCustomMain(e.target.value)}
                  className="flex-1 rounded-lg border border-glass bg-main/5 px-3 py-2 text-xs font-mono text-main uppercase"
                />
              </div>
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button variant="primary" onClick={handleCustomApply}>
              <LuPalette className="h-4 w-4" />
              <span>Apply Custom Colors Live</span>
            </Button>
          </div>
        </div>
      </DemoSection>
    </div>
  );
}
