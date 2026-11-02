import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import {
  bulkTicketTags, CATALOG_PAGE, catalogTags, changeTag, mergeTag, previewMerge, TAG_BULK_TICKETS, TAG_FILTER_MAX, tagSuggestions, type Tag, type TagBulkResult,
  type TagColor, type TagMatch, type TagMergePreview, type TagScope, type TagSuggestions,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import type { ProblemInfo } from '../api/problem.ts';

// Tag catalog care, colors and suggestions (TASK-072). Rights stay on the server: every control that changes a catalog
// appears only when the server marks the tag as manageable, and every refusal of the server is shown.

export const TAG_COLORS: readonly TagColor[] = ['red', 'orange', 'amber', 'yellow', 'lime', 'green', 'teal', 'cyan', 'blue', 'indigo', 'violet', 'pink'];

const MESSAGES: Readonly<Record<string, MessageKey>> = {
  'tag.name_taken': 'tags.error.taken',
  'tag.archived': 'tags.error.archived',
  'tag.merged': 'tags.error.merged',
  'tag.merge_too_large': 'tags.error.mergeTooLarge',
  'tag.limit_reached': 'tags.error.limit',
  'tag.version_conflict': 'tags.error.conflict',
  'auth.forbidden': 'tags.error.forbidden',
};

export function useTagProblemText(): (problem: ProblemInfo) => string {
  const text = useProblemText();
  return (problem) => text(problem, MESSAGES);
}

// The name of a tag with its color as a small mark. The name always stays readable as text; the color only adds to it.
export function TagLabel({ tag }: { readonly tag: Pick<Tag, 'name' | 'color'> }) {
  return (
    <span className="tag-label">
      {tag.color !== null && <span className={`tag-dot tag-color-${tag.color}`} aria-hidden="true" />}
      {tag.name}
    </span>
  );
}

type Option =
  | { readonly kind: 'tag'; readonly tag: Tag }
  | { readonly kind: 'create'; readonly catalog: 'company' | 'project'; readonly name: string };

// A text field with suggestions while typing, as a combobox with a list box (WAI-ARIA). Arrow keys move through the
// suggestions, Enter takes the marked one, Escape closes the list. Offers to create a tag when the server allows it.
export function TagPicker({ label, projectId, exclude, only, onPick, onCreate, disabled = false }: {
  readonly label: string;
  readonly projectId: string | null;
  readonly exclude: ReadonlySet<string>;
  readonly only?: 'company' | 'project';
  readonly onPick: (tag: Tag) => void;
  readonly onCreate?: (name: string, catalog: 'company' | 'project') => void;
  readonly disabled?: boolean;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const id = useId();
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [found, setFound] = useState<TagSuggestions | null>(null);
  const latest = useRef(0);
  const closing = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  // A blur closes the list a little later so that a click on an option still counts; focus or typing in between keeps it open.
  const keepOpen = () => {
    clearTimeout(closing.current);
    setOpen(true);
  };
  useEffect(() => () => clearTimeout(closing.current), []);

  useEffect(() => {
    if (!open) return;
    const ticket = ++latest.current;
    const timer = setTimeout(() => {
      void tagSuggestions(api, projectId, text).then((result) => {
        if (ticket !== latest.current) return;
        setFound(result.kind === 'ok' ? result.data.value : null);
        setActive(0);
      });
    }, 150);
    return () => clearTimeout(timer);
  }, [api, projectId, text, open]);

  const typed = text.trim();
  const items = (found?.items ?? []).filter((tag) => !exclude.has(tag.id) && (only === undefined || tag.catalog === only));
  const exact = (found?.items ?? []).some((tag) => tag.name.toLocaleLowerCase() === typed.toLocaleLowerCase());
  const options: Option[] = [
    ...items.map((tag) => ({ kind: 'tag' as const, tag })),
    ...(onCreate !== undefined && typed !== '' && !exact && found?.createProject === true && only !== 'company' && projectId !== null
      ? [{ kind: 'create' as const, catalog: 'project' as const, name: typed }] : []),
    ...(onCreate !== undefined && typed !== '' && !exact && found?.createCompany === true && only !== 'project'
      ? [{ kind: 'create' as const, catalog: 'company' as const, name: typed }] : []),
  ];
  const shown = open && found !== null;

  function choose(option: Option) {
    setText('');
    setOpen(false);
    setFound(null);
    if (option.kind === 'tag') onPick(option.tag);
    else onCreate?.(option.name, option.catalog);
  }

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!open) setOpen(true);
      else setActive((value) => (options.length === 0 ? 0 : (value + 1) % options.length));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((value) => (options.length === 0 ? 0 : (value - 1 + options.length) % options.length));
    } else if (event.key === 'Enter') {
      if (shown && options[active] !== undefined) {
        event.preventDefault();
        choose(options[active]);
      }
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        setOpen(false);
      }
    }
  }

  const optionText = (option: Option) => option.kind === 'tag'
    ? (option.tag.catalog === 'company' && projectId !== null ? t('tags.companyLabel', { name: option.tag.name }) : option.tag.name)
    : t(option.catalog === 'project' ? 'tags.createInProject' : 'tags.createInCompany', { name: option.name });

  return (
    <div className="field tag-picker">
      <label htmlFor={id}>{label}</label>
      <input id={id} type="text" role="combobox" autoComplete="off" value={text} disabled={disabled} maxLength={50}
        aria-autocomplete="list" aria-expanded={shown} aria-controls={`${id}-list`}
        aria-activedescendant={shown && options[active] !== undefined ? `${id}-option-${active}` : undefined}
        onChange={(event) => { setText(event.target.value); keepOpen(); }}
        onFocus={keepOpen}
        onBlur={() => { clearTimeout(closing.current); closing.current = setTimeout(() => setOpen(false), 150); }}
        onKeyDown={keyDown} />
      <ul id={`${id}-list`} role="listbox" aria-label={label} className="suggestions" hidden={!shown}>
        {shown && options.length === 0 && <li className="empty" role="presentation">{t('tags.noSuggestions')}</li>}
        {shown && options.map((option, index) => (
          <li key={option.kind === 'tag' ? option.tag.id : `create-${option.catalog}`} id={`${id}-option-${index}`} role="option"
            aria-selected={index === active} className={option.kind === 'create' ? 'create' : undefined}
            onMouseDown={(event) => { event.preventDefault(); choose(option); }}>
            {option.kind === 'tag' ? <TagLabel tag={{ name: optionText(option), color: option.tag.color }} /> : optionText(option)}
          </li>
        ))}
      </ul>
    </div>
  );
}

