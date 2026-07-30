/** A single selectable option. Generic over the value type (id, string, etc.). */
export interface SelectOption<V = string> {
  value: V;
  label: string;
}

/**
 * Build {@link SelectOption}s from any record list using the provided accessors.
 * Kept out of the SelectInput component module so react-fast-refresh sees only a
 * component export there (oxlint `react/only-export-components`).
 */
export function toOptions<T, V extends string>(
  items: readonly T[],
  getValue: (item: T) => V,
  getLabel: (item: T) => string,
): SelectOption<V>[] {
  return items.map((item) => ({ value: getValue(item), label: getLabel(item) }));
}
