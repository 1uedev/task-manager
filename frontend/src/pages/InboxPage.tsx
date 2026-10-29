import { useEffect, useState, type FormEvent } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  attachMessage, createContact, discardMessage, HELPDESK_STATES, INBOX_PAGE, listCandidates, listCustomers, listInbox, listOpenReplies, listProjects,
  readInboxMessage, readInboxSummary, ticketFromMessage, type MailMessage, type MessageState,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import { HELPDESK_RIGHT, useHelpdeskCode } from './HelpdeskPage.tsx';

const known = (key: string): key is MessageKey => Object.hasOwn(de, key);

// The review queue of the helpdesk (TASK-057): mails that did not become a ticket on their own. The server shows a person
// only the mails of projects in which they hold helpdesk.review; the list carries no text, the opened mail does.
export function InboxPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [summary, reloadSummary] = useLoad(() => readInboxSummary(api), [api]);
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [state, setState] = useState<MessageState>('review');
  const [messages, setMessages] = useState<MailMessage[] | null>(null);
  const [problem, setProblem] = useState<ProblemInfo | null>(null);
  const [more, setMore] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [round, setRound] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const projectName = (id: string) => (projects.state === 'ok' ? projects.value.find((project) => project.id === id)?.name : undefined) ?? id.slice(0, 8);
  const reasonText = (reason: string | null | undefined) => (reason && known(`inbox.reason.${reason}`) ? t(`inbox.reason.${reason}` as MessageKey) : reason ?? '');

  async function load(before: string | null) {
    const result = await listInbox(api, state, before);
    if (result.kind !== 'ok') return setProblem(result.problem);
    setProblem(null);
    setMessages((current) => (before === null || current === null ? result.data.value : [...current, ...result.data.value]));
    setMore(result.data.value.length === INBOX_PAGE);
  }

  useEffect(() => {
    void load(null);
  }, [api, state, round]);

  const refresh = () => {
    setRound((value) => value + 1);
    reloadSummary();
  };

  return (
    <>
      <PageHead title={t('inbox.title')} intro={t('inbox.intro')}>
        <button type="button" className="secondary" onClick={() => { setNotice(null); refresh(); }}>{t('inbox.refresh')}</button>
      </PageHead>
      {summary.state === 'ok' && (
        <p className="hint" data-testid="inbox-summary">{t('inbox.summary', { review: summary.value.review, automatic: summary.value.automatic })}</p>
      )}
      <NoticeView notice={notice} />
      <OpenReplies round={round} />
      <SelectField label={t('inbox.filter')} value={state} onChange={(value) => { setOpen(null); setState(value as MessageState); }}
        options={HELPDESK_STATES.map((value) => ({ value, label: t(`inbox.state.${value}` as MessageKey) }))} />
      {problem !== null ? <ProblemView problem={problem} onAction={refresh} actionLabel="states.retry" />
        : messages === null ? <LoadingView />
        : messages.length === 0 ? <EmptyText text={t(state === 'review' ? 'inbox.empty' : 'inbox.emptyState')} /> : (
        <>
          <ul className="item-list inbox-list" data-testid="inbox-list">
            {messages.map((message) => (
              <li key={message.id} data-message={message.id} className="card">
                <button type="button" className="link-button" aria-expanded={open === message.id}
                  onClick={() => setOpen(open === message.id ? null : message.id)}>
                  {message.subject ?? t('inbox.noSubject')}
                </button>
                <p className="hint">
                  {t('inbox.from', { from: message.fromName ? `${message.fromName} <${message.fromAddress ?? ''}>` : message.fromAddress ?? t('inbox.noSender'),
                    date: formatDateTime(message.receivedAt, language), project: projectName(message.projectId) })}
                </p>
                {message.reason && <p className="hint">{t('inbox.reason', { reason: reasonText(message.reason) })}</p>}
                {message.ticket && (
                  <p><a href={hrefFor({ name: 'ticket', projectId: message.ticket.projectId, number: message.ticket.number })}>
                    {t('inbox.toTicket', { number: message.ticket.number })}</a></p>
                )}
                {message.relatedTicket && (
                  <p><a href={hrefFor({ name: 'ticket', projectId: message.relatedTicket.projectId, number: message.relatedTicket.number })}>
                    {t('inbox.related', { number: message.relatedTicket.number })}</a></p>
                )}
                {open === message.id && (
                  <MessageView id={message.id} onDone={(text) => {
                    setOpen(null);
                    setNotice(text);
                    refresh();
                  }} />
                )}
              </li>
            ))}
          </ul>
          {more
            ? <p className="actions"><button type="button" className="secondary" onClick={() => void load(messages[messages.length - 1]?.receivedAt ?? null)}>{t('inbox.more')}</button></p>
            : <p className="hint">{t('inbox.end')}</p>}
        </>
      )}
    </>
  );
}

