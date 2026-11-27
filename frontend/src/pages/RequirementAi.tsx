import { useEffect, useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  readRequirementAssist, startRequirementReview, type Requirement, type RequirementReviewSource, type RequirementReviewStarted,
} from '../api/product.ts';
import { NoticeView, type Notice } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { useAiProblem, useAiReason } from './AiPage.tsx';
import { runText, usable, useAssistRun } from './assistRun.ts';
import { findingsOf, proposalFor, type Finding, type Proposal } from './requirementProposal.ts';

// The AI review of a requirement (TASK-127, rule 1): findings on the shown revision with passage, reason and proposal. A
// proposal goes only into the editor, after the server confirmed the suggestion once more; the AI approves nothing and changes
// no status. Every text of the model is shown as text.

const PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'assist.no_sources': 'assist.noSources',
  'requirement.revision_not_found': 'assist.review.revisionGone',
};

function useReviewProblem(): (problem: ProblemInfo) => string {
  const { t } = useI18n();
  const ai = useAiProblem();
  return (problem) => {
    const key = problem.code !== null ? PROBLEMS[problem.code] : undefined;
    return key ? t(key) : ai(problem);
  };
}

const kindKey = (kind: string): MessageKey => `assist.review.kind.${kind}` as MessageKey;

function useSourceLabel(): (source: RequirementReviewSource) => string {
  const { t } = useI18n();
  return (source) => (source.part === 'criterion' && source.criterion !== null
    ? t('assist.review.source.criterion', { criterion: source.criterion })
    : t(`assist.review.source.${source.part}` as MessageKey));
}

const writable = (requirement: Requirement, field: string) => (requirement.access.writableFields as readonly string[]).includes(field);

export function RequirementAiReview({ requirement, onTakeOver }: {
  readonly requirement: Requirement;
  readonly onTakeOver: (proposal: Proposal) => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const problem = useReviewProblem();
  const reason = useAiReason();
  const label = useSourceLabel();
  const { phase, start, cancel, recheck } = useAssistRun<RequirementReviewStarted>();
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  useEffect(() => {
    let alive = true;
    setAllowed(null);
    void readRequirementAssist(api, requirement.id, requirement.revision).then((result) => {
      if (alive) setAllowed(result.kind === 'ok' && result.data.value.review);
    });
    return () => {
      alive = false;
    };
  }, [api, requirement.id, requirement.revision]);
  if (allowed !== true) return null;

  const run = phase.kind === 'done' ? phase.run : null;
  const content = usable(run);
  const findings = findingsOf(content);
  const sources = phase.kind === 'done' || phase.kind === 'running' ? phase.started.sources : [];
  // A proposal goes into the editor only for the current revision the person may change.
  const editable = requirement.access.edit && requirement.status !== 'discarded' && phase.kind === 'done'
    && phase.started.revision === requirement.revision;
  const place = (finding: Finding) => (editable ? proposalFor(finding, requirement.content, (field) => writable(requirement, field)) : null);

  async function takeOver(index: number) {
    setNotice(null);
    // Only what the server still confirms for the revision as it is now goes into the editor (prüffall A09).
    const fresh = usable(await recheck());
    if (fresh === null) return;
    const finding = findingsOf(fresh)[index];
    const proposal = finding === undefined ? null : place(finding);
    if (proposal === null) return setNotice({ kind: 'info', text: t('assist.review.gone') });
    onTakeOver(proposal);
  }

  async function copy(finding: Finding) {
    setNotice(null);
    try {
      await navigator.clipboard.writeText(finding.suggestion ?? '');
      setNotice({ kind: 'success', text: t('assist.review.copied') });
    } catch {
      setNotice({ kind: 'info', text: t('assist.copyFailed') });
    }
  }

  return (
    <section aria-labelledby="requirement-ai-title" className="panel" data-testid="requirement-ai">
      <h2 id="requirement-ai-title">{t('assist.review.title')}</h2>
      <p className="hint">{t('assist.review.hint', { revision: requirement.revision })}</p>
      <p className="actions">
        <button type="button" disabled={phase.kind === 'running'} data-testid="requirement-ai-start"
          onClick={() => { setNotice(null); void start(() => startRequirementReview(api, requirement.id, requirement.revision, language === 'en' ? 'en' : 'de')); }}>
          {t(content ? 'assist.review.again' : 'assist.review.start')}
        </button>
        {phase.kind === 'running' && <button type="button" className="secondary" onClick={() => void cancel()}>{t('assist.cancel')}</button>}
      </p>
      {phase.kind === 'running' && <p role="status" className="hint">{t('assist.running')}</p>}
      {phase.kind === 'problem' && <NoticeView notice={{ kind: 'info', text: problem(phase.problem) }} />}
      {run && runText(run, reason, t) !== null && <p role="status" className="mode-notice" data-testid="assist-result">{runText(run, reason, t)}</p>}
      {content && (
        <div data-testid="requirement-ai-findings" role="status">
          <p className="hint">{t('assist.preview')}</p>
          {findings.length === 0 ? <p className="hint">{t('assist.review.none')}</p> : (
            <ol className="stack">
              {findings.map((finding, index) => {
                const proposal = place(finding);
                const shown = finding.sources.map((id) => sources.find((source) => source.id === id)).filter((s): s is RequirementReviewSource => s !== undefined);
                return (
                  <li key={index} className="card stack" data-testid="requirement-ai-finding" data-kind={finding.kind}>
                    <strong>{t(kindKey(finding.kind))}</strong>
                    <span data-testid="requirement-ai-location">{t('assist.review.location', { text: finding.location })}</span>
                    <span>{t('assist.review.reason', { text: finding.reason })}</span>
                    {finding.suggestion !== null && <span data-testid="requirement-ai-suggestion">{t('assist.review.suggestion', { text: finding.suggestion })}</span>}
                    {shown.length > 0 && <span className="hint">{t('assist.review.sources', { sources: shown.map((s) => `${s.id}: ${label(s)}`).join('; ') })}</span>}
                    {finding.suggestion !== null && (
                      <span className="actions">
                        {proposal !== null && (
                          <button type="button" data-testid="requirement-ai-take" onClick={() => void takeOver(index)}>{t('assist.review.takeOver')}</button>
                        )}
                        <button type="button" className="secondary" onClick={() => void copy(finding)}>{t('assist.review.copy')}</button>
                        {proposal === null && editable && <span className="hint" data-testid="requirement-ai-copy-only">{t('assist.review.onlyCopy')}</span>}
                      </span>
                    )}
                  </li>
                );
              })}
            </ol>
          )}
          <NoticeView notice={notice} />
        </div>
      )}
    </section>
  );
}
