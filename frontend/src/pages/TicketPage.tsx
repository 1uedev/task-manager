import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  assignTicketTag, companyTags, listMembers, projectTags, readHistory, readProject, readTicket, removeTicketTag, ticketTags, updateTicket,
  type CustomValues, type Project, type Tag, type Ticket, type TicketChange,
} from '../api/product.ts';
import {
  CUSTOM_PREFIX, customFieldsOf, formText, nextStatuses, parseViolation, requiredFor, sendValue, statusKeysOf, type CustomField,
} from '../app/configuration.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { CUSTOM_ERROR, CustomFieldInput, useCustomText } from './CustomFieldInput.tsx';
import { statusName, typeName } from './ProjectPage.tsx';

const FIELDS = ['title', 'description', 'type', 'status', 'assignee'] as const;
type Field = (typeof FIELDS)[number];
type Draft = Record<Field, string>;
// The text of every custom field control by field key.
type CustomDraft = Readonly<Record<string, string>>;
const FIELD_LABEL: Record<Field | 'tags', MessageKey> = {
  title: 'field.title', description: 'field.description', type: 'field.type', status: 'field.status', assignee: 'field.assignee', tags: 'field.tags',
};

function customDraftOf(ticket: Ticket, fields: readonly CustomField[]): CustomDraft {
  return Object.fromEntries(fields.map((field) => [field.key, formText(ticket.customFields[field.key])]));
}

function draftOf(ticket: Ticket): Draft {
  const fields = ticket.fields as Partial<Record<Field, string | null>>;
  return Object.fromEntries(FIELDS.map((field) => [field, fields[field] ?? ''])) as Draft;
}

// One ticket: the readable fields, a form for the writable ones, tags and history. Rights and rules stay on the server;
// the page only shows what the answer allows and keeps the input when the server refuses or the ticket changed meanwhile.
export function TicketPage({ projectId, number }: { readonly projectId: string; readonly number: number }) {
  const { api } = useBrand();
  const [project, reloadProject] = useLoad(() => readProject(api, projectId), [api, projectId]);
  const [ticket, reloadTicket, replaceTicket] = useLoad(() => readTicket(api, projectId, number), [api, projectId, number]);
  const [round, setRound] = useState(0);
  // Counts refusals: after one the rights may have changed, so tags and history are loaded again from scratch and their old
  // content is not shown meanwhile, even though the ticket version stayed the same (finding F1 of the review of TASK-030).
  const [refusals, setRefusals] = useState(0);

  return (
    <LoadedView loaded={project} retry={reloadProject}>
      {(projectValue) => (
        <LoadedView loaded={ticket} retry={reloadTicket}>
          {(ticketValue, etag) => (
            <TicketView key={ticketValue.id} project={projectValue} ticket={ticketValue} etag={etag ?? `"${ticketValue.version}"`} round={round}
              refusals={refusals}
              onChanged={(next, nextEtag) => {
                replaceTicket({ value: next, etag: nextEtag });
                setRound((value) => value + 1);
              }}
              onRefused={() => {
                reloadTicket();
                setRefusals((value) => value + 1);
              }}
              reloadTicket={reloadTicket} />
          )}
        </LoadedView>
      )}
    </LoadedView>
  );
}

