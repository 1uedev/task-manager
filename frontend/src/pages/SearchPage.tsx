import { useState, type FormEvent } from 'react';
import { listProjects, searchProjects, searchTickets, type ProjectHit, type TicketHit } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { EmptyText, PageHead, SelectField, TextField } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import { NO_TAG_FILTER, SelectionBar, SelectPage, SelectTicket, TagFilter, tagFilterActive, useTicketSelection, type TagFilterValue } from './TagParts.tsx';

type Found =
  | { readonly state: 'idle' }
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly tickets: { total: number; items: TicketHit[] }; readonly projects: { total: number; items: ProjectHit[] } | null };

// Search over tickets and projects. The server searches only what the person may read; hidden fields neither match
// nor show, so the page displays what comes back and nothing more.
export function SearchPage() {
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

  async function run(nextOffset: number) {
    if (q.trim().length > 200) return setError(t('search.error.long'));
    setError(null);
    setFound({ state: 'loading' });
    const tickets = await searchTickets(api, { q: q.trim(), project: project === '' ? [] : [project], offset: nextOffset,
      tag: tagFilter.tags.map((tag) => tag.id), tagMatch: tagFilter.match });
    if (tickets.kind !== 'ok') {
      if (tickets.problem.status === 400) {
        setFound({ state: 'idle' });
        return setError(t('search.error.invalid'));
      }
      return setFound({ state: 'problem', problem: tickets.problem });
    }
    // Projects carry tags of the company catalog only, so a tag filter applies to tickets here.
    const projectHits = q.trim() !== '' && project === '' && nextOffset === 0 && !tagFilterActive(tagFilter) ? await searchProjects(api, q.trim()) : null;
    setOffset(nextOffset);
    setFound({ state: 'ok', tickets: tickets.data.value, projects: projectHits?.kind === 'ok' ? projectHits.data.value : null });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(0);
  }

  const names = new Map((projects.state === 'ok' ? projects.value : []).map((item) => [item.id, item]));
  return (
    <>
      <PageHead title={t('search.title')} intro={t('search.intro')} />
      <form className="search-form" role="search" onSubmit={submit} noValidate>
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
