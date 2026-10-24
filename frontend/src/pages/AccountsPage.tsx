import { useEffect, useState, type FormEvent } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  ACCOUNT_AUDIT_PAGE, clearLoginDelay, convertAccountToLocal, createAccount, deactivateAccount, listAccounts, reactivateAccount, readAccountAudit, renameAccount, resetPassword,
  type Account, type AccountAuditEntry, type IssuedAccount,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { LdapPanel } from './LdapPanel.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

export const ACCOUNTS_RIGHT = 'accounts.manage';
const USERNAME = /^[a-z0-9][a-z0-9._@+-]{2,63}$/;
const NONE: readonly Account[] = [];

// Local accounts, created by an administrator with a one time password that is shown exactly once, and the accounts that
// came from the LDAP directory at their first sign in (TASK-046). The latter have neither a password here nor a name to change.
export function AccountsPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { reloadDirectory, view, refresh } = useSession();
  const ownName = view.state === 'ready' ? view.session.username : null;
  const text = useProblemText();
  const [accounts, reload, replace] = useLoad(() => listAccounts(api), [api]);
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [issued, setIssued] = useState<IssuedAccount | null>(null);
  // Counts changes made outside the list that belong in the log, such as the LDAP connection.
  const [auditTick, setAuditTick] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  // The account whose display name is being changed, with the text typed so far.
  const [editing, setEditing] = useState<{ readonly id: string; readonly value: string } | null>(null);
  // The directory account whose conversion to a local account waits for the confirmation.
  const [converting, setConverting] = useState<string | null>(null);
  // Set when the person converted their own account: the server ended their session, so nothing may be loaded any more and
  // the one time password stays on the page until they go to the login.
  const [sessionEnded, setSessionEnded] = useState(false);
  // Leaving with input asks first; a successful creation empties the form and so releases the guard.
  useUnsavedGuard(username !== '' || displayName !== '' || editing !== null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const name = username.trim().toLowerCase();
    if (!USERNAME.test(name)) return setError(t('accounts.error.username'));
    setError(null);
    const result = await createAccount(api, name, displayName.trim() === '' ? null : displayName.trim());
    if (result.kind !== 'ok') return setError(text(result.problem, { 'account.username_taken': 'accounts.error.taken', 'request.invalid': 'accounts.error.username' }));
    setUsername('');
    setDisplayName('');
    setIssued(result.data.value);
    reloadDirectory();
    reload();
  }

  async function rename(account: Account, value: string, list: readonly Account[]) {
    setNotice(null);
    const result = await renameAccount(api, account.id, value.trim() === '' ? null : value.trim());
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: text(result.problem, { 'request.invalid': 'accounts.error.displayName' }) });
    replace({ value: list.map((item) => (item.id === account.id ? result.data.value : item)), etag: null });
    setEditing(null);
    reloadDirectory();
    setNotice({ kind: 'success', text: t('accounts.renamed', { name: account.username }) });
  }

  async function convert(account: Account, list: readonly Account[]) {
    setNotice(null);
    setIssued(null);
    setConverting(null);
    const result = await convertAccountToLocal(api, account.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: text(result.problem, { 'account.already_local': 'accounts.error.alreadyLocal' }) });
    if (account.username === ownName) {
      setIssued(result.data.value);
      setSessionEnded(true);
      return;
    }
    replace({ value: list.map((item) => (item.id === account.id ? result.data.value.account : item)), etag: null });
    setIssued(result.data.value);
    reloadDirectory();
    reload();
  }

  async function endDelay(account: Account, list: readonly Account[]) {
    setNotice(null);
    const result = await clearLoginDelay(api, account.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'problem', problem: result.problem });
    replace({ value: list.map((item) => (item.id === account.id ? result.data.value : item)), etag: null });
    setNotice({ kind: 'success', text: t('accounts.delayCleared', { name: account.username }) });
  }

  // What became of the one time password, without ever showing it.
  const stateText = (account: Account): string => {
    if (!account.active) return t('accounts.inactive');
    const parts: string[] = [];
    if (account.authKind === 'ldap') {
      parts.push(t('accounts.fromDirectory'));
      if (account.loginDelayedUntil) parts.push(t('accounts.delayed', { date: formatDateTime(account.loginDelayedUntil, language) }));
      return parts.join('. ');
    }
    const expires = account.oneTimePasswordExpiresAt;
    if (account.oneTimePasswordState === 'open') parts.push(t('accounts.oneTimeOpen', { date: expires ? formatDateTime(expires, language) : '' }));
    else if (account.oneTimePasswordState === 'expired') parts.push(t('accounts.oneTimeExpired'));
    else if (account.oneTimePasswordState === 'used') parts.push(t('accounts.oneTimeUsed'));
    else parts.push(t('accounts.active'));
    if (account.loginDelayedUntil) parts.push(t('accounts.delayed', { date: formatDateTime(account.loginDelayedUntil, language) }));
    return parts.join('. ');
  };

  async function act(account: Account, action: 'deactivate' | 'reactivate' | 'reset', list: readonly Account[]) {
    setNotice(null);
    setIssued(null);
    if (action === 'reset') {
      const result = await resetPassword(api, account.id);
      if (result.kind === 'ok') return setIssued(result.data.value);
      return setNotice({ kind: 'problem', problem: result.problem });
    }
    const result = action === 'deactivate' ? await deactivateAccount(api, account.id) : await reactivateAccount(api, account.id);
    if (result.kind !== 'ok') {
      return setNotice({ kind: 'info', text: text(result.problem, { 'account.last_administrator': 'accounts.error.lastAdministrator' }) });
    }
    replace({ value: list.map((item) => (item.id === account.id ? result.data.value : item)), etag: null });
    reloadDirectory();
    setNotice({ kind: 'success', text: t(action === 'deactivate' ? 'accounts.deactivated' : 'accounts.reactivated', { name: account.username }) });
  }

  return (
    <>
      <PageHead title={t('accounts.title')} intro={t('accounts.intro')} />
      {issued !== null && (
        <div role="status" className="success one-time" data-testid="one-time">
          <p>{t('accounts.issued', { name: issued.account.username })}</p>
          <p className="secret"><code>{issued.oneTimePassword}</code></p>
          <p className="hint">{t('accounts.issuedHint', { date: formatDateTime(issued.oneTimePasswordExpiresAt, language) })}</p>
          {sessionEnded && (
            <>
              <p className="hint">{t('accounts.selfConverted')}</p>
              <p className="actions"><button type="button" onClick={() => void refresh()}>{t('accounts.toLogin')}</button></p>
            </>
          )}
        </div>
      )}
      <details className="panel">
        <summary>{t('accounts.create')}</summary>
        <form onSubmit={(event) => void submit(event)} noValidate>
          <TextField label={t('accounts.username')} hint={t('accounts.usernameHint')} value={username} onChange={setUsername} required maxLength={64} autoComplete="off" error={error} />
          <TextField label={t('accounts.displayName')} value={displayName} onChange={setDisplayName} maxLength={100} autoComplete="off" />
          <button type="submit">{t('accounts.createSubmit')}</button>
        </form>
      </details>
      <LdapPanel onChanged={() => setAuditTick((tick) => tick + 1)} />
      <NoticeView notice={notice} />
      <LoadedView loaded={accounts} retry={reload}>
        {(list) => (
          <div className="table-scroll">
            <table data-testid="account-table">
              <thead>
                <tr><th scope="col">{t('accounts.username')}</th><th scope="col">{t('accounts.displayName')}</th><th scope="col">{t('accounts.state')}</th><th scope="col">{t('accounts.actions')}</th></tr>
              </thead>
              <tbody>
                {list.map((account) => (
                  <tr key={account.id}>
                    <td>{account.username}</td>
                    <td>
                      {editing?.id === account.id ? (
                        <form className="inline-form" onSubmit={(event) => {
                          event.preventDefault();
                          void rename(account, editing.value, list);
                        }}>
                          <TextField label={t('accounts.displayNameOf', { name: account.username })} value={editing.value} maxLength={100}
                            onChange={(value) => setEditing({ id: account.id, value })} />
                          <button type="submit">{t('settings.save')}</button>
                          <button type="button" className="secondary" onClick={() => setEditing(null)}>{t('accounts.cancel')}</button>
                        </form>
                      ) : account.displayName ?? ''}
                    </td>
                    <td>{stateText(account)}</td>
                    <td className="row-actions">
                      {editing?.id !== account.id && account.authKind !== 'ldap' && (
                        <button type="button" className="secondary" onClick={() => setEditing({ id: account.id, value: account.displayName ?? '' })}>
                          {t('accounts.rename', { name: account.username })}
                        </button>
                      )}
                      {account.loginDelayedUntil && (
                        <button type="button" className="secondary" onClick={() => void endDelay(account, list)}>{t('accounts.clearDelay', { name: account.username })}</button>
                      )}
                      {account.active
                        ? <button type="button" className="secondary" onClick={() => void act(account, 'deactivate', list)}>{t('accounts.deactivate', { name: account.username })}</button>
                        : <button type="button" className="secondary" onClick={() => void act(account, 'reactivate', list)}>{t('accounts.reactivate', { name: account.username })}</button>}
                      {account.authKind !== 'ldap' && (
                        <button type="button" className="secondary" onClick={() => void act(account, 'reset', list)}>{t('accounts.reset', { name: account.username })}</button>
                      )}
                      {account.authKind === 'ldap' && converting !== account.id && (
                        <button type="button" className="secondary" onClick={() => setConverting(account.id)}>{t('accounts.convert', { name: account.username })}</button>
                      )}
                      {account.authKind === 'ldap' && converting === account.id && (
                        <span role="group" aria-label={t('accounts.convertAsk', { name: account.username })} className="inline-form">
                          <span className="hint">{t(account.username === ownName ? 'accounts.convertSelfWarning' : 'accounts.convertWarning', { name: account.username })}</span>
                          <button type="button" onClick={() => void convert(account, list)}>{t('accounts.convertConfirm', { name: account.username })}</button>
                          <button type="button" className="secondary" onClick={() => setConverting(null)}>{t('accounts.cancel')}</button>
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
      <AccountAudit accounts={accounts.state === 'ok' ? accounts.value : NONE} refresh={auditTick} />
    </>
  );
}

// The audit trail of the account administration, page by page (TASK-045). It reloads whenever the list changed.
function AccountAudit({ accounts, refresh }: { readonly accounts: readonly Account[]; readonly refresh: number }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [entries, setEntries] = useState<AccountAuditEntry[]>([]);
  const [problem, setProblem] = useState<ProblemInfo | null>(null);
  const [more, setMore] = useState(true);
  const [busy, setBusy] = useState(false);

  async function load(before: number | null) {
    setBusy(true);
    const result = await readAccountAudit(api, before);
    setBusy(false);
    if (result.kind !== 'ok') return setProblem(result.problem);
    setProblem(null);
    setEntries((current) => (before === null ? result.data.value : [...current, ...result.data.value]));
    setMore(result.data.value.length === ACCOUNT_AUDIT_PAGE);
  }
  useEffect(() => { void load(null); }, [api, accounts, refresh]);

  const subject = (id: string | null | undefined) => accounts.find((account) => account.id === id)?.username ?? t('accounts.audit.unknown');
  const described = (entry: AccountAuditEntry): string => {
    const key = `accounts.audit.event.${entry.event}`;
    return Object.hasOwn(de, key) ? t(key as MessageKey, { account: subject(entry.accountId) }) : `${entry.event} ${subject(entry.accountId)}`;
  };

  return (
    <section aria-labelledby="accounts-audit" className="panel">
      <h2 id="accounts-audit">{t('accounts.audit.title')}</h2>
      <p className="hint">{t('accounts.audit.intro')}</p>
      {problem !== null ? <p className="empty">{text(problem)}</p>
        : entries.length === 0 ? (busy ? null : <EmptyText text={t('accounts.audit.empty')} />) : (
        <>
          <ol className="history" data-testid="accounts-audit">
            {entries.map((entry) => (
              <li key={entry.id}>
                <p className="hint">{t('accounts.audit.entry', { date: formatDateTime(entry.occurredAt, language), by: entry.actor === 'cli' ? t('accounts.audit.server') : entry.actor })}</p>
                <p>{described(entry)}</p>
              </li>
            ))}
          </ol>
          {more
            ? <p className="actions"><button type="button" className="secondary" disabled={busy}
                onClick={() => void load(entries[entries.length - 1]?.id ?? null)}>{t('accounts.audit.more')}</button></p>
            : <p className="hint">{t('accounts.audit.end')}</p>}
        </>
      )}
    </section>
  );
}
