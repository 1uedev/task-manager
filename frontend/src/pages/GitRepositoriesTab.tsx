import { useRef, useState, type FormEvent } from 'react';
import {
  listGitCandidates, listGitProviders, listGitReleases, releaseGitRepository, withdrawGitRepository, type GitRelease, type GitRepository,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { EmptyText, LoadedView, NoticeView, SelectField, TextField, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { useGitProblem, useGitReason } from './GitPage.tsx';

// The repositories released for a project (TASK-111, rule 6). Name, path and address come live from the provider with the
// own connection; without it a release shows only that it is not available to the person, never what it is.
export function GitRepositoriesTab({ projectId }: { readonly projectId: string }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const reason = useGitReason();
  const problem = useGitProblem();
  const [releases, reload] = useLoad(() => listGitReleases(api, projectId), [api, projectId]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  async function withdraw(release: GitRelease) {
    const label = release.repository?.path ?? release.externalId;
    if (!globalThis.confirm(t('gitRepos.withdrawConfirm', { name: label }))) return;
    setNotice(null);
    const result = await withdrawGitRepository(api, projectId, release.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setNotice({ kind: 'success', text: t('gitRepos.withdrawn', { name: label }) });
    reload();
    heading.current?.focus();
  }

  return (
    <section aria-labelledby="git-repositories">
      <h2 id="git-repositories" ref={heading} tabIndex={-1}>{t('gitRepos.title')}</h2>
      <p className="hint">{t('gitRepos.intro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={releases} retry={reload}>
        {(value) => (
          <>
            {value.repositories.length === 0 ? <EmptyText text={t('gitRepos.empty')} /> : (
              <ul className="item-list" data-testid="git-repositories">
                {value.repositories.map((release) => (
                  <li key={release.id} className="item-row" data-release={release.id}>
                    {release.repository ? (
                      <>
                        <span className="item-title">
                          {release.repository.url
                            ? <a href={release.repository.url} target="_blank" rel="noopener noreferrer">{release.repository.path}</a>
                            : release.repository.path}
                        </span>
                        <span className="hint">{release.providerName}</span>
                      </>
                    ) : (
                      <>
                        <span className="item-title">{t('gitRepos.unavailable')}</span>
                        <span className="hint" data-testid="git-reason">
                          {release.providerName} · {reason(release.reason)}
                          {(release.reason === 'git.not_connected' || release.reason === 'git.connection_renew')
                            && <> · <a href={hrefFor('git')}>{t('gitRepos.toConnections')}</a></>}
                        </span>
                      </>
                    )}
                    <span className="hint">{t('gitRepos.releasedAt', { date: formatDateTime(release.releasedAt, language) })}</span>
                    {value.manageable && (
                      <button type="button" className="secondary" onClick={() => void withdraw(release)}>
                        {t('gitRepos.withdraw', { name: release.repository?.path ?? release.externalId })}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {value.manageable && <ReleaseForm projectId={projectId} onReleased={(text) => {
              setNotice({ kind: 'success', text });
              reload();
            }} />}
          </>
        )}
      </LoadedView>
    </section>
  );
}

function ReleaseForm({ projectId, onReleased }: { readonly projectId: string; readonly onReleased: (text: string) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useGitProblem();
  const [providers] = useLoad(() => listGitProviders(api), [api]);
  const [provider, setProvider] = useState('');
  const [query, setQuery] = useState('');
  const [found, setFound] = useState<readonly GitRepository[] | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const latest = useRef(0);

  const connected = providers.state === 'ok' ? providers.value.filter((item) => item.connection === 'active') : [];
  const chosen = provider !== '' ? provider : connected[0]?.id ?? '';

  async function search(event: FormEvent) {
    event.preventDefault();
    if (chosen === '') return;
    const ticket = ++latest.current;
    setNotice(null);
    setBusy(true);
    const result = await listGitCandidates(api, projectId, chosen, query.trim());
    if (ticket !== latest.current) return;
    setBusy(false);
    if (result.kind !== 'ok') {
      setFound(null);
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    setFound(result.data.value);
  }

  async function release(repository: GitRepository) {
    setNotice(null);
    setBusy(true);
    const result = await releaseGitRepository(api, projectId, chosen, repository.externalId);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setFound((current) => current?.filter((item) => item.externalId !== repository.externalId) ?? null);
    onReleased(t('gitRepos.released', { name: repository.path }));
  }

  return (
    <div className="panel" data-testid="git-release">
      <h3>{t('gitRepos.add')}</h3>
      {providers.state === 'ok' && connected.length === 0 ? (
        <p className="hint">{t('gitRepos.connectFirst')} <a href={hrefFor('git')}>{t('gitRepos.toConnections')}</a></p>
      ) : (
        <form onSubmit={(event) => void search(event)} noValidate>
          <SelectField label={t('gitRepos.provider')} value={chosen} onChange={(value) => {
            setProvider(value);
            setFound(null);
          }} options={connected.map((item) => ({ value: item.id, label: item.name }))} />
          <TextField label={t('gitRepos.search')} hint={t('gitRepos.searchHint')} value={query} onChange={setQuery} maxLength={100} autoComplete="off" />
          <p className="actions"><button type="submit" disabled={busy || chosen === ''}>{t('gitRepos.find')}</button></p>
        </form>
      )}
      <NoticeView notice={notice} />
      {found !== null && (found.length === 0 ? <EmptyText text={t('gitRepos.noneFound')} /> : (
        <ul className="item-list" data-testid="git-candidates">
          {found.map((repository) => (
            <li key={repository.externalId} className="item-row">
              <span className="item-title">{repository.path}</span>
              <button type="button" disabled={busy} onClick={() => void release(repository)}>{t('gitRepos.release', { name: repository.path })}</button>
            </li>
          ))}
        </ul>
      ))}
    </div>
  );
}
