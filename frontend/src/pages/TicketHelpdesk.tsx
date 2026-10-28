import { useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  readTicketHelpdesk, setTicketCustomer, ticketCustomerOptions, type Project, type Ticket, type TicketHelpdesk as HelpdeskView,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { NoticeView, SelectField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { useHelpdeskCode } from './HelpdeskPage.tsx';

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
      {readable.has('mail') && value.mails && value.mails.length > 0 && (
        <>
          <h3>{t('ticketHelpdesk.mails')}</h3>
          <ul className="plain-list" data-testid="ticket-mails">
            {value.mails.map((mail) => (
              <li key={mail.id}>
                <details>
                  <summary>{t('ticketHelpdesk.mail', { subject: mail.subject ?? t('inbox.noSubject'), from: mail.fromAddress ?? t('inbox.noSender'),
                    date: formatDateTime(mail.receivedAt, language) })}</summary>
                  <p className="prewrap mail-body">{mail.body ?? t('inbox.noBody')}</p>
                  {mail.attachments.map((file, index) => (
                    <p key={index} className="hint">{t('inbox.file', { name: file.name, size: formatBytes(file.size, language) })}
                      {file.refusal ? ` ${code(file.refusal)}` : ''}</p>
                  ))}
                </details>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
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
