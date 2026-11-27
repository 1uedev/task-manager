import { useEffect, useRef, useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import {
  readWikiAiResult, readWikiAssist, startWikiRevision, type WikiDocument, type WikiLanguage, type WikiRevisionGoal, type WikiRevisionResult,
  type WikiRevisionStarted,
} from '../api/product.ts';
import { NoticeView, SelectField, TextField, type Notice } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { useAiProblem, useAiReason } from './AiPage.tsx';
import { runText, usable, useAssistRun } from './assistRun.ts';
import { blocksOf, diffBlocks, type DocNode } from './wikiDiff.ts';
import { DiffView } from './WikiDiffView.tsx';

// The AI revision of a wiki draft (TASK-127, rule 2): a section or the whole version of the saved draft with a goal. The result
// is shown as a comparison of the section before and after; taking it over only puts the whole document the server assembled
// into the editor, which then has an unsaved change. Saving works as before, publishing stays a separate action.

const GOALS: readonly WikiRevisionGoal[] = ['shorter', 'clearer', 'factual', 'custom'];
const PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'assist.no_sources': 'assist.wiki.empty',
  'assist.not_ready': 'assist.running',
  'wiki.section_changed': 'assist.wiki.stale',
  'wiki.version_conflict': 'assist.wiki.stale',
  'wiki.page_archived': 'wiki.error.archived',
};

function useRevisionProblem(): (problem: ProblemInfo) => string {
  const { t } = useI18n();
  const ai = useAiProblem();
  return (problem) => {
    const key = problem.code !== null ? PROBLEMS[problem.code] : undefined;
    return key ? t(key) : ai(problem);
  };
}

const plain = (node: DocNode): string => (node.text ?? '') + (node.content ?? []).map(plain).join('');

/** The headings at the top of a document with their number as the server counts the sections. */
export function sectionsOf(document: WikiDocument): { readonly section: number; readonly heading: string }[] {
  const blocks = ((document as DocNode).content ?? []).filter((block) => block.type === 'heading');
  return blocks.map((block, index) => ({ section: index + 1, heading: plain(block).trim() }));
}

