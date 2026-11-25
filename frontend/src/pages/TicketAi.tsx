import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  cancelAiRun, readAiRun, readAssistOptions, startReplyDraft, startTicketSummary, type AiRun, type AssistChannel, type AssistOptions,
  type AssistSource, type AssistStarted, type Project, type Ticket,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { NoticeView, TextField, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { useAiProblem, useAiReason } from './AiPage.tsx';

const ASSIST_PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'assist.no_sources': 'assist.noSources',
  'helpdesk.reply_no_contact': 'ticketHelpdesk.error.noContact',
  'helpdesk.portal_no_customer': 'ticketHelpdesk.error.portalNoCustomer',
  'auth.forbidden': 'ticketHelpdesk.error.replyForbidden',
};

// A refused start: the helpdesk and assistance codes with their own text, the codes of the AI foundation with their reason.
function useAssistProblem(): (problem: ProblemInfo) => string {
  const { t } = useI18n();
  const ai = useAiProblem();
  return (problem) => {
    const key = problem.code !== null ? ASSIST_PROBLEMS[problem.code] : undefined;
    return key ? t(key) : ai(problem);
  };
}

// AI at the ticket (TASK-126): the summary as a preview to copy and the draft of an answer that goes into the editable text
// field. Neither writes anything; a draft is sent only through the check and confirmation of the answer.

type Phase =
  | { readonly kind: 'idle' }
  | { readonly kind: 'running'; readonly started: AssistStarted; readonly run: AiRun | null }
  | { readonly kind: 'done'; readonly started: AssistStarted; readonly run: AiRun }
  | { readonly kind: 'problem'; readonly problem: ProblemInfo };

type Begin = () => StartOutcome;
type StartOutcome = ReturnType<typeof startTicketSummary>;
type Finished = { readonly started: AssistStarted; readonly run: AiRun } | null;
type Reason = (code: string | null | undefined) => string;
type Translate = ReturnType<typeof useI18n>['t'];

