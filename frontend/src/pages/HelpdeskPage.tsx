import { useState, type FormEvent } from 'react';
import {
  createContact, createCustomer, createMailbox, listContacts, listCustomers, listMailboxes, listProjects, readHelpdeskStatus, readProject,
  testMailbox, updateContact, updateCustomer, updateMailbox, type Contact, type Customer, type Mailbox, type MailboxRequest, type ProjectSummary,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard, type Loaded } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { typeName } from './ProjectPage.tsx';

export const HELPDESK_RIGHT = 'helpdesk.manage';

type Security = 'tls' | 'starttls' | 'none';
const SECURITIES: readonly Security[] = ['tls', 'starttls', 'none'];
const asSecurity = (value: string | null | undefined): Security => (value === 'none' || value === 'starttls' ? value : 'tls');

const known = (key: string): key is MessageKey => Object.hasOwn(de, key);

/** The text of a stable code of the helpdesk, or the code itself for one the interface does not know yet. */
export function useHelpdeskCode(): (code: string) => string {
  const { t } = useI18n();
  return (code) => (known(`helpdesk.code.${code}`) ? t(`helpdesk.code.${code}` as MessageKey) : code);
}

// The reasons of a refused form, each with its own text; unknown reasons fall back to the general one.
function useReasons(): (problem: ProblemInfo) => string {
  const { t } = useI18n();
  const text = useProblemText();
  return (problem) => {
    const reasons = (problem.violations ?? []).map((reason) => (known(`helpdesk.violation.${reason}`) ? t(`helpdesk.violation.${reason}` as MessageKey) : null))
      .filter((reason): reason is string => reason !== null);
    if (reasons.length > 0) return reasons.join(' ');
    return text(problem, {
      'customer.name_taken': 'helpdesk.error.customerTaken',
      'contact.address_taken': 'helpdesk.error.addressTaken',
      'mailbox.name_taken': 'helpdesk.error.mailboxTaken',
      'customer.version_conflict': 'helpdesk.error.changed',
      'contact.version_conflict': 'helpdesk.error.changed',
      'mailbox.version_conflict': 'helpdesk.error.changed',
      'helpdesk.secret_key_missing': 'helpdesk.keyMissing',
    });
  };
}

// Mailboxes, customer organizations and their contacts (TASK-057). The mailbox password is entered here but never shown
// again; the server only says whether one is stored.
export function HelpdeskPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [status] = useLoad(() => readHelpdeskStatus(api), [api]);
  const [projects] = useLoad(() => listProjects(api), [api]);
  const [customers, reloadCustomers] = useLoad(() => listCustomers(api), [api]);
  const projectList = projects.state === 'ok' ? projects.value : [];
  return (
    <>
      <PageHead title={t('helpdesk.title')} intro={t('helpdesk.intro')} />
      {status.state === 'ok' && !status.value.secretKey && <p role="alert" className="mode-notice" data-testid="helpdesk-key-missing">{t('helpdesk.keyMissing')}</p>}
      <Mailboxes projects={projectList} />
      <Customers projects={projectList} customers={customers} reload={reloadCustomers} />
      <LoadedView loaded={customers} retry={reloadCustomers}>
        {(list) => <Contacts customers={list} />}
      </LoadedView>
    </>
  );
}

// ---------------------------------------------------------------- mailboxes

