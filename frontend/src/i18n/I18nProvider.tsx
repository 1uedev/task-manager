import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { resolveLanguage, translate, type Params } from './core.ts';
import { registry, supportedLanguages, type MessageKey } from './index.ts';
import { readStoredLanguage, storeLanguage } from '../app/preferences.ts';

interface I18n {
  readonly language: string;
  readonly languages: readonly string[];
  readonly setLanguage: (language: string) => void;
  readonly t: (key: MessageKey, params?: Params) => string;
}

const Context = createContext<I18n | null>(null);

export function I18nProvider({ children }: { readonly children: ReactNode }) {
  const [language, setLanguageState] = useState(() =>
    resolveLanguage(readStoredLanguage(), globalThis.navigator?.languages ?? [], supportedLanguages),
  );

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  const setLanguage = useCallback((next: string) => {
    if (!supportedLanguages.some((supported) => supported === next)) return;
    storeLanguage(next);
    setLanguageState(next);
  }, []);

  const value = useMemo<I18n>(
    () => ({
      language,
      languages: supportedLanguages,
      setLanguage,
      t: (key, params) =>
        translate(registry, language, key, params, (missingLanguage, missingKey) => {
          console.warn(`Missing translation ${missingLanguage}:${missingKey}`);
        }),
    }),
    [language, setLanguage],
  );

  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useI18n(): I18n {
  const value = useContext(Context);
  if (!value) throw new Error('I18nProvider is missing');
  return value;
}
