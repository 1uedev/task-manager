import { Fragment, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  approveRequirement, createRequirement, discardRequirement, listRequirementOwners, listRequirementProjects, listRequirements, readRequirement,
  readRequirementHistory, readRequirementRevision, restoreRequirement, returnRequirement, submitRequirement, updateRequirement,
  type Requirement, type RequirementAccess, type RequirementContent, type RequirementCriterion, type RequirementEvent, type RequirementFilter,
  type RequirementInput, type RequirementItem, type RequirementPriority, type RequirementProject, type RequirementRevision,
  type RequirementStatus, type WikiDocument,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { hrefFor, navigate } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { useReorder } from '../app/useReorder.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { RequirementLinksPanel } from './RequirementLinksPanel.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { blocksOf, diffBlocks, type DiffEntry } from './wikiDiff.ts';
import { DiffView } from './WikiDiffView.tsx';
import { mergeCriteria, type Draft } from './requirementCriteria.ts';
import { CUSTOM_PREFIX } from '../app/configuration.ts';
import { ProblemView } from '../states/StateViews.tsx';
import { CUSTOM_ERROR, CustomFieldInput, useCustomText } from './CustomFieldInput.tsx';
import { WikiContent } from './WikiContent.tsx';
import { GitLinksPanel, useGitHistoryText } from './GitLinks.tsx';
import { useWikiEditor, WikiEditorContent, WikiToolbar } from './WikiEditor.tsx';

// Requirements of a project (TASK-096, DEC-126). The server decides what a person sees and may do: `access` names the
// actions and the readable and writable fields, a field without a read rule comes as null. The interface offers only what
// `access` allows and shows a refusal of the server as it comes. A content change after an approval opens a new revision
// on the server; the approval names exactly the revision the dialog shows.

const STATUSES: readonly RequirementStatus[] = ['draft', 'review', 'approved', 'discarded'];
const PRIORITIES: readonly RequirementPriority[] = ['low', 'normal', 'high', 'critical'];
const MAX_CRITERIA = 50;
const CRITERION_MAX = 2000;
const REASON_MAX = 2000;

const VIOLATION: Record<string, MessageKey> = {
  'title.required': 'requirements.error.title', 'title.too_long': 'requirements.error.titleLong', 'title.invalid': 'requirements.error.title',
  'criteria.required': 'requirements.error.criteriaRequired', 'criteria.too_many': 'requirements.error.criteriaMany',
  'criteria.text_required': 'requirements.error.criterionEmpty', 'criteria.text_too_long': 'requirements.error.criterionLong',
  'criteria.text_invalid': 'requirements.error.criterionEmpty', 'criteria.unknown': 'requirements.error.criterionUnknown',
  'criteria.invalid': 'requirements.error.criterionUnknown', 'priority.invalid': 'requirements.error.priority', 'owner.invalid': 'requirements.error.owner',
  'owner.not_member': 'requirements.error.owner', 'description.image_not_allowed': 'requirements.error.image',
  'description.page_link_not_allowed': 'requirements.error.pageLink', 'description.too_large': 'wiki.error.tooLarge',
  'description.too_deep': 'wiki.error.tooDeepContent', 'description.unsupported': 'wiki.error.content', 'description.link_invalid': 'wiki.error.link',
  'description.link_unknown_page': 'requirements.error.pageLink', 'description.image_external': 'requirements.error.image',
  'description.image_unknown': 'requirements.error.image', 'description.too_many_images': 'requirements.error.image',
  'description.invalid': 'wiki.error.content', 'reason.required': 'requirements.error.reason', 'reason.too_long': 'requirements.error.reasonLong',
  'changes.required': 'requirements.error.nothing', 'text.too_long': 'requirements.error.textLong',
  'priority.not_readable': 'requirements.error.filterHidden', 'owner.not_readable': 'requirements.error.filterHidden',
  'text.not_readable': 'requirements.error.filterHidden', 'customFields.unknown': 'requirements.error.fieldUnknown',
  'customFields.invalid': 'requirements.error.fieldUnknown',
  // TASK-100 (F3): the stored values of a revision would exceed their limit.
  'customFields.too_large': 'requirements.error.fieldsTooLarge',
};

const SPECIFIC: Record<string, MessageKey> = {
  'requirement.version_conflict': 'requirements.error.conflict', 'requirement.busy': 'requirements.error.busy',
  'requirement.discarded': 'requirements.error.discarded', 'requirement.status_invalid': 'requirements.error.status',
  'requirement.content_not_readable': 'requirements.error.contentHidden', 'requirement.not_found': 'requirements.error.notFound',
  'requirement.revision_not_found': 'requirements.error.revision', 'project.not_found': 'requirements.error.projectNotFound',
  'auth.forbidden': 'requirements.error.forbidden', 'criteria.required': 'requirements.error.criteriaRequired',
  'requirement.required_field_inaccessible': 'requirements.error.requiredHidden',
};

// Custom fields of requirements (TASK-098): the definitions the reader may read come with every answer, the values in the
// content by key. The form holds every value as text and sends the JSON value of the field's kind.
type RequirementField = Requirement['customFields'][number];
type CustomValue = string | number | boolean;
type CustomValues = Readonly<Record<string, CustomValue>>;
const CUSTOM_VIOLATION = /^custom\.([a-z][a-z0-9_]*)\.([a-z_]+)$/;
const valuesOf = (content: RequirementContent): CustomValues => (content.customFields ?? {}) as CustomValues;
const customText = (value: CustomValue | undefined): string => (value === undefined ? '' : String(value));
const textsOf = (content: RequirementContent): Record<string, string> =>
  Object.fromEntries(Object.entries(valuesOf(content)).map(([key, value]) => [key, customText(value)]));
function customJson(field: RequirementField, text: string): CustomValue | null {
  if (text === '') return null;
  if (field.type === 'integer') return /^-?\d+$/.test(text.trim()) ? Number(text.trim()) : text;
  if (field.type === 'boolean') return text === 'true';
  return text;
}

function useRequirementProblem() {
  const { t } = useI18n();
  const text = useProblemText();
  return (problem: ProblemInfo, fields: readonly RequirementField[] = []) => {
    const known = (problem.violations ?? []).map((violation) => VIOLATION[violation]).filter((key): key is MessageKey => key !== undefined);
    // A refused value of a custom field names the field: "Bereich: Das Feld wird für „Zur Prüfung“ benötigt."
    const custom = (problem.violations ?? []).flatMap((violation) => {
      const match = CUSTOM_VIOLATION.exec(violation);
      if (match === null) return [];
      const [, key = '', code = ''] = match;
      const field = fields.find((item) => item.key === key)?.name ?? `${CUSTOM_PREFIX}${key}`;
      return [`${field}: ${t(CUSTOM_ERROR[code] ?? 'custom.error.invalid')}`];
    });
    if (problem.status === 400 && known.length + custom.length > 0) return [...[...new Set(known)].map((key) => t(key)), ...custom].join(' ');
    if (problem.status === 400 && problem.code !== null && VIOLATION[problem.code] !== undefined) return t(VIOLATION[problem.code] as MessageKey);
    return text(problem, SPECIFIC);
  };
}

const statusKey = (status: RequirementStatus): MessageKey => `requirements.status.${status}` as MessageKey;
const priorityKey = (priority: RequirementPriority): MessageKey => `requirements.priority.${priority}` as MessageKey;
const readable = (access: RequirementAccess, field: string) => (access.readableFields as readonly string[]).includes(field);
const writable = (access: RequirementAccess, field: string) => (access.writableFields as readonly string[]).includes(field);
const EMPTY_DOC: WikiDocument = { type: 'doc', content: [] };

function StatusBadge({ status }: { readonly status: RequirementStatus }) {
  const { t } = useI18n();
  return <span className={`badge requirement-status status-${status}`} data-testid="requirement-status" data-status={status}>{t(statusKey(status))}</span>;
}

// ---------------------------------------------------------------- the list with filters and creating

interface ListState {
  readonly items: readonly RequirementItem[];
  readonly nextAfter: number | null;
  readonly access: RequirementAccess | null;
  readonly loading: boolean;
  readonly problem: ProblemInfo | null;
}

function CreateForm({ projectId, access }: { readonly projectId: string; readonly access: RequirementAccess }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useRequirementProblem();
  const [title, setTitle] = useState('');
  const [priority, setPriority] = useState<RequirementPriority>('normal');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  // Typed input of a requirement not created yet is protected like that of the editor (F6 of the TASK-096 review).
  useUnsavedGuard(title !== '' || priority !== 'normal');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (title.trim() === '') return setError(t('requirements.error.title'));
    setError(null);
    setBusy(true);
    const body: RequirementInput = writable(access, 'priority') ? { title, priority } : { title };
    const result = await createRequirement(api, projectId, body);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    navigate({ name: 'requirementPage', projectId, requirementId: result.data.value.id });
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)} noValidate data-testid="requirement-create">
      <h2>{t('requirements.create.title')}</h2>
      <TextField label={t('requirements.field.title')} value={title} onChange={setTitle} required maxLength={200} error={error} disabled={busy} />
      {writable(access, 'priority') && (
        <SelectField label={t('requirements.field.priority')} value={priority} onChange={(value) => setPriority(value as RequirementPriority)} disabled={busy}
          options={PRIORITIES.map((value) => ({ value, label: t(priorityKey(value)) }))} />
      )}
      <NoticeView notice={notice} />
      <div className="actions"><button type="submit" disabled={busy}>{t('requirements.create.submit')}</button></div>
    </form>
  );
}