function Mailboxes({ projects }: { readonly projects: readonly ProjectSummary[] }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const code = useHelpdeskCode();
  const [mailboxes, reload] = useLoad(() => listMailboxes(api), [api]);
  const [editing, setEditing] = useState<Mailbox | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [testing, setTesting] = useState<string | null>(null);
  const projectName = (id: string) => projects.find((project) => project.id === id)?.name ?? id.slice(0, 8);

  async function check(mailbox: Mailbox) {
    setNotice(null);
    setTesting(mailbox.id);
    const result = await testMailbox(api, mailbox.id);
    setTesting(null);
    if (result.kind !== 'ok') return setNotice({ kind: 'problem', problem: result.problem });
    setNotice(result.data.value.ok
      ? { kind: 'success', text: t('helpdesk.testOk', { name: mailbox.name }) }
      : { kind: 'info', text: t('helpdesk.testFailed', { name: mailbox.name, reason: code(result.data.value.code ?? 'helpdesk.connection_failed') }) });
  }

  return (
    <section aria-labelledby="helpdesk-mailboxes" className="panel" data-testid="helpdesk-mailboxes">
      <h2 id="helpdesk-mailboxes">{t('helpdesk.mailboxes')}</h2>
      <p className="hint">{t('helpdesk.mailboxesIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={mailboxes} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('helpdesk.mailboxesEmpty')} /> : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">{t('helpdesk.name')}</th><th scope="col">{t('helpdesk.project')}</th>
                  <th scope="col">{t('helpdesk.server')}</th><th scope="col">{t('helpdesk.state')}</th><th scope="col">{t('helpdesk.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {list.map((mailbox) => (
                  <tr key={mailbox.id} data-mailbox={mailbox.name}>
                    <td>{mailbox.name}{!mailbox.enabled && <p className="hint">{t('helpdesk.disabled')}</p>}</td>
                    <td>{projectName(mailbox.projectId)}</td>
                    <td>
                      <span>{`${mailbox.username} @ ${mailbox.imapHost}:${mailbox.imapPort}`}</span>
                      <p className="hint">{mailbox.passwordSet ? t('helpdesk.passwordSet') : t('helpdesk.passwordMissing')}</p>
                    </td>
                    <td data-testid="mailbox-state">
                      {mailbox.status.errorCode
                        ? <p className="field-error">{t('helpdesk.failedSince', { reason: code(mailbox.status.errorCode), date: formatDateTime(mailbox.status.errorAt ?? '', language) })}</p>
                        : mailbox.status.succeededAt
                        ? <p className="hint">{t('helpdesk.fetchedAt', { date: formatDateTime(mailbox.status.succeededAt, language) })}</p>
                        : <p className="hint">{t('helpdesk.notFetched')}</p>}
                      {mailbox.status.notice && <p className="mode-notice">{code(mailbox.status.notice)}</p>}
                    </td>
                    <td className="row-actions">
                      <button type="button" className="secondary" onClick={() => setEditing(mailbox)}>{t('helpdesk.edit')}</button>
                      <button type="button" className="secondary" disabled={testing !== null} onClick={() => void check(mailbox)}>{t('helpdesk.test')}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
      {editing === null
        ? <p className="actions"><button type="button" onClick={() => setEditing('new')}>{t('helpdesk.addMailbox')}</button></p>
        : <MailboxForm key={editing === 'new' ? 'new' : editing.id} mailbox={editing === 'new' ? null : editing} projects={projects}
            onDone={(saved) => {
              setEditing(null);
              if (saved !== null) {
                setNotice({ kind: 'success', text: t('helpdesk.saved', { name: saved.name }) });
                reload();
              }
            }} />}
    </section>
  );
}

function MailboxForm({ mailbox, projects, onDone }: {
  readonly mailbox: Mailbox | null;
  readonly projects: readonly ProjectSummary[];
  readonly onDone: (saved: Mailbox | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const [name, setName] = useState(mailbox?.name ?? '');
  const [enabled, setEnabled] = useState(mailbox?.enabled ?? true);
  const [projectId, setProjectId] = useState(mailbox?.projectId ?? projects[0]?.id ?? '');
  const [ticketType, setTicketType] = useState(mailbox?.ticketType ?? '');
  const [imapHost, setImapHost] = useState(mailbox?.imapHost ?? '');
  const [imapPort, setImapPort] = useState(String(mailbox?.imapPort ?? 993));
  const [imapSecurity, setImapSecurity] = useState<Security>(asSecurity(mailbox?.imapSecurity));
  const [username, setUsername] = useState(mailbox?.username ?? '');
  const [password, setPassword] = useState('');
  const [removePassword, setRemovePassword] = useState(false);
  const [folder, setFolder] = useState(mailbox?.folder ?? 'INBOX');
  const [authservId, setAuthservId] = useState(mailbox?.authservId ?? '');
  const [smtpHost, setSmtpHost] = useState(mailbox?.smtpHost ?? '');
  const [smtpPort, setSmtpPort] = useState(mailbox?.smtpPort === null || mailbox?.smtpPort === undefined ? '' : String(mailbox.smtpPort));
  const [smtpSecurity, setSmtpSecurity] = useState<Security>(asSecurity(mailbox?.smtpSecurity));
  const [address, setAddress] = useState(mailbox?.address ?? '');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [project] = useLoad(() => readProject(api, projectId), [api, projectId]);
  const types = project.state === 'ok' ? project.value.configuration.ticketTypes : [];
  const chosenType = types.some((type) => type.key === ticketType) ? ticketType : types[0]?.key ?? ticketType;
  useUnsavedGuard(name !== (mailbox?.name ?? '') || password !== '' || imapHost !== (mailbox?.imapHost ?? '') || username !== (mailbox?.username ?? ''));

  const optional = (value: string) => (value.trim() === '' ? null : value.trim());
  const port = (value: string) => (/^[0-9]{1,5}$/.test(value.trim()) ? Number(value.trim()) : -1);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    const body: MailboxRequest = {
      name: name.trim(), enabled, projectId, ticketType: chosenType, imapHost: imapHost.trim(), imapPort: port(imapPort), imapSecurity,
      username: username.trim(), folder: optional(folder), authservId: optional(authservId), smtpHost: optional(smtpHost),
      smtpPort: smtpPort.trim() === '' ? null : port(smtpPort), smtpSecurity: optional(smtpHost) === null ? null : smtpSecurity, address: optional(address),
      ...(removePassword ? { password: '' } : password !== '' ? { password } : {}),
    };
    setBusy(true);
    const result = mailbox === null ? await createMailbox(api, body) : await updateMailbox(api, mailbox.id, mailbox.version, body);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    setPassword('');
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid="mailbox-form">
      <h3>{mailbox === null ? t('helpdesk.addMailbox') : t('helpdesk.editMailbox', { name: mailbox.name })}</h3>
      <TextField label={t('helpdesk.name')} value={name} onChange={setName} required maxLength={120} />
      <div className="field">
        <label><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /> {t('helpdesk.enabled')}</label>
      </div>
      <SelectField label={t('helpdesk.project')} value={projectId} onChange={setProjectId}
        options={projects.map((item) => ({ value: item.id, label: `${item.key} ${item.name}` }))} />
      {project.state === 'ok' && (
        <SelectField label={t('helpdesk.ticketType')} value={chosenType} onChange={setTicketType}
          options={types.map((type) => ({ value: type.key, label: typeName(project.value, type.key) }))} />
      )}
      <TextField label={t('helpdesk.imapHost')} value={imapHost} onChange={setImapHost} required maxLength={253} autoComplete="off" />
      <TextField label={t('helpdesk.imapPort')} value={imapPort} onChange={setImapPort} required maxLength={5} autoComplete="off" />
      <SelectField label={t('helpdesk.security')} value={imapSecurity} onChange={(value) => setImapSecurity(asSecurity(value))}
        options={SECURITIES.map((value) => ({ value, label: t(`helpdesk.security.${value}` as MessageKey) }))}
        {...(imapSecurity === 'none' ? { hint: t('helpdesk.securityNone') } : {})} />
      <TextField label={t('helpdesk.username')} value={username} onChange={setUsername} required maxLength={254} autoComplete="off" />
      <TextField label={t('helpdesk.password')} type="password" value={password} onChange={setPassword} maxLength={500} autoComplete="new-password"
        hint={mailbox?.passwordSet ? t('helpdesk.passwordKeep') : t('helpdesk.passwordHint')} disabled={removePassword} />
      {mailbox?.passwordSet && (
        <div className="field">
          <label><input type="checkbox" checked={removePassword} onChange={(event) => setRemovePassword(event.target.checked)} /> {t('helpdesk.passwordRemove')}</label>
        </div>
      )}
      <TextField label={t('helpdesk.folder')} value={folder} onChange={setFolder} maxLength={200} autoComplete="off" />
      <TextField label={t('helpdesk.authservId')} hint={t('helpdesk.authservIdHint')} value={authservId} onChange={setAuthservId} maxLength={253} autoComplete="off" />
      <details>
        <summary>{t('helpdesk.smtp')}</summary>
        <p className="hint">{t('helpdesk.smtpHint')}</p>
        <TextField label={t('helpdesk.smtpHost')} value={smtpHost} onChange={setSmtpHost} maxLength={253} autoComplete="off" />
        <TextField label={t('helpdesk.smtpPort')} value={smtpPort} onChange={setSmtpPort} maxLength={5} autoComplete="off" />
        <SelectField label={t('helpdesk.security')} value={smtpSecurity} onChange={(value) => setSmtpSecurity(asSecurity(value))}
          options={SECURITIES.map((value) => ({ value, label: t(`helpdesk.security.${value}` as MessageKey) }))} />
        <TextField label={t('helpdesk.address')} value={address} onChange={setAddress} maxLength={254} autoComplete="off" />
      </details>
      <p className="actions">
        <button type="submit" disabled={busy}>{t('helpdesk.save')}</button>
        <button type="button" className="secondary" onClick={() => onDone(null)}>{t('helpdesk.cancel')}</button>
      </p>
      <NoticeView notice={notice} />
    </form>
  );
}

// ---------------------------------------------------------------- customers

function Customers({ projects, customers, reload }: {
  readonly projects: readonly ProjectSummary[];
  readonly customers: Loaded<Customer[]>;
  readonly reload: () => void;
}) {
  const { t } = useI18n();
  const [editing, setEditing] = useState<Customer | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const projectName = (id: string) => projects.find((project) => project.id === id)?.key ?? id.slice(0, 8);
  return (
    <section aria-labelledby="helpdesk-customers" className="panel" data-testid="helpdesk-customers">
      <h2 id="helpdesk-customers">{t('helpdesk.customers')}</h2>
      <p className="hint">{t('helpdesk.customersIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={customers} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('helpdesk.customersEmpty')} /> : (
          <ul className="plain-list">
            {list.map((customer) => (
              <li key={customer.id} data-customer={customer.name}>
                <strong>{customer.name}</strong>{' '}
                <span className="hint">{customer.projects.length === 0 ? t('helpdesk.noProjects') : customer.projects.map(projectName).join(', ')}</span>{' '}
                <button type="button" className="link-button" onClick={() => setEditing(customer)}>{t('helpdesk.edit')}</button>
              </li>
            ))}
          </ul>
        )}
      </LoadedView>
      {editing === null
        ? <p className="actions"><button type="button" onClick={() => setEditing('new')}>{t('helpdesk.addCustomer')}</button></p>
        : <CustomerForm key={editing === 'new' ? 'new' : editing.id} customer={editing === 'new' ? null : editing} projects={projects}
            onDone={(saved) => {
              setEditing(null);
              if (saved !== null) {
                setNotice({ kind: 'success', text: t('helpdesk.saved', { name: saved.name }) });
                reload();
              }
            }} />}
    </section>
  );
}

function CustomerForm({ customer, projects, onDone }: {
  readonly customer: Customer | null;
  readonly projects: readonly ProjectSummary[];
  readonly onDone: (saved: Customer | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const [name, setName] = useState(customer?.name ?? '');
  const [released, setReleased] = useState<readonly string[]>(customer?.projects ?? []);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  useUnsavedGuard(name !== (customer?.name ?? ''));

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setBusy(true);
    const result = customer === null ? await createCustomer(api, name.trim(), released) : await updateCustomer(api, customer.id, customer.version, name.trim(), released);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid="customer-form">
      <h3>{customer === null ? t('helpdesk.addCustomer') : t('helpdesk.editCustomer', { name: customer.name })}</h3>
      <TextField label={t('helpdesk.name')} value={name} onChange={setName} required maxLength={120} />
      <fieldset>
        <legend>{t('helpdesk.released')}</legend>
        <p className="hint">{t('helpdesk.releasedHint')}</p>
        {projects.map((project) => (
          <label key={project.id} className="check">
            <input type="checkbox" checked={released.includes(project.id)}
              onChange={(event) => setReleased((current) => event.target.checked ? [...current, project.id] : current.filter((id) => id !== project.id))} />
            {` ${project.key} ${project.name}`}
          </label>
        ))}
      </fieldset>
      <p className="actions">
        <button type="submit" disabled={busy}>{t('helpdesk.save')}</button>
        <button type="button" className="secondary" onClick={() => onDone(null)}>{t('helpdesk.cancel')}</button>
      </p>
      <NoticeView notice={notice} />
    </form>
  );
}

// ---------------------------------------------------------------- contacts

function Contacts({ customers }: { readonly customers: readonly Customer[] }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [contacts, reload] = useLoad(() => listContacts(api), [api]);
  const [editing, setEditing] = useState<Contact | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const customerName = (id: string) => customers.find((customer) => customer.id === id)?.name ?? id.slice(0, 8);
  return (
    <section aria-labelledby="helpdesk-contacts" className="panel" data-testid="helpdesk-contacts">
      <h2 id="helpdesk-contacts">{t('helpdesk.contacts')}</h2>
      <p className="hint">{t('helpdesk.contactsIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={contacts} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('helpdesk.contactsEmpty')} /> : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr><th scope="col">{t('helpdesk.name')}</th><th scope="col">{t('helpdesk.customer')}</th><th scope="col">{t('helpdesk.addresses')}</th><th scope="col">{t('helpdesk.actions')}</th></tr>
              </thead>
              <tbody>
                {list.map((contact) => (
                  <tr key={contact.id} data-contact={contact.name}>
                    <td>{contact.name}</td>
                    <td>{customerName(contact.customerId)}</td>
                    <td>{contact.addresses.join(', ')}</td>
                    <td className="row-actions"><button type="button" className="secondary" onClick={() => setEditing(contact)}>{t('helpdesk.edit')}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
      {customers.length === 0 ? <p className="hint">{t('helpdesk.contactsNeedCustomer')}</p>
        : editing === null
        ? <p className="actions"><button type="button" onClick={() => setEditing('new')}>{t('helpdesk.addContact')}</button></p>
        : <ContactForm key={editing === 'new' ? 'new' : editing.id} contact={editing === 'new' ? null : editing} customers={customers}
            onDone={(saved) => {
              setEditing(null);
              if (saved !== null) {
                setNotice({ kind: 'success', text: t('helpdesk.saved', { name: saved.name }) });
                reload();
              }
            }} />}
    </section>
  );
}

function ContactForm({ contact, customers, onDone }: {
  readonly contact: Contact | null;
  readonly customers: readonly Customer[];
  readonly onDone: (saved: Contact | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const [customerId, setCustomerId] = useState(contact?.customerId ?? customers[0]?.id ?? '');
  const [name, setName] = useState(contact?.name ?? '');
  const [addresses, setAddresses] = useState((contact?.addresses ?? []).join('\n'));
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  useUnsavedGuard(name !== (contact?.name ?? '') || addresses !== (contact?.addresses ?? []).join('\n'));

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    const list = addresses.split(/[\s,;]+/).map((value) => value.trim()).filter((value) => value !== '');
    setBusy(true);
    const result = contact === null ? await createContact(api, customerId, name.trim(), list)
      : await updateContact(api, contact.id, contact.version, customerId, name.trim(), list);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid="contact-form">
      <h3>{contact === null ? t('helpdesk.addContact') : t('helpdesk.editContact', { name: contact.name })}</h3>
      <SelectField label={t('helpdesk.customer')} value={customerId} onChange={setCustomerId}
        options={customers.map((customer) => ({ value: customer.id, label: customer.name }))} />
      <TextField label={t('helpdesk.name')} value={name} onChange={setName} required maxLength={120} />
      <TextField label={t('helpdesk.addresses')} hint={t('helpdesk.addressesHint')} value={addresses} onChange={setAddresses} multiline required />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('helpdesk.save')}</button>
        <button type="button" className="secondary" onClick={() => onDone(null)}>{t('helpdesk.cancel')}</button>
      </p>
      <NoticeView notice={notice} />
    </form>
  );
}
