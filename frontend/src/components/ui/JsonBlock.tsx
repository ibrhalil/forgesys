/**
 * Pretty-printed JSON block (K-55 F3) — request bodies and other machine payloads.
 * Non-JSON input falls back to the raw text.
 */
export function JsonBlock({ value }: { value: string }) {
  let pretty = value;
  try {
    pretty = JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    // not JSON — render the raw text
  }
  return (
    <pre className="overflow-x-auto rounded-md border border-glass bg-bg/40 p-3 font-mono text-xs leading-relaxed text-muted">
      {pretty}
    </pre>
  );
}
