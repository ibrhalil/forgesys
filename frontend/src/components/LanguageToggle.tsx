import { useLocaleStore } from '../store/localeStore';
import type { Locale } from '../lib/i18n';
import { useT } from '../lib/i18n';
import { cn } from '../lib/cn';

const LOCALES: Locale[] = ['tr', 'en'];

/** Tiny segmented TR/EN switch; lives in the AppShell footer. */
export function LanguageToggle() {
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);
  const { t } = useT();

  return (
    <div
      role="group"
      aria-label={t('common.language')}
      className="flex overflow-hidden rounded-md border border-glass"
    >
      {LOCALES.map((l) => (
        <button
          key={l}
          type="button"
          onClick={() => setLocale(l)}
          aria-pressed={locale === l}
          className={cn(
            'px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide transition-colors',
            locale === l ? 'bg-accent/10 text-accent' : 'text-muted hover:text-accent',
          )}
        >
          {l}
        </button>
      ))}
    </div>
  );
}
