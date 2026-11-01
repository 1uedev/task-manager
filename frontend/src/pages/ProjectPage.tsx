import { useState, type FormEvent } from 'react';
import {
  addMember, assignedProjectTags, assignProjectTag, createProjectTag, createTicket, listMembers, listTickets, readProject, readProjectRights, removeProjectTag,
  removeMember, updateProject, type Member, type Project, type Ticket,
} from '../api/product.ts';
import { CUSTOM_PREFIX, customFieldsOf, sendValue } from '../app/configuration.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { hrefFor, navigate, type ProjectTab } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { TagCatalog, TagLabel, TagPicker, useTagProblemText } from './TagParts.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { CUSTOM_ERROR, CustomFieldInput } from './CustomFieldInput.tsx';
import { ProjectConfigurationEditor } from './ProjectConfigurationEditor.tsx';
import { ProjectRightsTab } from './ProjectRights.tsx';

const TAB_LABEL: Record<ProjectTab, MessageKey> = {
  tickets: 'project.tab.tickets', members: 'project.tab.members', tags: 'project.tab.tags', settings: 'project.tab.settings', rights: 'project.tab.rights',
};

export function statusName(project: Project, key: string | null | undefined): string {
  return project.configuration.statuses.find((status) => status.key === key)?.name ?? key ?? '';
}

export function typeName(project: Project, key: string | null | undefined): string {
  return project.configuration.ticketTypes.find((type) => type.key === key)?.name ?? key ?? '';
}

// One project with its tickets, members, tags and settings. What a tab offers follows the answers of the server.
export function ProjectPage({ id, tab }: { readonly id: string; readonly tab: ProjectTab }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [project, reload, replace] = useLoad(() => readProject(api, id), [api, id]);
  // Only the company administration and a project administration of this project get an answer; for everyone else the tab stays away.
  const [rights, reloadRights] = useLoad(() => readProjectRights(api, id), [api, id]);

  return (
    <LoadedView loaded={project} retry={reload}>
      {(value, etag) => (
        <>
          <PageHead title={`${value.key} · ${value.name}`} intro={value.description ?? undefined} />
          <nav className="tabs" aria-label={t('project.tabs')}>
            {(['tickets', 'members', 'tags', 'settings', 'rights'] as const)
              .filter((name) => (name !== 'settings' || value.manageable) && (name !== 'rights' || rights.state === 'ok')).map((name) => (
              <a key={name} href={hrefFor({ name: 'project', id, tab: name })} aria-current={name === tab ? 'page' : undefined}>{t(TAB_LABEL[name])}</a>
            ))}
          </nav>
          {tab === 'tickets' && <TicketsTab project={value} />}
          {tab === 'members' && <MembersTab project={value} />}
          {tab === 'tags' && <TagsTab project={value} />}
          {tab === 'settings' && value.manageable && etag !== null && (
            <SettingsTab project={value} etag={etag} onSaved={(next, nextEtag) => replace({ value: next, etag: nextEtag })} />
          )}
          {tab === 'rights' && rights.state === 'ok' && (
            <ProjectRightsTab projectId={id} rights={rights.value} reload={reloadRights}
              customNames={Object.fromEntries(customFieldsOf(value.configuration).map((field) => [`${CUSTOM_PREFIX}${field.key}`, field.name]))} />
          )}
        </>
      )}
    </LoadedView>
  );
}