// ---------------------------------------------------------------- catalog care

// One catalog with search, archived tags on request, usage counts and, for who may maintain it, editing and merging.
export function TagCatalog({ scope, round }: { readonly scope: TagScope; readonly round: number }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [q, setQ] = useState('');
  const [archived, setArchived] = useState(false);
  const [pages, setPages] = useState<Tag[] | null>(null);
  const [problem, setProblem] = useState<ProblemInfo | null>(null);
  const [more, setMore] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [editing, setEditing] = useState<string | null>(null);
  const [merging, setMerging] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const latest = useRef(0);
  const scopeKey = scope.kind === 'company' ? 'company' : scope.projectId;

  async function load(after: string | null, current: Tag[]) {
    const ticket = ++latest.current;
    const result = await catalogTags(api, scope, { q, archived, after });
    if (ticket !== latest.current) return;
    if (result.kind === 'problem') {
      setProblem(result.problem);
      return;
    }
    setProblem(null);
    setPages([...current, ...result.data.value]);
    setMore(result.data.value.length === CATALOG_PAGE);
  }

  useEffect(() => {
    const timer = setTimeout(() => void load(null, []), 200);
    return () => clearTimeout(timer);
  }, [api, scopeKey, q, archived, round, refresh]);

  const reload = (message: Notice | null) => {
    setEditing(null);
    setMerging(null);
    setNotice(message);
    setRefresh((value) => value + 1);
  };

  return (
    <div className="tag-catalog">
      <div className="catalog-filter">
        <TextField label={t('tags.search')} value={q} onChange={setQ} maxLength={50} />
        <label className="check">
          <input type="checkbox" checked={archived} onChange={(event) => setArchived(event.target.checked)} />
          {t('tags.showArchived')}
        </label>
      </div>
      <NoticeView notice={notice} />
      {problem !== null && <ProblemView problem={problem} onAction={() => reload(null)} actionLabel="states.retry" />}
      {problem === null && pages === null && <LoadingView />}
      {problem === null && pages !== null && (pages.length === 0 ? <EmptyText text={t(q.trim() === '' ? 'tags.empty' : 'tags.noMatch')} /> : (
        <ul className="catalog-list" data-testid={`catalog-${scope.kind}`}>
          {pages.map((tag) => (
            <li key={tag.id} className={tag.archived ? 'archived' : undefined}>
              <div className="catalog-row">
                <div>
                  <TagLabel tag={tag} />
                  {tag.archived && <span className="badge">{t('tags.archivedBadge')}</span>}
                  {tag.description !== null && <p className="hint tag-description">{tag.description}</p>}
                </div>
                <p className="hint usage">{t('tags.usage', { tickets: tag.usage?.tickets ?? 0, projects: tag.usage?.projects ?? 0 })}</p>
                {tag.manageable && (
                  <div className="row-actions">
                    <button type="button" className="secondary" aria-expanded={editing === tag.id}
                      onClick={() => { setMerging(null); setEditing(editing === tag.id ? null : tag.id); }}>
                      {t('tags.edit', { name: tag.name })}
                    </button>
                    <button type="button" className="secondary" aria-expanded={merging === tag.id}
                      onClick={() => { setEditing(null); setMerging(merging === tag.id ? null : tag.id); }}>
                      {t('tags.merge', { name: tag.name })}
                    </button>
                  </div>
                )}
              </div>
              {editing === tag.id && <TagEditor scope={scope} tag={tag} onDone={(message) => reload(message)} onCancel={() => setEditing(null)} />}
              {merging === tag.id && <TagMerge scope={scope} tag={tag} onDone={(message) => reload(message)} onCancel={() => setMerging(null)} />}
            </li>
          ))}
        </ul>
      ))}
      {problem === null && pages !== null && more && (
        <p className="actions">
          <button type="button" className="secondary" onClick={() => void load(pages[pages.length - 1]?.name ?? null, pages)}>{t('tags.more')}</button>
        </p>
      )}
    </div>
  );
}

