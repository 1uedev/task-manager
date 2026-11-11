import { useEffect, useRef, useState, type FormEvent } from 'react';
import { listRequirementProjects, searchRequirements, type RequirementHit } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, SelectField, TextField } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import { Marked } from './WikiSearch.tsx';

type Status = '' | 'draft' | 'review' | 'approved' | 'discarded';
type Priority = '' | 'low' | 'normal' | 'high' | 'critical';

interface Query {
  readonly q: string;
  readonly project: string;
  readonly approved: boolean;
  readonly status: Status;
  readonly priority: Priority;
  readonly owner: string;
  readonly everApproved: '' | 'yes' | 'no';
}

type Found =
  | { readonly state: 'idle' }
  | { readonly state: 'loading' }
  | { readonly state: 'problem'; readonly problem: ProblemInfo }
  | { readonly state: 'ok'; readonly total: number; readonly items: readonly RequirementHit[] };

const PAGE = 20;
const STATUSES = ['draft', 'review', 'approved', 'discarded'] as const;
const PRIORITIES = ['low', 'normal', 'high', 'critical'] as const;

// The requirements area of the search (TASK-103). The current revision is searched, or with "zuletzt freigegeben" the newest
// approved one; the two never mix. The server decides what is found and which fields show; a filter on priority or owner is
// offered only where the chosen projects let the person read that field.
export function RequirementSearch() {
  const { t } = useI18n();
  const { api } = useBrand();
  const { directory } = useSession();
  const [projects, retryProjects] = useLoad(() => listRequirementProjects(api), [api]);
  const [query, setQuery] = useState<Query>({ q: '', project: '', approved: false, status: '', priority: '', owner: '', everApproved: '' });
  const [found, setFound] = useState<Found>({ state: 'idle' });
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const sent = useRef<Query>(query);
  const asked = useRef(0);
  const latest = useRef(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const focusAfter = useRef(false);

  const known = projects.state === 'ok' ? projects.value : [];
  const readable = (next: Query, field: string) =>
    (next.project === '' ? known : known.filter((item) => item.projectId === next.project)).some((item) => item.access.readableFields.includes(field));
  const people = [...directory.values()].filter((entry) => entry.active);

  useEffect(() => {
    if (focusAfter.current && found.state === 'ok') {
      focusAfter.current = false;
      heading.current?.focus();
    }
  }, [found]);

  async function run(nextOffset: number, next: Query = sent.current, paging = false) {
    const ticket = ++latest.current;
    sent.current = next;
    asked.current = nextOffset;
    focusAfter.current = paging;
    setFound({ state: 'loading' });
    const result = await searchRequirements(api, { q: next.q.trim(), approved: next.approved, offset: nextOffset,
      ...(next.project === '' ? {} : { project: next.project }), ...(next.status === '' ? {} : { status: next.status }),
      ...(next.priority === '' ? {} : { priority: next.priority }), ...(next.owner === '' ? {} : { owner: next.owner }),
      ...(next.everApproved === '' ? {} : { everApproved: next.everApproved === 'yes' }) });
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

  // A filter the chosen projects do not let the person read is cleared with the choice of project.
  const set = (change: Partial<Query>) => setQuery((current) => {
    const next = { ...current, ...change };
    return { ...next, priority: readable(next, 'priority') ? next.priority : '', owner: readable(next, 'owner') ? next.owner : '' };
  });
  return (
    <>
      {projects.state === 'problem' && (
        <ProblemView problem={projects.problem} onAction={retryProjects} actionLabel="states.retry" />
      )}
      <form className="search-form" role="search" aria-label={t('search.requirements.label')} onSubmit={submit} noValidate>
        <TextField label={t('search.text')} hint={t('search.requirements.hint')} value={query.q} onChange={(q) => set({ q })} maxLength={200} error={error} />
        <SelectField label={t('search.project')} value={query.project} onChange={(project) => set({ project })}
          options={[{ value: '', label: t('search.allProjects') }, ...known.map((item) => ({ value: item.projectId, label: `${item.key} · ${item.name}` }))]} />
        <SelectField label={t('search.requirements.stage')} value={query.approved ? 'approved' : 'current'} onChange={(stage) => set({ approved: stage === 'approved' })}
          options={[{ value: 'current', label: t('search.requirements.current') }, { value: 'approved', label: t('search.requirements.approvedStage') }]} />
        <SelectField label={t('search.requirements.status')} value={query.status} onChange={(status) => set({ status: status as Status })}
          options={[{ value: '', label: t('search.requirements.allStatuses') }, ...STATUSES.map((value) => ({ value, label: t(`requirements.status.${value}`) }))]} />
        {readable(query, 'priority') && (
          <SelectField label={t('search.requirements.priority')} value={query.priority} onChange={(priority) => set({ priority: priority as Priority })}
            options={[{ value: '', label: t('search.requirements.any') }, ...PRIORITIES.map((value) => ({ value, label: t(`requirements.priority.${value}`) }))]} />
        )}
        {readable(query, 'owner') && (
          <OwnerField value={query.owner} people={people.map((entry) => entry.id)} onChange={(owner) => set({ owner })} />
        )}
        <SelectField label={t('search.requirements.everApproved')} value={query.everApproved} onChange={(value) => set({ everApproved: value as Query['everApproved'] })}
          options={[{ value: '', label: t('search.requirements.any') }, { value: 'yes', label: t('search.requirements.yes') }, { value: 'no', label: t('search.requirements.no') }]} />
        <button type="submit">{t('search.submit')}</button>
      </form>
      <div aria-live="polite" data-testid="requirement-search-status">
        {found.state === 'loading' && <LoadingView />}
        {found.state === 'problem' && <ProblemView problem={found.problem} onAction={() => void run(asked.current, sent.current, true)} actionLabel="states.retry" />}
        {found.state === 'ok' && (
          <section aria-labelledby="requirement-hits">
            <h2 id="requirement-hits" ref={heading} tabIndex={-1}>{t('search.requirements.found', { count: found.total })}</h2>
            {found.items.length === 0 ? <EmptyText text={t('search.none')} /> : (
              <ul className="item-list search-hits" data-testid="requirement-hits">
                {found.items.map((hit) => <Hit key={hit.id} hit={hit} />)}
              </ul>
            )}
            <p className="actions">
              {offset > 0 && <button type="button" className="secondary" onClick={() => void run(Math.max(0, offset - PAGE), sent.current, true)}>{t('search.previous')}</button>}
              {offset + found.items.length < found.total && offset + PAGE <= 1000 && (
                <button type="button" className="secondary" onClick={() => void run(offset + PAGE, sent.current, true)}>{t('search.next')}</button>
              )}
            </p>
          </section>
        )}
      </div>
    </>
  );
}

function OwnerField({ value, people, onChange }: { readonly value: string; readonly people: readonly string[]; readonly onChange: (owner: string) => void }) {
  const { t } = useI18n();
  const name = useAccountName();
  return (
    <SelectField label={t('search.requirements.owner')} value={value} onChange={onChange}
      options={[{ value: '', label: t('search.requirements.any') }, ...[...people].sort((a, b) => name(a).localeCompare(name(b))).map((id) => ({ value: id, label: name(id) }))]} />
  );
}

function Hit({ hit }: { readonly hit: RequirementHit }) {
  const { t } = useI18n();
  const snippet = hit.snippet;
  const approval = hit.approved ? t('search.requirements.isApproved')
    : hit.approvedRevision === null ? t('search.requirements.neverApproved') : t('search.requirements.approvedIs', { revision: hit.approvedRevision });
  return (
    <li>
      <a href={hrefFor({ name: 'requirementPage', projectId: hit.projectId, requirementId: hit.id })}>
        <span className="item-key">{hit.projectKey} {hit.key}</span>
        <span className="item-title">{hit.title === null ? t('search.requirements.titleHidden') : <Marked text={hit.title} marks={hit.titleMarks} />}</span>
      </a>
      <p className="hint">
        <span>{t(`requirements.status.${hit.status}`)}</span>
        {' · '}<span>{t('requirements.revision', { revision: hit.revision })}, {approval}</span>
        {hit.priority !== null && <> · {t(`requirements.priority.${hit.priority}`)}</>}
      </p>
      {snippet !== null && (
        <p className="search-snippet">
          {snippet.field === 'criteria' && snippet.criterion !== null && <strong>{t('search.requirements.criterion', { id: snippet.criterion })} </strong>}
          {snippet.excerpt.cutStart && '… '}<Marked text={snippet.excerpt.text} marks={snippet.excerpt.marks} />{snippet.excerpt.cutEnd && ' …'}
        </p>
      )}
    </li>
  );
}
