import { useState, type FormEvent } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  markAnswerSeen, readReplyForm, readTicketHelpdesk, sendReply, sendReplyAgain, setTicketCustomer, settleReply, ticketCustomerOptions, type Project, type Reply,
  type Ticket, type TicketHelpdesk as HelpdeskView,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { useHelpdeskCode } from './HelpdeskPage.tsx';
import { ANSWER_PROBLEMS } from './InboxPage.tsx';

// Customer, contact and the mails of a ticket (TASK-057). Each part follows the rules of its ticket field, which only the
// server knows: a part without the read rule is not shown, and the choice is offered only with the write rules.
export function TicketHelpdesk({ project, ticket, round, onChanged }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly round: number;
  // The history shows the change, so it is read again afterwards.
  readonly onChanged: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const code = useHelpdeskCode();
  const [view, reload, replace] = useLoad(() => readTicketHelpdesk(api, project.id, ticket.number), [api, project.id, ticket.number, round]);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  if (view.state !== 'ok') return null;
  const value = view.value;
  const readable = new Set(value.readable);
  if (readable.size === 0) return null;
  const writable = value.writable.includes('customer') && value.writable.includes('contact');

  return (
    <section aria-labelledby="ticket-helpdesk" className="panel" data-testid="ticket-helpdesk">
      <h2 id="ticket-helpdesk">{t('ticketHelpdesk.title')}</h2>
      {readable.has('customer') && (
        <p data-testid="ticket-customer"><span className="label-small">{t('field.customer')}</span>{' '}
          {value.customer?.name ?? t('ticketHelpdesk.noCustomer')}</p>
      )}
      {readable.has('contact') && (
        <p data-testid="ticket-contact"><span className="label-small">{t('field.contact')}</span>{' '}
          {value.contact ? `${value.contact.name} (${value.contact.addresses.join(', ')})` : t('ticketHelpdesk.noContact')}</p>
      )}
      {writable && !editing && (
        <p className="actions"><button type="button" className="secondary" onClick={() => setEditing(true)}>{t('ticketHelpdesk.change')}</button></p>
      )}
      {writable && editing && (
        <CustomerChoice projectId={project.id} number={ticket.number} customerId={value.customer?.id ?? ''} contactId={value.contact?.id ?? ''}
          onCancel={() => setEditing(false)}
          onSaved={(result) => {
            setEditing(false);
            if (result.kind === 'ok') {
              replace({ value: result.value, etag: null });
              setNotice({ kind: 'success', text: t('ticketHelpdesk.saved') });
              onChanged();
            } else {
              setNotice({ kind: 'info', text: text(result.problem, { 'request.invalid': 'ticketHelpdesk.error.invalid', 'auth.forbidden': 'ticketHelpdesk.error.forbidden' }) });
              reload();
            }
          }} />
      )}
      <NoticeView notice={notice} />
      {readable.has('mail') && (
        <MailThread value={value} project={project} ticket={ticket} onChanged={() => {
          reload();
          onChanged();
        }} />
      )}
      {value.replyAllowed && value.contact && (
        <ReplyComposer project={project} ticket={ticket} onSent={() => {
          reload();
          onChanged();
        }} />
      )}
    </section>
  );
}

// The mails of the ticket and its replies, oldest first (TASK-058). A reply shows where its sending stands; a failed one can be
// sent again, an uncertain one only after the person confirmed that the customer may get it twice, or recorded as arrived.
function MailThread({ value, project, ticket, onChanged }: {
  readonly value: HelpdeskView;
  readonly project: Project;
  readonly ticket: Ticket;
  readonly onChanged: () => void;
}) {
  const { t, language } = useI18n();
  const code = useHelpdeskCode();
  const mails = value.mails ?? [];
  const replies = value.replies ?? [];
  const entries = [
    ...mails.map((mail) => ({ at: mail.receivedAt, mail, reply: null })),
    ...replies.map((reply) => ({ at: reply.createdAt, mail: null, reply })),
  ].sort((a, b) => a.at.localeCompare(b.at));
  if (entries.length === 0) return null;
  return (
    <>
      <h3>{t('ticketHelpdesk.mails')}</h3>
      <ul className="plain-list" data-testid="ticket-mails">
        {entries.map(({ mail, reply }) => mail !== null ? (
          <li key={mail.id} data-mail={mail.id} className={mail.newAnswer ? 'new-answer' : undefined}>
            <details open={mail.newAnswer}>
              <summary>{t('ticketHelpdesk.mail', { subject: mail.subject ?? t('inbox.noSubject'), from: mail.fromAddress ?? t('inbox.noSender'),
                date: formatDateTime(mail.receivedAt, language) })}
                {mail.newAnswer && <>{' '}<span className="badge new-answer" data-testid="new-answer">{t('ticketHelpdesk.newAnswer')}</span></>}</summary>
              <p className="prewrap mail-body">{mail.body ?? t('inbox.noBody')}</p>
              {mail.attachments.map((file, index) => (
                <p key={index} className="hint">{t('inbox.file', { name: file.name, size: formatBytes(file.size, language) })}
                  {file.refusal ? ` ${code(file.refusal)}` : ''}</p>
              ))}
            </details>
            {mail.newAnswer && value.replyAllowed && <AnswerSeen id={mail.id} version={mail.version} onChanged={onChanged} />}
          </li>
        ) : reply !== null && (
          <li key={reply.id} data-reply={reply.id}>
            <details>
              <summary>{t('ticketHelpdesk.reply', { subject: reply.subject, to: reply.toAddress, date: formatDateTime(reply.createdAt, language) })}{' '}
                <span className={`badge reply-${reply.state}`} data-testid="reply-state">{t(`ticketHelpdesk.replyState.${reply.state}` as MessageKey)}</span></summary>
              <p className="prewrap mail-body">{reply.body}</p>
              {reply.errorCode && reply.state !== 'sent' && <p className="hint">{code(reply.errorCode)}</p>}
            </details>
            {value.replyAllowed && (reply.state === 'failed' || reply.state === 'uncertain') && (
              <ReplyRecovery project={project} ticket={ticket} reply={reply} onChanged={onChanged} />
            )}
          </li>
        ))}
      </ul>
    </>
  );
}