function TagEditor({ scope, tag, onDone, onCancel }: {
  readonly scope: TagScope;
  readonly tag: Tag;
  readonly onDone: (notice: Notice) => void;
  readonly onCancel: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problemText = useTagProblemText();
  const [name, setName] = useState(tag.name);
  const [description, setDescription] = useState(tag.description ?? '');
  const [color, setColor] = useState<string>(tag.color ?? '');
  const [archived, setArchived] = useState(tag.archived);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const dirty = name !== tag.name || description !== (tag.description ?? '') || color !== (tag.color ?? '') || archived !== tag.archived;
  useUnsavedGuard(dirty);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === '') return setNotice({ kind: 'info', text: t('tags.error.name') });
    if (!dirty) return onCancel();
    setBusy(true);
    const result = await changeTag(api, scope, tag.id, `"${tag.version}"`, {
      ...(name !== tag.name ? { name: name.trim() } : {}),
      ...(description !== (tag.description ?? '') ? { description: description.trim() === '' ? null : description } : {}),
      ...(color !== (tag.color ?? '') ? { color: color === '' ? null : color as TagColor } : {}),
      ...(archived !== tag.archived ? { archived } : {}),
    });
    setBusy(false);
    if (result.kind === 'ok') return onDone({ kind: 'success', text: t('tags.saved', { name: result.data.value.name }) });
    const violation = result.problem.code === 'request.invalid';
    setNotice({ kind: 'info', text: violation ? t('tags.error.details') : problemText(result.problem) });
  }

  return (
    <form className="tag-editor panel" onSubmit={(event) => void submit(event)} aria-label={t('tags.editLabel', { name: tag.name })}>
      <NoticeView notice={notice} />
      <TextField label={t('tags.name')} value={name} onChange={setName} maxLength={50} required />
      <TextField label={t('tags.description')} value={description} onChange={setDescription} maxLength={500} multiline hint={t('tags.descriptionHint')} />
      <SelectField label={t('tags.color')} value={color} onChange={setColor}
        options={[{ value: '', label: t('tags.color.none') }, ...TAG_COLORS.map((value) => ({ value, label: t(`tags.color.${value}` as MessageKey) }))]} />
      <p className="tag-preview" aria-hidden="true"><TagLabel tag={{ name: name.trim() === '' ? tag.name : name, color: color === '' ? null : color as TagColor }} /></p>
      <label className="check">
        <input type="checkbox" checked={archived} onChange={(event) => setArchived(event.target.checked)} />
        {t('tags.archived')}
      </label>
      <p className="hint">{t('tags.archivedHint')}</p>
      <p className="actions">
        <button type="submit" disabled={busy}>{t('tags.save')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('tags.cancel')}</button>
      </p>
    </form>
  );
}

