import { useEffect, useId, useRef, useState } from 'react';
import { createApi, readProbe, updateProbe, type Outcome, type LoadedProbe, type Probe } from '../api/client.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { SYNTHETIC_ID, scenarioIds, syntheticFetch, type ScenarioId } from '../api/synthetic.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { EmptyView, LoadingView, ProbeView, ProblemView } from '../states/StateViews.tsx';

type View =
  | { readonly kind: 'idle' }
  | { readonly kind: 'loading' }
  | { readonly kind: 'ok'; readonly probe: Probe }
  | { readonly kind: 'empty' }
  | { readonly kind: 'problem'; readonly problem: ProblemInfo; readonly scenario: ScenarioId };

const SCENARIO_LABEL: Record<ScenarioId, MessageKey> = {
  success: 'states.scenario.success',
  empty: 'states.scenario.empty',
  forbidden: 'states.scenario.forbidden',
  notFound: 'states.scenario.notFound',
  conflict: 'states.scenario.conflict',
  preconditionRequired: 'states.scenario.preconditionRequired',
  serverError: 'states.scenario.serverError',
  unavailable: 'states.scenario.unavailable',
  network: 'states.scenario.network',
  slow: 'states.scenario.slow',
};

function run(scenario: ScenarioId, signal: AbortSignal): Promise<Outcome<LoadedProbe>> {
  const api = createApi(syntheticFetch(scenario));
  const isWrite = scenario === 'conflict' || scenario === 'preconditionRequired';
  return isWrite ? updateProbe(api, SYNTHETIC_ID, 'Beispiel', '"synthetic-0"', signal) : readProbe(api, SYNTHETIC_ID, signal);
}

export function StatesPage() {
  const { t } = useI18n();
  const selectId = useId();
  const [scenario, setScenario] = useState<ScenarioId>('success');
  const [view, setView] = useState<View>({ kind: 'idle' });
  const current = useRef<AbortController | null>(null);

  useEffect(() => () => current.current?.abort(), []);

  async function load(target: ScenarioId) {
    current.current?.abort();
    const controller = new AbortController();
    current.current = controller;
    if (target === 'empty') {
      setView({ kind: 'empty' });
      return;
    }
    setView({ kind: 'loading' });
    try {
      const outcome = await run(target, controller.signal);
      if (controller.signal.aborted) return;
      setView(outcome.kind === 'ok' ? { kind: 'ok', probe: outcome.data.probe } : { kind: 'problem', problem: outcome.problem, scenario: target });
    } catch {
      // Aborted by a newer request; the newer request owns the view.
    }
  }

  function actionFor(problem: ProblemInfo, failed: ScenarioId) {
    if (problem.kind === 'conflict' || problem.kind === 'preconditionRequired') {
      return { label: 'states.reload' as const, run: () => void load('success') };
    }
    if (problem.kind === 'server' || problem.kind === 'unavailable' || problem.kind === 'network') {
      return { label: 'states.retry' as const, run: () => void load(failed) };
    }
    return null;
  }

  return (
    <>
      <h1 tabIndex={-1}>{t('states.title')}</h1>
      <p>{t('states.intro')}</p>
      <form
        className="inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          void load(scenario);
        }}
      >
        <label htmlFor={selectId}>{t('states.scenario.label')}</label>
        <select id={selectId} value={scenario} onChange={(event) => setScenario(event.target.value as ScenarioId)}>
          {scenarioIds.map((id) => (
            <option key={id} value={id}>
              {t(SCENARIO_LABEL[id])}
            </option>
          ))}
        </select>
        <button type="submit">{t('states.run')}</button>
      </form>
      <div aria-live="polite" aria-label={t('states.result.label')} role="region" className="result" data-testid="result">
        {view.kind === 'idle' && <p>{t('states.idle')}</p>}
        {view.kind === 'loading' && <LoadingView />}
        {view.kind === 'empty' && <EmptyView />}
        {view.kind === 'ok' && <ProbeView probe={view.probe} />}
        {view.kind === 'problem' && (() => {
          const action = actionFor(view.problem, view.scenario);
          return action ? (
            <ProblemView problem={view.problem} actionLabel={action.label} onAction={action.run} />
          ) : (
            <ProblemView problem={view.problem} />
          );
        })()}
      </div>
    </>
  );
}
