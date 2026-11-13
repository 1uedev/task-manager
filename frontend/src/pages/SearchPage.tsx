import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { listProjects, searchAll, searchProjects, searchTickets, type ProjectHit, type SearchOverview, type TicketHit } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor, type SearchArea } from '../app/route.ts';
import { EmptyText, PageHead, SelectField, TextField } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import { RequirementHitItem, RequirementSearch } from './RequirementSearch.tsx';
import { WikiHitItem, WikiSearch, type SearchStart } from './WikiSearch.tsx';
import { NO_TAG_FILTER, SelectionBar, SelectPage, SelectTicket, TagFilter, useTicketSelection, type TagFilterValue } from './TagParts.tsx';

interface Query {
  readonly q: string;
  readonly project: string;
  readonly tagFilter: TagFilterValue;
}

type Found<T> =
  | { readonly state: 'idle' }
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly value: T };

type Area = SearchArea;
const AREAS = ['all', 'tickets', 'projects', 'wiki', 'requirements'] as const;
const AREA_LABELS = {
  all: 'search.area.all', tickets: 'search.area.tickets', projects: 'search.area.projects', wiki: 'search.area.wiki',
  requirements: 'search.area.requirements',
} as const;

// The search with its areas (TASK-102 to TASK-104): the overview "Alle" and one tab per area, each with the filters and pages
// of its own search. Each area keeps its own input while another one is shown; an area is built the first time it is chosen,
// so the page asks for its projects only when needed. A text from the address (quick search) is searched in the area named.
export function SearchPage({ area: initial = 'all', q = '' }: { readonly area?: Area; readonly q?: string }) {
  const { t } = useI18n();
  const [area, setArea] = useState<Area>(initial);
  const [opened, setOpened] = useState<ReadonlySet<Area>>(new Set([initial]));
  // A text handed to an area: from the address at the start, or from "Alle anzeigen" of the overview.
  const [starts, setStarts] = useState<Partial<Record<Area, SearchStart>>>(q === '' ? {} : { [initial]: { q, n: 1 } });
  const choose = (value: Area) => {
    setArea(value);
    setOpened((current) => new Set([...current, value]));
  };
  // "Alle anzeigen" hides the button that was pressed; the focus goes to the search field of the chosen area (TASK-110, F6).
  const areaBoxes = useRef<Partial<Record<Area, HTMLDivElement | null>>>({});
  const [focusArea, setFocusArea] = useState<{ readonly area: Area; readonly n: number } | null>(null);
  useEffect(() => {
    if (focusArea === null) return;
    areaBoxes.current[focusArea.area]?.querySelector<HTMLInputElement>('form[role=search] input')?.focus();
  }, [focusArea]);
  const showAll = (value: Area, text: string) => {
    setStarts((current) => ({ ...current, [value]: { q: text, n: (current[value]?.n ?? 0) + 1 } }));
    choose(value);
    setFocusArea((current) => ({ area: value, n: (current?.n ?? 0) + 1 }));
  };
  const box = (value: Area) => (element: HTMLDivElement | null) => {
    areaBoxes.current[value] = element;
  };
  return (
    <>
      <PageHead title={t('search.title')} intro={t('search.intro')} />
      <fieldset className="search-areas">
        <legend>{t('search.area')}</legend>
        {AREAS.map((value) => (
          <label key={value} className="check">
            <input type="radio" name="search-area" value={value} checked={area === value} onChange={() => choose(value)} />
            {' '}{t(AREA_LABELS[value])}
          </label>
        ))}
      </fieldset>
      {opened.has('all') && <div hidden={area !== 'all'}><OverviewSearch start={starts.all} onShowAll={showAll} /></div>}
      {opened.has('tickets') && <div ref={box('tickets')} hidden={area !== 'tickets'}><TicketSearch start={starts.tickets} /></div>}
      {opened.has('projects') && <div ref={box('projects')} hidden={area !== 'projects'}><ProjectSearch start={starts.projects} /></div>}
      {opened.has('wiki') && <div ref={box('wiki')} hidden={area !== 'wiki'}><WikiSearch start={starts.wiki} /></div>}
      {opened.has('requirements') && <div ref={box('requirements')} hidden={area !== 'requirements'}><RequirementSearch start={starts.requirements} /></div>}
    </>
  );
}

