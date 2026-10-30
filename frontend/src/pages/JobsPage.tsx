import { useEffect, useRef, useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import { cancelJob, JOB_STATES, JOBS_PAGE, listJobs, readJobSummary, retryJob, type Job, type JobState, type JobSummary } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { EmptyText, NoticeView, PageHead, SelectField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';

export const JOBS_RIGHT = 'jobs.manage';

// The background jobs for the operation (TASK-054): the state of the queue, the jobs page by page, queuing a failed job again
// and cancelling a waiting one. The server shows no payload and no error message, only a code and the reference of the log.
export function JobsPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [summary, setSummary] = useState<JobSummary | null>(null);
  const [summaryProblem, setSummaryProblem] = useState<ProblemInfo | null>(null);
  const [filter, setFilter] = useState<JobState | 'all'>('all');
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [problem, setProblem] = useState<ProblemInfo | null>(null);
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  // The waiting job whose cancellation waits for the confirmation.
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [round, setRound] = useState(0);
  // Every request counts up its own number; only the answer of the request started last is shown, so a late answer of an
  // earlier filter, an earlier refresh or an earlier page never replaces a newer one (TASK-065, TASK-066).
  const summaryRequest = useRef(0);
  const listRequest = useRef(0);

  async function loadSummary() {
    const request = ++summaryRequest.current;
    const result = await readJobSummary(api);
    if (request !== summaryRequest.current) return;
    if (result.kind !== 'ok') return setSummaryProblem(result.problem);
    setSummaryProblem(null);
    setSummary(result.data.value);
  }

  async function load(before: number | null) {
    const request = ++listRequest.current;
    setBusy(true);
    const result = await listJobs(api, filter === 'all' ? null : filter, before);
    if (request !== listRequest.current) return;
    setBusy(false);
    if (result.kind !== 'ok') return setProblem(result.problem);
    setProblem(null);
    setJobs((current) => (before === null || current === null ? result.data.value : [...current, ...result.data.value]));
    setMore(result.data.value.length === JOBS_PAGE);
  }

  useEffect(() => {
    void loadSummary();
    void load(null);
  }, [api, filter, round]);

  const refresh = () => {
    setCancelling(null);
    setRound((value) => value + 1);
  };

  async function act(job: Job, action: 'retry' | 'cancel') {
    setNotice(null);
    setCancelling(null);
    const result = action === 'retry' ? await retryJob(api, job.id) : await cancelJob(api, job.id);
    if (result.kind !== 'ok') {
      setNotice({ kind: 'info', text: text(result.problem, { 'job.state_conflict': 'jobs.error.stateConflict', 'job.not_found': 'jobs.error.notFound' }) });
      return refresh();
    }
    setNotice({ kind: 'success', text: t(action === 'retry' ? 'jobs.retried' : 'jobs.cancelled') });
    refresh();
  }

  const known = (key: string): key is MessageKey => Object.hasOwn(de, key);
  const kindText = (kind: string) => (known(`jobs.kind.${kind}`) ? t(`jobs.kind.${kind}` as MessageKey) : kind);
  const codeText = (code: string) => (known(`jobs.code.${code}`) ? t(`jobs.code.${code}` as MessageKey) : code);
  const stateText = (state: JobState) => t(`jobs.state.${state}` as MessageKey);

  return (
    <>
      <PageHead title={t('jobs.title')} intro={t('jobs.intro')}>
        <button type="button" className="secondary" onClick={refresh}>{t('jobs.refresh')}</button>
      </PageHead>
      <section aria-labelledby="jobs-summary" className="panel" data-testid="jobs-summary">
        <h2 id="jobs-summary">{t('jobs.summary')}</h2>
        {summaryProblem !== null ? <ProblemView problem={summaryProblem} onAction={refresh} actionLabel="states.retry" />
          : summary === null ? <LoadingView /> : (
          <>
            <p role="status" className={summary.backlog ? 'mode-notice' : 'success'} data-testid="jobs-health">
              {t(summary.backlog ? 'jobs.backlog' : 'jobs.healthy')}
            </p>
            <ul className="job-counts">
              {JOB_STATES.map((state) => (
                <li key={state} data-state={state}><span>{stateText(state)}</span> <strong>{summary.states[state]}</strong></li>
              ))}
            </ul>
            <p className="hint">{t('jobs.workers', { count: summary.activeWorkers })}</p>
            {summary.oldestDueSeconds !== null && summary.oldestDueSeconds >= 60 && (
              <p className="hint">{t('jobs.oldestDue', { minutes: Math.floor(summary.oldestDueSeconds / 60) })}</p>
            )}
          </>
        )}
      </section>
      <NoticeView notice={notice} />
      <SelectField label={t('jobs.filter')} value={filter} onChange={(value) => setFilter(value as JobState | 'all')}
        options={[{ value: 'all', label: t('jobs.filter.all') }, ...JOB_STATES.map((state) => ({ value: state, label: stateText(state) }))]} />
      {problem !== null ? <ProblemView problem={problem} onAction={refresh} actionLabel="states.retry" />
        : jobs === null ? <LoadingView />
        : jobs.length === 0 ? <EmptyText text={t('jobs.empty')} /> : (
        <>
          <div className="table-scroll">
            <table data-testid="job-table">
              <thead>
                <tr>
                  <th scope="col">{t('jobs.kind')}</th><th scope="col">{t('jobs.stateColumn')}</th><th scope="col">{t('jobs.attempts')}</th>
                  <th scope="col">{t('jobs.times')}</th><th scope="col">{t('jobs.error')}</th><th scope="col">{t('jobs.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id} data-job={job.id}>
                    <td>
                      <span>{kindText(job.kind)}</span>
                      {job.installation && <p className="hint">{t('jobs.installation')}</p>}
                    </td>
                    <td>{stateText(job.state)}</td>
                    <td>{t('jobs.attemptsOf', { attempts: job.attempts, max: job.maxAttempts })}</td>
                    <td>
                      <p className="hint">{t('jobs.created', { date: formatDateTime(job.createdAt, language) })}</p>
                      {job.state === 'queued' && <p className="hint">{t('jobs.runAt', { date: formatDateTime(job.runAt, language) })}</p>}
                      {job.finishedAt && <p className="hint">{t('jobs.finished', { date: formatDateTime(job.finishedAt, language) })}</p>}
                    </td>
                    <td>
                      {job.errorCode && <p>{codeText(job.errorCode)}</p>}
                      {job.errorReference && <p className="hint"><code>{t('jobs.reference', { reference: job.errorReference })}</code></p>}
                    </td>
                    <td className="row-actions">
                      {(job.state === 'failed' || job.state === 'cancelled') && (
                        <button type="button" className="secondary" onClick={() => void act(job, 'retry')}>{t('jobs.retry')}</button>
                      )}
                      {job.state === 'queued' && cancelling !== job.id && (
                        <button type="button" className="secondary" onClick={() => setCancelling(job.id)}>{t('jobs.cancel')}</button>
                      )}
                      {job.state === 'queued' && cancelling === job.id && (
                        <span role="group" aria-label={t('jobs.cancelAsk')} className="inline-form">
                          <span className="hint">{t('jobs.cancelAsk')}</span>
                          <button type="button" onClick={() => void act(job, 'cancel')}>{t('jobs.cancelConfirm')}</button>
                          <button type="button" className="secondary" onClick={() => setCancelling(null)}>{t('jobs.keep')}</button>
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {more
            ? <p className="actions"><button type="button" className="secondary" disabled={busy}
                onClick={() => void load(jobs[jobs.length - 1]?.position ?? null)}>{t('jobs.more')}</button></p>
            : <p className="hint">{t('jobs.end')}</p>}
        </>
      )}
    </>
  );
}