function TicketView({ project, ticket, etag, round, refusals, onChanged, onRefused, reloadTicket }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly etag: string;
  readonly round: number;
  readonly refusals: number;
  readonly onChanged: (ticket: Ticket, etag: string | null) => void;
  // The server refused a change: ticket, tags, history and the rights of the session are read again.
  readonly onRefused: () => void;
  readonly reloadTicket: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const { directory, refresh } = useSession();
  const text = useProblemText();
  const customFields = customFieldsOf(project.configuration);
  const customText = useCustomText();
  const [base, setBase] = useState<Draft>(() => draftOf(ticket));
  const [draft, setDraft] = useState<Draft>(() => draftOf(ticket));
  const [customBase, setCustomBase] = useState<CustomDraft>(() => customDraftOf(ticket, customFields));
  const [customDraft, setCustomDraft] = useState<CustomDraft>(() => customDraftOf(ticket, customFields));
  const [customErrors, setCustomErrors] = useState<Readonly<Record<string, string>>>({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);
  const [members] = useLoad(() => listMembers(api, project.id), [api, project.id]);
  const readable = new Set(Object.keys(ticket.fields));
  const writable = new Set<string>(ticket.writableFields);
  const changed = FIELDS.filter((field) => writable.has(field) && draft[field] !== base[field]);
  const customChanged = customFields.filter((field) => writable.has(`${CUSTOM_PREFIX}${field.key}`) && customDraft[field.key] !== customBase[field.key]);
  useUnsavedGuard(changed.length > 0 || customChanged.length > 0);

  // A newer version from the server replaces what was shown, but never what the person typed and did not save.
  useEffect(() => {
    const fresh = draftOf(ticket);
    setDraft((current) => Object.fromEntries(FIELDS.map((field) => [field, current[field] !== base[field] ? current[field] : fresh[field]])) as Draft);
    setBase(fresh);
    const freshCustom = customDraftOf(ticket, customFields);
    setCustomDraft((current) => Object.fromEntries(customFields.map((field) => [field.key, current[field.key] !== customBase[field.key] ? current[field.key] ?? '' : freshCustom[field.key] ?? ''])));
    setCustomBase(freshCustom);
  }, [ticket]);

  const config = project.configuration;
  // The steps of the workflow of the type the ticket will have, from the status it has now.
  const statusOptions = useMemo(() => {
    const allowed = new Set(nextStatuses(config, draft.type, base.status));
    return config.statuses.filter((status) => allowed.has(status.key)).map((status) => ({ value: status.key, label: status.name }));
  }, [config, base.status, draft.type]);
  const required = draft.status !== base.status ? requiredFor(config, draft.type, base.status, draft.status) : [];
  const fieldName = (id: string) => id.startsWith(CUSTOM_PREFIX)
    ? customFields.find((field) => `${CUSTOM_PREFIX}${field.key}` === id)?.name ?? id : t(FIELD_LABEL[id as Field] ?? 'field.title');
  const people = members.state === 'ok'
    ? members.value.map((member) => member.accountId)
    : [...directory.values()].filter((entry) => entry.active).map((entry) => entry.id);
  const persons = people.map((id) => ({ id, name: name(id) }));

  async function save(event: FormEvent) {
    event.preventDefault();
    if (changed.length === 0 && customChanged.length === 0) return;
    if (changed.includes('title') && draft.title.trim() === '') return setNotice({ kind: 'info', text: t('tickets.error.title') });
    const change: TicketChange = {};
    for (const field of changed) {
      const value = field === 'title' ? draft.title.trim() : draft[field];
      Object.assign(change, { [field]: (field === 'description' || field === 'assignee') && value === '' ? null : value });
    }
    const values: Record<string, string | number | boolean | null> = {};
    const errors: Record<string, string> = {};
    for (const field of customChanged) {
      const value = sendValue(field.type, customDraft[field.key] ?? '');
      if (value === undefined) errors[field.key] = t('custom.error.invalid');
      else values[field.key] = value;
    }
    setCustomErrors(errors);
    if (Object.keys(errors).length > 0) return;
    if (customChanged.length > 0) Object.assign(change, { customFields: values satisfies CustomValues });
    // What was sent; input typed while the answer is outstanding is not part of it and stays unsaved (finding F2).
    const sent = draft;
    const sentCustom = customDraft;
    setBusy(true);
    setNotice(null);
    const result = await updateTicket(api, project.id, ticket.number, etag, change);
    setBusy(false);
    if (result.kind === 'ok') {
      const fresh = draftOf(result.data.value);
      const freshCustom = customDraftOf(result.data.value, customFields);
      setConflict(false);
      setBase(fresh);
      setDraft((current) => Object.fromEntries(FIELDS.map((field) => [field, current[field] !== sent[field] ? current[field] : fresh[field]])) as Draft);
      setCustomBase(freshCustom);
      setCustomDraft((current) => Object.fromEntries(customFields.map((field) => [field.key,
        current[field.key] !== sentCustom[field.key] ? current[field.key] ?? '' : freshCustom[field.key] ?? ''])));
      onChanged(result.data.value, result.data.etag);
      return setNotice({ kind: 'success', text: t('ticket.saved') });
    }
    if (result.problem.status === 412) {
      // Someone else saved first: load the current version, keep the input and let the person decide.
      setConflict(true);
      reloadTicket();
      return;
    }
    if (result.problem.status === 403) {
      onRefused();
      // The message says the rights are reloaded, so it waits for them.
      await refresh();
    }
    const reasons = (result.problem.violations ?? []).map((code) => violationText(code)).filter((reason): reason is string => reason !== null);
    const base = text(result.problem, {
      'ticket.transition_not_allowed': 'ticket.error.transition',
      'request.invalid': 'tickets.error.invalid',
      'auth.forbidden': 'ticket.error.forbidden',
    });
    setNotice({ kind: 'info', text: reasons.length > 0 ? `${base} ${reasons.join(' ')}` : base });
  }

  // A reason the server names for the input, in words; null for one the interface has no text for.
  function violationText(code: string): string | null {
    const parsed = parseViolation(code);
    const key = parsed === null ? undefined : CUSTOM_ERROR[parsed.reason];
    return parsed === null || key === undefined ? null : `${fieldName(parsed.field)}: ${t(key)}`;
  }

  const display = (field: Field): string => {
    const value = (ticket.fields as Partial<Record<Field, string | null>>)[field];
    if (field === 'status') return statusName(project, value);
    if (field === 'type') return typeName(project, value);
    if (field === 'assignee') return value ? name(value) : t('field.nobody');
    return value ?? '';
  };

  const input = (field: Field) => {
    const label = t(FIELD_LABEL[field]);
    const set = (value: string) => setDraft((current) => ({ ...current, [field]: value }));
    switch (field) {
      case 'title':
        return <TextField label={label} value={draft.title} onChange={set} required maxLength={200} />;
      case 'description':
        return <TextField label={label} value={draft.description} onChange={set} multiline maxLength={20000} />;
      case 'type':
        return <SelectField label={label} value={draft.type} onChange={set} options={config.ticketTypes.map((item) => ({ value: item.key, label: item.name }))} />;
      case 'status':
        return <SelectField label={label} value={draft.status} onChange={set} options={statusOptions} />;
      case 'assignee':
        return <SelectField label={label} value={draft.assignee} onChange={set}
          options={[{ value: '', label: t('field.nobody') }, ...people.map((id) => ({ value: id, label: name(id) }))]} />;
    }
  };

  return (
    <>
      <p className="breadcrumb"><a href={hrefFor({ name: 'project', id: project.id, tab: 'tickets' })}>{`${project.key} · ${project.name}`}</a></p>
      <PageHead title={readable.has('title') ? `${ticket.key} · ${display('title')}` : ticket.key}
        intro={t('ticket.meta', { version: ticket.version, by: name(ticket.updatedBy), date: formatDateTime(ticket.updatedAt, language) })} />
      {conflict && (
        <div role="alert" className="state state-problem" data-testid="ticket-conflict">
          <p className="state-title">{t('ticket.conflict.title')}</p>
          <p>{t('ticket.conflict.text')}</p>
          <p className="actions">
            <button type="button" className="secondary" onClick={() => {
              setDraft(draftOf(ticket));
              setConflict(false);
            }}>{t('ticket.conflict.discard')}</button>
          </p>
        </div>
      )}
      <NoticeView notice={notice} />
      <div className="ticket-layout">
        <section aria-labelledby="ticket-fields" className="panel">
          <h2 id="ticket-fields">{t('ticket.fields')}</h2>
          <form onSubmit={(event) => void save(event)} noValidate>
            {FIELDS.map((field) => {
              if (!readable.has(field)) return <p key={field} className="hint" data-field={field}>{t('ticket.hiddenField', { field: t(FIELD_LABEL[field]) })}</p>;
              if (writable.has(field)) return <div key={field} data-field={field}>{input(field)}</div>;
              return (
                <div key={field} className="field readonly" data-field={field}>
                  <span className="label-small">{t(FIELD_LABEL[field])}</span>
                  <p className={field === 'description' ? 'prewrap' : undefined}>{display(field) || t('field.empty')}</p>
                </div>
              );
            })}
            {required.length > 0 && <p className="hint" data-testid="transition-requires">{t('ticket.requires', { fields: required.map(fieldName).join(', ') })}</p>}
            {customFields.map((field) => {
              const id = `${CUSTOM_PREFIX}${field.key}`;
              const shown = field.key in ticket.customFields;
              if (writable.has(id)) {
                return (
                  <div key={id} data-field={id}>
                    <CustomFieldInput field={field} value={customDraft[field.key] ?? ''} people={persons} error={customErrors[field.key] ?? null}
                      onChange={(value) => setCustomDraft((current) => ({ ...current, [field.key]: value }))} />
                  </div>
                );
              }
              if (!shown) return null;
              return (
                <div key={id} className="field readonly" data-field={id}>
                  <span className="label-small">{field.name}</span>
                  <p className={field.type === 'longtext' ? 'prewrap' : undefined}>{customText(field, ticket.customFields[field.key], name)}</p>
                </div>
              );
            })}
            {writable.size > 0
              ? <button type="submit" disabled={busy || (changed.length === 0 && customChanged.length === 0)}>{t('ticket.save')}</button>
              : <p className="hint">{t('ticket.readOnly')}</p>}
          </form>
        </section>
        <div>
          <TicketTags key={`tags-${refusals}`} project={project} ticket={ticket} round={round} />
          <History key={`history-${refusals}`} project={project} ticket={ticket} round={round} />
        </div>
      </div>
    </>
  );
}

function TicketTags({ project, ticket, round }: { readonly project: Project; readonly ticket: Ticket; readonly round: number }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [assigned, reload] = useLoad(() => ticketTags(api, project.id, ticket.number), [api, project.id, ticket.number, round]);
  const [own] = useLoad(() => projectTags(api, project.id), [api, project.id]);
  const [shared] = useLoad(() => companyTags(api), [api]);
  const [choice, setChoice] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);

  // Without the read rule for tags the section is not shown at all.
  if (assigned.state === 'problem' && (assigned.problem.status === 403 || assigned.problem.status === 404)) return null;
  // writableFields does not name tags; an empty list means no ticket.edit, so nothing can be changed here. The server decides the rest.
  const editable = ticket.writableFields.length > 0;
  const catalog: Tag[] = [...(own.state === 'ok' ? own.value : []), ...(shared.state === 'ok' ? shared.value : [])].filter((tag) => !tag.archived);

  async function change(tagId: string, add: boolean) {
    setNotice(null);
    const result = add ? await assignTicketTag(api, project.id, ticket.number, tagId) : await removeTicketTag(api, project.id, ticket.number, tagId);
    if (result.kind === 'ok') {
      setChoice('');
      return reload();
    }
    // Without the read rule any more the section disappears on reload; the session reads its rights again on its own.
    if (result.problem.status === 403) reload();
    setNotice({ kind: 'info', text: text(result.problem, { 'tag.archived': 'tags.error.archived', 'auth.forbidden': 'tags.error.forbidden' }) });
  }

  return (
    <section aria-labelledby="ticket-tags" className="panel">
      <h2 id="ticket-tags">{t('field.tags')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={assigned} retry={reload}>
        {(items) => {
          const ids = new Set(items.map((tag) => tag.id));
          return (
            <>
              {items.length === 0 ? <EmptyText text={t('tags.none')} /> : (
                <ul className="chips" data-testid="ticket-tags">
                  {items.map((tag) => (
                    <li key={tag.id}>
                      {tag.name}
                      {editable && <button type="button" className="chip-remove" aria-label={t('tags.remove', { name: tag.name })} onClick={() => void change(tag.id, false)}>×</button>}
                    </li>
                  ))}
                </ul>
              )}
              {editable && <form className="inline-form" onSubmit={(event) => {
                event.preventDefault();
                if (choice !== '') void change(choice, true);
              }}>
                <SelectField label={t('tags.assign')} value={choice} onChange={setChoice}
                  options={[{ value: '', label: t('tags.choose') }, ...catalog.filter((tag) => !ids.has(tag.id)).map((tag) => ({
                    value: tag.id, label: tag.catalog === 'company' ? t('tags.companyLabel', { name: tag.name }) : tag.name,
                  }))]} />
                <button type="submit" disabled={choice === ''}>{t('tags.assignSubmit')}</button>
              </form>}
            </>
          );
        }}
      </LoadedView>
    </section>
  );
}

function History({ project, ticket, round }: { readonly project: Project; readonly ticket: Ticket; readonly round: number }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const [history, reload] = useLoad(() => readHistory(api, project.id, ticket.number), [api, project.id, ticket.number, round, ticket.version]);
  const [own] = useLoad(() => projectTags(api, project.id), [api, project.id]);
  const [shared] = useLoad(() => companyTags(api), [api]);
  const tagNames = new Map([...(own.state === 'ok' ? own.value : []), ...(shared.state === 'ok' ? shared.value : [])].map((tag) => [tag.id, tag.name]));

  const customText = useCustomText();
  const custom = customFieldsOf(project.configuration);
  const value = (field: string, raw: string | null | undefined) => {
    if (raw === null || raw === undefined || raw === '') return t('field.empty');
    if (field.startsWith(CUSTOM_PREFIX)) {
      const definition = custom.find((item) => `${CUSTOM_PREFIX}${item.key}` === field);
      return definition === undefined ? raw : customText(definition, raw, name);
    }
    if (field === 'status') return statusName(project, raw);
    if (field === 'type') return typeName(project, raw);
    if (field === 'assignee') return name(raw);
    if (field === 'tags') return tagNames.get(raw) ?? raw.slice(0, 8);
    return raw;
  };

  return (
    <section aria-labelledby="ticket-history" className="panel">
      <h2 id="ticket-history">{t('history.title')}</h2>
      <LoadedView loaded={history} retry={reload}>
        {(entries) => entries.length === 0
          ? <EmptyText text={t('history.empty')} /> : (
          <ol className="history" data-testid="history">
            {[...entries].reverse().map((entry, index) => (
              <li key={`${entry.version}-${index}`}>
                <p className="hint">{t('history.entry', { version: entry.version, by: name(entry.actor), date: formatDateTime(entry.occurredAt, language) })}</p>
                <ul>
                  {entry.changes.map((change, position) => (
                    <li key={position}>
                      {change.field === 'tags'
                        ? t(change.to ? 'history.tagAdded' : 'history.tagRemoved', { tag: value('tags', change.to ?? change.from) })
                        : t('history.change', { field: change.field.startsWith(CUSTOM_PREFIX)
                          ? custom.find((item) => `${CUSTOM_PREFIX}${item.key}` === change.field)?.name ?? change.field
                          : t(FIELD_LABEL[change.field as Field] ?? 'field.title'), from: value(change.field, change.from), to: value(change.field, change.to) })}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ol>
        )}
      </LoadedView>
    </section>
  );
}
