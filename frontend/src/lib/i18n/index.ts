import { messages, type Locale } from './messages';
import { useLocaleStore } from '../../store/localeStore';

export type MessageKey = keyof (typeof messages)['tr'];
export type { Locale } from './messages';

/**
 * Translate a key with optional `{param}` interpolation. Reads the current locale
 * from the store imperatively — fine for event handlers/toasts, but components must
 * render strings through {@link useT} so they re-render when the locale changes.
 * Unknown keys fall back to Turkish, then to the key itself (easy to spot gaps).
 */
export function t(key: MessageKey, params?: Record<string, string | number>): string {
  const locale = useLocaleStore.getState().locale;
  return translate(locale, key, params);
}

/**
 * Reactive translation hook. Subscribes to the locale store, so every component
 * using it re-renders when the user switches language.
 *
 *   const { t } = useT();
 *   t('users.title')
 *   t('common.showingRange', { from: 1, to: 10, total: 42 })
 */
export function useT() {
  const locale = useLocaleStore((s) => s.locale);
  return {
    locale,
    t: (key: MessageKey, params?: Record<string, string | number>) => translate(locale, key, params),
  };
}

function translate(locale: Locale, key: MessageKey, params?: Record<string, string | number>): string {
  const dict = messages[locale] as Record<string, string>;
  let text: string = dict[key] ?? (messages.tr as Record<string, string>)[key] ?? key;
  if (params) {
    for (const [name, value] of Object.entries(params)) {
      text = text.replaceAll(`{${name}}`, String(value));
    }
  }
  return text;
}
