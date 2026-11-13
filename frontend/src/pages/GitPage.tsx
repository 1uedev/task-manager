import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  checkGitProvider, connectGitProvider, createGitProvider, disconnectGitProvider, listGitAdminProviders, listGitProviders, readGitStatus,
  removeGitProvider, updateGitProvider, type GitAdminProvider, type GitKind, type GitProvider, type GitProviderRequest, type GitStatus,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

const KINDS: readonly GitKind[] = ['github', 'gitlab_com', 'gitlab'];

// The text for a code of the Git link; an unknown code gets the general one.
export function useGitReason(): (code: string | null | undefined) => string {
  const { t } = useI18n();
  return (code) => {
    const key = `git.reason.${(code ?? '').replace(/^git\./, '')}`;
    return Object.hasOwn(de, key) ? t(key as MessageKey) : t('git.reason.unknown');
  };
}

// The problem of a refused request of the Git link: its code when the page knows it, otherwise the general text.
export function useGitProblem(): (problem: ProblemInfo) => string {
  const reason = useGitReason();
  const text = useProblemText();
  return (problem) => (problem.code !== null && problem.code.startsWith('git.') && Object.hasOwn(de, `git.reason.${problem.code.slice(4)}`)
    ? reason(problem.code) : text(problem));
}

// What the provider sent the browser back with, read once from the address and then removed from it.
function takeOutcome(): { readonly connected: string | null; readonly error: string | null } {
  const hash = globalThis.location?.hash ?? '';
  const mark = hash.indexOf('?');
  if (mark < 0) return { connected: null, error: null };
  const query = new URLSearchParams(hash.slice(mark + 1));
  globalThis.history?.replaceState(null, '', `${globalThis.location.pathname}${globalThis.location.search}#/git`);
  return { connected: query.get('connected'), error: query.get('error') };
}

// The page "Git" (TASK-111): the own connections with GitHub and GitLab for every internal person, and for git.manage the
// provider instances. Tokens and secrets never reach this page.
export function GitPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [status, reloadStatus] = useLoad(() => readGitStatus(api), [api]);
  const outcome = useRef(takeOutcome());
  const [adminRound, setAdminRound] = useState(0);
  return (
    <>
      <PageHead title={t('git.title')} intro={t('git.intro')} />
      <LoadedView loaded={status} retry={reloadStatus}>
        {(value) => (
          <>
            {!value.secretKey && <p className="mode-notice" role="status" data-testid="git-no-key">{t('git.noSecretKey')}</p>}
            {value.callbackUrl === null && <p className="mode-notice" role="status" data-testid="git-no-url">{t('git.noPublicUrl')}</p>}
            <Connections outcome={outcome.current} round={adminRound} />
            {value.manage && <Administration status={value} onChanged={() => setAdminRound((round) => round + 1)} />}
          </>
        )}
      </LoadedView>
    </>
  );
}

