import { create } from 'zustand';
import type { Locale } from '../lib/i18n/messages';

const STORAGE_KEY = 'sf_locale';

function resolveInitialLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'tr' || stored === 'en') return stored;
  return 'tr';
}

interface LocaleState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  toggle: () => void;
}

/** UI language of the app. Persisted in localStorage; default Turkish. */
export const useLocaleStore = create<LocaleState>((set, get) => ({
  locale: resolveInitialLocale(),

  setLocale: (locale) => {
    localStorage.setItem(STORAGE_KEY, locale);
    document.documentElement.lang = locale;
    set({ locale });
  },

  toggle: () => {
    get().setLocale(get().locale === 'tr' ? 'en' : 'tr');
  },
}));
