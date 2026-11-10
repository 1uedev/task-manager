// The comparison of two documents by blocks as a list, shared by the wiki and the requirements (F3 of the TASK-096 review):
// a changed block names its link targets and images when they differ, a pure format change is said in words.
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { Block, DiffEntry } from './wikiDiff.ts';

function BlockText({ block, refs }: { readonly block: Block; readonly refs: boolean }) {
  const { t } = useI18n();
  return (
    <>
      {block.text}
      {refs && block.refs.length > 0 && (
        <span className="diff-refs" data-testid="wiki-diff-refs">{t('wiki.diff.refs', { refs: block.refs.join(', ') })}</span>
      )}
    </>
  );
}

/** `lang` marks the compared texts when they are in another language than the interface (H2 of the TASK-094 review). */
export function DiffView({ entries, testId, lang, changesOnly = false }: {
  readonly entries: readonly DiffEntry[];
  readonly testId: string;
  readonly lang?: string;
  /** Leaves out the unchanged blocks, as the comparison of requirements does. */
  readonly changesOnly?: boolean;
}) {
  const { t } = useI18n();
  const changes = entries.filter((entry) => entry.op !== 'same');
  if (changes.length === 0) return <p className="hint" data-testid={`${testId}-none`}>{t('wiki.diff.none')}</p>;
  return (
    <ol className="wiki-diff" data-testid={testId}>
      {entries.map((entry, index) => {
        if (entry.op === 'same') return changesOnly ? null : <li key={index} className="diff-same" lang={lang}>{entry.block.text || ' '}</li>;
        if (entry.op === 'changed') {
          // Same text with other targets, images or marks: the targets are named, a pure format change is said in words.
          const refs = entry.before.refs.join(' ') !== entry.after.refs.join(' ');
          const formatOnly = !refs && entry.before.text === entry.after.text;
          return (
            <li key={index} className="diff-changed" data-op="changed">
              <span className="diff-label">{t('wiki.diff.changed')}</span>
              {formatOnly && <span className="diff-refs" data-testid="wiki-diff-format">{t('wiki.diff.format')}</span>}
              <del lang={lang}><BlockText block={entry.before} refs={refs} /></del>
              <ins lang={lang}><BlockText block={entry.after} refs={refs} /></ins>
            </li>
          );
        }
        return (
          <li key={index} className={`diff-${entry.op}`} data-op={entry.op}>
            <span className="diff-label">{t(entry.op === 'added' ? 'wiki.diff.added' : 'wiki.diff.removed')}</span>
            {entry.op === 'added'
              ? <ins lang={lang}><BlockText block={entry.block} refs /></ins>
              : <del lang={lang}><BlockText block={entry.block} refs /></del>}
          </li>
        );
      })}
    </ol>
  );
}
