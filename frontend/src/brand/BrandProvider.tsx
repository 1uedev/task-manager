import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from 'react';
import { APPEARANCE_RIGHT, createServerApi, readAppearance, readSession, type LoadedAppearance, type ServerApi } from '../api/client.ts';
import { readStoredTheme, storeTheme } from '../app/preferences.ts';
import { accentBrand, brandToCssVariables, DEFAULT_ACCENT, isAccentColor, type Brand } from './brand.ts';

export type Theme = 'system' | 'light' | 'dark';
export const THEMES: readonly Theme[] = ['light', 'dark', 'system'];

// What the server said about the company appearance. Without a complete login the interface keeps the default
// accent and the appearance page only offers a local preview; the server checks every change again.
export type Connection =
  | { readonly state: 'loading' }
  | { readonly state: 'offline' }
  | { readonly state: 'anonymous' }
  | { readonly state: 'ready'; readonly mayChangeAppearance: boolean; readonly loaded: LoadedAppearance };

interface BrandState {
  readonly brand: Brand;
  readonly accent: string;
  readonly savedAccent: string;
  readonly defaultAccent: string;
  readonly connection: Connection;
  readonly api: ServerApi;
  // A color shown everywhere while the appearance page is open, before it is saved. null shows the saved one.
  readonly preview: (color: string | null) => void;
  readonly accept: (loaded: LoadedAppearance) => void;
  readonly reload: () => void;
  readonly theme: Theme;
  readonly setTheme: (theme: Theme) => void;
  readonly dark: boolean;
}

const Context = createContext<BrandState | null>(null);

function isTheme(value: string | null): value is Theme {
  return THEMES.some((theme) => theme === value);
}

function useSystemDark(): boolean {
  const query = useMemo(() => (typeof globalThis.matchMedia === 'function' ? globalThis.matchMedia('(prefers-color-scheme: dark)') : null), []);
  const [dark, setDark] = useState(query?.matches ?? false);
  useEffect(() => {
    if (!query) return;
    const onChange = () => setDark(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [query]);
  return dark;
}

export function BrandProvider({ children, api: given }: { readonly children: ReactNode; readonly api?: ServerApi }) {
  const api = useMemo(() => given ?? createServerApi(), [given]);
  const [connection, setConnection] = useState<Connection>({ state: 'loading' });
  const [previewAccent, setPreviewAccent] = useState<string | null>(null);
  const [round, setRound] = useState(0);
  const [theme, setThemeState] = useState<Theme>(() => {
    const stored = readStoredTheme();
    return isTheme(stored) ? stored : 'system';
  });
  const systemDark = useSystemDark();
  const dark = theme === 'dark' || (theme === 'system' && systemDark);

  useEffect(() => {
    const abort = new AbortController();
    void (async () => {
      const session = await readSession(api, abort.signal);
      if (abort.signal.aborted) return;
      if (session.kind !== 'ok') return setConnection({ state: 'offline' });
      if (!session.data.full) return setConnection({ state: 'anonymous' });
      const appearance = await readAppearance(api, abort.signal);
      if (abort.signal.aborted) return;
      if (appearance.kind !== 'ok') return setConnection({ state: 'offline' });
      setConnection({ state: 'ready', mayChangeAppearance: session.data.rights.includes(APPEARANCE_RIGHT), loaded: appearance.data });
    })().catch(() => {
      // Aborted because the provider went away or a newer load started.
    });
    return () => abort.abort();
  }, [api, round]);

  const saved = connection.state === 'ready' ? connection.loaded.appearance : null;
  const savedAccent = saved?.accentColor ?? DEFAULT_ACCENT;
  const accent = previewAccent ?? savedAccent;
  const logoUrl = saved?.logo?.url ?? null;

  const brand = useMemo(() => accentBrand(accent, logoUrl, dark ? 'dark' : 'light'), [accent, logoUrl, dark]);

  useLayoutEffect(() => {
    const root = document.documentElement;
    const variables = brandToCssVariables(brand);
    for (const [name, value] of Object.entries(variables)) root.style.setProperty(name, value);
    root.dataset.theme = dark ? 'dark' : 'light';
    return () => {
      for (const name of Object.keys(variables)) root.style.removeProperty(name);
      delete root.dataset.theme;
    };
  }, [brand, dark]);

  const preview = useCallback((color: string | null) => {
    setPreviewAccent(color !== null && isAccentColor(color) ? color.toLowerCase() : null);
  }, []);
  const accept = useCallback((loaded: LoadedAppearance) => {
    setConnection((current) => (current.state === 'ready' ? { ...current, loaded } : current));
  }, []);
  const reload = useCallback(() => setRound((value) => value + 1), []);
  const setTheme = useCallback((next: Theme) => {
    storeTheme(next);
    setThemeState(next);
  }, []);

  const value = useMemo<BrandState>(
    () => ({
      brand,
      accent,
      savedAccent,
      defaultAccent: saved?.defaultAccentColor ?? DEFAULT_ACCENT,
      connection,
      api,
      preview,
      accept,
      reload,
      theme,
      setTheme,
      dark,
    }),
    [brand, accent, savedAccent, saved, connection, api, preview, accept, reload, theme, setTheme, dark],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useBrand(): BrandState {
  const value = useContext(Context);
  if (!value) throw new Error('BrandProvider is missing');
  return value;
}