// The overview "Alle" (TASK-104, rule 1): per area the first five hits with the total and "Alle anzeigen"; no common ranking,
// an area without a right does not appear.
function OverviewSearch({ start, onShowAll }: { readonly start: SearchStart | undefined; readonly onShowAll: (area: Area, q: string) => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [q, setQ] = useState(start?.q ?? '');
  const [found, setFound] = useState<Found<SearchOverview>>({ state: 'idle' });
  const [error, setError] = useState<string | null>(null);
  const sent = useRef('');
  const latest = useRef(0);

  async function run(text: string) {
    const ticket = ++latest.current;
    sent.current = text;
    setFound({ state: 'loading' });
    const result = await searchAll(api, text.trim(), language);
    if (ticket !== latest.current) return;
    if (result.kind !== 'ok') {
      if (result.problem.status === 400) {
        setFound({ state: 'idle' });
        return setError(t('search.error.invalid'));
      }
      return setFound({ state: 'problem', problem: result.problem });
    }
    setFound({ state: 'ok', value: result.data.value });
  }

  useEffect(() => {
    if (start === undefined) return;
    setQ(start.q);
    setError(null);
    void run(start.q);
  }, [start?.n]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    void run(q);
  }

  const value = found.state === 'ok' ? found.value : null;
  const section = (area: Exclude<Area, 'all'>, heading: string, items: readonly unknown[], total: number, list: ReactNode) => (
    <section aria-labelledby={`overview-${area}`} data-testid={`overview-${area}`}>
      <h2 id={`overview-${area}`}>{heading}</h2>
      {items.length === 0 ? <EmptyText text={t('search.none')} /> : list}
      {total > items.length && (
        <p className="actions">
          <button type="button" className="secondary" onClick={() => onShowAll(area, sent.current)}
            aria-label={t('search.showAllOf', { area: t(AREA_LABELS[area]) })}>{t('search.showAll')}</button>
        </p>
      )}
    </section>
  );
  return (
    <>
      <form className="search-form" role="search" aria-label={t('search.all.label')} onSubmit={submit} noValidate>
        <TextField label={t('search.text')} hint={t('search.all.hint')} value={q} onChange={setQ} maxLength={200} error={error} />
        <button type="submit">{t('search.submit')}</button>
      </form>
      <div aria-live="polite" data-testid="overview-status">
        {found.state === 'loading' && <LoadingView />}
        {found.state === 'problem' && <ProblemView problem={found.problem} onAction={() => void run(sent.current)} actionLabel="states.retry" />}
        {value !== null && (
          <div className="stack" data-testid="overview">
            {value.tickets === null && value.projects === null && value.wiki === null && value.requirements === null
              && <EmptyText text={t('search.all.nothing')} />}
            {value.tickets !== null && section('tickets', t('search.tickets', { count: value.tickets.total }), value.tickets.items, value.tickets.total, (
              <ul className="item-list search-hits">
                {value.tickets.items.map((hit) => (
                  <li key={hit.id}>
                    <a href={hrefFor({ name: 'ticket', projectId: hit.projectId, number: hit.number })}>
                      <span className="item-key">{hit.key}</span>
                      <span className="item-title">{hit.fields.title ?? t('field.hidden')}</span>
                    </a>
                  </li>
                ))}
              </ul>
            ))}
            {value.projects !== null && section('projects', t('search.projects', { count: value.projects.total }), value.projects.items, value.projects.total, (
              <ProjectList items={value.projects.items} />
            ))}
            {value.wiki !== null && section('wiki', t('search.wiki.pages', { count: value.wiki.total }), value.wiki.items, value.wiki.total, (
              <ul className="item-list search-hits">{value.wiki.items.map((hit) => <WikiHitItem key={hit.id} hit={hit} />)}</ul>
            ))}
            {value.requirements !== null && section('requirements', t('search.requirements.found', { count: value.requirements.total }),
              value.requirements.items, value.requirements.total, (
                <ul className="item-list search-hits">{value.requirements.items.map((hit) => <RequirementHitItem key={hit.id} hit={hit} />)}</ul>
              ))}
          </div>
        )}
      </div>
    </>
  );
}

function ProjectList({ items }: { readonly items: readonly ProjectHit[] }) {
  return (
    <ul className="item-list">
      {items.map((hit) => (
        <li key={hit.id}>
          <a href={hrefFor({ name: 'project', id: hit.id, tab: 'tickets' })}>
            <span className="item-key">{hit.key}</span>
            <span className="item-title">{hit.name}</span>
          </a>
          {hit.tags.length > 0 && <p className="hint">{hit.tags.map((tag) => tag.name).join(', ')}</p>}
        </li>
      ))}
    </ul>
  );
}

// The projects area (TASK-104): projects by key, name and description with the tags of the company catalog, page by page.
function ProjectSearch({ start }: { readonly start: SearchStart | undefined }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [q, setQ] = useState(start?.q ?? '');
  const [tagFilter, setTagFilter] = useState<TagFilterValue>(NO_TAG_FILTER);
  const [found, setFound] = useState<Found<{ total: number; items: ProjectHit[] }>>({ state: 'idle' });
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const sent = useRef<{ q: string; tagFilter: TagFilterValue }>({ q: '', tagFilter: NO_TAG_FILTER });
  const latest = useRef(0);

  async function run(nextOffset: number, query = sent.current) {
    const ticket = ++latest.current;
    sent.current = query;
    setFound({ state: 'loading' });
    const result = await searchProjects(api, { q: query.q.trim(), tag: query.tagFilter.tags.map((tag) => tag.id), tagMatch: query.tagFilter.match, offset: nextOffset });
    if (ticket !== latest.current) return;
    if (result.kind !== 'ok') {
      if (result.problem.status === 400) {
        setFound({ state: 'idle' });
        return setError(t('search.error.invalid'));
      }
      return setFound({ state: 'problem', problem: result.problem });
    }
    setOffset(nextOffset);
    setFound({ state: 'ok', value: result.data.value });
  }

  useEffect(() => {
    if (start === undefined) return;
    setQ(start.q);
    setError(null);
    void run(0, { q: start.q, tagFilter });
  }, [start?.n]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    void run(0, { q, tagFilter });
  }

  return (
    <>
      <form className="search-form" role="search" aria-label={t('search.projects.label')} onSubmit={submit} noValidate>
        <TextField label={t('search.text')} hint={t('search.projects.hint')} value={q} onChange={setQ} maxLength={200} error={error} />
        <TagFilter projectId={null} value={tagFilter} onChange={setTagFilter} />
        <button type="submit">{t('search.submit')}</button>
      </form>
      <div aria-live="polite">
        {found.state === 'loading' && <LoadingView />}
        {found.state === 'problem' && <ProblemView problem={found.problem} onAction={() => void run(offset)} actionLabel="states.retry" />}
        {found.state === 'ok' && (
          <section aria-labelledby="project-hits">
            <h2 id="project-hits">{t('search.projects', { count: found.value.total })}</h2>
            {found.value.items.length === 0 ? <EmptyText text={t('search.none')} /> : <ProjectList items={found.value.items} />}
            <p className="actions">
              {offset > 0 && <button type="button" className="secondary" onClick={() => void run(Math.max(0, offset - 20))}>{t('search.previous')}</button>}
              {offset + found.value.items.length < found.value.total && offset + 20 <= 1000 && (
                <button type="button" className="secondary" onClick={() => void run(offset + 20)}>{t('search.next')}</button>
              )}
            </p>
          </section>
        )}
      </div>
    </>
  );
}

// The tickets area. The server searches only what the person may read; hidden fields neither match nor show, so the page
// displays what comes back and nothing more. Projects have their own area since TASK-104.
function TicketSearch({ start }: { readonly start: SearchStart | undefined }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [q, setQ] = useState(start?.q ?? '');
  const [project, setProject] = useState('');
  const [found, setFound] = useState<Found<{ total: number; items: TicketHit[] }>>({ state: 'idle' });
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
    setOffset(nextOffset);
    setFound({ state: 'ok', value: tickets.data.value });
  }

  useEffect(() => {
    if (start === undefined) return;
    setQ(start.q);
    setError(null);
    void run(0, { q: start.q, project, tagFilter });
  }, [start?.n]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    void run(0, { q, project, tagFilter });
  }

  const names = new Map((projects.state === 'ok' ? projects.value : []).map((item) => [item.id, item]));
  return (
    <>
      <form className="search-form" role="search" aria-label={t('search.tickets.label')} onSubmit={submit} noValidate>
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
            <h2 id="ticket-hits">{t('search.tickets', { count: found.value.total })}</h2>
            {found.value.items.length === 0 ? <EmptyText text={t('search.none')} /> : (
              <>
              <p className="select-page"><label><SelectPage selection={selection} items={found.value.items} /> {t('tickets.selectPage')}</label></p>
              <ul className="item-list selectable" data-testid="ticket-hits">
                {found.value.items.map((hit) => (
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
              {offset + found.value.items.length < found.value.total && offset + 20 <= 1000 && (
                <button type="button" className="secondary" onClick={() => void run(offset + 20)}>{t('search.next')}</button>
              )}
            </p>
          </section>
        </div>
      )}
    </>
  );
}
