import { useState, type FormEvent } from 'react';
import {
  createPortalAccount, listPortalAccounts, listPortalOptions, updatePortalAccount,
  type IssuedPortalAccount, type PortalAccount, type PortalOption,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

const USERNAME = /^[a-z0-9][a-z0-9._@+-]{2,63}$/;
const PROBLEMS = {
  'account.username_taken': 'accounts.error.taken',
  'permissions.portal_company_right': 'portalAccounts.error.companyRight',
  'account.not_portal': 'portalAccounts.error.notPortal',
} as const;

// Accounts of customers for the portal (TASK-075, DEC-105). Each belongs to one customer organization of the helpdesk, which
// never changes, optionally to one of its contacts, and is a member of the projects released to the organization that the
// administration chose. Deactivation, password reset and display name use the list of all accounts above.
export function PortalAccountsPanel({ onChanged }: { readonly onChanged: () => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [accounts, reload, replace] = useLoad(() => listPortalAccounts(api), [api]);
  const [options, reloadOptions] = useLoad(() => listPortalOptions(api), [api]);
  const [issued, setIssued] = useState<IssuedPortalAccount | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [editing, setEditing] = useState<string | null>(null);

  const organizations = options.state === 'ok' ? options.value : [];
  const organization = (id: string) => organizations.find((option) => option.id === id);
  const contactName = (account: PortalAccount) =>
    organization(account.organizationId)?.contacts.find((contact) => contact.id === account.contactId)?.name ?? '';
  const projectNames = (account: PortalAccount) => {
    const released = organization(account.organizationId)?.projects ?? [];
    return account.projectIds.map((id) => released.find((project) => project.id === id)?.key ?? id).join(', ');
  };

  return (
    <section aria-labelledby="portal-accounts" className="panel" data-testid="portal-accounts">
      <h2 id="portal-accounts">{t('portalAccounts.title')}</h2>
      <p className="hint">{t('portalAccounts.intro')}</p>
      {issued !== null && (
        <div role="status" className="success one-time" data-testid="portal-one-time">
          <p>{t('accounts.issued', { name: issued.account.username })}</p>
          <p className="secret"><code>{issued.oneTimePassword}</code></p>
          <p className="hint">{t('accounts.issuedHint', { date: formatDateTime(issued.oneTimePasswordExpiresAt, language) })}</p>
        </div>
      )}
      <NoticeView notice={notice} />
      <LoadedView loaded={options} retry={reloadOptions}>
        {(list) => list.length === 0
          ? <EmptyText text={t('portalAccounts.noCustomers')} /> : (
            <details>
              <summary>{t('portalAccounts.create')}</summary>
              <PortalAccountForm options={list} account={null} onDone={(result) => {
                if (result === null) return;
                setNotice(null);
                setIssued(result as IssuedPortalAccount);
                reload();
                onChanged();
              }} />
            </details>
          )}
      </LoadedView>
      <LoadedView loaded={accounts} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('portalAccounts.empty')} /> : (
          <div className="table-scroll">
            <table data-testid="portal-account-table">
              <thead>
                <tr>
                  <th scope="col">{t('accounts.username')}</th>
                  <th scope="col">{t('portalAccounts.organization')}</th>
                  <th scope="col">{t('portalAccounts.contact')}</th>
                  <th scope="col">{t('portalAccounts.projects')}</th>
                  <th scope="col">{t('accounts.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {list.map((account) => (
                  <tr key={account.id}>
                    <td>{account.username}{account.active ? '' : ` (${t('accounts.inactive')})`}</td>
                    <td>{organization(account.organizationId)?.name ?? ''}</td>
                    <td>{contactName(account)}</td>
                    <td>
                      {editing === account.id && options.state === 'ok' ? (
                        <PortalAccountForm options={options.value} account={account} onDone={(result) => {
                          setEditing(null);
                          if (result === null) return;
                          const changed = result as PortalAccount;
                          replace({ value: list.map((item) => (item.id === changed.id ? changed : item)), etag: null });
                          setNotice({ kind: 'success', text: t('portalAccounts.saved', { name: account.username }) });
                        }} />
                      ) : projectNames(account)}
                    </td>
                    <td className="row-actions">
                      {editing !== account.id && (
                        <button type="button" className="secondary" onClick={() => { setNotice(null); setEditing(account.id); }}>
                          {t('portalAccounts.edit', { name: account.username })}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
    </section>
  );

}

// Creates a portal account, or changes contact and projects of an existing one; the organization is fixed after creation.
function PortalAccountForm({ options: list, account, onDone }: {
  readonly options: readonly PortalOption[];
  readonly account: PortalAccount | null;
  readonly onDone: (result: IssuedPortalAccount | PortalAccount | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [organizationId, setOrganizationId] = useState(account?.organizationId ?? list[0]?.id ?? '');
  const [contactId, setContactId] = useState(account?.contactId ?? '');
  const [projectIds, setProjectIds] = useState<string[]>(account ? [...account.projectIds] : []);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useUnsavedGuard(account === null ? username !== '' || displayName !== '' : false);
  const chosen = list.find((option) => option.id === organizationId);

  function pick(id: string) {
    setOrganizationId(id);
    setContactId('');
    setProjectIds([]);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (account === null) {
      const name = username.trim().toLowerCase();
      if (!USERNAME.test(name)) return setError(t('accounts.error.username'));
      setBusy(true);
      const result = await createPortalAccount(api, {
        username: name, displayName: displayName.trim() === '' ? null : displayName.trim(), organizationId,
        contactId: contactId === '' ? null : contactId, projectIds,
      });
      setBusy(false);
      if (result.kind !== 'ok') return setError(text(result.problem, PROBLEMS));
      setUsername('');
      setDisplayName('');
      setProjectIds([]);
      return onDone(result.data.value);
    }
    setBusy(true);
    const result = await updatePortalAccount(api, account.id, contactId === '' ? null : contactId, projectIds);
    setBusy(false);
    if (result.kind !== 'ok') return setError(text(result.problem, PROBLEMS));
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid={account === null ? 'portal-account-form' : 'portal-account-edit'}>
      {account === null && (
        <>
          <TextField label={t('accounts.username')} hint={t('accounts.usernameHint')} value={username} onChange={setUsername} required
            maxLength={64} autoComplete="off" />
          <TextField label={t('accounts.displayName')} value={displayName} onChange={setDisplayName} maxLength={100} autoComplete="off" />
          <SelectField label={t('portalAccounts.organization')} hint={t('portalAccounts.organizationHint')} value={organizationId}
            onChange={pick} options={list.map((option) => ({ value: option.id, label: option.name }))} />
        </>
      )}
      <SelectField label={t('portalAccounts.contact')} value={contactId} onChange={setContactId}
        options={[{ value: '', label: t('portalAccounts.noContact') },
          ...(chosen?.contacts ?? []).map((contact) => ({ value: contact.id, label: contact.name }))]} />
      <fieldset>
        <legend>{t('portalAccounts.projects')}</legend>
        {(chosen?.projects.length ?? 0) === 0
          ? <p className="hint">{t('portalAccounts.noProjects')}</p>
          : chosen?.projects.map((project) => (
            <label key={project.id} className="check">
              <input type="checkbox" checked={projectIds.includes(project.id)}
                onChange={(event) => setProjectIds((current) => event.target.checked ? [...current, project.id] : current.filter((id) => id !== project.id))} />
              {` ${project.key} ${project.name}`}
            </label>
          ))}
      </fieldset>
      {error !== null && <p role="alert" className="error">{error}</p>}
      <p className="actions">
        <button type="submit" disabled={busy || organizationId === ''}>{account === null ? t('portalAccounts.createSubmit') : t('settings.save')}</button>
        {account !== null && <button type="button" className="secondary" onClick={() => onDone(null)}>{t('accounts.cancel')}</button>}
      </p>
    </form>
  );
}