function TagMerge({ scope, tag, onDone, onCancel }: {
  readonly scope: TagScope;
  readonly tag: Tag;
  readonly onDone: (notice: Notice) => void;
  readonly onCancel: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problemText = useTagProblemText();
  const [target, setTarget] = useState<Tag | null>(null);
  const [preview, setPreview] = useState<TagMergePreview | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const projectId = scope.kind === 'project' ? scope.projectId : null;

  async function pick(chosen: Tag) {
    setTarget(chosen);
    setPreview(null);
    setNotice(null);
    const result = await previewMerge(api, scope, tag.id, chosen.id);
    if (result.kind === 'ok') setPreview(result.data.value);
    else setNotice({ kind: 'info', text: problemText(result.problem) });
  }

  async function confirm() {
    if (target === null) return;
    setBusy(true);
    const result = await mergeTag(api, scope, tag.id, tag.version, target.id);
    setBusy(false);
    if (result.kind === 'ok') return onDone({ kind: 'success', text: t('tags.merged', { source: tag.name, target: result.data.value.name }) });
    setNotice({ kind: 'info', text: problemText(result.problem) });
  }

  return (
    <div className="tag-merge panel" role="group" aria-label={t('tags.mergeLabel', { name: tag.name })}>
      <p>{t('tags.mergeIntro', { name: tag.name })}</p>
      <TagPicker label={t('tags.mergeTarget')} projectId={projectId} only={scope.kind} exclude={new Set([tag.id])} onPick={(chosen) => void pick(chosen)} />
      <NoticeView notice={notice} />
      {target !== null && preview !== null && (
        <div className="merge-preview" data-testid="merge-preview">
          <p>{t('tags.mergePreview', { source: tag.name, target: target.name, tickets: preview.usage.tickets, projects: preview.usage.projects })}</p>
          <p className="hint">{t('tags.mergeHidden')}</p>
          <p className="hint">{t('tags.mergeName', { name: tag.name })}</p>
          <p className="actions">
            <button type="button" disabled={busy} onClick={() => void confirm()}>{t('tags.mergeConfirm', { target: target.name })}</button>
          </p>
        </div>
      )}
      <p className="actions"><button type="button" className="secondary" onClick={onCancel}>{t('tags.cancel')}</button></p>
    </div>
  );
}

// ---------------------------------------------------------------- filter and bulk change (TASK-073)

export interface TagFilterValue {
  readonly match: TagMatch;
  readonly tags: readonly Tag[];
}

export const NO_TAG_FILTER: TagFilterValue = { match: 'all', tags: [] };

// True when the filter asks something of the tags; "all" without tags asks nothing.
export const tagFilterActive = (filter: TagFilterValue) => filter.match === 'untagged' || filter.tags.length > 0;

// Chooses how up to ten tags filter: all of them, at least one, none of them, or tickets without any tag.
export function TagFilter({ projectId, value, onChange }: {
  readonly projectId: string | null;
  readonly value: TagFilterValue;
  readonly onChange: (value: TagFilterValue) => void;
}) {
  const { t } = useI18n();
  const untagged = value.match === 'untagged';
  return (
    <fieldset className="tag-filter" data-testid="tag-filter">
      <legend>{t('tagFilter.legend')}</legend>
      <SelectField label={t('tagFilter.match')} value={value.match} onChange={(match) => onChange({ match: match as TagMatch, tags: match === 'untagged' ? [] : value.tags })}
        options={(['all', 'any', 'none', 'untagged'] as const).map((match) => ({ value: match, label: t(`tagFilter.match.${match}`) }))} />
      {!untagged && value.tags.length > 0 && (
        <ul className="chips" aria-label={t('tagFilter.chosen')}>
          {value.tags.map((tag) => (
            <li key={tag.id}>
              <TagLabel tag={tag} />
              <button type="button" className="chip-remove" aria-label={t('tagFilter.remove', { name: tag.name })}
                onClick={() => onChange({ ...value, tags: value.tags.filter((item) => item.id !== tag.id) })}>×</button>
            </li>
          ))}
        </ul>
      )}
      {!untagged && value.tags.length < TAG_FILTER_MAX && (
        <TagPicker label={t('tagFilter.add')} projectId={projectId} exclude={new Set(value.tags.map((tag) => tag.id))}
          onPick={(tag) => onChange({ ...value, tags: [...value.tags, tag] })} />
      )}
      {!untagged && TAG_FILTER_MAX <= value.tags.length && <p className="hint">{t('tagFilter.full', { count: TAG_FILTER_MAX })}</p>}
    </fieldset>
  );
}

// Adds or removes up to ten existing tags at the selected tickets. A check names what will happen before anything changes;
// the server then checks and changes every ticket for itself and the result counts each outcome.
export function TagBulk({ selected, projectId, onDone }: {
  readonly selected: readonly string[];
  readonly projectId: string | null;
  readonly onDone: (result: TagBulkResult) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problemText = useTagProblemText();
  const [action, setAction] = useState<'add' | 'remove'>('add');
  const [chosen, setChosen] = useState<readonly Tag[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [result, setResult] = useState<TagBulkResult | null>(null);
  const names = chosen.map((tag) => `„${tag.name}“`).join(', ');

  async function apply() {
    setBusy(true);
    setNotice(null);
    const answer = await bulkTicketTags(api, action, chosen.map((tag) => tag.id), selected);
    setBusy(false);
    setConfirming(false);
    if (answer.kind !== 'ok') return setNotice({ kind: 'info', text: problemText(answer.problem) });
    setResult(answer.data.value);
    onDone(answer.data.value);
  }

  return (
    <section aria-labelledby="tag-bulk" className="panel tag-bulk" data-testid="tag-bulk">
      <h2 id="tag-bulk">{t('tagBulk.title', { count: selected.length })}</h2>
      <NoticeView notice={notice} />
      {result !== null && <TagBulkSummary result={result} />}
      <SelectField label={t('tagBulk.action')} value={action} onChange={(value) => { setAction(value as 'add' | 'remove'); setConfirming(false); }}
        options={[{ value: 'add', label: t('tagBulk.add') }, { value: 'remove', label: t('tagBulk.remove') }]} />
      {chosen.length > 0 && (
        <ul className="chips" aria-label={t('tagBulk.chosen')}>
          {chosen.map((tag) => (
            <li key={tag.id}>
              <TagLabel tag={tag} />
              <button type="button" className="chip-remove" aria-label={t('tagFilter.remove', { name: tag.name })}
                onClick={() => { setChosen(chosen.filter((item) => item.id !== tag.id)); setConfirming(false); }}>×</button>
            </li>
          ))}
        </ul>
      )}
      {chosen.length < TAG_FILTER_MAX && (
        <TagPicker label={t('tagBulk.tag')} projectId={projectId} exclude={new Set(chosen.map((tag) => tag.id))}
          onPick={(tag) => { setChosen([...chosen, tag]); setConfirming(false); }} />
      )}
      {!confirming ? (
        <p className="actions">
          <button type="button" disabled={chosen.length === 0 || selected.length === 0} onClick={() => setConfirming(true)}>{t('tagBulk.check')}</button>
        </p>
      ) : (
        <div className="confirm" data-testid="tag-bulk-preview">
          <p>{t(action === 'add' ? 'tagBulk.previewAdd' : 'tagBulk.previewRemove', { tags: names, count: selected.length })}</p>
          <p className="hint">{t('tagBulk.previewHint')}</p>
          <p className="actions">
            <button type="button" disabled={busy} onClick={() => void apply()}>{t('tagBulk.confirm')}</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => setConfirming(false)}>{t('tags.cancel')}</button>
          </p>
        </div>
      )}
    </section>
  );
}

const BULK_REASONS: Readonly<Record<string, MessageKey>> = {
  'auth.forbidden': 'tagBulk.reason.forbidden',
  'tag.limit_reached': 'tagBulk.reason.limit',
  'tag.archived': 'tagBulk.reason.archived',
  'tag.merged': 'tagBulk.reason.archived',
  'tag.busy': 'tagBulk.reason.busy',
};

function TagBulkSummary({ result }: { readonly result: TagBulkResult }) {
  const { t } = useI18n();
  const named = result.items.filter((item) => item.outcome === 'refused' && item.key !== null);
  return (
    <div className="notice info" role="status" data-testid="tag-bulk-result">
      <p>{t('tagBulk.result', { changed: result.changed, unchanged: result.unchanged, notMatching: result.notMatching, refused: result.refused })}</p>
      {named.length > 0 && (
        <ul>
          {named.map((item) => (
            <li key={item.ticketId}>{item.key}: {t((item.code !== null ? BULK_REASONS[item.code] : undefined) ?? 'tagBulk.reason.other')}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

// The tickets chosen for a bulk change, kept across pages and filters up to the limit of the server.
export function useTicketSelection() {
  const [selected, setSelected] = useState<ReadonlyMap<string, string>>(new Map());
  const full = selected.size >= TAG_BULK_TICKETS;
  return {
    selected,
    full,
    toggle(id: string, key: string, on: boolean) {
      setSelected((current) => {
        const next = new Map(current);
        if (!on) next.delete(id);
        else if (next.size < TAG_BULK_TICKETS) next.set(id, key);
        return next;
      });
    },
    setMany(items: readonly { readonly id: string; readonly key: string }[], on: boolean) {
      setSelected((current) => {
        const next = new Map(current);
        for (const item of items) {
          if (!on) next.delete(item.id);
          else if (next.size < TAG_BULK_TICKETS) next.set(item.id, item.key);
        }
        return next;
      });
    },
    clear() {
      setSelected(new Map());
    },
  };
}

export type TicketSelection = ReturnType<typeof useTicketSelection>;

// The checkbox of one ticket row; the label names the ticket for screen readers.
export function SelectTicket({ selection, id, ticketKey }: { readonly selection: TicketSelection; readonly id: string; readonly ticketKey: string }) {
  const { t } = useI18n();
  const on = selection.selected.has(id);
  return <input type="checkbox" checked={on} disabled={!on && selection.full} aria-label={t('tickets.select', { key: ticketKey })}
    onChange={(event) => selection.toggle(id, ticketKey, event.target.checked)} />;
}

// Selects or clears all shown tickets at once.
export function SelectPage({ selection, items }: { readonly selection: TicketSelection; readonly items: readonly { readonly id: string; readonly key: string }[] }) {
  const { t } = useI18n();
  const all = items.length > 0 && items.every((item) => selection.selected.has(item.id));
  return <input type="checkbox" checked={all} aria-label={t('tickets.selectPage')} onChange={(event) => selection.setMany(items, event.target.checked)} />;
}

// The count of the selection with the way to clear it, and the bulk change for it.
export function SelectionBar({ selection, projectId, onDone }: {
  readonly selection: TicketSelection;
  readonly projectId: string | null;
  readonly onDone: () => void;
}) {
  const { t } = useI18n();
  if (selection.selected.size === 0) return null;
  return (
    <>
      <p className="selection-bar" role="status">
        {t('tickets.selected', { count: selection.selected.size })}
        {selection.full && <> · {t('tickets.selectionFull', { count: TAG_BULK_TICKETS })}</>}
        {' '}<button type="button" className="secondary" onClick={selection.clear}>{t('tickets.clearSelection')}</button>
      </p>
      <TagBulk selected={[...selection.selected.keys()]} projectId={projectId} onDone={onDone} />
    </>
  );
}