export function WikiAiRevision({ pageId, language, base, dirty, blocked, onTakeOver }: {
  readonly pageId: string;
  readonly language: WikiLanguage;
  /** The saved draft the editor shows; the revision works on it. */
  readonly base: { readonly revision: number; readonly content: WikiDocument };
  readonly dirty: boolean;
  /** A conflict or a running save. */
  readonly blocked: boolean;
  readonly onTakeOver: (document: WikiDocument) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useRevisionProblem();
  const reason = useAiReason();
  const { phase, start, cancel, reset } = useAssistRun<WikiRevisionStarted>();
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [section, setSection] = useState('');
  const [goal, setGoal] = useState<WikiRevisionGoal>('shorter');
  const [own, setOwn] = useState('');
  const [ownError, setOwnError] = useState<string | null>(null);
  const [result, setResult] = useState<WikiRevisionResult | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const round = useRef(0);
  useEffect(() => {
    let alive = true;
    setAllowed(null);
    void readWikiAssist(api, pageId, language).then((answer) => {
      if (alive) setAllowed(answer.kind === 'ok' && answer.data.value.revision);
    });
    return () => {
      alive = false;
      round.current += 1;
    };
  }, [api, pageId, language]);
  if (allowed !== true) return null;

  const sections = sectionsOf(base.content);
  const chosen = section === '' ? null : Number(section);
  const run = phase.kind === 'done' ? phase.run : null;

  async function begin() {
    setNotice(null);
    setResult(null);
    if (goal === 'custom' && own.trim() === '') return setOwnError(t('assist.wiki.ownMissing'));
    setOwnError(null);
    const mine = ++round.current;
    const done = await start(() => startWikiRevision(api, pageId, language, base.revision, chosen, goal, goal === 'custom' ? own.trim() : null));
    if (done === null || mine !== round.current || usable(done.run) === null) return;
    const read = await readWikiAiResult(api, pageId, language, done.started.runId);
    if (mine !== round.current) return;
    if (read.kind !== 'ok') return setNotice({ kind: 'info', text: problem(read.problem) });
    setResult(read.data.value);
  }

  // The server reads the suggestion once more with every check and assembles it into the draft as it is now; only a result for
  // exactly the revision the editor shows, without own unsaved input, goes into the editor.
  async function takeOver() {
    if (phase.kind !== 'done' || dirty || blocked) return;
    setNotice(null);
    const mine = ++round.current;
    const read = await readWikiAiResult(api, pageId, language, phase.started.runId);
    if (mine !== round.current) return;
    if (read.kind !== 'ok') {
      setResult(null);
      return setNotice({ kind: 'info', text: problem(read.problem) });
    }
    if (read.data.value.revision !== base.revision) {
      setResult(null);
      return setNotice({ kind: 'info', text: t('assist.wiki.stale') });
    }
    onTakeOver(read.data.value.document as unknown as WikiDocument);
    setResult(null);
    reset();
    setNotice({ kind: 'success', text: t('assist.wiki.taken') });
  }

  function discard() {
    round.current += 1;
    setResult(null);
    setNotice(null);
    reset();
  }

  const current = result !== null && result.revision === base.revision;
  return (
    <section aria-labelledby="wiki-ai-title" className="card stack" data-testid="wiki-ai">
      <h3 id="wiki-ai-title">{t('assist.wiki.title')}</h3>
      <p className="hint">{t('assist.wiki.hint')}</p>
      <SelectField label={t('assist.wiki.section')} value={section} onChange={setSection} disabled={phase.kind === 'running'}
        options={[{ value: '', label: t('assist.wiki.whole') },
          ...sections.map((item) => ({ value: String(item.section), label: t('assist.wiki.sectionOption', { heading: item.heading }) }))]} />
      <SelectField label={t('assist.wiki.goal')} value={goal} onChange={(value) => setGoal(value as WikiRevisionGoal)} disabled={phase.kind === 'running'}
        options={GOALS.map((value) => ({ value, label: t(`assist.wiki.goal.${value}` as MessageKey) }))} />
      {goal === 'custom' && (
        <TextField label={t('assist.wiki.own')} hint={t('assist.wiki.ownHint')} value={own} onChange={setOwn} maxLength={500} error={ownError} />
      )}
      {dirty && <p className="hint" data-testid="wiki-ai-dirty">{t('assist.wiki.dirty')}</p>}
      <p className="actions">
        <button type="button" className="secondary" disabled={phase.kind === 'running' || dirty || blocked} data-testid="wiki-ai-start"
          onClick={() => void begin()}>{t('assist.wiki.start')}</button>
        {phase.kind === 'running' && <button type="button" className="secondary" onClick={() => void cancel()}>{t('assist.cancel')}</button>}
      </p>
      {phase.kind === 'running' && <p role="status" className="hint">{t('assist.running')}</p>}
      {phase.kind === 'problem' && <NoticeView notice={{ kind: 'info', text: problem(phase.problem) }} />}
      {run && runText(run, reason, t) !== null && <p role="status" className="mode-notice" data-testid="assist-result">{runText(run, reason, t)}</p>}
      {result !== null && (
        <div className="stack" data-testid="wiki-ai-result" role="status">
          <p className="hint">{t('assist.preview')}</p>
          <p className="hint">{t('assist.wiki.compare', { part: result.heading === null ? t('assist.wiki.whole') : result.heading })}</p>
          <div lang={language}>
            <DiffView entries={diffBlocks(blocksOf(result.original as DocNode), blocksOf(result.revised as DocNode))} testId="wiki-ai-diff" lang={language} />
          </div>
          {result.notes.length > 0 && (
            <div className="hint" data-testid="wiki-ai-notes">
              <p>{t('assist.wiki.notes')}</p>
              <ul>{result.notes.map((note, index) => <li key={index}>{note}</li>)}</ul>
            </div>
          )}
          {!current && <p className="hint">{t('assist.wiki.stale')}</p>}
          <p className="actions">
            <button type="button" disabled={dirty || blocked || !current} data-testid="wiki-ai-take" onClick={() => void takeOver()}>{t('assist.wiki.takeOver')}</button>
            <button type="button" className="secondary" onClick={discard}>{t('assist.wiki.discard')}</button>
          </p>
        </div>
      )}
      <NoticeView notice={notice} />
    </section>
  );
}