function TicketsTab({ project }: { readonly project: Project }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const [extra, setExtra] = useState<Ticket[]>([]);
  const [more, setMore] = useState(true);
  const [tickets, reload] = useLoad(() => listTickets(api, project.id), [api, project.id]);

  async function loadMore(current: readonly Ticket[]) {
    const smallest = Math.min(...current.map((ticket) => ticket.number));
    const result = await listTickets(api, project.id, smallest);
    if (result.kind === 'ok') {
      setExtra((previous) => [...previous, ...result.data.value]);
      setMore(result.data.value.length === 50);
    }
  }

  return (
    <>
      <CreateTicket project={project} />
      <section aria-labelledby="ticket-list">
        <h2 id="ticket-list">{t('tickets.list')}</h2>
        <LoadedView loaded={tickets} retry={reload}>
          {(first) => {
            const all = [...first, ...extra];
            if (all.length === 0) return <EmptyText text={t('tickets.empty')} />;
            return (
              <>
                <div className="table-scroll">
                  <table data-testid="ticket-table">
                    <thead>
                      <tr><th scope="col">{t('tickets.key')}</th><th scope="col">{t('field.title')}</th><th scope="col">{t('field.status')}</th><th scope="col">{t('field.assignee')}</th><th scope="col">{t('tickets.updated')}</th></tr>
                    </thead>
                    <tbody>
                      {all.map((ticket) => (
                        <tr key={ticket.id}>
                          <td><a href={hrefFor({ name: 'ticket', projectId: project.id, number: ticket.number })}>{ticket.key}</a></td>
                          <td>{'title' in ticket.fields ? ticket.fields.title : <span className="hidden-field">{t('field.hidden')}</span>}</td>
                          <td>{'status' in ticket.fields ? statusName(project, ticket.fields.status) : <span className="hidden-field">{t('field.hidden')}</span>}</td>
                          <td>{'assignee' in ticket.fields ? name(ticket.fields.assignee) || t('field.nobody') : <span className="hidden-field">{t('field.hidden')}</span>}</td>
                          <td><time dateTime={ticket.updatedAt}>{formatDateTime(ticket.updatedAt, language)}</time></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {more && first.length === 50 && <button type="button" className="secondary" onClick={() => void loadMore(all)}>{t('tickets.more')}</button>}
              </>
            );
          }}
        </LoadedView>
      </section>
    </>
  );
}

function CreateTicket({ project }: { readonly project: Project }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const name = useAccountName();
  const config = project.configuration;
  const [members] = useLoad(() => listMembers(api, project.id), [api, project.id]);
  const [type, setType] = useState(config.ticketTypes[0]?.key ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assignee, setAssignee] = useState('');
  const [custom, setCustom] = useState<Readonly<Record<string, string>>>({});
  const [customErrors, setCustomErrors] = useState<Readonly<Record<string, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const required = (key: string) => config.fields.find((field) => field.key === key)?.required ?? false;
  const fields = customFieldsOf(config).filter((field) => field.active);
  const persons = members.state === 'ok' ? members.value.map((member: Member) => ({ id: member.accountId, name: name(member.accountId) })) : [];
  useUnsavedGuard(title !== '' || description !== '' || Object.values(custom).some((value) => value !== ''));

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (title.trim() === '') return setError(t('tickets.error.title'));
    const values: Record<string, string | number | boolean> = {};
    const errors: Record<string, string> = {};
    for (const field of fields) {
      const value = sendValue(field.type, custom[field.key] ?? '');
      if (value === undefined) errors[field.key] = t('custom.error.invalid');
      else if (value !== null) values[field.key] = value;
      else if (field.required) errors[field.key] = t('custom.error.required');
    }
    setCustomErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setBusy(true);
    setError(null);
    const result = await createTicket(api, project.id, {
      type,
      title: title.trim(),
      ...(description.trim() !== '' ? { description: description.trim() } : {}),
      ...(assignee !== '' ? { assignee } : {}),
      ...(Object.keys(values).length > 0 ? { customFields: values } : {}),
    });
    setBusy(false);
    if (result.kind === 'ok') {
      setTitle('');
      setDescription('');
      setCustom({});
      return navigate({ name: 'ticket', projectId: project.id, number: result.data.value.number });
    }
    const reasons = (result.problem.violations ?? []).map((code) => {
      const match = /^custom\.([a-z0-9_]+)\.([a-z_]+)$/.exec(code);
      const key = match === null ? undefined : CUSTOM_ERROR[match[2] ?? ''];
      const label = fields.find((field) => field.key === match?.[1])?.name;
      return key === undefined || label === undefined ? null : `${label}: ${t(key)}`;
    }).filter((reason): reason is string => reason !== null);
    const base = text(result.problem, { 'ticket.required_field_inaccessible': 'tickets.error.requiredField', 'request.invalid': 'tickets.error.invalid', 'auth.forbidden': 'tickets.error.forbidden' });
    setError(reasons.length > 0 ? `${base} ${reasons.join(' ')}` : base);
  }

  const people = members.state === 'ok' ? members.value : [];
  return (
    <details className="panel" open={false}>
      <summary>{t('tickets.create')}</summary>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <SelectField label={t('field.type')} value={type} onChange={setType} options={config.ticketTypes.map((item) => ({ value: item.key, label: item.name }))} />
        <TextField label={t('field.title')} value={title} onChange={setTitle} required maxLength={200} />
        <TextField label={t('field.description')} value={description} onChange={setDescription} multiline required={required('description')} maxLength={20000} />
        <SelectField label={t('field.assignee')} value={assignee} onChange={setAssignee}
          options={[{ value: '', label: t('field.nobody') }, ...people.map((member: Member) => ({ value: member.accountId, label: name(member.accountId) }))]} />
        {fields.map((field) => (
          <CustomFieldInput key={field.key} field={field} value={custom[field.key] ?? ''} people={persons} error={customErrors[field.key] ?? null}
            onChange={(value) => setCustom((current) => ({ ...current, [field.key]: value }))} />
        ))}
        {error !== null && <p role="alert" className="field-error">{error}</p>}
        <button type="submit" disabled={busy}>{t('tickets.createSubmit')}</button>
      </form>
    </details>
  );
}

function MembersTab({ project }: { readonly project: Project }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { directory } = useSession();
  const name = useAccountName();
  const text = useProblemText();
  const [members, reload, replace] = useLoad(() => listMembers(api, project.id), [api, project.id]);
  const [candidate, setCandidate] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);

  async function change(etag: string, accountId: string, add: boolean) {
    setNotice(null);
    const result = add ? await addMember(api, project.id, etag, accountId) : await removeMember(api, project.id, etag, accountId);
    if (result.kind === 'ok') {
      replace(result.data);
      setCandidate('');
      return setNotice({ kind: 'success', text: t(add ? 'members.added' : 'members.removed', { name: name(accountId) }) });
    }
    if (result.problem.status === 412) reload();
    setNotice({ kind: 'info', text: text(result.problem, { 'project.version_conflict': 'members.conflict' }) });
  }

  return (
    <section aria-labelledby="member-list">
      <h2 id="member-list">{t('members.title')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={members} retry={reload}>
        {(items, etag) => {
          const ids = new Set(items.map((member) => member.accountId));
          const candidates = [...directory.values()].filter((entry) => entry.active && !ids.has(entry.id));
          return (
            <>
              {items.length === 0 ? <EmptyText text={t('members.empty')} /> : (
                <ul className="item-list" data-testid="member-list">
                  {items.map((member) => (
                    <li key={member.accountId} className="item-row">
                      <span className="item-title">{name(member.accountId)}</span>
                      <span className="hint">{t('members.since', { date: formatDateTime(member.addedAt, language) })}</span>
                      {project.manageable && etag !== null && (
                        <button type="button" className="secondary" onClick={() => void change(etag, member.accountId, false)}>
                          {t('members.remove', { name: name(member.accountId) })}
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {project.manageable && etag !== null && (
                <form className="inline-form" onSubmit={(event) => {
                  event.preventDefault();
                  if (candidate !== '') void change(etag, candidate, true);
                }}>
                  <SelectField label={t('members.add')} value={candidate} onChange={setCandidate}
                    options={[{ value: '', label: t('members.choose') }, ...candidates.map((entry) => ({ value: entry.id, label: entry.displayName ?? entry.username }))]} />
                  <button type="submit" disabled={candidate === ''}>{t('members.addSubmit')}</button>
                </form>
              )}
            </>
          );
        }}
      </LoadedView>
    </section>
  );
}

function TagsTab({ project }: { readonly project: Project }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problemText = useTagProblemText();
  const [tagName, setTagName] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [round, setRound] = useState(0);
  const [assigned, reloadAssigned] = useLoad(() => assignedProjectTags(api, project.id), [api, project.id]);
  const [assignNotice, setAssignNotice] = useState<Notice | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (tagName.trim() === '') return;
    const result = await createProjectTag(api, project.id, tagName.trim());
    if (result.kind === 'ok') {
      setTagName('');
      setNotice({ kind: 'success', text: t('tags.created', { name: result.data.value.name }) });
      setRound((value) => value + 1);
      return;
    }
    setNotice({ kind: 'info', text: result.problem.code === 'request.invalid' ? t('tags.error.name') : problemText(result.problem) });
  }

  // Tags of the project itself come from the company catalog; changing them follows the project rights.
  async function change(target: { readonly tagId: string } | { readonly name: string }, add: boolean) {
    setAssignNotice(null);
    const result = add ? await assignProjectTag(api, project.id, target)
      : await removeProjectTag(api, project.id, 'tagId' in target ? target.tagId : '');
    if (result.kind === 'ok') return reloadAssigned();
    setAssignNotice({ kind: 'info', text: problemText(result.problem) });
  }

  return (
    <>
      <section aria-labelledby="project-own-tags">
        <h2 id="project-own-tags">{t('tags.ofProject')}</h2>
        <p className="hint">{t('tags.ofProjectHint')}</p>
        <NoticeView notice={assignNotice} />
        <LoadedView loaded={assigned} retry={reloadAssigned}>
          {(items) => (
            <>
              {items.length === 0 ? <EmptyText text={t('tags.none')} /> : (
                <ul className="chips" data-testid="project-assigned-tags">
                  {items.map((tag) => (
                    <li key={tag.id} className={tag.archived ? 'archived' : undefined}>
                      <TagLabel tag={tag} />
                      {project.manageable && <button type="button" className="chip-remove" aria-label={t('tags.remove', { name: tag.name })}
                        onClick={() => void change({ tagId: tag.id }, false)}>×</button>}
                    </li>
                  ))}
                </ul>
              )}
              {project.manageable && (
                <TagPicker label={t('tags.assign')} projectId={null} exclude={new Set(items.map((tag) => tag.id))}
                  onPick={(tag) => void change({ tagId: tag.id }, true)} onCreate={(name) => void change({ name }, true)} />
              )}
            </>
          )}
        </LoadedView>
      </section>
      <section aria-labelledby="project-tags">
        <h2 id="project-tags">{t('tags.project')}</h2>
        <NoticeView notice={notice} />
        <form className="inline-form" onSubmit={(event) => void submit(event)}>
          <TextField label={t('tags.new')} value={tagName} onChange={setTagName} maxLength={50} />
          <button type="submit" disabled={tagName.trim() === ''}>{t('tags.createSubmit')}</button>
        </form>
        <TagCatalog scope={{ kind: 'project', projectId: project.id }} round={round} />
      </section>
    </>
  );
}

function SettingsTab({ project, etag, onSaved }: { readonly project: Project; readonly etag: string; readonly onSaved: (project: Project, etag: string | null) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [notice, setNotice] = useState<Notice | null>(null);
  const dirty = name !== project.name || description !== (project.description ?? '');
  useUnsavedGuard(dirty);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === '') return setNotice({ kind: 'info', text: t('projects.error.name') });
    const result = await updateProject(api, project.id, etag, name.trim(), description.trim() === '' ? null : description.trim());
    if (result.kind === 'ok') {
      onSaved(result.data.value, result.data.etag);
      return setNotice({ kind: 'success', text: t('settings.saved') });
    }
    setNotice({ kind: 'info', text: text(result.problem, { 'project.version_conflict': 'settings.conflict' }) });
  }

  return (
    <>
      <section aria-labelledby="settings-general">
        <h2 id="settings-general">{t('settings.general')}</h2>
        <NoticeView notice={notice} />
        <form onSubmit={(event) => void submit(event)} noValidate>
          <TextField label={t('projects.name')} value={name} onChange={setName} required maxLength={80} />
          <TextField label={t('projects.description')} value={description} onChange={setDescription} multiline maxLength={2000} />
          <button type="submit" disabled={!dirty}>{t('settings.save')}</button>
        </form>
      </section>
      <ProjectConfigurationEditor project={project} etag={etag} onPublished={onSaved} />
    </>
  );
}
