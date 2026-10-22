import { useEffect, useState, type FormEvent } from 'react';
import {
  AUDIT_PAGE, createGroup, listGroups, listProjects, permissionCatalog, readAudit, readGroup, setGroupAction, setGroupField, setGroupMember,
  type AuditEntry, type Group, type GroupDetail, type ProjectSummary, type Result, type TicketField,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { hrefFor, navigate } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

export const PERMISSIONS_RIGHT = 'permissions.manage';

// Freely named groups instead of fixed roles. Only the company administration (permissions.manage) reaches these pages.
export function GroupsPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [groups, reload] = useLoad(() => listGroups(api), [api]);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  // Leaving with input asks first; after a successful creation navigate() passes without asking.
  useUnsavedGuard(name !== '');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === '' || name.trim().length > 80) return setError(t('groups.error.name'));
    const result = await createGroup(api, name.trim());
    if (result.kind === 'ok') return navigate({ name: 'group', id: result.data.value.id });
    setError(text(result.problem, { 'permissions.group_name_taken': 'groups.error.nameTaken' }));
  }

  return (
    <>
      <PageHead title={t('groups.title')} intro={t('groups.intro')} />
      <form className="inline-form" onSubmit={(event) => void submit(event)} noValidate>
        <TextField label={t('groups.new')} value={name} onChange={setName} maxLength={80} error={error} />
        <button type="submit">{t('groups.createSubmit')}</button>
      </form>
      <LoadedView loaded={groups} retry={reload}>
        {(items) => items.length === 0
          ? <EmptyText text={t('groups.empty')} /> : (
          <ul className="item-list" data-testid="group-list">
            {items.map((group) => <li key={group.id}><a href={hrefFor({ name: 'group', id: group.id })}><span className="item-title">{group.name}</span></a></li>)}
          </ul>
        )}
      </LoadedView>
      <AuditLog groups={groups.state === 'ok' ? groups.value : []} />
    </>
  );
}

// The change log of the permissions module, page by page (TASK-043). It needs its own right, permissions.audit.read.
function AuditLog({ groups }: { readonly groups: readonly Group[] }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const text = useProblemText();
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [problem, setProblem] = useState<ProblemInfo | null>(null);
  const [more, setMore] = useState(true);
  const [busy, setBusy] = useState(false);

  async function load(before: number | null) {
    setBusy(true);
    const result = await readAudit(api, before);
    setBusy(false);
    if (result.kind !== 'ok') return setProblem(result.problem);
    setProblem(null);
    setEntries((current) => (before === null ? result.data.value : [...current, ...result.data.value]));
    setMore(result.data.value.length === AUDIT_PAGE);
  }
  useEffect(() => { void load(null); }, [api]);

  const groupName = (id: string | null | undefined) => groups.find((group) => group.id === id)?.name ?? t('audit.unknownGroup');
  const projectKey = (id: string) => (projects.state === 'ok' ? projects.value.find((item: ProjectSummary) => item.id === id)?.key : undefined) ?? id.slice(0, 8);
  const effect = (value: string | null | undefined) => t(value === 'allow' ? 'group.effect.allow' : value === 'deny' ? 'group.effect.deny' : 'audit.effect.remove');
  const right = (action: string | null | undefined) => t(ACTION_LABEL[action ?? ''] ?? 'right.unknown');
  const field = (key: string | null | undefined) => t(FIELD_LABEL[key ?? ''] ?? 'field.title');
  function change(entry: AuditEntry): string {
    const removed = entry.effect === 'remove';
    const described = entry.operation === 'group.created' ? t('audit.created')
      : entry.operation === 'membership.changed' ? t(removed ? 'audit.memberRemoved' : 'audit.memberAdded', { name: name(entry.subjectId ?? '') })
      : entry.operation === 'action.changed' ? t(removed ? 'audit.revoked' : 'audit.granted', { right: right(entry.action) })
      : t(entry.operation === 'field.write' ? 'audit.fieldWrite' : 'audit.fieldRead', { field: field(entry.field), effect: effect(entry.effect) });
    return entry.projectId ? t('audit.inProject', { change: described, project: projectKey(entry.projectId) }) : described;
  }
  const before = (entry: AuditEntry) => entry.operation === 'group.created' ? ''
    : entry.previousEffect ? effect(entry.previousEffect)
    // A removal always replaced something; without a recorded value it is an entry from before TASK-043.
    : t(entry.effect === 'remove' ? 'audit.previous.unknown' : 'audit.previous.none');

  return (
    <section aria-labelledby="audit-heading" className="panel">
      <h2 id="audit-heading">{t('audit.title')}</h2>
      <p className="hint">{t('audit.intro')}</p>
      {problem !== null ? <p className="empty">{problem.status === 403 ? t('audit.forbidden') : text(problem)}</p>
        : entries.length === 0 ? (busy ? null : <EmptyText text={t('audit.empty')} />) : (
        <>
          <div className="table-scroll">
            <table data-testid="audit-log">
              <caption className="visually-hidden">{t('audit.title')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('audit.when')}</th><th scope="col">{t('audit.who')}</th><th scope="col">{t('audit.group')}</th>
                  <th scope="col">{t('audit.change')}</th><th scope="col">{t('audit.before')}</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id} data-version={entry.rightsVersion}>
                    <td>{formatDateTime(entry.occurredAt, language)}</td>
                    <td>{name(entry.actorId)}</td>
                    <td>{groupName(entry.groupId)}</td>
                    <td>{change(entry)}</td>
                    <td>{before(entry)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {more
            ? <p className="actions"><button type="button" className="secondary" disabled={busy}
                onClick={() => void load(entries[entries.length - 1]?.rightsVersion ?? null)}>{t('audit.more')}</button></p>
            : <p className="hint">{t('audit.end')}</p>}
        </>
      )}
    </section>
  );
}

