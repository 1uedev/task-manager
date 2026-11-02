import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  createPortalRequest, listPortalActivity, listPortalAttachments, listPortalProjects, listPortalTickets, markPortalSeen, portalAttachmentUrl,
  postInPortal, readPortalConversation, readPortalTicket, uploadPortalAttachment, type PortalEntry, type PortalFile, type PortalFilter,
  type PortalProject, type Ticket,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// The customer portal (TASK-075, TASK-076, DEC-105, DEC-106): the requests of the organization of a portal account with filters
// and the marker of new answers of the support, creating a request with one of the offered types, and per request the
// conversation with posts and the files visible to the customer. The status is shown by its name and never changed here.

type View =
  | { readonly kind: 'list' }
  | { readonly kind: 'create' }
  | { readonly kind: 'ticket'; readonly project: PortalProject; readonly number: number; readonly notice?: string | undefined };

const PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'request.invalid': 'portal.error.invalid',
  'auth.forbidden': 'portal.error.forbidden',
  'ticket.not_found': 'portal.error.notFound',
  'project.not_found': 'portal.error.notFound',
  'attachment.too_large': 'attachments.error.tooLarge',
  'attachment.type_blocked': 'attachments.error.blocked',
  'attachment.empty': 'attachments.error.empty',
};

export function PortalPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, reload] = useLoad(() => listPortalProjects(api), [api]);
  const [view, setView] = useState<View>({ kind: 'list' });

  if (view.kind === 'ticket') {
    return <PortalTicket project={view.project} number={view.number} notice={view.notice} onBack={() => setView({ kind: 'list' })} />;
  }
  return (
    <LoadedView loaded={projects} retry={reload}>
      {(list) => view.kind === 'create'
        ? <PortalCreate projects={list} onCancel={() => setView({ kind: 'list' })} onCreated={(project, number, notice) => setView({ kind: 'ticket', project, number, notice })} />
        : (
          <>
            <PageHead title={t('portal.title')} intro={t('portal.intro')}>
              {list.some((project) => project.ticketTypes.length > 0) && (
                <button type="button" onClick={() => setView({ kind: 'create' })}>{t('portal.create')}</button>
              )}
            </PageHead>
            {list.length === 0
              ? <EmptyText text={t('portal.noProjects')} />
              : <PortalOverview projects={list} onOpen={(project, number) => setView({ kind: 'ticket', project, number })} />}
          </>
        )}
    </LoadedView>
  );
}

// ---------------------------------------------------------------- the start page

function PortalOverview({ projects, onOpen }: {
  readonly projects: readonly PortalProject[];
  readonly onOpen: (project: PortalProject, number: number) => void;
}) {
  const { t } = useI18n();
  const [projectId, setProjectId] = useState('');
  const [status, setStatus] = useState('');
  const [state, setState] = useState<'' | 'open' | 'done'>('');
  const [text, setText] = useState('');
  const [query, setQuery] = useState('');
  const shown = projectId === '' ? projects : projects.filter((project) => project.id === projectId);
  const statuses = new Map<string, string>();
  shown.forEach((project) => project.statuses.forEach((item) => statuses.set(item.key, item.name)));
  const filter: PortalFilter = { status: status === '' ? null : status, state: state === '' ? null : state, q: query === '' ? null : query };

  function search(event: FormEvent) {
    event.preventDefault();
    setQuery(text.trim());
  }

  return (
    <>
      <form className="filters" role="search" aria-label={t('portal.filters')} onSubmit={search} data-testid="portal-filters">
        {projects.length > 1 && (
          <SelectField label={t('portal.filter.project')} value={projectId} onChange={(value) => { setProjectId(value); setStatus(''); }}
            options={[{ value: '', label: t('portal.filter.allProjects') }, ...projects.map((project) => ({ value: project.id, label: `${project.key} ${project.name}` }))]} />
        )}
        <SelectField label={t('portal.filter.status')} value={status} onChange={setStatus}
          options={[{ value: '', label: t('portal.filter.allStatuses') }, ...[...statuses].map(([key, name]) => ({ value: key, label: name }))]} />
        <SelectField label={t('portal.filter.state')} value={state} onChange={(value) => setState(value as '' | 'open' | 'done')}
          options={[{ value: '', label: t('portal.filter.allStates') }, { value: 'open', label: t('portal.filter.open') }, { value: 'done', label: t('portal.filter.done') }]} />
        <TextField label={t('portal.filter.text')} value={text} onChange={setText} maxLength={200} />
        <p className="actions"><button type="submit">{t('portal.filter.search')}</button></p>
      </form>
      {shown.map((project) => (
        <PortalProjectTickets key={project.id} project={project} filter={filter} onOpen={(number) => onOpen(project, number)} />
      ))}
    </>
  );
}