function MessageView({ id, onDone }: { readonly id: string; readonly onDone: (notice: Notice) => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const code = useHelpdeskCode();
  const [message, reload] = useLoad(() => readInboxMessage(api, id), [api, id]);
  const { has } = useSession();
  const [candidateRound, setCandidateRound] = useState(0);
  const [candidates] = useLoad(() => listCandidates(api, id), [api, id, candidateRound]);
  const [contact, setContact] = useState<string>('');
  const [reason, setReason] = useState('');
  const [discarding, setDiscarding] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const specific: Readonly<Record<string, MessageKey>> = {
    'helpdesk.message_processed': 'inbox.error.processed',
    'helpdesk.version_conflict': 'inbox.error.changed',
    'helpdesk.message_not_found': 'inbox.error.gone',
  };

  async function create(etag: string) {
    setNotice(null);
    setBusy(true);
    const result = await ticketFromMessage(api, id, etag, contact === '' ? null : contact);
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.code === 'helpdesk.version_conflict') reload();
      return setNotice({ kind: 'info', text: text(result.problem, specific) });
    }
    onDone({ kind: 'success', text: t('inbox.created', { number: result.data.value.number }) });
  }

  async function attach(etag: string) {
    setNotice(null);
    setBusy(true);
    const result = await attachMessage(api, id, etag);
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.code === 'helpdesk.version_conflict') reload();
      return setNotice({ kind: 'info', text: text(result.problem, specific) });
    }
    onDone({ kind: 'success', text: t('inbox.attached', { number: result.data.value.number }) });
  }

  async function discard(event: FormEvent, etag: string) {
    event.preventDefault();
    setNotice(null);
    setBusy(true);
    const result = await discardMessage(api, id, etag, reason.trim());
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.code === 'helpdesk.version_conflict') reload();
      return setNotice({ kind: 'info', text: result.problem.code === 'request.invalid' ? t('inbox.error.reason') : text(result.problem, specific) });
    }
    onDone({ kind: 'success', text: t('inbox.discarded') });
  }

  return (
    <div className="mail" data-testid="inbox-message">
      <LoadedView loaded={message} retry={reload}>
        {(value, etag) => (
          <>
            <dl className="facts">
              <dt>{t('inbox.to')}</dt><dd>{value.to ?? t('field.empty')}</dd>
              {value.cc && <><dt>{t('inbox.cc')}</dt><dd>{value.cc}</dd></>}
              <dt>{t('inbox.size')}</dt><dd>{formatBytes(value.sizeBytes, language)}</dd>
            </dl>
            <p className="prewrap mail-body" data-testid="inbox-body">{value.body ?? t('inbox.noBody')}</p>
            {value.attachments.length > 0 && (
              <ul className="plain-list">
                {value.attachments.map((file, index) => (
                  <li key={index}>
                    {t('inbox.file', { name: file.name, size: formatBytes(file.size, language) })}
                    {file.refusal && <span className="hint"> {code(file.refusal)}</span>}
                  </li>
                ))}
              </ul>
            )}
            {value.state === 'discarded' && value.discardReason && <p className="hint">{t('inbox.discardReason', { reason: value.discardReason })}</p>}
            {(value.state === 'review' || value.state === 'automatic') && etag !== null && (
              <>
                <SelectField label={t('inbox.contact')} value={contact} onChange={setContact}
                  hint={t('inbox.contactHint')}
                  options={[{ value: '', label: t('inbox.noContact') },
                    ...(candidates.state === 'ok' ? candidates.value : []).map((candidate) => ({
                      value: candidate.contactId, label: `${candidate.contactName} (${candidate.customerName})${candidate.sender ? ` ${t('inbox.sender')}` : ''}` }))]} />
                <p className="actions">
                  {value.state === 'review' && value.relatedTicket && (
                    <button type="button" disabled={busy} onClick={() => void attach(etag)}>{t('inbox.attach', { number: value.relatedTicket.number })}</button>
                  )}
                  <button type="button" disabled={busy} className={value.relatedTicket ? 'secondary' : undefined} onClick={() => void create(etag)}>{t('inbox.createTicket')}</button>
                  {!discarding && <button type="button" className="secondary" onClick={() => setDiscarding(true)}>{t('inbox.discard')}</button>}
                </p>
                {has(HELPDESK_RIGHT) && value.fromAddress && (
                  <NewContact address={value.fromAddress} name={value.fromName ?? ''} onCreated={(created) => {
                    setCandidateRound((round) => round + 1);
                    setContact(created);
                  }} />
                )}
                {discarding && (
                  <form onSubmit={(event) => void discard(event, etag)} noValidate>
                    <TextField label={t('inbox.discardReasonLabel')} value={reason} onChange={setReason} required maxLength={500} />
                    <p className="actions">
                      <button type="submit" disabled={busy}>{t('inbox.discardConfirm')}</button>
                      <button type="button" className="secondary" onClick={() => setDiscarding(false)}>{t('helpdesk.cancel')}</button>
                    </p>
                  </form>
                )}
              </>
            )}
            <NoticeView notice={notice} />
          </>
        )}
      </LoadedView>
    </div>
  );
}