// A new answer of the customer (TASK-067) stays highlighted until a person who may reply marks it as seen for everybody.
function AnswerSeen({ id, version, onChanged }: { readonly id: string; readonly version: number; readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  async function seen() {
    setBusy(true);
    setNotice(null);
    const result = await markAnswerSeen(api, id, version);
    setBusy(false);
    if (result.kind !== 'ok') {
      setNotice({ kind: 'info', text: text(result.problem, ANSWER_PROBLEMS) });
    }
    onChanged();
  }

  return (
    <div className="inline-form" data-testid="answer-seen">
      <p className="actions"><button type="button" className="secondary" disabled={busy} onClick={() => void seen()}>{t('inbox.markSeen')}</button></p>
      <NoticeView notice={notice} />
    </div>
  );
}

function ReplyRecovery({ project, ticket, reply, onChanged }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly reply: Reply;
  readonly onChanged: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const uncertain = reply.state === 'uncertain';

  async function act(settle: boolean) {
    setBusy(true);
    setNotice(null);
    const result = settle
      ? await settleReply(api, project.id, ticket.number, reply.id, reply.version)
      : await sendReplyAgain(api, project.id, ticket.number, reply.id, reply.version, accepted);
    setBusy(false);
    if (result.kind !== 'ok') {
      return setNotice({ kind: 'info', text: text(result.problem, REPLY_PROBLEMS) });
    }
    onChanged();
  }

  return (
    <div className="inline-form" data-testid="reply-recovery">
      {uncertain && <p className="hint">{t('ticketHelpdesk.uncertainHint')}</p>}
      {uncertain && (
        <label className="check"><input type="checkbox" checked={accepted} onChange={(event) => setAccepted(event.target.checked)} />
          {' '}{t('ticketHelpdesk.duplicateAccepted')}</label>
      )}
      <p className="actions">
        <button type="button" disabled={busy || (uncertain && !accepted)} onClick={() => void act(false)}>{t('ticketHelpdesk.sendAgain')}</button>
        {uncertain && <button type="button" className="secondary" disabled={busy} onClick={() => void act(true)}>{t('ticketHelpdesk.settle')}</button>}
      </p>
      <NoticeView notice={notice} />
    </div>
  );
}

const REPLY_PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'request.invalid': 'ticketHelpdesk.error.replyInvalid',
  'auth.forbidden': 'ticketHelpdesk.error.replyForbidden',
  'helpdesk.reply_no_contact': 'ticketHelpdesk.error.noContact',
  'helpdesk.version_conflict': 'ticketHelpdesk.error.replyChanged',
  'helpdesk.reply_uncertain': 'ticketHelpdesk.error.replyChanged',
  'helpdesk.reply_not_failed': 'ticketHelpdesk.error.replyChanged',
  'helpdesk.reply_not_uncertain': 'ticketHelpdesk.error.replyChanged',
};