function PortalProjectTickets({ project, filter, onOpen }: {
  readonly project: PortalProject;
  readonly filter: PortalFilter;
  readonly onOpen: (number: number) => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [tickets, reload] = useLoad(() => listPortalTickets(api, project.id, filter), [api, project.id, filter.status, filter.state, filter.q]);
  const [activity] = useLoad(() => listPortalActivity(api, project.id), [api, project.id]);
  const rows = new Map((activity.state === 'ok' ? activity.value : []).map((row) => [row.number, row]));
  const statusName = (key: string | null | undefined) => project.statuses.find((item) => item.key === key)?.name ?? key ?? '';
  const headingId = `portal-project-${project.id}`;
  return (
    <section aria-labelledby={headingId} className="panel" data-testid="portal-project">
      <h2 id={headingId}>{`${project.key} ${project.name}`}</h2>
      <LoadedView loaded={tickets} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('portal.noTickets')} />
          : (
            <div className="table-scroll">
              <table data-testid="portal-tickets">
                <thead>
                  <tr>
                    <th scope="col">{t('portal.number')}</th>
                    <th scope="col">{t('portal.ticketTitle')}</th>
                    <th scope="col">{t('portal.status')}</th>
                    <th scope="col">{t('portal.updated')}</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((ticket) => {
                    const row = rows.get(ticket.number);
                    const last = row?.lastActivityAt && row.lastActivityAt > ticket.updatedAt ? row.lastActivityAt : ticket.updatedAt;
                    return (
                      <tr key={ticket.id} data-unread={row?.unread || undefined}>
                        <td>{`${ticket.key}-${ticket.number}`}</td>
                        <td>
                          <button type="button" className="link-button" onClick={() => onOpen(ticket.number)}>{ticket.fields.title ?? ''}</button>
                          {row?.unread && <span className="badge" data-testid="portal-unread">{` ${t('portal.unread')}`}</span>}
                        </td>
                        <td>{statusName(ticket.fields.status)}</td>
                        <td>{formatDateTime(last, language)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
      </LoadedView>
    </section>
  );
}

// ---------------------------------------------------------------- a new request

function PortalCreate({ projects, onCancel, onCreated }: {
  readonly projects: readonly PortalProject[];
  readonly onCancel: () => void;
  readonly onCreated: (project: PortalProject, number: number, notice?: string) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const offering = projects.filter((project) => project.ticketTypes.length > 0);
  const [projectId, setProjectId] = useState(offering[0]?.id ?? '');
  const project = offering.find((item) => item.id === projectId);
  const [type, setType] = useState(project?.ticketTypes[0]?.key ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useUnsavedGuard(title !== '' || description !== '' || files.length > 0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (project === undefined) return;
    if (title.trim() === '') return setError(t('portal.error.title'));
    setBusy(true);
    const created = await createPortalRequest(api, project.id, { type, title: title.trim(), description: description.trim() === '' ? null : description });
    if (created.kind !== 'ok') {
      setBusy(false);
      return setError(text(created.problem, PROBLEMS));
    }
    const number = created.data.value.number;
    const failed = await uploadAll(files, { upload: (file) => uploadPortalAttachment(api, project.id, number, file) });
    setBusy(false);
    setTitle('');
    setDescription('');
    setFiles([]);
    // The request exists; a refused file is named on the page of the request.
    onCreated(project, number, failed === null ? undefined : t('portal.uploadFailed', { name: failed.name, reason: text(failed.problem, PROBLEMS) }));
  }

  return (
    <>
      <PageHead title={t('portal.create')} intro={t('portal.createIntro')} />
      <form className="panel" onSubmit={(event) => void submit(event)} noValidate data-testid="portal-create">
        {offering.length > 1 && (
          <SelectField label={t('portal.filter.project')} value={projectId} onChange={(value) => {
            setProjectId(value);
            setType(offering.find((item) => item.id === value)?.ticketTypes[0]?.key ?? '');
          }} options={offering.map((item) => ({ value: item.id, label: `${item.key} ${item.name}` }))} />
        )}
        <SelectField label={t('portal.field.type')} value={type} onChange={setType}
          options={(project?.ticketTypes ?? []).map((item) => ({ value: item.key, label: item.name }))} />
        <TextField label={t('portal.ticketTitle')} value={title} onChange={setTitle} required maxLength={200} />
        <TextField label={t('portal.field.description')} value={description} onChange={setDescription} multiline maxLength={20000} />
        <FilePicker files={files} onChange={setFiles} />
        {error !== null && <p role="alert" className="error">{error}</p>}
        <p className="actions">
          <button type="submit" disabled={busy || project === undefined}>{t('portal.createSubmit')}</button>
          <button type="button" className="secondary" disabled={busy} onClick={onCancel}>{t('portal.cancel')}</button>
        </p>
      </form>
    </>
  );
}

interface Refused {
  readonly name: string;
  readonly problem: ProblemInfo;
}

interface Uploads {
  upload(file: File): ReturnType<typeof uploadPortalAttachment>;
  arrived?(file: PortalFile): void;
}

/** Uploads one file after the other; returns the first refused file with the reason, or null when all arrived. */
async function uploadAll(files: readonly File[], uploads: Uploads): Promise<Refused | null> {
  for (const file of files) {
    const result = await uploads.upload(file);
    if (result.kind !== 'ok') return { name: file.name, problem: result.problem };
    uploads.arrived?.(result.data.value);
  }
  return null;
}

function FilePicker({ files, onChange }: { readonly files: readonly File[]; readonly onChange: (files: File[]) => void }) {
  const { t, language } = useI18n();
  const input = useRef<HTMLInputElement>(null);
  return (
    <div className="field">
      <label htmlFor="portal-files">{t('portal.files')}</label>
      <input id="portal-files" ref={input} type="file" multiple data-testid="portal-files"
        onChange={(event) => {
          onChange([...files, ...(event.target.files ?? [])]);
          event.target.value = '';
        }} />
      {files.length > 0 && (
        <ul className="plain-list">
          {files.map((file, index) => (
            <li key={`${file.name}-${index}`}>
              {`${file.name} (${formatBytes(file.size, language)}) `}
              <button type="button" className="link-button" onClick={() => onChange(files.filter((_, i) => i !== index))}>
                {t('portal.removeFile', { name: file.name })}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- one request

const SHOWN = ['type', 'status', 'priority'] as const;

function PortalTicket({ project, number, notice, onBack }: {
  readonly project: PortalProject;
  readonly number: number;
  readonly notice?: string | undefined;
  readonly onBack: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [round, setRound] = useState(0);
  const [ticket, reload] = useLoad(() => readPortalTicket(api, project.id, number), [api, project.id, number]);
  const [conversation, reloadConversation] = useLoad(() => readPortalConversation(api, project.id, number), [api, project.id, number, round]);
  const [files, reloadFiles] = useLoad(() => listPortalAttachments(api, project.id, number), [api, project.id, number, round]);
  const seen = useRef(false);

  // Opening the request clears its marker of new answers once the conversation was shown.
  useEffect(() => {
    if (conversation.state === 'ok' && !seen.current) {
      seen.current = true;
      void markPortalSeen(api, project.id, number);
    }
  }, [conversation.state, api, project.id, number]);

  const name = (field: string, value: string | null | undefined) => {
    if (value === null || value === undefined) return '';
    if (field === 'status') return project.statuses.find((item) => item.key === value)?.name ?? value;
    if (field === 'type') return project.ticketTypes.find((item) => item.key === value)?.name ?? value;
    return value;
  };

  return (
    <>
      <p><button type="button" className="link-button" onClick={onBack}>{t('portal.back')}</button></p>
      <NoticeView notice={notice === undefined ? null : { kind: 'info', text: notice }} />
      <LoadedView loaded={ticket} retry={reload}>
        {(value: Ticket) => {
          const fields = value.fields as Readonly<Record<string, string | null | undefined>>;
          return (
            <article data-testid="portal-ticket">
              <PageHead title={`${value.key}-${value.number} ${value.fields.title ?? ''}`} />
              <dl className="facts">
                {SHOWN.filter((field) => fields[field] !== undefined).map((field) => (
                  <Fragment key={field}><dt>{t(`portal.field.${field}`)}</dt><dd data-field={field}>{name(field, fields[field])}</dd></Fragment>
                ))}
                <dt>{t('portal.updated')}</dt><dd>{formatDateTime(value.updatedAt, language)}</dd>
              </dl>
              {value.fields.description !== undefined && value.fields.description !== null && (
                <section aria-label={t('portal.field.description')}><p className="prewrap">{value.fields.description}</p></section>
              )}
            </article>
          );
        }}
      </LoadedView>
      {files.state === 'ok' && files.value.length > 0 && (
        <section aria-labelledby="portal-files-heading" className="panel">
          <h2 id="portal-files-heading">{t('portal.attachments')}</h2>
          <FileLinks project={project} number={number} files={files.value} testId="portal-attachments" />
        </section>
      )}
      <section aria-labelledby="portal-conversation" className="panel">
        <h2 id="portal-conversation">{t('portal.conversation')}</h2>
        <LoadedView loaded={conversation} retry={reloadConversation}>
          {(entries) => entries.length === 0
            ? <EmptyText text={t('portal.noEntries')} />
            : (
              <ol className="plain-list conversation" data-testid="portal-conversation">
                {entries.map((entry) => <ConversationItem key={`${entry.kind}-${entry.id}`} entry={entry} project={project} number={number} />)}
              </ol>
            )}
        </LoadedView>
        <PortalPostForm project={project} number={number} onPosted={() => {
          setRound((value) => value + 1);
          reloadFiles();
        }} />
      </section>
    </>
  );
}

function ConversationItem({ entry, project, number }: { readonly entry: PortalEntry; readonly project: PortalProject; readonly number: number }) {
  const { t, language } = useI18n();
  const who: MessageKey = entry.author === 'support'
    ? (entry.kind === 'reply' ? 'portal.entry.supportMail' : 'portal.entry.support')
    : entry.kind === 'mail' ? 'portal.entry.customerMail' : entry.own ? 'portal.entry.own' : 'portal.entry.colleague';
  return (
    <li className={`conversation-entry entry-${entry.author}`} data-kind={entry.kind} data-author={entry.author}>
      <p className="label-small">{`${t(who)}, ${formatDateTime(entry.at, language)}`}</p>
      {entry.subject && <p><strong>{entry.subject}</strong></p>}
      {entry.body && <p className="prewrap">{entry.body}</p>}
      {entry.attachments.length > 0 && <FileLinks project={project} number={number} files={entry.attachments} />}
    </li>
  );
}

function FileLinks({ project, number, files, testId }: {
  readonly project: PortalProject;
  readonly number: number;
  readonly files: readonly PortalFile[];
  readonly testId?: string;
}) {
  const { t, language } = useI18n();
  return (
    <ul className="attachments" data-testid={testId}>
      {files.map((file) => (
        <li key={file.id}>
          <a href={portalAttachmentUrl(project.id, number, file.id)} download={file.name} aria-label={t('attachments.download', { name: file.name })}>{file.name}</a>
          <span className="hint">{` ${formatBytes(file.size, language)}`}</span>
        </li>
      ))}
    </ul>
  );
}

function PortalPostForm({ project, number, onPosted }: { readonly project: PortalProject; readonly number: number; readonly onPosted: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [body, setBody] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  useUnsavedGuard(body.trim() !== '' || files.length > 0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    if (body.trim() === '') return setNotice({ kind: 'info', text: t('portal.error.body') });
    setBusy(true);
    const ids: string[] = [];
    const failed = await uploadAll(files, {
      upload: (file) => uploadPortalAttachment(api, project.id, number, file),
      arrived: (file) => { ids.push(file.id); },
    });
    if (failed !== null) {
      setBusy(false);
      // Files that arrived stay at the request and are listed there; the post waits until the person decides.
      setFiles(files.slice(ids.length));
      onPosted();
      return setNotice({ kind: 'info', text: t('portal.uploadFailed', { name: failed.name, reason: text(failed.problem, PROBLEMS) }) });
    }
    const result = await postInPortal(api, project.id, number, body, ids);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: text(result.problem, PROBLEMS) });
    setBody('');
    setFiles([]);
    setNotice({ kind: 'success', text: t('portal.posted') });
    onPosted();
  }

  return (
    <form className="inline-form" onSubmit={(event) => void submit(event)} noValidate data-testid="portal-post">
      <h3>{t('portal.answer')}</h3>
      <TextField label={t('portal.message')} value={body} onChange={setBody} required multiline maxLength={100000} />
      <FilePicker files={files} onChange={setFiles} />
      <p className="actions"><button type="submit" disabled={busy}>{t('portal.send')}</button></p>
      <NoticeView notice={notice} />
    </form>
  );
}
