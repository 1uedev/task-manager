import { useId } from 'react';
import { problemMessageKey, type ProblemInfo } from '../api/problem.ts';
import type { Probe } from '../api/client.ts';
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

export function EmptyView() {
  const { t } = useI18n();
  const heading = useId();
  return (
    <section aria-labelledby={heading} className="state state-empty">
      <h3 id={heading}>{t('states.empty.title')}</h3>
      <p>{t('states.empty.text')}</p>
    </section>
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

export function ProbeView({ probe }: { readonly probe: Probe }) {
  const { t } = useI18n();
  const heading = useId();
  return (
    <section aria-labelledby={heading} className="state state-success">
      <h3 id={heading}>{t('states.success.title')}</h3>
      <dl>
        <dt>{t('states.success.id')}</dt>
        <dd>{probe.id}</dd>
        <dt>{t('states.success.label')}</dt>
        <dd>{probe.label}</dd>
        <dt>{t('states.success.version')}</dt>
        <dd>{probe.version}</dd>
        <dt>{t('states.success.note')}</dt>
        <dd>{probe.note ?? t('states.success.noNote')}</dd>
      </dl>
    </section>
  );
}