// A reply to the contact of the ticket (TASK-058): written, then checked once more with recipient and sender in view, and only
// then confirmed. The server sends it in the background; the thread shows where it stands.
function ReplyComposer({ project, ticket, onSent }: { readonly project: Project; readonly ticket: Ticket; readonly onSent: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [open, setOpen] = useState(false);
  const [form] = useLoad(() => (open ? readReplyForm(api, project.id, ticket.number) : Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })),
    [api, project.id, ticket.number, open]);
  const [mailbox, setMailbox] = useState('');
  const [to, setTo] = useState('');
  const [subject, setSubject] = useState<string | null>(null);
  const [body, setBody] = useState('');
  const [checking, setChecking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  useUnsavedGuard(open && body.trim() !== '');

  if (!open) {
    return (
      <>
        <p className="actions"><button type="button" onClick={() => { setNotice(null); setOpen(true); }}>{t('ticketHelpdesk.compose')}</button></p>
        <NoticeView notice={notice} />
      </>
    );
  }
  if (form.state !== 'ok' || form.value === null) {
    return form.state === 'problem' ? <NoticeView notice={{ kind: 'info', text: text(form.problem, REPLY_PROBLEMS) }} /> : null;
  }
  const options = form.value;
  if (options.mailboxes.length === 0) {
    return <p className="hint" data-testid="reply-no-mailbox">{t('ticketHelpdesk.noSender')}</p>;
  }
  const chosenMailbox = mailbox || options.suggestedMailbox || options.mailboxes[0]?.id || '';
  const chosenTo = to || options.recipients[0] || '';
  const chosenSubject = subject ?? options.subject ?? '';
  const sender = options.mailboxes.find((item) => item.id === chosenMailbox);

  function check(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    if (chosenSubject.trim() === '' || body.trim() === '') {
      return setNotice({ kind: 'info', text: t('ticketHelpdesk.error.replyInvalid') });
    }
    setChecking(true);
  }

  async function confirm() {
    setBusy(true);
    const result = await sendReply(api, project.id, ticket.number, chosenMailbox, chosenTo, chosenSubject.trim(), body);
    setBusy(false);
    if (result.kind !== 'ok') {
      setChecking(false);
      return setNotice({ kind: 'info', text: text(result.problem, REPLY_PROBLEMS) });
    }
    setOpen(false);
    setChecking(false);
    setBody('');
    setSubject(null);
    setNotice({ kind: 'success', text: t('ticketHelpdesk.replyQueued') });
    onSent();
  }

  return (
    <form className="inline-form" onSubmit={check} noValidate data-testid="reply-form">
      <h3>{t('ticketHelpdesk.compose')}</h3>
      {!checking ? (
        <>
          <SelectField label={t('ticketHelpdesk.from')} value={chosenMailbox} onChange={setMailbox}
            options={options.mailboxes.map((item) => ({ value: item.id, label: `${item.name} <${item.address}>` }))} />
          <SelectField label={t('ticketHelpdesk.to')} value={chosenTo} onChange={setTo}
            options={options.recipients.map((address) => ({ value: address, label: `${options.contactName} <${address}>` }))} />
          <TextField label={t('ticketHelpdesk.subject')} value={chosenSubject} onChange={setSubject} required maxLength={998} />
          <TextField label={t('ticketHelpdesk.body')} value={body} onChange={setBody} required multiline maxLength={100000} />
          <p className="actions">
            <button type="submit">{t('ticketHelpdesk.check')}</button>
            <button type="button" className="secondary" onClick={() => { setOpen(false); setNotice(null); }}>{t('helpdesk.cancel')}</button>
          </p>
        </>
      ) : (
        <div data-testid="reply-check">
          <p>{t('ticketHelpdesk.confirmText', { to: `${options.contactName} <${chosenTo}>`, from: sender ? `${sender.name} <${sender.address}>` : '' })}</p>
          <p><strong>{chosenSubject}</strong></p>
          <p className="prewrap mail-body">{body}</p>
          <p className="actions">
            <button type="button" disabled={busy} onClick={() => void confirm()}>{t('ticketHelpdesk.confirm')}</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => setChecking(false)}>{t('ticketHelpdesk.back')}</button>
          </p>
        </div>
      )}
      <NoticeView notice={notice} />
    </form>
  );
}

type Saved = { readonly kind: 'ok'; readonly value: HelpdeskView } | { readonly kind: 'problem'; readonly problem: ProblemInfo };

function CustomerChoice({ projectId, number, customerId, contactId, onCancel, onSaved }: {
  readonly projectId: string;
  readonly number: number;
  readonly customerId: string;
  readonly contactId: string;
  readonly onCancel: () => void;
  readonly onSaved: (result: Saved) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [options] = useLoad(() => ticketCustomerOptions(api, projectId, number), [api, projectId, number]);
  const [customer, setCustomer] = useState(customerId);
  const [contact, setContact] = useState(contactId);
  const [busy, setBusy] = useState(false);
  const list = options.state === 'ok' ? options.value : [];
  const contacts = list.find((option) => option.id === customer)?.contacts ?? [];

  async function save() {
    setBusy(true);
    const result = await setTicketCustomer(api, projectId, number, customer === '' ? null : customer, customer === '' || contact === '' ? null : contact);
    setBusy(false);
    onSaved(result.kind === 'ok' ? { kind: 'ok', value: result.data.value } : { kind: 'problem', problem: result.problem });
  }

  return (
    <div className="inline-form" data-testid="customer-choice">
      <SelectField label={t('field.customer')} value={customer} onChange={(value) => { setCustomer(value); setContact(''); }}
        options={[{ value: '', label: t('ticketHelpdesk.noCustomer') }, ...list.map((option) => ({ value: option.id, label: option.name }))]} />
      <SelectField label={t('field.contact')} value={contact} onChange={setContact} disabled={customer === ''}
        options={[{ value: '', label: t('ticketHelpdesk.noContact') }, ...contacts.map((option) => ({ value: option.id, label: option.name }))]} />
      <p className="actions">
        <button type="button" disabled={busy} onClick={() => void save()}>{t('ticketHelpdesk.save')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('helpdesk.cancel')}</button>
      </p>
    </div>
  );
}