// Starts an execution and reads it until it ended; a later start or leaving the page drops the answers of an earlier one.
function useAssistRun() {
  const { api } = useBrand();
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' });
  const round = useRef(0);
  useEffect(() => () => {
    round.current += 1;
  }, []);

  async function start(begin: Begin): Promise<Finished> {
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

  return { phase, start, cancel, reset };
}

// Whether the person may start the use cases at this ticket now; nothing is shown while it is not known.
function useAssistOptions(project: Project, ticket: Ticket): AssistOptions | null {
  const { api } = useBrand();
  const [options, setOptions] = useState<AssistOptions | null>(null);
  useEffect(() => {
    let alive = true;
    setOptions(null);
    void readAssistOptions(api, project.id, ticket.number).then((result) => {
      if (alive && result.kind === 'ok') setOptions(result.data.value);
    });
    return () => {
      alive = false;
    };
  }, [api, project.id, ticket.number]);
  return options;
}

// The label of a source in the language of the page: the ticket itself or an entry of the conversation with its time.
function useSourceLabel(): (source: AssistSource) => string {
  const { t, language } = useI18n();
  return (source) => {
    if (source.kind === 'ticket') return t('assist.source.ticket');
    const date = source.at ? formatDateTime(source.at, language) : '';
    if (source.kind === 'mail') return t('assist.source.mail', { date });
    if (source.kind === 'reply') return t('assist.source.reply', { date });
    return t(source.author === 'support' ? 'assist.source.supportPost' : 'assist.source.customerPost', { date });
  };
}

// Leads to the entry a source refers to: the mail, reply or post in the ticket opens and gets the focus.
function showEntry(source: AssistSource) {
  const entry = document.querySelector<HTMLElement>(source.kind === 'ticket' ? '#ticket-fields' : `[data-entry="${CSS.escape(source.entryId)}"]`);
  if (entry === null) return;
  entry.querySelector('details')?.setAttribute('open', '');
  if (!entry.hasAttribute('tabindex')) entry.setAttribute('tabindex', '-1');
  entry.scrollIntoView({ block: 'center' });
  entry.focus();
}

function SourceLinks({ ids, sources }: { readonly ids: readonly string[]; readonly sources: readonly AssistSource[] }) {
  const { t } = useI18n();
  const label = useSourceLabel();
  const known = ids.map((id) => sources.find((source) => source.id === id)).filter((source): source is AssistSource => source !== undefined);
  if (known.length === 0) return <span className="hint">{' '}{t('assist.noSource')}</span>;
  return (
    <span className="assist-sources">
      {known.map((source) => (
        <button key={source.id} type="button" className="link-button" data-source={source.id} onClick={() => showEntry(source)}>
          {source.id}: {label(source)}
        </button>
      ))}
    </span>
  );
}

interface Point { readonly text: string; readonly sources: readonly string[] }

function points(value: unknown): Point[] {
  if (!Array.isArray(value)) return [];
  return value.filter((p): p is Point => typeof p === 'object' && p !== null && typeof (p as Point).text === 'string' && Array.isArray((p as Point).sources));
}

function runText(run: AiRun, reason: Reason, t: Translate): string | null {
  if (run.state === 'succeeded' && run.invalid) return t('assist.invalid', { reason: reason(run.invalid) });
  if (run.state === 'cancelled') return t('assist.cancelled');
  if (run.state !== 'succeeded') return t('assist.failed', { reason: reason(run.code ?? run.state) });
  return null;
}

// The summary of the ticket (rule 1): a preview of state and open points with their sources, to copy; nothing is stored.
export function TicketAiSummary({ project, ticket }: { readonly project: Project; readonly ticket: Ticket }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const problem = useAssistProblem();
  const reason = useAiReason();
  const label = useSourceLabel();
  const options = useAssistOptions(project, ticket);
  const { phase, start, cancel } = useAssistRun();
  const [notice, setNotice] = useState<Notice | null>(null);
  if (options === null || !options.summary) return null;

  const run = phase.kind === 'done' ? phase.run : null;
  const content = run?.state === 'succeeded' && !run.invalid && run.content ? run.content as Record<string, unknown> : null;
  const state = content ? points(content.state) : [];
  const open = content ? points(content.open) : [];
  const sources = phase.kind === 'done' || phase.kind === 'running' ? phase.started.sources : [];

  function plain(): string {
    const line = (point: Point) => {
      const refs = point.sources.map((id) => sources.find((s) => s.id === id)).filter((s): s is AssistSource => s !== undefined)
        .map((s) => `${s.id}: ${label(s)}`);
      return `- ${point.text}${refs.length > 0 ? ` (${refs.join('; ')})` : ''}`;
    };
    return [t('assist.state'), ...state.map(line), '', t('assist.open'), ...(open.length > 0 ? open.map(line) : [`- ${t('assist.noOpen')}`])].join('\n');
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(plain());
      setNotice({ kind: 'success', text: t('assist.copied') });
    } catch {
      setNotice({ kind: 'info', text: t('assist.copyFailed') });
    }
  }

  return (
    <section aria-labelledby="ticket-ai-summary" className="panel" data-testid="ticket-ai-summary">
      <h2 id="ticket-ai-summary">{t('assist.summaryTitle')}</h2>
      <p className="hint">{t('assist.summaryHint')}</p>
      <p className="actions">
        <button type="button" disabled={phase.kind === 'running'}
          onClick={() => { setNotice(null); void start(() => startTicketSummary(api, project.id, ticket.number, language === 'en' ? 'en' : 'de')); }}>
          {t(content ? 'assist.summarizeAgain' : 'assist.summarize')}
        </button>
        {phase.kind === 'running' && <button type="button" className="secondary" onClick={() => void cancel()}>{t('assist.cancel')}</button>}
      </p>
      {phase.kind === 'running' && <p role="status" className="hint">{t('assist.running')}</p>}
      {phase.kind === 'problem' && <NoticeView notice={{ kind: 'info', text: problem(phase.problem) }} />}
      {run && runText(run, reason, t) !== null && <p role="status" className="mode-notice" data-testid="assist-result">{runText(run, reason, t)}</p>}
      {content && (
        <div data-testid="assist-summary" role="status">
          <p className="hint">{t('assist.preview')}{run?.truncated ? ` ${t('ai.truncated')}` : ''}</p>
          <h3>{t('assist.state')}</h3>
          <ul data-testid="assist-state">
            {state.map((point, index) => <li key={index}>{point.text} <SourceLinks ids={point.sources} sources={sources} /></li>)}
          </ul>
          <h3>{t('assist.open')}</h3>
          {open.length === 0 ? <p className="hint">{t('assist.noOpen')}</p> : (
            <ul data-testid="assist-open">
              {open.map((point, index) => <li key={index}>{point.text} <SourceLinks ids={point.sources} sources={sources} /></li>)}
            </ul>
          )}
          <p className="actions"><button type="button" className="secondary" onClick={() => void copy()}>{t('assist.copy')}</button></p>
          <NoticeView notice={notice} />
        </div>
      )}
    </section>
  );
}

