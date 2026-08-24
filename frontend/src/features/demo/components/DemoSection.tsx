import type { ReactNode } from 'react';
import { useState } from 'react';

interface DemoSectionProps {
  title: string;
  description: string;
  code: string;
  children: ReactNode;
}

/**
 * Reusable demo example container: title, description, collapsible code snippet,
 * and a live render area. No external syntax highlighting dependency — plain <pre>.
 */
export function DemoSection({ title, description, code, children }: DemoSectionProps) {
  const [codeOpen, setCodeOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section className="rounded-xl border border-glass bg-surface shadow-sm shadow-black/[0.03]">
      {/* Header */}
      <div className="border-b border-glass px-6 py-4">
        <h2 className="text-base font-semibold text-main">{title}</h2>
        <p className="mt-0.5 text-sm text-muted">{description}</p>
      </div>

      {/* Live preview */}
      <div className="p-6">{children}</div>

      {/* Code footer */}
      <div className="border-t border-glass bg-bg/30">
        <div className="flex items-center justify-between px-6 py-2">
          <button
            type="button"
            onClick={() => setCodeOpen((v) => !v)}
            className="text-xs font-medium text-muted transition-colors hover:text-main"
          >
            {codeOpen ? '▲ Hide code' : '▼ Show code'}
          </button>
          {codeOpen && (
            <button
              type="button"
              onClick={handleCopy}
              className="text-xs font-medium text-accent transition-colors hover:underline"
            >
              {copied ? '✓ Copied!' : 'Copy'}
            </button>
          )}
        </div>
        {codeOpen && (
          <pre className="overflow-x-auto border-t border-glass px-6 py-4 text-xs leading-relaxed text-main/80 bg-main/[0.02]">
            <code>{code}</code>
          </pre>
        )}
      </div>
    </section>
  );
}
