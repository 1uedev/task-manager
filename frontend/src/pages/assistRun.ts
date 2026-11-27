import { useEffect, useRef, useState } from 'react';
import { cancelAiRun, readAiRun, type AiRun, type Result } from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import type { useI18n } from '../i18n/I18nProvider.tsx';

// The course of one execution of the AI assistance (TASK-126, shared with TASK-127): started, read until it ended, read again
// before a suggestion is used. A later start or leaving the page drops the answers of an earlier one.

export type AssistPhase<S> =
  | { readonly kind: 'idle' }
  | { readonly kind: 'running'; readonly started: S; readonly run: AiRun | null }
  | { readonly kind: 'done'; readonly started: S; readonly run: AiRun }
  | { readonly kind: 'problem'; readonly problem: ProblemInfo };

type Started = { readonly runId: string };
type Finished<S> = { readonly started: S; readonly run: AiRun } | null;
type Reason = (code: string | null | undefined) => string;
type Translate = ReturnType<typeof useI18n>['t'];

// Starts an execution and reads it until it ended; a later start or leaving the page drops the answers of an earlier one.
// A finished execution can be read again: the server checks its suggestion against the sources as they are now (TASK-141, F3).
export function useAssistRun<S extends Started>() {
  const { api } = useBrand();
  const [phase, setPhaseState] = useState<AssistPhase<S>>({ kind: 'idle' });
  const current = useRef<AssistPhase<S>>(phase);
  const round = useRef(0);
  useEffect(() => () => {
    round.current += 1;
  }, []);

  function setPhase(next: AssistPhase<S>) {
    current.current = next;
    setPhaseState(next);
  }

  async function start(begin: () => Promise<Result<S>>): Promise<Finished<S>> {
    const mine = ++round.current;
    setPhase({ kind: 'idle' });
    const started = await begin();
    if (mine !== round.current) return null;
    if (started.kind !== 'ok') {
      setPhase({ kind: 'problem', problem: started.problem });
      return null;
    }
    const value = started.data.value;
    setPhase({ kind: 'running', started: value, run: null });
    for (;;) {
      const read = await readAiRun(api, value.runId);
      if (mine !== round.current) return null;
      if (read.kind !== 'ok') {
        setPhase({ kind: 'problem', problem: read.problem });
        return null;
      }
      const run = read.data.value;
      if (run.state !== 'queued' && run.state !== 'running') {
        setPhase({ kind: 'done', started: value, run });
        return { started: value, run };
      }
      setPhase({ kind: 'running', started: value, run });
      await new Promise((resolve) => setTimeout(resolve, 700));
      if (mine !== round.current) return null;
    }
  }

  async function cancel() {
    if (phase.kind !== 'running') return;
    const id = phase.started.runId;
    round.current += 1;
    const result = await cancelAiRun(api, id);
    setPhase(result.kind === 'ok' ? { kind: 'done', started: phase.started, run: result.data.value } : { kind: 'idle' });
  }

  function reset() {
    round.current += 1;
    setPhase({ kind: 'idle' });
  }

  // Reads the finished execution again and answers it, or null when nothing was finished, the reading failed or a later start
  // or leaving the page overtook it. An answer that no longer counts comes without content and with its reason.
  async function recheck(): Promise<AiRun | null> {
    const shown = current.current;
    if (shown.kind !== 'done') return null;
    const mine = round.current;
    const read = await readAiRun(api, shown.started.runId);
    if (mine !== round.current || current.current !== shown) return null;
    if (read.kind !== 'ok') {
      setPhase({ kind: 'problem', problem: read.problem });
      return null;
    }
    setPhase({ kind: 'done', started: shown.started, run: read.data.value });
    return read.data.value;
  }

  // Whether the phase is still the one shown: an answer that arrives later belongs to it only then.
  const stillShown = (phase: AssistPhase<S>) => current.current === phase;

  return { phase, start, cancel, reset, recheck, stillShown };
}

// The content of a suggestion that still counts, else null.
export function usable(run: AiRun | null): Record<string, unknown> | null {
  return run !== null && run.state === 'succeeded' && !run.invalid && run.content ? run.content as Record<string, unknown> : null;
}

// What the person reads about an execution that ended without a usable suggestion, or null when it has one.
export function runText(run: AiRun, reason: Reason, t: Translate): string | null {
  if (run.state === 'succeeded' && run.invalid) return t('assist.invalid', { reason: reason(run.invalid) });
  if (run.state === 'cancelled') return t('assist.cancelled');
  if (run.state !== 'succeeded') return t('assist.failed', { reason: reason(run.code ?? run.state) });
  return null;
}
