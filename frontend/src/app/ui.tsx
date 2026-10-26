import { useId, type ReactNode } from 'react';
import { problemMessageKey, type ProblemInfo } from '../api/problem.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import type { Loaded } from './hooks.ts';

// Small building blocks shared by the pages. Every visible word comes from the catalogs.

export function PageHead({ title, intro, children }: { readonly title: string; readonly intro?: string | undefined; readonly children?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        <h1 tabIndex={-1}>{title}</h1>
        {intro !== undefined && <p className="hint">{intro}</p>}
      </div>
      {children !== undefined && <div className="page-actions">{children}</div>}
    </div>
  );
}

export function TextField({ label, value, onChange, error, hint, type = 'text', required = false, autoComplete, maxLength, multiline = false, disabled = false }: {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly error?: string | null;
  readonly hint?: string;
  readonly type?: 'text' | 'password' | 'date';
  readonly required?: boolean;
  readonly autoComplete?: string;
  readonly maxLength?: number;
  readonly multiline?: boolean;
  readonly disabled?: boolean;
}) {
  const { t } = useI18n();
  const id = useId();
  const described = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined;
  const common = {
    id,
    value,
    required,
    disabled,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': described,
    maxLength,
  };
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {required && <span className="required"> ({t('form.required')})</span>}
      </label>
      {hint && <p id={`${id}-hint`} className="hint">{hint}</p>}
      {multiline
        ? <textarea {...common} rows={5} onChange={(event) => onChange(event.target.value)} />
        : <input {...common} type={type} autoComplete={autoComplete} onChange={(event) => onChange(event.target.value)} />}
      {error && <p id={`${id}-error`} className="field-error">{error}</p>}
    </div>
  );
}

export function SelectField({ label, value, onChange, options, disabled = false, hint }: {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly options: readonly { readonly value: string; readonly label: string }[];
  readonly disabled?: boolean;
  readonly hint?: string;
}) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {hint && <p id={`${id}-hint`} className="hint">{hint}</p>}
      <select id={id} value={value} disabled={disabled} aria-describedby={hint ? `${id}-hint` : undefined} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </div>
  );
}

export type Notice = { readonly kind: 'success'; readonly text: string } | { readonly kind: 'problem'; readonly problem: ProblemInfo } | { readonly kind: 'info'; readonly text: string };

export function NoticeView({ notice }: { readonly notice: Notice | null }) {
  if (notice === null) return null;
  if (notice.kind === 'problem') return <ProblemView problem={notice.problem} />;
  return <p role="status" className={notice.kind === 'success' ? 'success' : 'mode-notice'}>{notice.text}</p>;
}

// The text of a refused request: a translated reason for a known code, else the general one for its kind.
export function useProblemText(): (problem: ProblemInfo, specific?: Readonly<Record<string, MessageKey>>) => string {
  const { t } = useI18n();
  return (problem, specific = {}) => {
    const key = problem.code !== null ? specific[problem.code] : undefined;
    return t(key ?? problemMessageKey(problem));
  };
}

// Renders the three states of a load: waiting, refused with a retry, or the content.
export function LoadedView<T>({ loaded, retry, children }: {
  readonly loaded: Loaded<T>;
  readonly retry: () => void;
  readonly children: (value: T, etag: string | null) => ReactNode;
}) {
  if (loaded.state === 'loading') return <LoadingView />;
  if (loaded.state === 'problem') return <ProblemView problem={loaded.problem} onAction={retry} actionLabel="states.retry" />;
  return <>{children(loaded.value, loaded.etag)}</>;
}

export function EmptyText({ text }: { readonly text: string }) {
  return <p className="empty">{text}</p>;
}