export const ACTION_LABEL: Readonly<Record<string, MessageKey>> = {
  'accounts.manage': 'right.accounts.manage',
  'appearance.manage': 'right.appearance.manage',
  'permissions.audit.read': 'right.permissions.audit.read',
  'permissions.manage': 'right.permissions.manage',
  'projects.manage': 'right.projects.manage',
  'session_duration.manage': 'right.session_duration.manage',
  'project.manage': 'right.project.manage',
  'project.read': 'right.project.read',
  'tag.create': 'right.tag.create',
  'tags.manage': 'right.tags.manage',
  'ticket.create': 'right.ticket.create',
  'ticket.edit': 'right.ticket.edit',
  'ticket.read': 'right.ticket.read',
};

export const FIELD_LABEL: Readonly<Record<string, MessageKey>> = {
  title: 'field.title', description: 'field.description', type: 'field.type', status: 'field.status', assignee: 'field.assignee', tags: 'field.tags',
};

type GroupAnswer = Promise<Result<GroupDetail | 'gone'>>;

export function GroupPage({ id }: { readonly id: string }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const { directory, refresh } = useSession();
  const name = useAccountName();
  const text = useProblemText();
  const [group, reload, replace] = useLoad(() => readGroup(api, id), [api, id]);
  const [catalog] = useLoad(() => permissionCatalog(api), [api]);
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [scope, setScope] = useState('');
  const [candidate, setCandidate] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  async function apply(call: () => GroupAnswer, success: string) {
    setBusy(true);
    setNotice(null);
    const result = await call();
    setBusy(false);
    if (result.kind === 'ok') {
      if (result.data.value === 'gone') {
        // The person took the right to manage groups from themselves; the session now says what remains.
        await refresh();
        return navigate({ name: 'start' });
      }
      replace({ value: result.data.value, etag: null });
      return setNotice({ kind: 'success', text: success });
    }
    setNotice({ kind: 'info', text: text(result.problem, { 'permissions.last_administrator': 'groups.error.lastAdministrator' }) });
  }

  const projectId = scope === '' ? null : scope;
  return (
    <LoadedView loaded={group} retry={reload}>
      {(detail) => {
        const members = new Set(detail.members);
        const candidates = [...directory.values()].filter((entry) => entry.active && !members.has(entry.id));
        const held = new Set(detail.actions.filter((rule) => (rule.projectId ?? null) === projectId).map((rule) => rule.action));
        const rule = (field: string, operation: 'read' | 'write') =>
          detail.fields.find((item) => item.field === field && item.operation === operation && (item.projectId ?? null) === projectId)?.effect ?? '';
        const actions = catalog.state === 'ok' ? [...(projectId === null ? catalog.value.companyActions : []), ...catalog.value.projectActions] : [];
        const fields = catalog.state === 'ok' ? catalog.value.fields.find((item) => item.resourceType === 'ticket')?.fields ?? [] : [];
        return (
          <>
            <p className="breadcrumb"><a href={hrefFor('groups')}>{t('groups.title')}</a></p>
            <PageHead title={detail.name} intro={t('group.intro')} />
            <NoticeView notice={notice} />
            <section aria-labelledby="group-members" className="panel">
              <h2 id="group-members">{t('group.members')}</h2>
              {detail.members.length === 0 ? <EmptyText text={t('group.noMembers')} /> : (
                <ul className="item-list" data-testid="group-members">
                  {detail.members.map((member) => (
                    <li key={member} className="item-row">
                      <span className="item-title">{name(member)}</span>
                      <button type="button" className="secondary" disabled={busy}
                        onClick={() => void apply(() => setGroupMember(api, id, member, false), t('group.memberRemoved', { name: name(member) }))}>
                        {t('members.remove', { name: name(member) })}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <form className="inline-form" onSubmit={(event) => {
                event.preventDefault();
                if (candidate === '') return;
                const who = candidate;
                setCandidate('');
                void apply(() => setGroupMember(api, id, who, true), t('group.memberAdded', { name: name(who) }));
              }}>
                <SelectField label={t('members.add')} value={candidate} onChange={setCandidate}
                  options={[{ value: '', label: t('members.choose') }, ...candidates.map((entry) => ({ value: entry.id, label: entry.displayName ?? entry.username }))]} />
                <button type="submit" disabled={candidate === '' || busy}>{t('members.addSubmit')}</button>
              </form>
            </section>
            <section aria-labelledby="group-scope" className="panel">
              <h2 id="group-scope">{t('group.rights')}</h2>
              <SelectField label={t('group.scope')} hint={t('group.scopeHint')} value={scope} onChange={setScope}
                options={[{ value: '', label: t('group.scopeAll') }, ...(projects.state === 'ok' ? projects.value : []).map((item) => ({ value: item.id, label: `${item.key} · ${item.name}` }))]} />
              <fieldset data-testid="group-actions">
                <legend>{t('group.actions')}</legend>
                {actions.map((action) => (
                  <label key={action} className="check">
                    <input type="checkbox" checked={held.has(action)} disabled={busy}
                      onChange={(event) => {
                        const enabled = event.target.checked;
                        void apply(() => setGroupAction(api, id, action, projectId, enabled), t(enabled ? 'group.granted' : 'group.revoked', { right: t(ACTION_LABEL[action] ?? 'right.unknown') }));
                      }} />
                    <span>{t(ACTION_LABEL[action] ?? 'right.unknown')}</span>
                    <code className="hint">{action}</code>
                  </label>
                ))}
              </fieldset>
              <div className="table-scroll">
                <table data-testid="group-fields">
                  <caption>{t('group.fields')}</caption>
                  <thead><tr><th scope="col">{t('group.field')}</th><th scope="col">{t('group.read')}</th><th scope="col">{t('group.write')}</th></tr></thead>
                  <tbody>
                    {fields.map((field) => (
                      <tr key={field}>
                        <th scope="row">{t(FIELD_LABEL[field] ?? 'field.title')}</th>
                        {(['read', 'write'] as const).map((operation) => (
                          <td key={operation}>
                            <select aria-label={t(operation === 'read' ? 'group.readOf' : 'group.writeOf', { field: t(FIELD_LABEL[field] ?? 'field.title') })}
                              value={rule(field, operation)} disabled={busy}
                              onChange={(event) => {
                                const effect = event.target.value === '' ? 'remove' : event.target.value as 'allow' | 'deny';
                                void apply(() => setGroupField(api, id, field as TicketField, projectId, operation, effect), t('group.fieldSaved'));
                              }}>
                              <option value="">{t('group.effect.none')}</option>
                              <option value="allow">{t('group.effect.allow')}</option>
                              <option value="deny">{t('group.effect.deny')}</option>
                            </select>
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="hint">{t('group.fieldsHint')}</p>
            </section>
          </>
        );
      }}
    </LoadedView>
  );
}
