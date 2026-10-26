import { useState, type FormEvent } from 'react';
import { changePassword, login, type LoginProblem } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useSession } from '../app/session.tsx';
import { TextField } from '../app/ui.tsx';
import { problemMessageKey } from '../api/problem.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

const LOGIN_CODES: Readonly<Record<string, MessageKey>> = {
  'auth.invalid_credentials': 'login.error.invalid',
  'auth.one_time_password_used': 'login.error.oneTimeUsed',
  'auth.one_time_password_expired': 'login.error.oneTimeExpired',
  'auth.too_many_attempts': 'login.error.tooMany',
  'auth.directory_unavailable': 'login.error.directory',
};

const PASSWORD_REASONS: Readonly<Record<string, MessageKey>> = {
  'password.too_short': 'password.error.tooShort',
  'password.too_long': 'password.error.tooLong',
  'password.too_common': 'password.error.tooCommon',
  'password.equals_username': 'password.error.equalsUsername',
  'password.unchanged': 'password.error.unchanged',
};

export const PASSWORD_MIN = 12;

function useRefusal() {
  const { t } = useI18n();
  return (problem: LoginProblem, codes: Readonly<Record<string, MessageKey>>) => {
    const key = problem.code !== null ? codes[problem.code] : undefined;
    if (key === 'login.error.tooMany') return t(key, { seconds: problem.retryAfter ?? 60 });
    return t(key ?? problemMessageKey(problem));
  };
}

// The login. A one time password leads to the restricted session that only allows the password change.
export function LoginPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const { view, refresh } = useSession();
  const refusal = useRefusal();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const reason = view.state === 'anonymous' ? view.reason : 'none';

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (username.trim() === '' || password === '') return setError(t('login.error.missing'));
    setBusy(true);
    setError(null);
    const result = await login(api, username.trim(), password);
    setBusy(false);
    if (result.kind === 'ok') {
      setPassword('');
      await refresh();
    } else if (result.kind === 'refused') {
      setError(refusal(result.problem, LOGIN_CODES));
    } else {
      setError(t(problemMessageKey(result.problem)));
    }
  }

  return (
    <div className="login-card">
      <h1 tabIndex={-1}>{t('login.title')}</h1>
      {reason === 'expired' && <p role="status" className="mode-notice">{t('login.expired')}</p>}
      {reason === 'loggedOut' && <p role="status" className="success">{t('login.loggedOut')}</p>}
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField label={t('login.username')} value={username} onChange={setUsername} autoComplete="username" required />
        <TextField label={t('login.password')} value={password} onChange={setPassword} type="password" autoComplete="current-password" required />
        {error !== null && <p role="alert" className="field-error" data-testid="login-error">{error}</p>}
        <button type="submit" disabled={busy}>{t('login.submit')}</button>
      </form>
      <p className="hint">{t('login.hint')}</p>
    </div>
  );
}

// The mandatory change of the one time password after the first login. Nothing else is possible before it.
export function PasswordPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { view, refresh, signOut } = useSession();
  const refusal = useRefusal();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState<{ current?: string; next?: string; repeat?: string; general?: string }>({});
  const [busy, setBusy] = useState(false);
  const by = view.state === 'pending' ? view.session.passwordChangeBy : null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: typeof errors = {};
    if (current === '') found.current = t('form.error.required');
    if (next.length < PASSWORD_MIN) found.next = t('password.error.tooShort', { min: PASSWORD_MIN });
    if (repeat !== next) found.repeat = t('password.error.repeat');
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    const result = await changePassword(api, current, next);
    setBusy(false);
    if (result.kind === 'ok') {
      setCurrent('');
      setNext('');
      setRepeat('');
      await refresh();
      return;
    }
    if (result.kind === 'refused' && result.problem.code === 'password.current_incorrect') return setErrors({ current: t('password.error.current') });
    if (result.kind === 'refused' && result.problem.violations.length > 0) {
      const reason = result.problem.violations.map((violation) => PASSWORD_REASONS[violation]).find((key) => key !== undefined);
      return setErrors({ next: reason ? t(reason, { min: PASSWORD_MIN }) : t('problem.kind.validation') });
    }
    setErrors({ general: result.kind === 'refused' ? refusal(result.problem, LOGIN_CODES) : t(problemMessageKey(result.problem)) });
  }

  return (
    <div className="login-card">
      <h1 tabIndex={-1}>{t('password.title')}</h1>
      <p>{t('password.intro')}</p>
      {by !== null && <p className="hint">{t('password.deadline', { time: new Intl.DateTimeFormat(language, { timeStyle: 'short' }).format(new Date(by)) })}</p>}
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField label={t('password.current')} value={current} onChange={setCurrent} type="password" autoComplete="current-password" required error={errors.current ?? null} />
        <TextField label={t('password.new')} value={next} onChange={setNext} type="password" autoComplete="new-password" required
          hint={t('password.rules', { min: PASSWORD_MIN })} error={errors.next ?? null} />
        <TextField label={t('password.repeat')} value={repeat} onChange={setRepeat} type="password" autoComplete="new-password" required error={errors.repeat ?? null} />
        {errors.general !== undefined && <p role="alert" className="field-error">{errors.general}</p>}
        <p className="actions">
          <button type="submit" disabled={busy}>{t('password.submit')}</button>
          <button type="button" className="link-button" onClick={() => void signOut()}>{t('session.logout')}</button>
        </p>
      </form>
    </div>
  );
}
