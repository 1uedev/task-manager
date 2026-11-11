import { useRef, useState, type FormEvent, type ReactNode } from 'react';
import { listWikiProjects, searchWiki, type SearchMark, type WikiHit } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { EmptyText, SelectField, TextField } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';

interface WikiQuery {
  readonly q: string;
  readonly project: string;
  readonly language: '' | 'de' | 'en';
  readonly drafts: boolean;
  readonly archived: boolean;
}

type Found =
  | { readonly state: 'idle' }
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly total: number; readonly items: readonly WikiHit[] };

const PAGE = 20;

/** The text with the found words marked; the server sends plain text and the places, never HTML (TASK-102). */
export function Marked({ text, marks }: { readonly text: string; readonly marks: readonly SearchMark[] }) {
  const parts: ReactNode[] = [];
  let at = 0;
  [...marks].sort((a, b) => a.start - b.start).forEach((mark, index) => {
    if (mark.start < at || mark.end > text.length) return;
    if (mark.start > at) parts.push(text.slice(at, mark.start));
    parts.push(<mark key={index}>{text.slice(mark.start, mark.end)}</mark>);
    at = mark.end;
  });
  if (at < text.length) parts.push(text.slice(at));
  return <>{parts}</>;
}

// The wiki area of the search (TASK-102). Readers search the published pages; who may read drafts can search the drafts
// instead, who publishes can add archived pages. The server decides what is found; the page shows what comes back.
export function WikiSearch() {
  const { t, language: uiLanguage } = useI18n();
  const { api } = useBrand();
  const [projects] = useLoad(() => listWikiProjects(api), [api]);
  const [query, setQuery] = useState<WikiQuery>({ q: '', project: '', language: '', drafts: false, archived: false });
  const [found, setFound] = useState<Found>({ state: 'idle' });
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const sent = useRef<WikiQuery>(query);
  // Only the answer of the search started last is shown (as TASK-086 for tickets).
  const latest = useRef(0);

  const known = projects.state === 'ok' ? projects.value : [];
  const mayDrafts = known.some((item) => item.access.edit || item.access.publish);
  const mayArchive = known.some((item) => item.access.publish);

  async function run(nextOffset: number, next: WikiQuery = sent.current) {
    const ticket = ++latest.current;
    sent.current = next;
    setFound({ state: 'loading' });
    const result = await searchWiki(api, { q: next.q.trim(), ...(next.project === '' ? {} : { project: next.project }),
      ...(next.language === '' ? {} : { language: next.language }), preferred: uiLanguage, drafts: next.drafts, archived: next.archived, offset: nextOffset });
    if (ticket !== latest.current) return;
    if (result.kind !== 'ok') {
      if (result.problem.status === 400) {
        setFound({ state: 'idle' });
        return setError(t('search.error.invalid'));
      }
      return setFound({ state: 'problem', problem: result.problem });
    }
    setOffset(nextOffset);
    setFound({ state: 'ok', total: result.data.value.total, items: result.data.value.items });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (query.q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    void run(0, query);
  }

  const set = (change: Partial<WikiQuery>) => setQuery((current) => ({ ...current, ...change }));
  return (
    <>
      <form className="search-form" role="search" aria-label={t('search.wiki.label')} onSubmit={submit} noValidate>
        <TextField label={t('search.text')} hint={t('search.wiki.hint')} value={query.q} onChange={(q) => set({ q })} maxLength={200} error={error} />
        <SelectField label={t('search.project')} value={query.project} onChange={(project) => set({ project })}
          options={[{ value: '', label: t('search.allProjects') }, ...known.map((item) => ({ value: item.projectId, label: `${item.key} · ${item.name}` }))]} />
        <SelectField label={t('search.wiki.language')} value={query.language} onChange={(language) => set({ language: language as WikiQuery['language'] })}
          options={[{ value: '', label: t('search.wiki.bothLanguages') }, { value: 'de', label: t('search.wiki.german') }, { value: 'en', label: t('search.wiki.english') }]} />
        {mayDrafts && (
          <label className="check"><input type="checkbox" checked={query.drafts} onChange={(event) => set({ drafts: event.target.checked })} /> {t('search.wiki.drafts')}</label>
        )}
        {mayArchive && (
          <label className="check"><input type="checkbox" checked={query.archived} onChange={(event) => set({ archived: event.target.checked })} /> {t('search.wiki.archived')}</label>
        )}
        <button type="submit">{t('search.submit')}</button>
      </form>
      {found.state === 'loading' && <LoadingView />}
      {found.state === 'problem' && <ProblemView problem={found.problem} onAction={() => void run(offset)} actionLabel="states.retry" />}
      {found.state === 'ok' && (
        <section aria-labelledby="wiki-hits" aria-live="polite">
          <h2 id="wiki-hits">{t('search.wiki.pages', { count: found.total })}</h2>
          {found.items.length === 0 ? <EmptyText text={t('search.none')} /> : (
            <ul className="item-list search-hits" data-testid="wiki-hits">
              {found.items.map((hit) => (
                <li key={hit.id}>
                  <a href={hrefFor({ name: 'wikiPage', projectId: hit.projectId, pageId: hit.id })}>
                    <span className="item-key">{hit.projectKey}</span>
                    <span className="item-title"><Marked text={hit.title} marks={hit.titleMarks} /></span>
                  </a>
                  <p className="hint">
                    {hit.path.length > 0 && <span className="search-path">{hit.path.map((entry) => entry.title).join(' › ')} · </span>}
                    <span lang={hit.language}>{hit.language === 'en' ? t('search.wiki.english') : t('search.wiki.german')}</span>
                    {hit.stage === 'draft' && <> · <span className="preview-badge">{t('search.wiki.draft', { revision: hit.revision })}</span></>}
                    {hit.archived && <> · <span className="preview-badge">{t('search.wiki.archivedBadge')}</span></>}
                    {hit.publishedAt !== null && <> · {t('search.wiki.publishedAt', { at: formatDateTime(hit.publishedAt, uiLanguage) })}</>}
                  </p>
                  {hit.snippet !== null && (
                    <p className="search-snippet" lang={hit.language}>
                      {hit.snippet.cutStart && '… '}<Marked text={hit.snippet.text} marks={hit.snippet.marks} />{hit.snippet.cutEnd && ' …'}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
          <p className="actions">
            {offset > 0 && <button type="button" className="secondary" onClick={() => void run(Math.max(0, offset - PAGE))}>{t('search.previous')}</button>}
            {offset + found.items.length < found.total && offset + PAGE <= 1000 && (
              <button type="button" className="secondary" onClick={() => void run(offset + PAGE)}>{t('search.next')}</button>
            )}
          </p>
        </section>
      )}
    </>
  );
}
