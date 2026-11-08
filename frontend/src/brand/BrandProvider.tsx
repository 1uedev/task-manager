import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from 'react';
import { APPEARANCE_RIGHT, createServerApi, isBrandName, normalizeBrandName, readAppearance, readPublicAppearance, readSession, type LoadedAppearance, type PublicAppearance, type ServerApi } from '../api/client.ts';
import { readStoredTheme, storeTheme } from '../app/preferences.ts';
import { accentBrand, brandToCssVariables, DEFAULT_ACCENT, DEFAULT_NAME, isAccentColor, type Brand } from './brand.ts';

export type Theme = 'system' | 'light' | 'dark';
export const THEMES: readonly Theme[] = ['light', 'dark', 'system'];

// What the server said about the company appearance. Without a complete login the login page shows the public name, color
// and logo (TASK-081), or the default when they cannot be read, and the appearance page only offers a local preview; the
// server checks every change again.
export type Connection =
  | { readonly state: 'loading' }
  | { readonly state: 'offline' }
  | { readonly state: 'anonymous'; readonly shown: PublicAppearance | null }
  | { readonly state: 'ready'; readonly mayChangeAppearance: boolean; readonly loaded: LoadedAppearance };

interface BrandState {
  readonly brand: Brand;
  readonly accent: string;
  readonly savedAccent: string;
  readonly defaultAccent: string;
  readonly savedName: string;
  readonly defaultName: string;
  readonly connection: Connection;
  readonly api: ServerApi;
  // A color shown everywhere while the appearance page is open, before it is saved. null shows the saved one.
  readonly preview: (color: string | null) => void;
  // A name shown everywhere while the appearance page is open, before it is saved. null shows the saved one.
  readonly previewName: (name: string | null) => void;
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

// The version in an ETag such as "9" or W/"9"; null when it is not a whole number.
function etagVersion(etag: string): number | null {
  const match = /^(?:W\/)?"(\d+)"$/.exec(etag.trim());
  return match ? Number(match[1]) : null;
}

export function BrandProvider({ children, api: given }: { readonly children: ReactNode; readonly api?: ServerApi }) {
  const api = useMemo(() => given ?? createServerApi(), [given]);
  const [connection, setConnection] = useState<Connection>({ state: 'loading' });
  const [previewAccent, setPreviewAccent] = useState<string | null>(null);
  const [previewedName, setPreviewedName] = useState<string | null>(null);
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
      if (!session.data.full) {
        // The login page and the password change read what the company shows before a login; without it the default stays.
        const shown = await readPublicAppearance(api, abort.signal);
        if (abort.signal.aborted) return;
        return setConnection({ state: 'anonymous', shown: shown.kind === 'ok' ? shown.data : null });
      }
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
  const shown = connection.state === 'anonymous' ? connection.shown : null;
  const savedAccent = saved?.accentColor ?? shown?.accentColor ?? DEFAULT_ACCENT;
  const savedName = saved?.name ?? shown?.name ?? DEFAULT_NAME;
  const accent = previewAccent ?? savedAccent;
  const name = previewedName ?? savedName;
  const logoUrl = saved?.logo?.url ?? shown?.logoUrl ?? null;

  const brand = useMemo(() => accentBrand(accent, logoUrl, dark ? 'dark' : 'light', name), [accent, logoUrl, dark, name]);

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
  const previewName = useCallback((next: string | null) => {
    setPreviewedName(next !== null && isBrandName(normalizeBrandName(next)) ? normalizeBrandName(next) : null);
  }, []);
  // An answer that arrives late never takes back a newer version this window already knows (TASK-090, T1 and T2 of
  // docs/REVIEW_TASK-089_PR-263.md): the version only grows, so an older one is dropped and an equal one is taken.
  const accept = useCallback((loaded: LoadedAppearance) => {
    setConnection((current) => {
      if (current.state !== 'ready') return current;
      const known = etagVersion(current.loaded.etag);
      const offered = etagVersion(loaded.etag);
      if (known !== null && offered !== null && offered < known) return current;
      return { ...current, loaded };
    });
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
      savedName,
      defaultName: saved?.defaultName ?? DEFAULT_NAME,
      connection,
      api,
      preview,
      previewName,
      accept,
      reload,
      theme,
      setTheme,
      dark,
    }),
    [brand, accent, savedAccent, savedName, saved, connection, api, preview, previewName, accept, reload, theme, setTheme, dark],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useBrand(): BrandState {
  const value = useContext(Context);
  if (!value) throw new Error('BrandProvider is missing');
  return value;
}