// Whoever also manages the helpdesk can turn the sender into a contact of a customer right here (TASK-057); the contact is
// then offered for the ticket. Nobody else creates contacts from mails.
function NewContact({ address, name, onCreated }: { readonly address: string; readonly name: string; readonly onCreated: (contactId: string) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [customers] = useLoad(() => listCustomers(api), [api]);
  const [customer, setCustomer] = useState('');
  const [contactName, setContactName] = useState(name);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const list = customers.state === 'ok' ? customers.value : [];
  if (list.length === 0) return null;
  const chosen = customer === '' ? list[0]?.id ?? '' : customer;

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setBusy(true);
    const result = await createContact(api, chosen, contactName.trim(), [address]);
    setBusy(false);
    if (result.kind !== 'ok') {
      return setNotice({ kind: 'info', text: text(result.problem, { 'contact.address_taken': 'helpdesk.error.addressTaken', 'request.invalid': 'inbox.error.contact' }) });
    }
    setNotice({ kind: 'success', text: t('inbox.contactCreated', { name: result.data.value.name }) });
    onCreated(result.data.value.id);
  }

  return (
    <details>
      <summary>{t('inbox.newContact', { address })}</summary>
      <form onSubmit={(event) => void save(event)} noValidate data-testid="new-contact">
        <SelectField label={t('helpdesk.customer')} value={chosen} onChange={setCustomer} options={list.map((item) => ({ value: item.id, label: item.name }))} />
        <TextField label={t('helpdesk.name')} value={contactName} onChange={setContactName} required maxLength={120} />
        <p className="actions"><button type="submit" disabled={busy}>{t('inbox.newContactSave')}</button></p>
        <NoticeView notice={notice} />
      </form>
    </details>
  );
}

// Replies to customers that the mail server has not accepted yet (TASK-058), of the projects in which the person may reply:
// pending ones, and failed or uncertain ones that wait for a decision in their ticket.
function OpenReplies({ round }: { readonly round: number }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const code = useHelpdeskCode();
  const [replies] = useLoad(() => listOpenReplies(api), [api, round]);
  if (replies.state !== 'ok' || replies.value.length === 0) return null;
  return (
    <section className="panel" aria-labelledby="open-replies" data-testid="open-replies">
      <h2 id="open-replies">{t('inbox.openReplies')}</h2>
      <ul className="plain-list">
        {replies.value.map((reply) => (
          <li key={reply.id}>
            <a href={hrefFor({ name: 'ticket', projectId: reply.projectId, number: reply.number })}>
              {t('inbox.openReply', { subject: reply.subject, to: reply.toAddress, number: reply.number })}</a>{' '}
            <span className={`badge reply-${reply.state}`}>{t(`ticketHelpdesk.replyState.${reply.state}` as MessageKey)}</span>
            {reply.errorCode && <span className="hint"> {code(reply.errorCode)}</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}
