import { useRef, useState, type FormEvent } from 'react';
import { listProjects, searchProjects, searchTickets, type ProjectHit, type TicketHit } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { EmptyText, PageHead, SelectField, TextField } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import { RequirementSearch } from './RequirementSearch.tsx';
import { WikiSearch } from './WikiSearch.tsx';
import { NO_TAG_FILTER, SelectionBar, SelectPage, SelectTicket, TagFilter, tagFilterActive, useTicketSelection, type TagFilterValue } from './TagParts.tsx';

interface Query {
  readonly q: string;
  readonly project: string;
  readonly tagFilter: TagFilterValue;
}

type Found =
  | { readonly state: 'idle' }
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly tickets: { total: number; items: TicketHit[] }; readonly projects: { total: number; items: ProjectHit[] } | null };

type Area = 'tickets' | 'wiki' | 'requirements';
const AREA_LABELS = { tickets: 'search.area.tickets', wiki: 'search.area.wiki', requirements: 'search.area.requirements' } as const;

// The search with its areas (TASK-102, TASK-103): tickets and projects, the wiki and requirements. Each area keeps its own
// input while another one is shown. The wiki and requirements areas are built the first time they are chosen, so the page asks
// for their projects only when needed.
export function SearchPage() {
  const { t } = useI18n();
  const [area, setArea] = useState<Area>('tickets');
  const [opened, setOpened] = useState<ReadonlySet<Area>>(new Set(['tickets']));
  return (
    <>
      <PageHead title={t('search.title')} intro={t('search.intro')} />
      <fieldset className="search-areas">
        <legend>{t('search.area')}</legend>
        {(['tickets', 'wiki', 'requirements'] as const).map((value) => (
          <label key={value} className="check">
            <input type="radio" name="search-area" value={value} checked={area === value} onChange={() => { setArea(value); setOpened((current) => new Set([...current, value])); }} />
            {' '}{t(AREA_LABELS[value])}
          </label>
        ))}
      </fieldset>
      <div hidden={area !== 'tickets'}><TicketSearch /></div>
      {opened.has('wiki') && <div hidden={area !== 'wiki'}><WikiSearch /></div>}
      {opened.has('requirements') && <div hidden={area !== 'requirements'}><RequirementSearch /></div>}
    </>
  );
}

// Search over tickets and projects. The server searches only what the person may read; hidden fields neither match
// nor show, so the page displays what comes back and nothing more.
function TicketSearch() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [q, setQ] = useState('');
  const [project, setProject] = useState('');
  const [found, setFound] = useState<Found>({ state: 'idle' });
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [tagFilter, setTagFilter] = useState<TagFilterValue>(NO_TAG_FILTER);
  const selection = useTicketSelection();

  // The query last sent: paging, retry and the reload after a bulk change repeat it, not fields changed since (TASK-086).
  const sent = useRef<Query>({ q: '', project: '', tagFilter: NO_TAG_FILTER });
  // Only the answer of the search started last is shown; an earlier one that arrives late is dropped (TASK-086, Codex H1).
  const latest = useRef(0);

  async function run(nextOffset: number, query: Query = sent.current) {
    const ticket = ++latest.current;
    sent.current = query;
    const text = query.q.trim();
    setFound({ state: 'loading' });
    const tickets = await searchTickets(api, { q: text, project: query.project === '' ? [] : [query.project], offset: nextOffset,
      tag: query.tagFilter.tags.map((tag) => tag.id), tagMatch: query.tagFilter.match });
    if (ticket !== latest.current) return;
    if (tickets.kind !== 'ok') {
      if (tickets.problem.status === 400) {
        setFound({ state: 'idle' });
        return setError(t('search.error.invalid'));
      }
      return setFound({ state: 'problem', problem: tickets.problem });
    }
    // Projects carry tags of the company catalog only, so a tag filter applies to tickets here.
    const projectHits = text !== '' && query.project === '' && nextOffset === 0 && !tagFilterActive(query.tagFilter) ? await searchProjects(api, text) : null;
    if (ticket !== latest.current) return;
    setOffset(nextOffset);
    setFound({ state: 'ok', tickets: tickets.data.value, projects: projectHits?.kind === 'ok' ? projectHits.data.value : null });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    void run(0, { q, project, tagFilter });
  }

  const names = new Map((projects.state === 'ok' ? projects.value : []).map((item) => [item.id, item]));
  return (
    <>
      <form className="search-form" role="search" aria-label={t('search.area.tickets')} onSubmit={submit} noValidate>
        <TextField label={t('search.text')} hint={t('search.hint')} value={q} onChange={setQ} maxLength={200} error={error} />
        <SelectField label={t('search.project')} value={project} onChange={setProject}
          options={[{ value: '', label: t('search.allProjects') }, ...(projects.state === 'ok' ? projects.value : []).map((item) => ({ value: item.id, label: `${item.key} · ${item.name}` }))]} />
        <TagFilter projectId={project === '' ? null : project} value={tagFilter} onChange={setTagFilter} />
        <button type="submit">{t('search.submit')}</button>
      </form>
      <SelectionBar selection={selection} projectId={project === '' ? null : project} onDone={() => void run(offset)} />
      {found.state === 'loading' && <LoadingView />}
      {found.state === 'problem' && <ProblemView problem={found.problem} onAction={() => void run(offset)} actionLabel="states.retry" />}
      {found.state === 'ok' && (
        <div aria-live="polite">
          <section aria-labelledby="ticket-hits">
            <h2 id="ticket-hits">{t('search.tickets', { count: found.tickets.total })}</h2>
            {found.tickets.items.length === 0 ? <EmptyText text={t('search.none')} /> : (
              <>
              <p className="select-page"><label><SelectPage selection={selection} items={found.tickets.items} /> {t('tickets.selectPage')}</label></p>
              <ul className="item-list selectable" data-testid="ticket-hits">
                {found.tickets.items.map((hit) => (
                  <li key={hit.id}>
                    <SelectTicket selection={selection} id={hit.id} ticketKey={hit.key} />
                    <a href={hrefFor({ name: 'ticket', projectId: hit.projectId, number: hit.number })}>
                      <span className="item-key">{hit.key}</span>
                      <span className="item-title">{hit.fields.title ?? t('field.hidden')}</span>
                    </a>
                    <p className="hint">
                      {names.get(hit.projectId)?.name ?? ''}
                      {hit.tags !== null && hit.tags.length > 0 && ` · ${hit.tags.map((tag) => tag.name).join(', ')}`}
                    </p>
                  </li>
                ))}
              </ul>
              </>
            )}
            <p className="actions">
              {offset > 0 && <button type="button" className="secondary" onClick={() => void run(Math.max(0, offset - 20))}>{t('search.previous')}</button>}
              {offset + found.tickets.items.length < found.tickets.total && offset + 20 <= 1000 && (
                <button type="button" className="secondary" onClick={() => void run(offset + 20)}>{t('search.next')}</button>
              )}
            </p>
          </section>
          {found.projects !== null && (
            <section aria-labelledby="project-hits">
              <h2 id="project-hits">{t('search.projects', { count: found.projects.total })}</h2>
              {found.projects.items.length === 0 ? <EmptyText text={t('search.none')} /> : (
                <ul className="item-list">
                  {found.projects.items.map((hit) => (
                    <li key={hit.id}>
                      <a href={hrefFor({ name: 'project', id: hit.id, tab: 'tickets' })}>
                        <span className="item-key">{hit.key}</span>
                        <span className="item-title">{hit.name}</span>
                      </a>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </div>
      )}
    </>
  );
}