function Connections({ outcome, round }: { readonly outcome: { readonly connected: string | null; readonly error: string | null }; readonly round: number }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const reason = useGitReason();
  const problem = useGitProblem();
  const [providers, reload, replace] = useLoad(() => listGitProviders(api), [api, round]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const shown = useRef(false);

  useEffect(() => {
    if (shown.current || providers.state !== 'ok') return;
    shown.current = true;
    if (outcome.error !== null) setNotice({ kind: 'info', text: t('git.connectFailed', { reason: reason(outcome.error) }) });
    else if (outcome.connected !== null) {
      const name = providers.value.find((provider) => provider.id === outcome.connected)?.name ?? '';
      setNotice({ kind: 'success', text: t('git.connected', { name }) });
    }
  }, [providers.state]);

  async function connect(provider: GitProvider) {
    setNotice(null);
    setBusy(true);
    const result = await connectGitProvider(api, provider.id);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    globalThis.location.assign(result.data.value.authorizeUrl);
  }

  async function disconnect(provider: GitProvider, list: readonly GitProvider[]) {
    setNotice(null);
    setBusy(true);
    const result = await disconnectGitProvider(api, provider.id);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    replace({ value: list.map((item) => (item.id === provider.id ? { ...item, connection: 'none', connectedAt: null } : item)), etag: null });
    setNotice({ kind: 'success', text: t('git.disconnected', { name: provider.name }) });
  }

  return (
    <section aria-labelledby="git-connections" className="panel">
      <h2 id="git-connections">{t('git.connections')}</h2>
      <p className="hint">{t('git.connectionsIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={providers} retry={reload}>
        {(list) => {
          if (list.length === 0) return <EmptyText text={t('git.noProviders')} />;
          return (
          <ul className="item-list" data-testid="git-connections">
            {list.map((provider) => (
              <li key={provider.id} className="item-row" data-provider={provider.id}>
                <span className="item-title">{provider.name}</span>
                <span className="hint">{t(`git.kind.${provider.kind}` as MessageKey)} · {provider.baseUrl}</span>
                <span className="hint" data-testid="git-connection-state">
                  {provider.connection === 'active' && provider.connectedAt
                    ? t('git.state.active', { date: formatDateTime(provider.connectedAt, language) })
                    : t(provider.connection === 'renew' ? 'git.state.renew' : 'git.state.none')}
                </span>
                {provider.connection !== 'active' && (
                  <button type="button" disabled={busy} onClick={() => void connect(provider)}>
                    {t(provider.connection === 'renew' ? 'git.renew' : 'git.connect', { name: provider.name })}
                  </button>
                )}
                {provider.connection !== 'none' && (
                  <button type="button" className="secondary" disabled={busy} onClick={() => void disconnect(provider, list)}>
                    {t('git.disconnect', { name: provider.name })}
                  </button>
                )}
              </li>
            ))}
          </ul>
          );
        }}
      </LoadedView>
    </section>
  );
}

function Administration({ status, onChanged }: { readonly status: GitStatus; readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [providers, reload] = useLoad(() => listGitAdminProviders(api), [api]);
  const [editing, setEditing] = useState<GitAdminProvider | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const problem = useGitProblem();
  const reason = useGitReason();
  const heading = useRef<HTMLHeadingElement>(null);

  const changed = (text: string) => {
    setEditing(null);
    setNotice({ kind: 'success', text });
    reload();
    onChanged();
    heading.current?.focus();
  };

  async function check(provider: GitAdminProvider) {
    setNotice(null);
    const result = await checkGitProvider(api, provider.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    const value = result.data.value;
    if (value.ok) {
      setNotice({ kind: 'success', text: value.version ? t('git.checkOkVersion', { name: provider.name, version: value.version }) : t('git.checkOk', { name: provider.name }) });
    } else {
      setNotice({ kind: 'info', text: t('git.checkFailed', { name: provider.name, reason: reason(value.code) }) });
    }
  }

  async function remove(provider: GitAdminProvider) {
    if (!globalThis.confirm(t('git.removeConfirm', { name: provider.name }))) return;
    setNotice(null);
    const result = await removeGitProvider(api, provider.id, provider.version);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) reload();
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    changed(t('git.removed', { name: provider.name }));
  }

  return (
    <section aria-labelledby="git-admin" className="panel" data-testid="git-admin">
      <h2 id="git-admin" ref={heading} tabIndex={-1}>{t('git.admin')}</h2>
      <p className="hint">{t('git.adminIntro')}</p>
      {status.callbackUrl !== null && (
        <p className="hint" data-testid="git-callback">{t('git.callbackUrl')} <code>{status.callbackUrl}</code></p>
      )}
      <NoticeView notice={notice} />
      <LoadedView loaded={providers} retry={reload}>
        {(list) => (
          <>
            {list.length === 0 ? <EmptyText text={t('git.noProviders')} /> : (
              <ul className="item-list" data-testid="git-admin-providers">
                {list.map((provider) => (
                  <li key={provider.id} className="item-row">
                    <span className="item-title">{provider.name}</span>
                    <span className="hint">{t(`git.kind.${provider.kind}` as MessageKey)} · {provider.baseUrl}</span>
                    <span className="hint">{provider.secretSet ? t('git.secretSet') : t('git.secretMissing')}</span>
                    <button type="button" className="secondary" onClick={() => void check(provider)}>{t('git.check', { name: provider.name })}</button>
                    <button type="button" className="secondary" onClick={() => setEditing(provider)}>{t('git.edit', { name: provider.name })}</button>
                    <button type="button" className="secondary" onClick={() => void remove(provider)}>{t('git.remove', { name: provider.name })}</button>
                  </li>
                ))}
              </ul>
            )}
            {editing === null && <p className="actions"><button type="button" onClick={() => setEditing('new')}>{t('git.add')}</button></p>}
            {editing !== null && (
              <ProviderForm key={editing === 'new' ? 'new' : `${editing.id}/${editing.version}`} provider={editing === 'new' ? null : editing}
                onCancel={() => setEditing(null)} onSaved={(saved, created) => changed(t(created ? 'git.created' : 'git.saved', { name: saved.name }))}
                onConflict={reload} />
            )}
          </>
        )}
      </LoadedView>
    </section>
  );
}

function ProviderForm({ provider, onCancel, onSaved, onConflict }: {
  readonly provider: GitAdminProvider | null;
  readonly onCancel: () => void;
  readonly onSaved: (saved: GitAdminProvider, created: boolean) => void;
  readonly onConflict: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useGitProblem();
  const [kind, setKind] = useState<GitKind>(provider?.kind ?? 'github');
  const [name, setName] = useState(provider?.name ?? '');
  const [baseUrl, setBaseUrl] = useState(provider?.kind === 'gitlab' ? provider.baseUrl : '');
  const [clientId, setClientId] = useState(provider?.clientId ?? '');
  const [secret, setSecret] = useState('');
  const [ca, setCa] = useState(provider?.caCertificate ?? '');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [violations, setViolations] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const first = useRef<HTMLDivElement>(null);
  const dirty = name !== (provider?.name ?? '') || clientId !== (provider?.clientId ?? '') || secret !== ''
    || ca !== (provider?.caCertificate ?? '') || (kind === 'gitlab' && baseUrl !== (provider?.kind === 'gitlab' ? provider.baseUrl : ''));
  useUnsavedGuard(dirty);

  useEffect(() => {
    first.current?.querySelector<HTMLElement>('select, input')?.focus();
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setViolations([]);
    const body: GitProviderRequest = {
      kind,
      name: name.trim(),
      baseUrl: kind === 'gitlab' ? baseUrl.trim() : null,
      clientId: clientId.trim(),
      caCertificate: kind === 'gitlab' && ca.trim() !== '' ? ca.trim() : null,
      ...(provider === null || secret !== '' ? { clientSecret: secret } : {}),
    };
    setBusy(true);
    const result = provider === null ? await createGitProvider(api, body) : await updateGitProvider(api, provider.id, provider.version, body);
    setBusy(false);
    if (result.kind === 'ok') return onSaved(result.data.value, provider === null);
    if (result.problem.status === 412) onConflict();
    setViolations(result.problem.violations ?? []);
    setNotice({ kind: 'info', text: problem(result.problem) });
  }

  const error = (field: string) => (violations.includes(`${field}.invalid`) ? t(`git.invalid.${field}` as MessageKey) : null);

  return (
    <form onSubmit={(event) => void save(event)} noValidate className="panel" data-testid="git-provider-form">
      <h3>{provider === null ? t('git.add') : t('git.editTitle', { name: provider.name })}</h3>
      <div ref={first}>
        <SelectField label={t('git.kind')} value={kind} disabled={provider !== null} onChange={(value) => setKind(KINDS.find((k) => k === value) ?? 'github')}
          options={KINDS.map((value) => ({ value, label: t(`git.kind.${value}` as MessageKey) }))} />
      </div>
      <TextField label={t('git.name')} value={name} onChange={setName} maxLength={80} required error={error('name')} autoComplete="off" />
      {kind === 'gitlab' && (
        <TextField label={t('git.baseUrl')} hint={t('git.baseUrlHint')} value={baseUrl} onChange={setBaseUrl} maxLength={300} required
          error={error('baseUrl')} autoComplete="off" />
      )}
      <TextField label={t('git.clientId')} hint={t(kind === 'github' ? 'git.clientIdHintGithub' : 'git.clientIdHintGitlab')} value={clientId}
        onChange={setClientId} maxLength={200} required error={error('clientId')} autoComplete="off" />
      <TextField label={t('git.clientSecret')} hint={provider?.secretSet ? t('git.clientSecretKeep') : t('git.clientSecretHint')} value={secret}
        onChange={setSecret} maxLength={1000} type="password" error={error('clientSecret')} autoComplete="new-password" />
      {kind === 'gitlab' && (
        <TextField label={t('git.caCertificate')} hint={t('git.caCertificateHint')} value={ca} onChange={setCa} multiline
          error={error('caCertificate')} />
      )}
      <NoticeView notice={notice} />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('git.save')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('git.cancel')}</button>
      </p>
    </form>
  );
}