function ListView({ project }: { readonly project: RequirementProject }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { directory } = useSession();
  const name = useAccountName();
  const problem = useRequirementProblem();
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [owner, setOwner] = useState('');
  const [text, setText] = useState('');
  const [applied, setApplied] = useState<RequirementFilter>({ status: null, priority: null, owner: null, text: null });
  const [state, setState] = useState<ListState>({ items: [], nextAfter: null, access: null, loading: true, problem: null });
  // Only the answer of the latest request counts: a late page of an earlier filter is dropped.
  const request = useRef(0);
  const access = state.access ?? project.access;

  async function load(filter: RequirementFilter, after: number) {
    const ticket = ++request.current;
    setState((current) => ({ ...current, loading: true, problem: null, ...(after === 0 ? { items: [], nextAfter: null } : {}) }));
    const result = await listRequirements(api, project.projectId, filter, after);
    if (ticket !== request.current) return;
    if (result.kind !== 'ok') return setState((current) => ({ ...current, loading: false, problem: result.problem }));
    const page = result.data.value;
    setState((current) => ({ items: after === 0 ? page.items : [...current.items, ...page.items], nextAfter: page.nextAfter ?? null,
      access: page.access, loading: false, problem: null }));
  }

  useEffect(() => {
    void load(applied, 0);
  }, [api, project.projectId, applied]);

  function search(event: FormEvent) {
    event.preventDefault();
    setApplied({
      status: status === '' ? null : status as RequirementStatus,
      priority: priority === '' ? null : priority as RequirementPriority,
      owner: owner === '' ? null : owner,
      text: text.trim() === '' ? null : text.trim(),
    });
  }

  const people = [...directory.values()].filter((entry) => entry.active).sort((a, b) => name(a.id).localeCompare(name(b.id), language));
  return (
    <div className="stack">
      <form className="card filters" onSubmit={search} data-testid="requirement-filters" aria-label={t('requirements.filter.label')}>
        <SelectField label={t('requirements.field.status')} value={status} onChange={setStatus}
          options={[{ value: '', label: t('requirements.filter.active') }, ...STATUSES.map((value) => ({ value, label: t(statusKey(value)) }))]} />
        {readable(access, 'priority') && (
          <SelectField label={t('requirements.field.priority')} value={priority} onChange={setPriority}
            options={[{ value: '', label: t('requirements.filter.any') }, ...PRIORITIES.map((value) => ({ value, label: t(priorityKey(value)) }))]} />
        )}
        {readable(access, 'owner') && (
          <SelectField label={t('requirements.field.owner')} value={owner} onChange={setOwner}
            options={[{ value: '', label: t('requirements.filter.any') }, ...people.map((entry) => ({ value: entry.id, label: name(entry.id) }))]} />
        )}
        {readable(access, 'title') && <TextField label={t('requirements.filter.text')} value={text} onChange={setText} maxLength={200} />}
        <div className="actions"><button type="submit">{t('requirements.filter.apply')}</button></div>
      </form>
      {state.problem !== null && (
        <div className="stack" data-testid="requirement-list-problem">
          <p role="status" className="mode-notice">{problem(state.problem)}</p>
          <div className="actions"><button type="button" className="secondary" onClick={() => void load(applied, 0)}>{t('states.retry')}</button></div>
        </div>
      )}
      {state.problem === null && !state.loading && state.items.length === 0 && <EmptyText text={t('requirements.list.empty')} />}
      {state.items.length > 0 && (
        <div className="table-scroll">
          <table data-testid="requirement-list">
            <caption className="visually-hidden">{t('requirements.list.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('requirements.field.key')}</th>
                <th scope="col">{t('requirements.field.title')}</th>
                <th scope="col">{t('requirements.field.status')}</th>
                {readable(access, 'priority') && <th scope="col">{t('requirements.field.priority')}</th>}
                {readable(access, 'owner') && <th scope="col">{t('requirements.field.owner')}</th>}
                <th scope="col">{t('requirements.field.revision')}</th>
                <th scope="col">{t('requirements.field.updated')}</th>
              </tr>
            </thead>
            <tbody>
              {state.items.map((item) => (
                <tr key={item.id} data-testid="requirement-row" data-key={item.key}>
                  <td><a href={hrefFor({ name: 'requirementPage', projectId: project.projectId, requirementId: item.id })}>{item.key}</a></td>
                  <td>{item.title ?? <span className="hint">{t('requirements.hidden')}</span>}</td>
                  <td><StatusBadge status={item.status} /></td>
                  {readable(access, 'priority') && <td>{item.priority === null ? '' : t(priorityKey(item.priority))}</td>}
                  {readable(access, 'owner') && <td>{name(item.ownerId)}</td>}
                  <td>
                    {t('requirements.revision', { revision: item.revision })}
                    {item.approvedRevision !== null && item.approvedRevision !== item.revision && (
                      <span className="hint"> {t('requirements.approvedRevision', { revision: item.approvedRevision })}</span>
                    )}
                  </td>
                  <td>{formatDateTime(item.updatedAt, language)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {state.loading && <p className="hint" role="status" data-testid="requirement-list-loading">{t('requirements.list.loading')}</p>}
      {!state.loading && state.nextAfter !== null && (
        <div className="actions">
          <button type="button" className="secondary" onClick={() => void load(applied, state.nextAfter ?? 0)} data-testid="requirement-more">
            {t('requirements.list.more')}
          </button>
        </div>
      )}
      {access.edit && writable(access, 'title') && <CreateForm projectId={project.projectId} access={access} />}
    </div>
  );
}

// ---------------------------------------------------------------- reading one requirement

function ContentView({ content, access, fields, testId }: {
  readonly content: RequirementContent; readonly access: RequirementAccess; readonly fields: readonly RequirementField[]; readonly testId: string;
}) {
  const { t } = useI18n();
  const name = useAccountName();
  const text = useCustomText();
  const values = valuesOf(content);
  return (
    <div className="stack" data-testid={testId}>
      <section aria-labelledby={`${testId}-description`}>
        <h3 id={`${testId}-description`}>{t('requirements.field.description')}</h3>
        {!readable(access, 'description') || content.descriptionHtml === null
          ? <p className="hint">{t('requirements.hidden')}</p>
          : content.descriptionHtml === ''
            ? <EmptyText text={t('requirements.description.empty')} />
            : <WikiContent html={content.descriptionHtml} testId={`${testId}-description-content`} />}
      </section>
      <section aria-labelledby={`${testId}-criteria`}>
        <h3 id={`${testId}-criteria`}>{t('requirements.field.criteria')}</h3>
        {content.criteria === null
          ? <p className="hint">{t('requirements.hidden')}</p>
          : content.criteria.length === 0
            ? <EmptyText text={t('requirements.criteria.empty')} />
            : (
              <ol className="criteria" data-testid={`${testId}-criteria-list`}>
                {content.criteria.map((criterion) => (
                  <li key={criterion.id} data-criterion={criterion.id}>
                    <strong>K{criterion.id}</strong> <span className="criterion-text">{criterion.text}</span>
                  </li>
                ))}
              </ol>
            )}
      </section>
      {fields.length > 0 && (
        <section aria-labelledby={`${testId}-custom`}>
          <h3 id={`${testId}-custom`}>{t('requirements.field.custom')}</h3>
          <dl className="facts" data-testid={`${testId}-custom`}>
            {fields.map((field) => (
              <Fragment key={field.key}>
                <dt>{field.name}</dt>
                <dd data-field={field.key}>{text(field, values[field.key], name)}</dd>
              </Fragment>
            ))}
          </dl>
        </section>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- editing with criteria and conflict


const draftsOf = (criteria: readonly RequirementCriterion[] | null, next: () => number): Draft[] =>
  (criteria ?? []).map((criterion) => ({ key: next(), id: criterion.id, text: criterion.text }));
const sameCriteria = (drafts: readonly Draft[], criteria: readonly RequirementCriterion[] | null) =>
  criteria !== null && drafts.length === criteria.length && drafts.every((draft, index) => draft.id === criteria[index]?.id && draft.text === criteria[index]?.text);

function CriteriaEditor({ items, setItems, disabled }: {
  readonly items: readonly Draft[];
  readonly setItems: (next: Draft[]) => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  const counter = useRef(1_000_000);
  const label = (item: Draft) => (item.id === null ? t('requirements.criteria.new') : `K${item.id}`);
  const { itemProps, announcement, moveItem } = useReorder(items, setItems, (item, position, count) =>
    t('requirements.criteria.moved', { criterion: label(item), position, count }));
  return (
    <fieldset className="stack criteria-editor" data-testid="requirement-criteria-editor">
      <legend>{t('requirements.field.criteria')}</legend>
      <p className="hint">{t('requirements.criteria.hint')}</p>
      <ol className="criteria-edit-list">
        {items.map((item, index) => (
          <li key={item.key} className="criterion-row" data-testid="requirement-criterion" data-criterion={item.id ?? 'new'} {...(disabled ? {} : itemProps(index))}>
            <label htmlFor={`criterion-${item.key}`} className="criterion-label">{label(item)}</label>
            <textarea id={`criterion-${item.key}`} value={item.text} rows={2} maxLength={CRITERION_MAX} disabled={disabled}
              aria-label={t('requirements.criteria.text', { criterion: label(item) })}
              onChange={(event) => setItems(items.map((other) => (other.key === item.key ? { ...other, text: event.target.value } : other)))} />
            <div className="actions">
              <button type="button" className="secondary" disabled={disabled || index === 0} onClick={() => moveItem(index, index - 1)}
                aria-label={t('requirements.criteria.up', { criterion: label(item) })}>↑</button>
              <button type="button" className="secondary" disabled={disabled || index === items.length - 1} onClick={() => moveItem(index, index + 1)}
                aria-label={t('requirements.criteria.down', { criterion: label(item) })}>↓</button>
              <button type="button" className="secondary danger" disabled={disabled} onClick={() => setItems(items.filter((other) => other.key !== item.key))}
                data-testid="requirement-criterion-remove" aria-label={t('requirements.criteria.remove', { criterion: label(item) })}>✕</button>
            </div>
          </li>
        ))}
      </ol>
      <p className="visually-hidden" aria-live="polite">{announcement}</p>
      <div className="actions">
        <button type="button" className="secondary" disabled={disabled || items.length >= MAX_CRITERIA} data-testid="requirement-criterion-add"
          onClick={() => setItems([...items, { key: ++counter.current, id: null, text: '' }])}>{t('requirements.criteria.add')}</button>
      </div>
      {MAX_CRITERIA <= items.length && <p className="hint">{t('requirements.error.criteriaMany')}</p>}
    </fieldset>
  );
}

function EditorPanel({ requirement, onSaved, onClose }: {
  readonly requirement: Requirement;
  readonly onSaved: (next: Requirement) => void;
  readonly onClose: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useRequirementProblem();
  const access = requirement.access;
  const keys = useRef(0);
  const next = () => ++keys.current;
  // The version the next save builds on; after a conflict the person takes over the newer one explicitly.
  const [base, setBase] = useState(requirement);
  const [title, setTitle] = useState(requirement.content.title ?? '');
  const [priority, setPriority] = useState<string>(requirement.content.priority ?? 'normal');
  const [owner, setOwner] = useState(requirement.ownerId ?? '');
  const [criteria, setCriteria] = useState<Draft[]>(() => draftsOf(requirement.content.criteria, next));
  const editableFields = requirement.customFields.filter((field) => writable(access, `${CUSTOM_PREFIX}${field.key}`));
  const [custom, setCustom] = useState<Record<string, string>>(() => textsOf(requirement.content));
  const [titleError, setTitleError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState<Requirement | null>(null);
  const [editorVersion, setEditorVersion] = useState(0);
  const canDescription = writable(access, 'description') && requirement.content.description !== null;
  const editor = useWikiEditor((requirement.content.description ?? EMPTY_DOC) as WikiDocument, canDescription, () => undefined);
  const [baseline, setBaseline] = useState<string | null>(null);
  // The choice of persons serves the owner and the active custom person fields one may write (TASK-100, DEC-130).
  const needsPeople = writable(access, 'owner') || editableFields.some((field) => field.type === 'user' && field.active !== false);
  const [owners, reloadOwners] = useLoad(() => (needsPeople ? listRequirementOwners(api, requirement.projectId)
    : Promise.resolve({ kind: 'ok' as const, data: { value: [] as string[], etag: null } })), [api, requirement.projectId]);
  useEffect(() => {
    if (editor !== null) setBaseline(JSON.stringify(editor.getJSON()));
  }, [editor]);
  useEffect(() => {
    if (editor === null) return;
    const update = () => setEditorVersion((value) => value + 1);
    editor.on('update', update);
    return () => {
      editor.off('update', update);
    };
  }, [editor]);
  const descriptionChanged = useMemo(() => canDescription && editor !== null && baseline !== null && JSON.stringify(editor.getJSON()) !== baseline,
    [canDescription, editor, baseline, editorVersion]);
  const changes = (): RequirementInput => {
    const body: { -readonly [K in keyof RequirementInput]: RequirementInput[K] } = {};
    if (writable(access, 'title') && title !== (base.content.title ?? '')) body.title = title;
    if (writable(access, 'priority') && priority !== (base.content.priority ?? 'normal')) body.priority = priority as RequirementPriority;
    if (writable(access, 'owner') && owner !== (base.ownerId ?? '')) body.ownerId = owner === '' ? null : owner;
    if (writable(access, 'criteria') && !sameCriteria(criteria, base.content.criteria)) body.criteria = criteria.map((item) => ({ id: item.id, text: item.text }));
    if (descriptionChanged && editor !== null) body.description = editor.getJSON() as WikiDocument;
    const customBody: Record<string, CustomValue | null> = {};
    for (const field of editableFields) {
      const value = custom[field.key] ?? '';
      if (value !== customText(valuesOf(base.content)[field.key])) customBody[field.key] = customJson(field, value);
    }
    if (Object.keys(customBody).length > 0) body.customFields = customBody;
    return body;
  };
  // The values of the own input for the comparison after a conflict: an emptied field has no value.
  const ownValues = (): Record<string, CustomValue> => {
    const values: Record<string, CustomValue> = { ...valuesOf(base.content) };
    for (const field of editableFields) {
      const value = customJson(field, custom[field.key] ?? '');
      if (value === null) delete values[field.key];
      else values[field.key] = value;
    }
    return values;
  };
  const dirty = Object.keys(changes()).length > 0;
  useUnsavedGuard(dirty);
  const contentChanged = (() => {
    const body = changes();
    return body.title !== undefined || body.priority !== undefined || body.criteria !== undefined || body.description !== undefined
      || body.customFields !== undefined;
  })();

  async function save(event: FormEvent) {
    event.preventDefault();
    if (writable(access, 'title') && title.trim() === '') return setTitleError(t('requirements.error.title'));
    if (criteria.some((item) => item.text.trim() === '')) return setNotice({ kind: 'info', text: t('requirements.error.criterionEmpty') });
    setTitleError(null);
    const body = changes();
    if (Object.keys(body).length === 0) return;
    setBusy(true);
    setNotice(null);
    const sent = editor === null ? null : JSON.stringify(editor.getJSON());
    const result = await updateRequirement(api, requirement.id, base.version, body);
    if (result.kind === 'ok') {
      setBusy(false);
      const saved = result.data.value;
      setBase(saved);
      setTitle(saved.content.title ?? '');
      setPriority(saved.content.priority ?? 'normal');
      setOwner(saved.ownerId ?? '');
      setCriteria(draftsOf(saved.content.criteria, next));
      setCustom(textsOf(saved.content));
      if (editor !== null) {
        if (sent !== null && JSON.stringify(editor.getJSON()) === sent && saved.content.description !== null) {
          editor.commands.setContent(saved.content.description as WikiDocument, { emitUpdate: false });
          setBaseline(JSON.stringify(editor.getJSON()));
        } else {
          setBaseline(sent);
        }
      }
      setConflict(null);
      setNotice({ kind: 'success', text: saved.revision !== base.revision
        ? t('requirements.savedNewRevision', { revision: saved.revision }) : t('requirements.saved', { revision: saved.revision }) });
      onSaved(saved);
      return;
    }
    if (result.problem.status === 412) {
      // Someone changed the requirement meanwhile. The own input stays; the newer state appears beside it.
      const newer = await readRequirement(api, requirement.id);
      setBusy(false);
      if (newer.kind === 'ok') return setConflict(newer.data.value);
      return setNotice({ kind: 'info', text: problem(newer.problem) });
    }
    setBusy(false);
    setNotice({ kind: 'info', text: problem(result.problem, requirement.customFields) });
  }

  // Only what the person changed stays theirs; every untouched part takes the newer value, so nothing of the other change is
  // overwritten with the stale value the form still showed.
  function keepMine() {
    if (conflict === null) return;
    if (title === (base.content.title ?? '')) setTitle(conflict.content.title ?? '');
    if (priority === (base.content.priority ?? 'normal')) setPriority(conflict.content.priority ?? 'normal');
    if (owner === (base.ownerId ?? '')) setOwner(conflict.ownerId ?? '');
    setCriteria(mergeCriteria(base.content.criteria, criteria, conflict.content.criteria, next));
    const untouched = editableFields.filter((field) => (custom[field.key] ?? '') === customText(valuesOf(base.content)[field.key]));
    setCustom((current) => ({ ...current, ...Object.fromEntries(untouched.map((field) => [field.key, customText(valuesOf(conflict.content)[field.key])])) }));
    if (editor !== null && !descriptionChanged && conflict.content.description !== null) {
      editor.commands.setContent(conflict.content.description as WikiDocument, { emitUpdate: false });
      setBaseline(JSON.stringify(editor.getJSON()));
    }
    setBase(conflict);
    setConflict(null);
    setNotice({ kind: 'info', text: t('requirements.conflict.keptMine', { version: conflict.version }) });
  }

  function takeTheirs() {
    if (conflict === null) return;
    setBase(conflict);
    setTitle(conflict.content.title ?? '');
    setPriority(conflict.content.priority ?? 'normal');
    setOwner(conflict.ownerId ?? '');
    setCriteria(draftsOf(conflict.content.criteria, next));
    setCustom(textsOf(conflict.content));
    if (editor !== null && conflict.content.description !== null) {
      editor.commands.setContent(conflict.content.description as WikiDocument, { emitUpdate: false });
      setBaseline(JSON.stringify(editor.getJSON()));
    }
    setConflict(null);
    onSaved(conflict);
    setNotice({ kind: 'info', text: t('requirements.conflict.tookTheirs', { version: conflict.version }) });
  }

  function close() {
    if (dirty && !globalThis.confirm(t('unsaved.confirm'))) return;
    onClose();
  }

  const ownerOptions = owners.state === 'ok' ? owners.value : [];
  const ownerChoices = owner !== '' && !ownerOptions.includes(owner) ? [owner, ...ownerOptions] : ownerOptions;
  return (
    <form className="stack requirement-editor" onSubmit={(event) => void save(event)} noValidate data-testid="requirement-edit">
      {base.status === 'approved' && (
        <p className="mode-notice" data-testid="requirement-new-revision-hint">{t('requirements.edit.newRevision', { revision: base.revision + 1 })}</p>
      )}
      {writable(access, 'title') && (
        <TextField label={t('requirements.field.title')} value={title} onChange={setTitle} required maxLength={200} error={titleError} disabled={busy} />
      )}
      {writable(access, 'priority') && (
        <SelectField label={t('requirements.field.priority')} value={priority} onChange={setPriority} disabled={busy}
          options={PRIORITIES.map((value) => ({ value, label: t(priorityKey(value)) }))} />
      )}
      {owners.state === 'problem' && (
        <div data-testid="requirement-people-error">
          <p className="hint">{t('requirements.people.loadFailed')}</p>
          <ProblemView problem={owners.problem} onAction={reloadOwners} actionLabel="states.retry" />
        </div>
      )}
      {writable(access, 'owner') && (
        <SelectField label={t('requirements.field.owner')} value={owner} onChange={setOwner} disabled={busy} hint={t('requirements.owner.hint')}
          options={[{ value: '', label: t('requirements.owner.none') }, ...ownerChoices.map((id) => ({ value: id, label: name(id) }))]} />
      )}
      {canDescription && editor !== null && (
        <div className="field">
          <span className="label" id="requirement-description-label">{t('requirements.field.description')}</span>
          <p className="hint">{t('requirements.description.hint')}</p>
          <WikiToolbar editor={editor} pages={[]} disabled={busy} />
          <WikiEditorContent editor={editor} />
        </div>
      )}
      {writable(access, 'criteria') && <CriteriaEditor items={criteria} setItems={setCriteria} disabled={busy} />}
      {editableFields.length > 0 && (
        <fieldset className="stack" data-testid="requirement-custom-editor">
          <legend>{t('requirements.field.custom')}</legend>
          {editableFields.map((field) => (
            <CustomFieldInput key={field.key} field={field} value={custom[field.key] ?? ''} disabled={busy}
              onChange={(value) => setCustom((current) => ({ ...current, [field.key]: value }))}
              people={ownerOptions.map((id) => ({ id, name: name(id) }))} />
          ))}
        </fieldset>
      )}
      <p className="hint" data-testid="requirement-save-state" data-dirty={dirty ? 'true' : 'false'}>
        {dirty ? t('requirements.state.unsaved')
          : t('requirements.state.saved', { version: base.version, time: formatDateTime(base.updatedAt, language), person: name(base.updatedBy) })}
      </p>
      {dirty && contentChanged && base.status === 'approved' && <p className="hint">{t('requirements.edit.opensRevision')}</p>}
      {conflict !== null && (
        <section className="card stack" data-testid="requirement-conflict" aria-labelledby="requirement-conflict-title">
          <h2 id="requirement-conflict-title">{t('requirements.conflict.title')}</h2>
          <p>{t('requirements.conflict.text', { person: name(conflict.updatedBy), time: formatDateTime(conflict.updatedAt, language), revision: conflict.revision })}</p>
          <ComparisonView legend={t('requirements.conflict.legend')} comparison={compareContents(base.revision, {
            title: writable(access, 'title') ? title : base.content.title,
            priority: writable(access, 'priority') ? priority as RequirementPriority : base.content.priority,
            description: editor !== null && canDescription ? editor.getJSON() as WikiDocument : base.content.description,
            descriptionHtml: null,
            // New criteria have no number yet; they count as added.
            criteria: writable(access, 'criteria') ? criteria.map((item, index) => ({ id: item.id ?? -(index + 1), text: item.text })) : base.content.criteria,
            customFields: ownValues(),
          }, conflict.revision, conflict.content, conflict.customFields)} />
          <details>
            <summary>{t('requirements.conflict.show')}</summary>
            <ContentView content={conflict.content} access={conflict.access} fields={conflict.customFields} testId="requirement-conflict-newer" />
          </details>
          <div className="actions">
            <button type="button" onClick={keepMine} data-testid="requirement-conflict-keep">{t('requirements.conflict.keep')}</button>
            <button type="button" className="secondary" onClick={takeTheirs} data-testid="requirement-conflict-take">{t('requirements.conflict.take')}</button>
          </div>
        </section>
      )}
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" disabled={busy || conflict !== null || !dirty} data-testid="requirement-save">{t('requirements.save')}</button>
        <button type="button" className="secondary" onClick={close} disabled={busy}>{t('requirements.closeEditor')}</button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- approving, returning and discarding

function ApproveDialog({ requirement, onDone, onStale, onCancel }: {
  readonly requirement: Requirement;
  readonly onDone: (next: Requirement) => void;
  // After a refusal because of a newer version the dialog shows the newer content, never the stale one.
  readonly onStale: () => void;
  readonly onCancel: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useRequirementProblem();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  useEffect(() => confirmRef.current?.focus(), []);

  async function approve() {
    setBusy(true);
    const result = await approveRequirement(api, requirement.id, requirement.version, requirement.revision);
    setBusy(false);
    if (result.kind !== 'ok') {
      setNotice({ kind: 'info', text: problem(result.problem) });
      if (result.problem.status === 412) onStale();
      return;
    }
    onDone(result.data.value);
  }

  return (
    <section className="card stack" role="dialog" aria-modal="false" aria-labelledby="requirement-approve-title" data-testid="requirement-approve-dialog">
      <h2 id="requirement-approve-title">{t('requirements.approve.title', { key: requirement.key })}</h2>
      <dl className="facts">
        <dt>{t('requirements.approve.revision')}</dt>
        <dd data-testid="requirement-approve-revision">{t('requirements.revision', { revision: requirement.revision })}</dd>
        <dt>{t('requirements.field.title')}</dt>
        <dd data-testid="requirement-approve-title-value">{requirement.content.title ?? t('requirements.hidden')}</dd>
        <dt>{t('requirements.field.priority')}</dt>
        <dd data-testid="requirement-approve-priority">
          {requirement.content.priority === null ? t('requirements.hidden') : t(priorityKey(requirement.content.priority))}
        </dd>
        <dt>{t('requirements.approve.editors')}</dt>
        <dd data-testid="requirement-approve-editors">{requirement.editors.map((id) => name(id)).join(', ') || t('requirements.approve.noEditors')}</dd>
        <dt>{t('requirements.approve.saved')}</dt>
        <dd>{formatDateTime(requirement.updatedAt, language)}</dd>
      </dl>
      <p className="hint">{t('requirements.approve.hint')}</p>
      <ContentView content={requirement.content} access={requirement.access} fields={requirement.customFields} testId="requirement-approve-content" />
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="button" ref={confirmRef} onClick={() => void approve()} disabled={busy} data-testid="requirement-approve-confirm">
          {t('requirements.approve.confirm', { revision: requirement.revision })}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('requirements.cancel')}</button>
      </div>
    </section>
  );
}

function ReasonDialog({ requirement, kind, onDone, onCancel }: {
  readonly requirement: Requirement;
  readonly kind: 'return' | 'discard';
  readonly onDone: (next: Requirement) => void;
  readonly onCancel: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useRequirementProblem();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (kind === 'return' && reason.trim() === '') return setError(t('requirements.error.reason'));
    setError(null);
    setBusy(true);
    const result = kind === 'return'
      ? await returnRequirement(api, requirement.id, requirement.version, reason)
      : await discardRequirement(api, requirement.id, requirement.version, reason.trim() === '' ? null : reason);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)} noValidate data-testid={`requirement-${kind}-dialog`}>
      <h2>{t(kind === 'return' ? 'requirements.return.title' : 'requirements.discard.title', { key: requirement.key })}</h2>
      <TextField label={t('requirements.field.reason')} value={reason} onChange={setReason} multiline required={kind === 'return'} maxLength={REASON_MAX}
        error={error} disabled={busy} hint={t(kind === 'return' ? 'requirements.return.hint' : 'requirements.discard.hint')} />
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" className={kind === 'discard' ? 'danger' : undefined} disabled={busy} data-testid={`requirement-${kind}-confirm`}>
          {t(kind === 'return' ? 'requirements.return.confirm' : 'requirements.discard.confirm')}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('requirements.cancel')}</button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- history and comparison

interface Comparison {
  readonly left: number;
  readonly right: number;
  readonly title: { readonly before: string; readonly after: string } | null;
  readonly priority: { readonly before: string; readonly after: string } | null;
  readonly description: readonly DiffEntry[] | null;
  readonly criteria: readonly CriterionChange[] | null;
  readonly custom: readonly { readonly field: RequirementField; readonly before: CustomValue | undefined; readonly after: CustomValue | undefined }[];
}

type CriterionChange =
  | { readonly op: 'added' | 'removed' | 'same'; readonly criterion: RequirementCriterion }
  | { readonly op: 'changed'; readonly before: RequirementCriterion; readonly after: RequirementCriterion };

/** Criteria compared by their fixed numbers: added, removed, changed text; the order is named separately. */
export function compareCriteria(before: readonly RequirementCriterion[], after: readonly RequirementCriterion[]): { changes: CriterionChange[]; moved: boolean } {
  const old = new Map(before.map((criterion) => [criterion.id, criterion]));
  const kept = new Set(after.map((criterion) => criterion.id));
  const changes: CriterionChange[] = [];
  for (const criterion of after) {
    const previous = old.get(criterion.id);
    if (previous === undefined) changes.push({ op: 'added', criterion });
    else if (previous.text !== criterion.text) changes.push({ op: 'changed', before: previous, after: criterion });
    else changes.push({ op: 'same', criterion });
  }
  for (const criterion of before) if (!kept.has(criterion.id)) changes.push({ op: 'removed', criterion });
  const order = (list: readonly RequirementCriterion[]) => list.filter((criterion) => old.has(criterion.id) && kept.has(criterion.id)).map((criterion) => criterion.id).join(',');
  return { changes, moved: order(before) !== order(after) };
}

function compare(left: RequirementRevision, right: RequirementRevision): Comparison & { readonly moved: boolean } {
  return compareContents(left.revision, left.content, right.revision, right.content, right.customFields);
}

function compareContents(leftRevision: number, a: RequirementContent, rightRevision: number, b: RequirementContent,
  fields: readonly RequirementField[]): Comparison & { readonly moved: boolean } {
  const criteria = a.criteria !== null && b.criteria !== null ? compareCriteria(a.criteria, b.criteria) : null;
  return {
    left: leftRevision,
    right: rightRevision,
    title: a.title !== null && b.title !== null && a.title !== b.title ? { before: a.title, after: b.title } : null,
    priority: a.priority !== null && b.priority !== null && a.priority !== b.priority ? { before: a.priority, after: b.priority } : null,
    description: a.description !== null && b.description !== null
      ? diffBlocks(blocksOf(a.description as WikiDocument), blocksOf(b.description as WikiDocument)) : null,
    criteria: criteria?.changes ?? null,
    moved: criteria?.moved ?? false,
    // Only fields the reader may read come with their definition; a hidden field is never compared.
    custom: fields.map((field) => ({ field, before: valuesOf(a)[field.key], after: valuesOf(b)[field.key] }))
      .filter((entry) => customText(entry.before) !== customText(entry.after)),
  };
}

function EventText({ event, projectKey, pageTitles, fields }: {
  readonly event: RequirementEvent;
  readonly projectKey: string;
  readonly pageTitles: Readonly<Record<string, string>>;
  readonly fields: readonly RequirementField[];
}) {
  const { t } = useI18n();
  const name = useAccountName();
  const text = useCustomText();
  const gitText = useGitHistoryText();
  // A custom field (TASK-098) is named by its definition; the server sends its events only to whom may read it.
  const own = event.field?.startsWith(CUSTOM_PREFIX) ? fields.find((item) => `${CUSTOM_PREFIX}${item.key}` === event.field) : undefined;
  // Links name no field of the requirement (TASK-097).
  const field = event.field === null || event.field === 'tickets' || event.field === 'pages' || event.field === 'git' ? ''
    : event.field.startsWith(CUSTOM_PREFIX) ? own?.name ?? event.field : t(`requirements.field.${event.field}` as MessageKey);
  const value = (raw: string | null) => {
    if (raw === null) return t('requirements.event.empty');
    if (own !== undefined) return text(own, raw, name);
    if (event.field === 'owner') return name(raw);
    if (event.field === 'priority') return t(priorityKey(raw as RequirementPriority));
    return raw;
  };
  const status = (raw: string | null) => (raw === null ? '' : t(statusKey(raw as RequirementStatus)));
  // Links (TASK-097): a ticket as "number:revision", a page by its identifier with the published title the reader may see.
  const ticketPart = (raw: string | null, index: 0 | 1) => (raw ?? '').split(':')[index] ?? '';
  const ticket = (raw: string | null) => `${projectKey}-${ticketPart(raw, 0)}`;
  const page = (raw: string | null) => (raw !== null && pageTitles[raw] !== undefined ? pageTitles[raw] : t('requirements.links.pageUnavailable'));
  switch (event.kind) {
    case 'created': return <>{t('requirements.event.created')}</>;
    case 'changed':
      return event.field === 'description'
        ? <>{t('requirements.event.description')}</>
        : <>{t('requirements.event.changed', { field, from: value(event.from), to: value(event.to) })}</>;
    case 'criterion_added': return <>{t('requirements.event.criterionAdded', { criterion: event.criterion ?? '', text: event.to ?? '' })}</>;
    case 'criterion_changed': return <>{t('requirements.event.criterionChanged', { criterion: event.criterion ?? '', from: event.from ?? '', to: event.to ?? '' })}</>;
    case 'criterion_removed': return <>{t('requirements.event.criterionRemoved', { criterion: event.criterion ?? '', text: event.from ?? '' })}</>;
    case 'criteria_moved': {
      const list = (raw: string | null) => (raw ?? '').split(',').filter(Boolean).join(', ');
      return <>{t('requirements.event.criteriaMoved', { from: list(event.from), to: list(event.to) })}</>;
    }
    case 'revision_opened': return <>{t('requirements.event.revisionOpened', { revision: event.to ?? event.revision, approved: event.from ?? '' })}</>;
    case 'submitted': return <>{t('requirements.event.submitted', { revision: event.revision })}</>;
    case 'approved': return <>{t('requirements.event.approved', { revision: event.revision })}</>;
    case 'returned': return <>{t('requirements.event.returned', { reason: event.reason ?? '' })}</>;
    case 'discarded': return <>{event.reason ? t('requirements.event.discardedReason', { reason: event.reason }) : t('requirements.event.discarded')}</>;
    case 'restored': return <>{t('requirements.event.restored', { status: status(event.to) })}</>;
    case 'ticket_linked': return <>{t('requirements.event.ticketLinked', { ticket: ticket(event.to), revision: ticketPart(event.to, 1) })}</>;
    case 'ticket_switched':
      return <>{t('requirements.event.ticketSwitched', { ticket: ticket(event.to), from: ticketPart(event.from, 1), to: ticketPart(event.to, 1) })}</>;
    case 'ticket_unlinked': return <>{t('requirements.event.ticketUnlinked', { ticket: ticket(event.from) })}</>;
    case 'page_linked': return <>{t('requirements.event.pageLinked', { page: page(event.to) })}</>;
    case 'page_unlinked': return <>{t('requirements.event.pageUnlinked', { page: page(event.from) })}</>;
    case 'git_linked': case 'git_unlinked': return <>{gitText(event.from, event.to)}</>;
    default: return <>{event.kind}</>;
  }
}

function HistoryPanel({ requirement }: { readonly requirement: Requirement }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useRequirementProblem();
  const [history, retry] = useLoad(() => readRequirementHistory(api, requirement.id, language === 'en' ? 'en' : 'de'), [api, requirement.id, requirement.version, language]);
  const [shown, setShown] = useState<RequirementRevision | null>(null);
  const [left, setLeft] = useState('');
  const [right, setRight] = useState('');
  const [compared, setCompared] = useState<(Comparison & { readonly moved: boolean }) | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  // Only the answer of the latest request to show or compare counts.
  const shownRequest = useRef(0);
  const comparedRequest = useRef(0);

  async function show(revision: number) {
    const ticket = ++shownRequest.current;
    const result = await readRequirementRevision(api, requirement.id, revision);
    if (ticket !== shownRequest.current) return;
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setShown(result.data.value);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (left === '' || right === '') return;
    const ticket = ++comparedRequest.current;
    const [a, b] = await Promise.all([readRequirementRevision(api, requirement.id, Number(left)), readRequirementRevision(api, requirement.id, Number(right))]);
    if (ticket !== comparedRequest.current) return;
    if (a.kind !== 'ok') return setNotice({ kind: 'info', text: problem(a.problem) });
    if (b.kind !== 'ok') return setNotice({ kind: 'info', text: problem(b.problem) });
    setCompared(compare(a.data.value, b.data.value));
  }

  return (
    <section className="stack" data-testid="requirement-history" aria-labelledby="requirement-history-title">
      <h2 id="requirement-history-title">{t('requirements.history.title')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={history} retry={retry}>
        {(value) => {
          const options = value.revisions.map((revision) => ({ value: String(revision.revision), label: t('requirements.revision', { revision: revision.revision }) }));
          return (
            <>
              <ul className="list">
                {value.revisions.map((revision) => (
                  <li key={revision.revision} className="card" data-testid="requirement-revision" data-revision={revision.revision}
                    data-approved={revision.approvedAt !== null ? 'true' : 'false'}>
                    <p>
                      <strong>{t('requirements.revision', { revision: revision.revision })}</strong>
                      {' · '}{t('requirements.history.saved', { person: name(revision.savedBy), time: formatDateTime(revision.savedAt, language) })}
                      {revision.approvedAt !== null && (
                        <span className="badge" data-testid="requirement-revision-approved">
                          {' '}{t('requirements.history.approved', { person: name(revision.approvedBy), time: formatDateTime(revision.approvedAt, language) })}
                        </span>
                      )}
                      {revision.revision === requirement.revision && <span className="badge"> {t('requirements.history.current')}</span>}
                    </p>
                    <div className="actions">
                      <button type="button" className="secondary" onClick={() => void show(revision.revision)} data-testid="requirement-revision-show">
                        {t('requirements.history.show')}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              {shown !== null && (
                <section className="card stack" data-testid="requirement-revision-view">
                  <h3>{t('requirements.history.viewing', { revision: shown.revision, title: shown.content.title ?? '' })}</h3>
                  {shown.content.priority !== null && <p>{t('requirements.field.priority')}: {t(priorityKey(shown.content.priority))}</p>}
                  <ContentView content={shown.content} access={requirement.access} fields={shown.customFields} testId="requirement-revision-content" />
                  <div className="actions"><button type="button" className="secondary" onClick={() => setShown(null)}>{t('requirements.history.hide')}</button></div>
                </section>
              )}
              {value.revisions.length > 1 && (
                <form className="card stack" onSubmit={(event) => void submit(event)} data-testid="requirement-compare">
                  <h3>{t('requirements.history.compare')}</h3>
                  <SelectField label={t('requirements.history.from')} value={left} onChange={setLeft} options={[{ value: '', label: t('requirements.history.pick') }, ...options]} />
                  <SelectField label={t('requirements.history.to')} value={right} onChange={setRight} options={[{ value: '', label: t('requirements.history.pick') }, ...options]} />
                  <div className="actions"><button type="submit" disabled={left === '' || right === ''}>{t('requirements.history.compareSubmit')}</button></div>
                  {compared !== null && <ComparisonView comparison={compared} />}
                </form>
              )}
              <h3>{t('requirements.history.events')}</h3>
              <ul className="list" data-testid="requirement-events">
                {value.events.map((event, index) => (
                  <li key={index} data-kind={event.kind}>
                    <span className="badge">{t('requirements.revision', { revision: event.revision })}</span>{' '}
                    <EventText event={event} projectKey={requirement.projectKey} pageTitles={value.pageTitles} fields={requirement.customFields} />{' · '}{name(event.actorId)}{' · '}{formatDateTime(event.occurredAt, language)}
                  </li>
                ))}
              </ul>
            </>
          );
        }}
      </LoadedView>
    </section>
  );
}

function ComparisonView({ comparison, legend }: { readonly comparison: Comparison & { readonly moved: boolean }; readonly legend?: string }) {
  const { t } = useI18n();
  const name = useAccountName();
  const text = useCustomText();
  const label = (id: number) => (id > 0 ? `K${id}` : t('requirements.criteria.new'));
  const nothing = comparison.title === null && comparison.priority === null && (comparison.description ?? []).every((entry) => entry.op === 'same')
    && (comparison.criteria ?? []).every((entry) => entry.op === 'same') && !comparison.moved && comparison.custom.length === 0;
  return (
    <div className="stack" data-testid="requirement-compare-result">
      <p className="hint">{legend ?? t('requirements.history.comparing', { from: comparison.left, to: comparison.right })}</p>
      {nothing && <p className="hint" data-testid="requirement-compare-none">{t('requirements.history.noDifference')}</p>}
      {comparison.title !== null && (
        <p data-testid="requirement-compare-title">{t('requirements.field.title')}: <del>{comparison.title.before}</del> <ins>{comparison.title.after}</ins></p>
      )}
      {comparison.priority !== null && (
        <p data-testid="requirement-compare-priority">
          {t('requirements.field.priority')}: <del>{t(priorityKey(comparison.priority.before as RequirementPriority))}</del>{' '}
          <ins>{t(priorityKey(comparison.priority.after as RequirementPriority))}</ins>
        </p>
      )}
      {comparison.description !== null && comparison.description.some((entry) => entry.op !== 'same') && (
        <DiffView entries={comparison.description} testId="requirement-compare-description" changesOnly />
      )}
      {comparison.criteria !== null && comparison.criteria.some((entry) => entry.op !== 'same') && (
        <ul className="wiki-diff" data-testid="requirement-compare-criteria">
          {comparison.criteria.filter((entry) => entry.op !== 'same').map((entry) => (
            <li key={`${entry.op} ${entry.op === 'changed' ? entry.after.id : entry.criterion.id}`} className={`diff-${entry.op}`} data-op={entry.op}>
              {entry.op === 'changed' && <><span className="diff-label">{label(entry.after.id)} {t('wiki.diff.changed')}</span><del>{entry.before.text}</del><ins>{entry.after.text}</ins></>}
              {entry.op === 'added' && <><span className="diff-label">{label(entry.criterion.id)} {t('wiki.diff.added')}</span><ins>{entry.criterion.text}</ins></>}
              {entry.op === 'removed' && <><span className="diff-label">{label(entry.criterion.id)} {t('wiki.diff.removed')}</span><del>{entry.criterion.text}</del></>}
            </li>
          ))}
        </ul>
      )}
      {comparison.moved && <p className="hint" data-testid="requirement-compare-moved">{t('requirements.history.moved')}</p>}
      {comparison.custom.map((entry) => (
        <p key={entry.field.key} data-testid="requirement-compare-custom" data-field={entry.field.key}>
          {entry.field.name}: <del>{text(entry.field, entry.before, name)}</del> <ins>{text(entry.field, entry.after, name)}</ins>
        </p>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- one requirement

type Mode = 'read' | 'edit' | 'approve' | 'return' | 'discard' | 'history';
type Change = typeof submitRequirement extends (...args: never[]) => infer R ? () => R : never;

function DetailView({ projectId, requirementId }: { readonly projectId: string; readonly requirementId: string }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useRequirementProblem();
  const [loaded, retry, replace] = useLoad(() => readRequirement(api, requirementId), [api, requirementId]);
  const [mode, setMode] = useState<Mode>('read');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  function changed(next: Requirement, text: string | null, nextMode: Mode = 'read') {
    replace({ value: next, etag: `"${next.version}"` });
    setMode(nextMode);
    setNotice(text === null ? null : { kind: 'success', text });
  }

  async function act(call: Change, success: (next: Requirement) => string, confirmText: string | null) {
    if (confirmText !== null && !globalThis.confirm(confirmText)) return;
    setBusy(true);
    setNotice(null);
    const result = await call();
    setBusy(false);
    if (result.kind !== 'ok') {
      setNotice({ kind: 'info', text: problem(result.problem, loaded.state === 'ok' ? loaded.value.customFields : []) });
      if (result.problem.status === 412) retry();
      return;
    }
    changed(result.data.value, success(result.data.value));
  }

  return (
    <LoadedView loaded={loaded} retry={retry}>
      {(requirement) => {
        const { access, content } = requirement;
        const active = requirement.status !== 'discarded';
        const canEdit = access.edit && active && access.writableFields.length > 0;
        return (
          <article className="stack requirement" data-testid="requirement" data-id={requirement.id} data-status={requirement.status}
            data-version={requirement.version} data-revision={requirement.revision}>
            <p className="breadcrumb"><a href={hrefFor({ name: 'requirementPage', projectId, requirementId: null })}>{t('requirements.backToList')}</a></p>
            <h2 data-testid="requirement-title">
              <span className="requirement-key">{requirement.key}</span>{' '}
              {content.title ?? <span className="hint">{t('requirements.hidden')}</span>}
            </h2>
            <p className="hint" data-testid="requirement-state">
              <StatusBadge status={requirement.status} />{' '}
              {t('requirements.revision', { revision: requirement.revision })}
              {requirement.approval !== null && (
                <>
                  {' · '}
                  <span data-testid="requirement-approval">
                    {t('requirements.approvalInfo', { revision: requirement.approval.revision, person: name(requirement.approval.approvedBy),
                      time: formatDateTime(requirement.approval.approvedAt, language) })}
                  </span>
                </>
              )}
              {requirement.approvedRevision !== null && requirement.approvedRevision !== requirement.revision && (
                <span data-testid="requirement-newer-revision">{' · '}{t('requirements.newerRevision', { revision: requirement.revision, approved: requirement.approvedRevision })}</span>
              )}
            </p>
            <dl className="facts">
              <dt>{t('requirements.field.priority')}</dt>
              <dd data-testid="requirement-priority">{content.priority === null ? t('requirements.hidden') : t(priorityKey(content.priority))}</dd>
              <dt>{t('requirements.field.owner')}</dt>
              <dd data-testid="requirement-owner">
                {!readable(access, 'owner') ? t('requirements.hidden') : requirement.ownerId === null ? t('requirements.owner.none') : name(requirement.ownerId)}
              </dd>
              <dt>{t('requirements.field.created')}</dt>
              <dd>{t('requirements.byAt', { person: name(requirement.createdBy), time: formatDateTime(requirement.createdAt, language) })}</dd>
              <dt>{t('requirements.field.updated')}</dt>
              <dd>{t('requirements.byAt', { person: name(requirement.updatedBy), time: formatDateTime(requirement.updatedAt, language) })}</dd>
            </dl>
            <NoticeView notice={notice} />
            {mode !== 'edit' && (
              <div className="actions" data-testid="requirement-actions">
                {mode !== 'read' && <button type="button" className="secondary" onClick={() => setMode('read')}>{t('requirements.action.read')}</button>}
                {canEdit && <button type="button" onClick={() => { setNotice(null); setMode('edit'); }} data-testid="requirement-edit-open">{t('requirements.action.edit')}</button>}
                {access.edit && requirement.status === 'draft' && (
                  <button type="button" disabled={busy} data-testid="requirement-submit"
                    onClick={() => void act(() => submitRequirement(api, requirement.id, requirement.version),
                      (next) => t('requirements.submitted', { revision: next.revision }), null)}>{t('requirements.action.submit')}</button>
                )}
                {access.approve && requirement.status === 'review' && (
                  <button type="button" onClick={() => { setNotice(null); setMode('approve'); }} data-testid="requirement-approve">{t('requirements.action.approve')}</button>
                )}
                {access.approve && requirement.status === 'review' && (
                  <button type="button" className="secondary" onClick={() => { setNotice(null); setMode('return'); }} data-testid="requirement-return">
                    {t('requirements.action.return')}
                  </button>
                )}
                {access.edit && active && (
                  <button type="button" className="secondary danger" onClick={() => { setNotice(null); setMode('discard'); }} data-testid="requirement-discard">
                    {t('requirements.action.discard')}
                  </button>
                )}
                {access.edit && !active && (
                  <button type="button" disabled={busy} data-testid="requirement-restore"
                    onClick={() => void act(() => restoreRequirement(api, requirement.id, requirement.version),
                      (next) => t('requirements.restored', { status: t(statusKey(next.status)) }), t('requirements.restoreConfirm'))}>
                    {t('requirements.action.restore')}
                  </button>
                )}
                <button type="button" className="secondary" onClick={() => setMode('history')} data-testid="requirement-history-open">{t('requirements.action.history')}</button>
              </div>
            )}
            {mode === 'edit' && (
              <EditorPanel requirement={requirement} onSaved={(saved) => replace({ value: saved, etag: `"${saved.version}"` })} onClose={() => setMode('read')} />
            )}
            {mode === 'approve' && (
              <ApproveDialog requirement={requirement} onCancel={() => setMode('read')} onStale={retry}
                onDone={(next) => changed(next, t('requirements.approved', { revision: next.approval?.revision ?? requirement.revision }))} />
            )}
            {(mode === 'return' || mode === 'discard') && (
              <ReasonDialog key={mode} requirement={requirement} kind={mode} onCancel={() => setMode('read')}
                onDone={(next) => changed(next, t(mode === 'return' ? 'requirements.returned' : 'requirements.discarded'))} />
            )}
            {mode === 'history' && <HistoryPanel key={requirement.id} requirement={requirement} />}
            {(mode === 'read' || mode === 'return' || mode === 'discard') && <ContentView content={content} access={access} fields={requirement.customFields} testId="requirement-content" />}
            {mode === 'read' && <RequirementLinksPanel key={requirement.id} requirement={requirement} />}
            {mode === 'read' && <GitLinksPanel key={`git-${requirement.id}`} target={{ kind: 'requirement', id: requirement.id }} />}
          </article>
        );
      }}
    </LoadedView>
  );
}

// ---------------------------------------------------------------- the requirements of a project and the choice of projects

function ProjectRequirements({ projectId, requirementId }: { readonly projectId: string; readonly requirementId: string | null }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, retry] = useLoad(() => listRequirementProjects(api), [api]);
  return (
    <LoadedView loaded={projects} retry={retry}>
      {(list) => {
        const project = list.find((item) => item.projectId === projectId);
        return (
          <div className="stack">
            <PageHead title={t('requirements.title')} intro={project === undefined ? t('requirements.intro') : `${project.key} · ${project.name}`}>
              <a className="button secondary" href={hrefFor('requirements')}>{t('requirements.allProjects')}</a>
            </PageHead>
            {project === undefined
              ? <EmptyText text={t('requirements.error.projectNotFound')} />
              : requirementId === null
                ? <ListView project={project} />
                : <DetailView key={requirementId} projectId={projectId} requirementId={requirementId} />}
          </div>
        );
      }}
    </LoadedView>
  );
}

function ProjectChoice() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, retry] = useLoad(() => listRequirementProjects(api), [api]);
  return (
    <div className="stack">
      <PageHead title={t('requirements.title')} intro={t('requirements.intro')} />
      <LoadedView loaded={projects} retry={retry}>
        {(list) => (list.length > 0
          ? (
            <ul className="list" data-testid="requirement-projects">
              {list.map((project) => (
                <li key={project.projectId} className="card" data-testid="requirement-project">
                  <a href={hrefFor({ name: 'requirementPage', projectId: project.projectId, requirementId: null })}>{project.key} · {project.name}</a>
                </li>
              ))}
            </ul>
          )
          : <EmptyText text={t('requirements.noProjects')} />)}
      </LoadedView>
    </div>
  );
}

export function RequirementsPage({ projectId, requirementId }: { readonly projectId: string | null; readonly requirementId: string | null }) {
  return projectId === null ? <ProjectChoice /> : <ProjectRequirements projectId={projectId} requirementId={requirementId} />;
}
