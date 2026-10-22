import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { onForbidden, onSessionLost, readSession, type SessionState } from '../api/client.ts';
import { logout as endSession, readDirectory, type DirectoryEntry } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';

// The login of this browser as the server reports it. The interface never decides on its own that someone is logged in:
// every state comes from GET /session, and a 401 on any other call returns to the login.
export type SessionView =
  | { readonly state: 'loading' }
  | { readonly state: 'offline' }
  | { readonly state: 'anonymous'; readonly reason: 'none' | 'expired' | 'loggedOut' }
  | { readonly state: 'pending'; readonly session: SessionState }
  | { readonly state: 'ready'; readonly session: SessionState };

interface SessionContext {
  readonly view: SessionView;
  refresh(): Promise<void>;
  signOut(): Promise<void>;
  // Names of the accounts for showing who is a member, assigned or changed something; empty until loaded.
  readonly directory: ReadonlyMap<string, DirectoryEntry>;
  readonly reloadDirectory: () => void;
  readonly has: (right: string) => boolean;
}

const Context = createContext<SessionContext | null>(null);

export function SessionProvider({ children }: { readonly children: ReactNode }) {
  const { api, reload } = useBrand();
  const [view, setView] = useState<SessionView>({ state: 'loading' });
  const [directory, setDirectory] = useState<ReadonlyMap<string, DirectoryEntry>>(new Map());
  const [directoryRound, setDirectoryRound] = useState(0);

  const refresh = useCallback(async () => {
    const result = await readSession(api);
    if (result.kind !== 'ok') return setView({ state: 'offline' });
    const session = result.data;
    if (!session.authenticated) return setView((current) => ({ state: 'anonymous', reason: current.state === 'anonymous' ? current.reason : 'none' }));
    setView(session.full ? { state: 'ready', session } : { state: 'pending', session });
  }, [api]);

  useEffect(() => {
    void refresh();
    const stopLost = onSessionLost(() => setView({ state: 'anonymous', reason: 'expired' }));
    // A refusal means the rights may have changed; the navigation and every offered action follow the fresh state.
    const stopForbidden = onForbidden(() => void refresh());
    return () => {
      stopLost();
      stopForbidden();
    };
  }, [refresh]);

  // The company appearance needs a complete login; it is loaded again whenever the login changes.
  const ready = view.state === 'ready';
  useEffect(() => {
    reload();
  }, [ready, reload]);

  useEffect(() => {
    if (!ready) {
      setDirectory(new Map());
      return;
    }
    const abort = new AbortController();
    void readDirectory(api).then((result) => {
      if (!abort.signal.aborted && result.kind === 'ok') setDirectory(new Map(result.data.value.map((entry) => [entry.id, entry])));
    });
    return () => abort.abort();
  }, [api, ready, directoryRound]);

  // Logging out also removes the CSRF cookie. GET /session issues a new one for the anonymous session before the login
  // form is shown again, whatever stage the login had reached; otherwise the next login would be refused.
  const signOut = useCallback(async () => {
    await endSession(api);
    await readSession(api);
    setView({ state: 'anonymous', reason: 'loggedOut' });
  }, [api]);

  const value = useMemo<SessionContext>(() => ({
    view,
    refresh,
    signOut,
    directory,
    reloadDirectory: () => setDirectoryRound((round) => round + 1),
    has: (right) => view.state === 'ready' && view.session.rights.includes(right),
  }), [view, refresh, signOut, directory]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useSession(): SessionContext {
  const value = useContext(Context);
  if (!value) throw new Error('SessionProvider is missing');
  return value;
}

// The name to show for an account: the display name, else the login name, else a shortened identifier.
export function useAccountName(): (id: string | null | undefined) => string {
  const { directory } = useSession();
  return useCallback((id) => {
    if (!id) return '';
    const entry = directory.get(id);
    return entry ? entry.displayName ?? entry.username : id.slice(0, 8);
  }, [directory]);
}
