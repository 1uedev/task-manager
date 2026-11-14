import { useRef, useState, type FormEvent } from 'react';
import {
  linkGit, listGitChoices, listGitLinks, unlinkGit, type GitChoice, type GitLink, type GitLinks, type GitObjectKind, type GitTarget,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { hrefFor } from '../app/route.ts';
import { useLoad } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, SelectField, TextField, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { useGitProblem, useGitReason } from './GitPage.tsx';

const KIND_LABEL: Record<GitObjectKind, MessageKey> = {
  repository: 'gitLinks.kind.repository', commit: 'gitLinks.kind.commit', pull_request: 'gitLinks.kind.pull_request',
  merge_request: 'gitLinks.kind.merge_request',
};

// The kind of a Git object as the history names it with the internal identifier of the link: "commit:<id>" (TASK-112).
export function useGitHistoryText(): (from: string | null | undefined, to: string | null | undefined) => string {
  const { t } = useI18n();
  return (from, to) => {
    const raw = to ?? from ?? '';
    const at = raw.indexOf(':');
    const kind = raw.slice(0, at) as GitObjectKind;
    const label = KIND_LABEL[kind] ? t(KIND_LABEL[kind]) : kind;
    return t(to ? 'history.gitLinked' : 'history.gitUnlinked', { kind: label, id: raw.slice(at + 1, at + 9) });
  };
}

// Git objects linked to a ticket or a requirement (TASK-112). Everything shown of an object is read live with the own
// connection; an object the person cannot read shows only its kind, never path, title, number or address.
export function GitLinksPanel({ target, round = 0, onChanged }: {
  readonly target: GitTarget;
  readonly round?: number;
  readonly onChanged?: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useGitProblem();
  const [refresh, setRefresh] = useState(0);
  const key = target.kind === 'ticket' ? `${target.projectId}:${target.number}` : target.id;
  // "Aktualisieren" reads past the cache of the server; every other load may use it.
  const forced = useRef(false);
  const [links, reload] = useLoad(() => {
    const fresh = forced.current;
    forced.current = false;
    return listGitLinks(api, target, fresh);
  }, [api, key, round, refresh]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  if (links.state === 'ok' && !links.value.readable && !links.value.writable) return null;

  function changed(text: string) {
    setNotice({ kind: 'success', text });
    reload();
    onChanged?.();
    heading.current?.focus();
  }

  async function unlink(link: GitLink, name: string) {
    if (!globalThis.confirm(t('gitLinks.unlinkConfirm', { name }))) return;
    setNotice(null);
    const result = await unlinkGit(api, target, link.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    changed(t('gitLinks.unlinked', { name }));
  }

  return (
    <section aria-labelledby={`git-links-${key}`} className="panel" data-testid="git-links">
      <h2 id={`git-links-${key}`} ref={heading} tabIndex={-1}>{t('gitLinks.title')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={links} retry={reload}>
        {(value) => (
          <>
            {value.readable ? <LinkList value={value} onUnlink={(link, name) => void unlink(link, name)} /> : <p className="hint">{t('gitLinks.hidden')}</p>}
            {value.readable && value.links.length > 0 && (
              <p className="actions">
                <button type="button" className="secondary" onClick={() => {
                  forced.current = true;
                  setRefresh((count) => count + 1);
                }}>{t('gitLinks.refresh')}</button>
              </p>
            )}
            {value.writable && <LinkForm target={target} onLinked={(text) => changed(text)} />}
          </>
        )}
      </LoadedView>
    </section>
  );
}

function LinkList({ value, onUnlink }: { readonly value: GitLinks; readonly onUnlink: (link: GitLink, name: string) => void }) {
  const { t, language } = useI18n();
  const reason = useGitReason();
  if (value.links.length === 0) return <EmptyText text={t('gitLinks.empty')} />;
  return (
    <ul className="item-list" data-testid="git-link-list">
      {value.links.map((link, index) => {
        const kind = t(KIND_LABEL[link.kind]);
        // The name of a link for buttons: what it shows, or its kind and position when it is not available.
        const name = link.available ? title(link) : `${kind} ${index + 1}`;
        return (
          <li key={link.id} className="item-row" data-link={link.id} data-kind={link.kind}>
            {link.available ? (
              <>
                <span className="item-title">
                  {link.url ? <a href={link.url} target="_blank" rel="noopener noreferrer">{name}</a> : name}
                  {link.change && <span className="badge" data-testid="git-link-state"> {t(`gitLinks.state.${link.change.state}` as MessageKey)}</span>}
                </span>
                <span className="hint">{details(link)}</span>
              </>
            ) : (
              <>
                <span className="item-title">{t('gitLinks.unavailable')} <span className="hint">({kind})</span></span>
                <span className="hint" data-testid="git-link-reason">
                  {link.reason === 'git.rate_limited' ? t('gitLinks.rateLimited') : reason(link.reason)}
                  {(link.reason === 'git.not_connected' || link.reason === 'git.connection_renew')
                    && <> · <a href={hrefFor('git')}>{t('gitRepos.toConnections')}</a></>}
                </span>
              </>
            )}
            {value.writable && (
              <button type="button" className="secondary danger" aria-label={t('gitLinks.unlinkAria', { name })} onClick={() => onUnlink(link, name)}>
                {t('gitLinks.unlink')}
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );

  function title(link: GitLink): string {
    const path = link.repository?.path ?? '';
    if (link.commit) return t('gitLinks.commit', { path, sha: link.commit.shortSha });
    if (link.change) return t(link.kind === 'merge_request' ? 'gitLinks.merge' : 'gitLinks.pull', { path, number: link.change.number });
    return path;
  }

  function details(link: GitLink): string {
    const parts: string[] = [];
    if (link.commit?.message) parts.push(link.commit.message);
    if (link.change?.title) parts.push(link.change.title);
    const author = link.commit?.author ?? link.change?.author;
    if (author) parts.push(t('gitLinks.by', { author }));
    if (link.commit?.committedAt) parts.push(formatDateTime(link.commit.committedAt, language));
    parts.push(link.providerName ?? '');
    return parts.filter((part) => part !== '').join(' · ');
  }
}

function LinkForm({ target, onLinked }: { readonly target: GitTarget; readonly onLinked: (text: string) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useGitProblem();
  const [address, setAddress] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [choosing, setChoosing] = useState(false);
  const [choices, setChoices] = useState<readonly GitChoice[] | null>(null);
  const [release, setRelease] = useState('');
  const [kind, setKind] = useState<GitObjectKind>('commit');
  const [objectKey, setObjectKey] = useState('');

  const readable = (choices ?? []).filter((choice) => choice.available && choice.repository !== null);
  const chosen = readable.find((choice) => choice.releaseId === release) ?? readable[0];
  const kinds: GitObjectKind[] = ['commit', chosen?.providerKind === 'github' ? 'pull_request' : 'merge_request', 'repository'];
  const shownKind = kinds.includes(kind) ? kind : 'commit';

  async function submit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setError(null);
    if (!choosing && address.trim() === '') return setError(t('gitLinks.addressRequired'));
    if (choosing && chosen === undefined) return;
    if (choosing && shownKind !== 'repository' && objectKey.trim() === '') return setError(t('gitLinks.keyRequired'));
    setBusy(true);
    const result = await linkGit(api, target, choosing && chosen
      ? { releaseId: chosen.releaseId, kind: shownKind, key: shownKind === 'repository' ? '' : objectKey.trim() }
      : { url: address.trim() });
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setAddress('');
    setObjectKey('');
    onLinked(t('gitLinks.linked', { kind: t(KIND_LABEL[result.data.value.kind]) }));
  }

  async function choose() {
    setChoosing(true);
    setNotice(null);
    setError(null);
    const result = await listGitChoices(api, target);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setChoices(result.data.value);
  }

  return (
    <form className="stack" onSubmit={(event) => void submit(event)} noValidate data-testid="git-link-form">
      {choosing ? (
        choices === null ? null : readable.length === 0 ? <p className="hint">{t('gitLinks.noChoices')}</p> : (
          <>
            <SelectField label={t('gitLinks.repository')} value={chosen?.releaseId ?? ''} onChange={setRelease}
              options={readable.map((choice) => ({ value: choice.releaseId, label: `${choice.repository?.path ?? ''} (${choice.providerName ?? ''})` }))} />
            <SelectField label={t('gitLinks.objectKind')} value={shownKind} onChange={(next) => setKind(next as GitObjectKind)}
              options={kinds.map((item) => ({ value: item, label: t(KIND_LABEL[item]) }))} />
            {shownKind !== 'repository' && (
              <TextField label={t(shownKind === 'commit' ? 'gitLinks.sha' : 'gitLinks.number')} value={objectKey} onChange={setObjectKey}
                error={error} maxLength={64} autoComplete="off" />
            )}
          </>
        )
      ) : (
        <TextField label={t('gitLinks.address')} hint={t('gitLinks.addressHint')} value={address} onChange={setAddress} error={error}
          maxLength={2000} autoComplete="off" />
      )}
      <NoticeView notice={notice} />
      <p className="actions">
        <button type="submit" disabled={busy || (choosing && chosen === undefined)}>{t('gitLinks.link')}</button>
        <button type="button" className="secondary" disabled={busy} onClick={() => {
          if (choosing) {
            setChoosing(false);
            setError(null);
          } else {
            void choose();
          }
        }}>{t(choosing ? 'gitLinks.byAddress' : 'gitLinks.choose')}</button>
      </p>
    </form>
  );
}