// The draft of an answer (rule 2): an optional short goal, then the text goes into the field of the answer. A text the person
// already wrote is replaced only on request. Questions and notes are for the person and never part of the answer.
export function AiDraft({ project, ticket, channel, body, onDraft }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly channel: AssistChannel;
  readonly body: string;
  readonly onDraft: (text: string) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAssistProblem();
  const reason = useAiReason();
  const options = useAssistOptions(project, ticket);
  const { phase, start, cancel, reset } = useAssistRun();
  const [goal, setGoal] = useState('');
  const [pending, setPending] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  if (options === null || !options.replyDraft) return null;

  async function draft() {
    setNotice(null);
    setPending(null);
    const trimmed = goal.trim();
    const result = await start(() => startReplyDraft(api, project.id, ticket.number, channel, trimmed === '' ? null : trimmed));
    if (result === null) return;
    const content = result.run.state === 'succeeded' && !result.run.invalid ? result.run.content as Record<string, unknown> | null : null;
    const text = content && typeof content.body === 'string' ? content.body : null;
    if (text === null) return;
    if (body.trim() === '') {
      onDraft(text);
      setNotice({ kind: 'success', text: t('assist.draftInserted') });
    } else {
      setPending(text);
    }
  }

  function keys(event: KeyboardEvent<HTMLDivElement>) {
    // Enter in the goal starts the draft, never the check or the sending of the answer around it.
    if (event.key === 'Enter' && (event.target as HTMLElement).tagName === 'INPUT') {
      event.preventDefault();
      if (phase.kind !== 'running') void draft();
    }
  }

  const run = phase.kind === 'done' ? phase.run : null;
  const content = run?.state === 'succeeded' && !run.invalid && run.content ? run.content as Record<string, unknown> : null;
  const questions = content && Array.isArray(content.questions) ? content.questions.filter((q): q is string => typeof q === 'string') : [];
  const notes = content && Array.isArray(content.notes) ? content.notes.filter((n): n is string => typeof n === 'string') : [];

  return (
    <div className="assist-draft" data-testid={`assist-draft-${channel}`} onKeyDown={keys}>
      <TextField label={t('assist.goal')} hint={t('assist.goalHint')} value={goal} onChange={setGoal} maxLength={500} />
      <p className="actions">
        <button type="button" className="secondary" disabled={phase.kind === 'running'} onClick={() => void draft()}>{t('assist.draft')}</button>
        {phase.kind === 'running' && <button type="button" className="secondary" onClick={() => void cancel()}>{t('assist.cancel')}</button>}
      </p>
      {phase.kind === 'running' && <p role="status" className="hint">{t('assist.running')}</p>}
      {phase.kind === 'problem' && <NoticeView notice={{ kind: 'info', text: problem(phase.problem) }} />}
      {run && runText(run, reason, t) !== null && <p role="status" className="mode-notice" data-testid="assist-result">{runText(run, reason, t)}</p>}
      {pending !== null && (
        <p className="actions" data-testid="assist-replace">
          <span className="hint">{t('assist.draftReady')}</span>{' '}
          <button type="button" className="secondary" onClick={() => {
            onDraft(pending);
            setPending(null);
            setNotice({ kind: 'success', text: t('assist.draftInserted') });
          }}>{t('assist.replace')}</button>
          <button type="button" className="secondary" onClick={() => { setPending(null); reset(); }}>{t('assist.discard')}</button>
        </p>
      )}
      <NoticeView notice={notice} />
      {(questions.length > 0 || notes.length > 0) && (
        <div className="hint" data-testid="assist-notes">
          <p>{t('assist.forYou')}</p>
          <ul>
            {questions.map((question, index) => <li key={`q${index}`}>{t('assist.question', { text: question })}</li>)}
            {notes.map((note, index) => <li key={`n${index}`}>{note}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}
