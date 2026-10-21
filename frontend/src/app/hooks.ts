import { useCallback, useEffect, useRef, useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import type { Result, Versioned } from '../api/product.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { setNavigationGuard } from './route.ts';

export type Loaded<T> =
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly value: T; readonly etag: string | null };

// Loads on mount and whenever a dependency changes; an older answer never replaces a newer one.
export function useLoad<T>(load: () => Promise<Result<T>>, deps: readonly unknown[]): readonly [Loaded<T>, () => void, (next: Versioned<T>) => void] {
  const [state, setState] = useState<Loaded<T>>({ state: 'loading' });
  const [round, setRound] = useState(0);
  const latest = useRef(0);
  useEffect(() => {
    const ticket = ++latest.current;
    setState((current) => (current.state === 'ok' ? current : { state: 'loading' }));
    void load().then((result) => {
      if (ticket !== latest.current) return;
      setState(result.kind === 'ok' ? { state: 'ok', value: result.data.value, etag: result.data.etag } : { state: 'problem', problem: result.problem });
    });
  }, [...deps, round]);
  const reload = useCallback(() => setRound((value) => value + 1), []);
  const replace = useCallback((next: Versioned<T>) => {
    latest.current += 1;
    setState({ state: 'ok', value: next.value, etag: next.etag });
  }, []);
  return [state, reload, replace] as const;
}

// Unsaved input: leaving the page or the browser tab asks first, in the language of the interface.
export function useUnsavedGuard(dirty: boolean): void {
  const { t } = useI18n();
  useEffect(() => {
    if (!dirty) return;
    const message = t('unsaved.confirm');
    const beforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    globalThis.addEventListener('beforeunload', beforeUnload);
    setNavigationGuard(() => globalThis.confirm(message));
    return () => {
      globalThis.removeEventListener('beforeunload', beforeUnload);
      setNavigationGuard(null);
    };
  }, [dirty, t]);
}
