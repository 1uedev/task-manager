import { problemMessageKey, type ProblemInfo } from '../api/problem.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

export function LoadingView() {
  const { t } = useI18n();
  return (
    <div role="status" className="state state-loading">
      <span className="spinner" aria-hidden="true" />
      <span>{t('states.loading')}</span>
    </div>
  );
}

export function ProblemView({ problem, onAction, actionLabel }: {
  readonly problem: ProblemInfo;
  readonly onAction?: () => void;
  readonly actionLabel?: 'states.retry' | 'states.reload';
}) {
  const { t } = useI18n();
  return (
    <div role="alert" className={`state state-problem kind-${problem.kind}`}>
      <p className="state-title">
        <span aria-hidden="true">⚠ </span>
        {t('problem.title')}
      </p>
      <p>{t(problemMessageKey(problem))}</p>
      {problem.correlationId !== null && <p className="reference">{t('problem.reference', { id: problem.correlationId })}</p>}
      {onAction && actionLabel && (
        <button type="button" onClick={onAction}>
          {t(actionLabel)}
        </button>
      )}
    </div>
  );
}
