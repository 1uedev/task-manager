import { useState, type FormEvent } from 'react';
import { createAccount, deactivateAccount, listAccounts, reactivateAccount, resetPassword, type Account, type IssuedAccount } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { useSession } from '../app/session.tsx';
import { LoadedView, NoticeView, PageHead, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

export const ACCOUNTS_RIGHT = 'accounts.manage';
const USERNAME = /^[a-z0-9][a-z0-9._@+-]{2,63}$/;

// Local accounts: created by an administrator with a one time password that is shown exactly once.
export function AccountsPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { reloadDirectory } = useSession();
  const text = useProblemText();
  const [accounts, reload, replace] = useLoad(() => listAccounts(api), [api]);
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [issued, setIssued] = useState<IssuedAccount | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  // Leaving with input asks first; a successful creation empties the form and so releases the guard.
  useUnsavedGuard(username !== '' || displayName !== '');

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
                    <td>{account.displayName ?? ''}</td>
                    <td>{t(!account.active ? 'accounts.inactive' : account.passwordChangeRequired ? 'accounts.pending' : 'accounts.active')}</td>
                    <td className="row-actions">
                      {account.active
                        ? <button type="button" className="secondary" onClick={() => void act(account, 'deactivate', list)}>{t('accounts.deactivate', { name: account.username })}</button>
                        : <button type="button" className="secondary" onClick={() => void act(account, 'reactivate', list)}>{t('accounts.reactivate', { name: account.username })}</button>}
                      <button type="button" className="secondary" onClick={() => void act(account, 'reset', list)}>{t('accounts.reset', { name: account.username })}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
    </>
  );
}
