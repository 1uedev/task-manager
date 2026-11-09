import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import type { Editor } from '@tiptap/react';
import {
  archiveWikiPage, createWikiPage, listWikiProjects, moveWikiPage, publishWikiPage, readWikiHistory, readWikiPage, readWikiRevision, readWikiTree,
  restoreWikiRevision, saveWikiDraft, unarchiveWikiPage, uploadWikiImage, withdrawWikiPage,
  type WikiDocument, type WikiPage as Page, type WikiProject, type WikiRevisionContent, type WikiTree, type WikiTreeItem,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { hrefFor, navigate } from '../app/route.ts';
import { useAccountName } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { blocksOf, diffBlocks, type DiffEntry } from './wikiDiff.ts';
import { WikiContent } from './WikiContent.tsx';
import { useWikiEditor, WikiEditorContent, WikiToolbar } from './WikiEditor.tsx';

// The wiki of a project (TASK-093, DEC-123). The server decides who sees what: readers get the published revision only,
// people with `wiki.edit` the shared draft and the history, people with `wiki.publish` publication, archive and restore.
// The interface offers only what `access` allows and shows a refusal of the server as it comes.

const MAX_DEPTH = 6;

/** Whether a page at this depth may still get a subpage. */
const roomBelow = (depth: number) => MAX_DEPTH - depth > 0;

const VIOLATION: Record<string, MessageKey> = {
  'title.required': 'wiki.error.title', 'title.too_long': 'wiki.error.titleLong', 'title.invalid': 'wiki.error.title',
  'content.too_large': 'wiki.error.tooLarge', 'content.too_many_images': 'wiki.error.tooManyImages', 'content.too_deep': 'wiki.error.tooDeepContent',
  'content.invalid': 'wiki.error.content', 'content.unsupported': 'wiki.error.content', 'image.external': 'wiki.error.externalImage',
  'image.unknown': 'wiki.error.unknownImage', 'link.invalid': 'wiki.error.link', 'link.unknown_page': 'wiki.error.linkPage',
  'parent.unknown': 'wiki.error.parent', 'parent.too_deep': 'wiki.error.tooDeep', 'parent.cycle': 'wiki.error.cycle',
};

const SPECIFIC: Record<string, MessageKey> = {
  'wiki.version_conflict': 'wiki.error.conflict', 'wiki.busy': 'wiki.error.busy', 'wiki.page_archived': 'wiki.error.archived',
  'wiki.page_not_archived': 'wiki.error.notArchived', 'wiki.not_published': 'wiki.error.notPublished', 'wiki.page_not_found': 'wiki.error.notFound',
  'auth.forbidden': 'wiki.error.forbidden', 'image.too_large': 'wiki.error.imageTooLarge', 'image.type_unsupported': 'wiki.error.imageType',
  'image.empty': 'wiki.error.imageEmpty', 'image.storage_unavailable': 'wiki.error.imageStorage',
};

function useWikiProblem() {
  const { t } = useI18n();
  const text = useProblemText();
  return (problem: ProblemInfo) => {
    const known = (problem.violations ?? []).map((violation) => VIOLATION[violation]).filter((key): key is MessageKey => key !== undefined);
    if (problem.status === 400 && known.length > 0) return [...new Set(known)].map((key) => t(key)).join(' ');
    return text(problem, SPECIFIC);
  };
}

const Content = WikiContent;

// ---------------------------------------------------------------- the tree

interface Node {
  readonly item: WikiTreeItem;
  readonly children: Node[];
}

function buildTree(items: readonly WikiTreeItem[], language: string): Node[] {
  const byParent = new Map<string | null, WikiTreeItem[]>();
  const ids = new Set(items.map((item) => item.id));
  for (const item of items) {
    const parent = item.parentId !== null && ids.has(item.parentId) ? item.parentId : null;
    byParent.set(parent, [...(byParent.get(parent) ?? []), item]);
  }
  const collator = new Intl.Collator(language, { sensitivity: 'base', numeric: true });
  const build = (parent: string | null): Node[] => (byParent.get(parent) ?? [])
    .sort((a, b) => collator.compare(a.title, b.title) || a.id.localeCompare(b.id))
    .map((item) => ({ item, children: build(item.id) }));
  return build(null);
}

function flatten(nodes: readonly Node[], depth = 1): { readonly item: WikiTreeItem; readonly depth: number }[] {
  return nodes.flatMap((node) => [{ item: node.item, depth }, ...flatten(node.children, depth + 1)]);
}

function TreeView({ nodes, projectId, current, drafts }: {
  readonly nodes: readonly Node[];
  readonly projectId: string;
  readonly current: string | null;
  readonly drafts: boolean;
}) {
  const { t } = useI18n();
  if (nodes.length === 0) return null;
  return (
    <ul className="wiki-tree-level">
      {nodes.map((node) => (
        <li key={node.item.id} data-testid="wiki-tree-item" data-page={node.item.id}>
          <a href={hrefFor({ name: 'wikiPage', projectId, pageId: node.item.id })} aria-current={node.item.id === current ? 'page' : undefined}>
            {node.item.title}
          </a>
          {drafts && !node.item.published && <span className="badge" data-testid="wiki-tree-draft"> {t('wiki.badge.draft')}</span>}
          {drafts && node.item.published && node.item.changed && <span className="badge" data-testid="wiki-tree-changed"> {t('wiki.badge.changed')}</span>}
          <TreeView nodes={node.children} projectId={projectId} current={current} drafts={drafts} />
        </li>
      ))}
    </ul>
  );
}

// ---------------------------------------------------------------- creating a page

function CreateForm({ projectId, tree, parentId, onCancel }: {
  readonly projectId: string;
  readonly tree: readonly { readonly item: WikiTreeItem; readonly depth: number }[];
  readonly parentId: string | null;
  readonly onCancel?: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useWikiProblem();
  const [title, setTitle] = useState('');
  const [parent, setParent] = useState(parentId ?? '');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (title.trim() === '') return setError(t('wiki.error.title'));
    setError(null);
    setBusy(true);
    const result = await createWikiPage(api, projectId, title, parent === '' ? null : parent);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    navigate({ name: 'wikiPage', projectId, pageId: result.data.value.id });
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)} noValidate data-testid="wiki-create">
      <h2>{t('wiki.create.title')}</h2>
      <TextField label={t('wiki.field.title')} value={title} onChange={setTitle} required maxLength={200} error={error} disabled={busy} />
      <SelectField label={t('wiki.field.parent')} value={parent} onChange={setParent} disabled={busy}
        options={[{ value: '', label: t('wiki.parent.top') },
          ...tree.filter((row) => roomBelow(row.depth)).map((row) => ({ value: row.item.id, label: `${'· '.repeat(row.depth - 1)}${row.item.title}` }))]} />
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" disabled={busy}>{t('wiki.create.submit')}</button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('wiki.cancel')}</button>}
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- the editor with save and conflict

function DiffView({ entries, testId }: { readonly entries: readonly DiffEntry[]; readonly testId: string }) {
  const { t } = useI18n();
  const changes = entries.filter((entry) => entry.op !== 'same');
  if (changes.length === 0) return <p className="hint" data-testid={`${testId}-none`}>{t('wiki.diff.none')}</p>;
  return (
    <ol className="wiki-diff" data-testid={testId}>
      {entries.map((entry, index) => {
        if (entry.op === 'same') return <li key={index} className="diff-same">{entry.block.text || ' '}</li>;
        if (entry.op === 'changed') {
          return (
            <li key={index} className="diff-changed" data-op="changed">
              <span className="diff-label">{t('wiki.diff.changed')}</span>
              <del>{entry.before.text}</del>
              <ins>{entry.after.text}</ins>
            </li>
          );
        }
        return (
          <li key={index} className={`diff-${entry.op}`} data-op={entry.op}>
            <span className="diff-label">{t(entry.op === 'added' ? 'wiki.diff.added' : 'wiki.diff.removed')}</span>
            {entry.op === 'added' ? <ins>{entry.block.text}</ins> : <del>{entry.block.text}</del>}
          </li>
        );
      })}
    </ol>
  );
}

function ImageForm({ page, editor, onClose }: { readonly page: Page; readonly editor: Editor; readonly onClose: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useWikiProblem();
  const [file, setFile] = useState<File | null>(null);
  const [alt, setAlt] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  // A form of its own would sit inside the form of the editor, so this is a group with a button.
  async function submit() {
    if (file === null) return setNotice({ kind: 'info', text: t('wiki.image.choose') });
    if (alt.trim() === '') return setNotice({ kind: 'info', text: t('wiki.image.altRequired') });
    setBusy(true);
    const result = await uploadWikiImage(api, page.id, file);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    editor.chain().focus().setImage({ src: result.data.value.src, alt: alt.trim() }).run();
    onClose();
  }

  return (
    <div className="card stack" role="group" aria-label={t('wiki.tool.image')} data-testid="wiki-image-form">
      <div className="field">
        <label htmlFor="wiki-image-file">{t('wiki.image.file')}</label>
        <p className="hint" id="wiki-image-hint">{t('wiki.image.hint')}</p>
        <input id="wiki-image-file" type="file" accept="image/png,image/jpeg,image/webp" aria-describedby="wiki-image-hint" disabled={busy}
          onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      </div>
      <TextField label={t('wiki.image.alt')} value={alt} onChange={setAlt} required maxLength={500} disabled={busy} hint={t('wiki.image.altHint')} />
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="button" onClick={() => void submit()} disabled={busy}>{t('wiki.image.insert')}</button>
        <button type="button" className="secondary" onClick={onClose} disabled={busy}>{t('wiki.cancel')}</button>
      </div>
    </div>
  );
}

const asDocument = (value: unknown) => value as WikiDocument;

function EditorPanel({ page, links, onSaved, onClose }: {
  readonly page: Page & { readonly draft: NonNullable<Page['draft']> };
  readonly links: readonly { readonly id: string; readonly title: string }[];
  readonly onSaved: (page: Page) => void;
  readonly onClose: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useWikiProblem();
  // The revision the next save builds on; after a conflict the person takes over the newer one explicitly.
  const [base, setBase] = useState(page.draft);
  const [title, setTitle] = useState(page.draft.title);
  const [titleError, setTitleError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [pasteNotice, setPasteNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [imageOpen, setImageOpen] = useState(false);
  const [conflict, setConflict] = useState<NonNullable<Page['draft']> | null>(null);
  const [version, setVersion] = useState(0);
  const editor = useWikiEditor(page.draft.content, true, (count) => setPasteNotice(t('wiki.paste.removed', { count })));
  const baseline = useRef<string>('');
  useEffect(() => {
    if (editor !== null) baseline.current = JSON.stringify(editor.getJSON());
  }, [editor]);
  useEffect(() => {
    if (editor === null) return;
    const update = () => setVersion((value) => value + 1);
    editor.on('update', update);
    return () => {
      editor.off('update', update);
    };
  }, [editor]);
  const dirty = useMemo(() => editor !== null && (title !== base.title || JSON.stringify(editor.getJSON()) !== baseline.current),
    [editor, title, base.title, version]);
  useUnsavedGuard(dirty);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (editor === null) return;
    if (title.trim() === '') return setTitleError(t('wiki.error.title'));
    setTitleError(null);
    setBusy(true);
    setNotice(null);
    const content = asDocument(editor.getJSON());
    const result = await saveWikiDraft(api, page.id, base.revision, title, content);
    if (result.kind === 'ok') {
      setBusy(false);
      const saved = result.data.value;
      if (saved.draft !== null) {
        setBase(saved.draft);
        setTitle(saved.draft.title);
        editor.commands.setContent(saved.draft.content, { emitUpdate: false });
        baseline.current = JSON.stringify(editor.getJSON());
        setVersion((value) => value + 1);
      }
      setConflict(null);
      setNotice({ kind: 'success', text: t('wiki.saved', { revision: saved.draft?.revision ?? base.revision }) });
      onSaved(saved);
      return;
    }
    if (result.problem.status === 412) {
      // Someone saved meanwhile. The own input stays; the newer draft appears beside it with the differences.
      const newer = await readWikiPage(api, page.id);
      setBusy(false);
      if (newer.kind === 'ok' && newer.data.value.draft !== null) {
        setConflict(newer.data.value.draft);
        return;
      }
      return setNotice({ kind: 'info', text: newer.kind === 'ok' ? problem(result.problem) : problem(newer.problem) });
    }
    setBusy(false);
    setNotice({ kind: 'info', text: problem(result.problem) });
  }

  function keepMine() {
    if (conflict === null) return;
    setBase(conflict);
    setConflict(null);
    setNotice({ kind: 'info', text: t('wiki.conflict.keptMine', { revision: conflict.revision }) });
  }

  function takeTheirs() {
    if (conflict === null || editor === null) return;
    editor.commands.setContent(conflict.content, { emitUpdate: false });
    baseline.current = JSON.stringify(editor.getJSON());
    setBase(conflict);
    setTitle(conflict.title);
    setConflict(null);
    setVersion((value) => value + 1);
    setNotice({ kind: 'info', text: t('wiki.conflict.tookTheirs', { revision: conflict.revision }) });
  }

  function close() {
    if (dirty && !globalThis.confirm(t('unsaved.confirm'))) return;
    onClose();
  }

  if (editor === null) return null;
  const mine = blocksOf(asDocument(editor.getJSON()));
  return (
    <form className="stack wiki-editor" onSubmit={(event) => void save(event)} noValidate data-testid="wiki-edit">
      <TextField label={t('wiki.field.title')} value={title} onChange={setTitle} required maxLength={200} error={titleError} disabled={busy} />
      <p className="hint" data-testid="wiki-save-state" data-dirty={dirty ? 'true' : 'false'}>
        {dirty ? t('wiki.state.unsaved') : t('wiki.state.saved', { revision: base.revision, time: formatDateTime(base.savedAt, language), person: name(base.savedBy) })}
      </p>
      <WikiToolbar editor={editor} pages={links} onImage={() => setImageOpen(true)} disabled={busy} />
      {imageOpen && <ImageForm page={page} editor={editor} onClose={() => setImageOpen(false)} />}
      {pasteNotice !== null && <p role="status" className="mode-notice" data-testid="wiki-paste-notice">{pasteNotice}</p>}
      <WikiEditorContent editor={editor} />
      {conflict !== null && (
        <section className="card stack wiki-conflict" data-testid="wiki-conflict" aria-labelledby="wiki-conflict-title">
          <h2 id="wiki-conflict-title">{t('wiki.conflict.title')}</h2>
          <p>{t('wiki.conflict.text', { revision: conflict.revision, person: name(conflict.savedBy), time: formatDateTime(conflict.savedAt, language) })}</p>
          {conflict.title !== title && <p data-testid="wiki-conflict-title-differs">{t('wiki.conflict.titleDiffers', { title: conflict.title })}</p>}
          <p className="hint">{t('wiki.conflict.legend')}</p>
          <DiffView entries={diffBlocks(mine, blocksOf(conflict.content))} testId="wiki-conflict-diff" />
          <details>
            <summary>{t('wiki.conflict.show')}</summary>
            <Content html={conflict.html} testId="wiki-conflict-newer" />
          </details>
          <div className="actions">
            <button type="button" onClick={keepMine} data-testid="wiki-conflict-keep">{t('wiki.conflict.keep')}</button>
            <button type="button" className="secondary" onClick={takeTheirs} data-testid="wiki-conflict-take">{t('wiki.conflict.take')}</button>
          </div>
        </section>
      )}
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" disabled={busy || conflict !== null || !dirty} data-testid="wiki-save">{t('wiki.save')}</button>
        <button type="button" className="secondary" onClick={close} disabled={busy}>{t('wiki.closeEditor')}</button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- publication

function PublishDialog({ page, projectName, onDone, onCancel }: {
  readonly page: Page & { readonly draft: NonNullable<Page['draft']> };
  readonly projectName: string;
  readonly onDone: (page: Page) => void;
  readonly onCancel: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useWikiProblem();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  useEffect(() => confirmRef.current?.focus(), []);

  async function publish() {
    setBusy(true);
    const result = await publishWikiPage(api, page.id, page.draft.revision);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    onDone(result.data.value);
  }

  return (
    <section className="card stack" role="dialog" aria-modal="false" aria-labelledby="wiki-publish-title" data-testid="wiki-publish-dialog">
      <h2 id="wiki-publish-title">{t('wiki.publish.title')}</h2>
      <dl className="facts">
        <dt>{t('wiki.publish.revision')}</dt>
        <dd data-testid="wiki-publish-revision">{t('wiki.publish.revisionValue', { revision: page.draft.revision, person: name(page.draft.savedBy),
          time: formatDateTime(page.draft.savedAt, language) })}</dd>
        <dt>{t('wiki.publish.language')}</dt>
        <dd>{t(page.language === 'en' ? 'language.en' : 'language.de')}</dd>
        <dt>{t('wiki.publish.readers')}</dt>
        <dd>{t('wiki.publish.readersValue', { project: projectName })}</dd>
      </dl>
      {page.published !== null && <p className="hint">{t('wiki.publish.replaces', { revision: page.published.revision })}</p>}
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="button" ref={confirmRef} onClick={() => void publish()} disabled={busy} data-testid="wiki-publish-confirm">
          {t('wiki.publish.confirm', { revision: page.draft.revision })}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('wiki.cancel')}</button>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- history

function HistoryPanel({ page, onRestored }: { readonly page: Page; readonly onRestored: (page: Page) => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useWikiProblem();
  const [history, retry] = useLoad(() => readWikiHistory(api, page.id), [api, page.id, page.draft?.revision, page.published?.revision]);
  const [shown, setShown] = useState<WikiRevisionContent | null>(null);
  const [left, setLeft] = useState('');
  const [right, setRight] = useState('');
  const [compared, setCompared] = useState<{ readonly left: number; readonly right: number; readonly entries: DiffEntry[] } | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  async function show(revision: number) {
    const result = await readWikiRevision(api, page.id, revision);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setShown(result.data.value);
  }

  async function compare(event: FormEvent) {
    event.preventDefault();
    if (left === '' || right === '') return;
    const [a, b] = await Promise.all([readWikiRevision(api, page.id, Number(left)), readWikiRevision(api, page.id, Number(right))]);
    if (a.kind !== 'ok') return setNotice({ kind: 'info', text: problem(a.problem) });
    if (b.kind !== 'ok') return setNotice({ kind: 'info', text: problem(b.problem) });
    setCompared({ left: Number(left), right: Number(right), entries: diffBlocks(blocksOf(a.data.value.content), blocksOf(b.data.value.content)) });
  }

  async function restore(revision: number) {
    if (page.draft === null) return;
    if (!globalThis.confirm(t('wiki.history.restoreConfirm', { revision }))) return;
    setBusy(true);
    const result = await restoreWikiRevision(api, page.id, revision, page.draft.revision);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setShown(null);
    setNotice({ kind: 'success', text: t('wiki.history.restored', { revision, draft: result.data.value.draft?.revision ?? revision }) });
    onRestored(result.data.value);
  }

  return (
    <section className="stack" data-testid="wiki-history" aria-labelledby="wiki-history-title">
      <h2 id="wiki-history-title">{t('wiki.history.title')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={history} retry={retry}>
        {(value) => {
          const options = value.revisions.map((revision) => ({ value: String(revision.revision), label: t('wiki.history.option', { revision: revision.revision }) }));
          return (
            <>
              <ul className="list">
                {value.revisions.map((revision) => (
                  <li key={revision.revision} className="card" data-testid="wiki-revision" data-revision={revision.revision}
                    data-published={revision.published ? 'true' : 'false'}>
                    <p>
                      <strong>{t('wiki.history.option', { revision: revision.revision })}</strong>
                      {' · '}{revision.title}{' · '}{name(revision.savedBy)}{' · '}{formatDateTime(revision.savedAt, language)}
                      {revision.published && <span className="badge" data-testid="wiki-revision-published"> {t('wiki.badge.published')}</span>}
                      {revision.revision === value.draftRevision && <span className="badge"> {t('wiki.badge.currentDraft')}</span>}
                      {revision.restoredFrom !== null && <span className="hint"> {t('wiki.history.restoredFrom', { revision: revision.restoredFrom })}</span>}
                    </p>
                    <div className="actions">
                      <button type="button" className="secondary" onClick={() => void show(revision.revision)}>{t('wiki.history.show')}</button>
                      {page.access.edit && revision.revision !== value.draftRevision && (
                        <button type="button" className="secondary" disabled={busy} onClick={() => void restore(revision.revision)}
                          data-testid="wiki-restore">{t('wiki.history.restore')}</button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              {shown !== null && (
                <section className="card stack" data-testid="wiki-revision-view">
                  <h3>{t('wiki.history.viewing', { revision: shown.revision, title: shown.title })}</h3>
                  <Content html={shown.html} testId="wiki-revision-content" />
                  <div className="actions"><button type="button" className="secondary" onClick={() => setShown(null)}>{t('wiki.history.hide')}</button></div>
                </section>
              )}
              {value.revisions.length > 1 && (
                <form className="card stack" onSubmit={(event) => void compare(event)} data-testid="wiki-compare">
                  <h3>{t('wiki.history.compare')}</h3>
                  <SelectField label={t('wiki.history.from')} value={left} onChange={setLeft} options={[{ value: '', label: t('wiki.history.pick') }, ...options]} />
                  <SelectField label={t('wiki.history.to')} value={right} onChange={setRight} options={[{ value: '', label: t('wiki.history.pick') }, ...options]} />
                  <div className="actions"><button type="submit" disabled={left === '' || right === ''}>{t('wiki.history.compareSubmit')}</button></div>
                  {compared !== null && (
                    <>
                      <p className="hint">{t('wiki.history.comparing', { from: compared.left, to: compared.right })}</p>
                      <DiffView entries={compared.entries} testId="wiki-compare-diff" />
                    </>
                  )}
                </form>
              )}
              <h3>{t('wiki.history.events')}</h3>
              <ul className="list" data-testid="wiki-events">
                {value.events.map((event, index) => (
                  <li key={index} data-kind={event.kind}>
                    {t(`wiki.event.${event.kind}` as MessageKey, { revision: event.revision ?? '' })}{' · '}{name(event.actorId)}{' · '}
                    {formatDateTime(event.occurredAt, language)}
                  </li>
                ))}
              </ul>
            </>
          );
        }}
      </LoadedView>
    </section>
  );
}

// ---------------------------------------------------------------- moving and archiving

function MoveForm({ page, tree, onDone, onCancel }: {
  readonly page: Page;
  readonly tree: readonly Node[];
  readonly onDone: (page: Page) => void;
  readonly onCancel: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useWikiProblem();
  const [parent, setParent] = useState(page.parentId ?? '');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  // A page cannot move below itself; the server checks depth and cycles again.
  const excluded = new Set<string>();
  const collect = (nodes: readonly Node[], inside: boolean) => {
    for (const node of nodes) {
      const below = inside || node.item.id === page.id;
      if (below) excluded.add(node.item.id);
      collect(node.children, below);
    }
  };
  collect(tree, false);
  const choices = flatten(tree).filter((row) => !excluded.has(row.item.id));

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    const result = await moveWikiPage(api, page.id, page.version, parent === '' ? null : parent);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)} data-testid="wiki-move">
      <SelectField label={t('wiki.field.parent')} value={parent} onChange={setParent} disabled={busy}
        options={[{ value: '', label: t('wiki.parent.top') }, ...choices.map((row) => ({ value: row.item.id, label: `${'· '.repeat(row.depth - 1)}${row.item.title}` }))]} />
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" disabled={busy}>{t('wiki.move.submit')}</button>
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('wiki.cancel')}</button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- one page

type PageCall = { (): ReturnType<typeof withdrawWikiPage> };

type Mode = 'read' | 'edit' | 'history' | 'publish' | 'move' | 'create';

function PageView({ pageId, projectId, projectName, tree, onTreeChanged }: {
  readonly pageId: string;
  readonly projectId: string;
  readonly projectName: string;
  readonly tree: WikiTree;
  readonly onTreeChanged: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const problem = useWikiProblem();
  const [loaded, retry, replace] = useLoad(() => readWikiPage(api, pageId), [api, pageId]);
  const [mode, setMode] = useState<Mode>('read');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const nodes = useMemo(() => buildTree(tree.pages, language), [tree.pages, language]);
  const flat = useMemo(() => flatten(nodes), [nodes]);
  const depth = flat.find((row) => row.item.id === pageId)?.depth ?? 1;

  function changed(page: Page, text: string | null, next: Mode = 'read') {
    replace({ value: page, etag: page.draft === null ? null : `"${page.draft.revision}"` });
    setMode(next);
    setNotice(text === null ? null : { kind: 'success', text });
    onTreeChanged();
  }

  async function act(run: PageCall, success: MessageKey, confirmKey: MessageKey | null) {
    if (confirmKey !== null && !globalThis.confirm(t(confirmKey))) return;
    setBusy(true);
    const result = await run();
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    changed(result.data.value, t(success));
  }

  return (
    <LoadedView loaded={loaded} retry={retry}>
      {(page) => {
        const drafts = page.access.edit || page.access.publish;
        const shownTitle = (drafts ? page.draft?.title : null) ?? page.published?.title ?? '';
        const draft = page.draft;
        return (
          <article className="stack wiki-page" data-testid="wiki-page" data-page={page.id} data-archived={page.archived ? 'true' : 'false'}>
            {page.path.length > 0 && (
              <nav aria-label={t('wiki.path')} className="hint" data-testid="wiki-path">
                {page.path.map((crumb, index) => (
                  <span key={crumb.id}>
                    {index > 0 && ' › '}
                    <a href={hrefFor({ name: 'wikiPage', projectId, pageId: crumb.id })}>{crumb.title}</a>
                  </span>
                ))}
              </nav>
            )}
            <h2 className="wiki-title" data-testid="wiki-title">{shownTitle}</h2>
            <p className="hint" data-testid="wiki-status">
              {page.archived && <span className="badge" data-testid="wiki-archived">{t('wiki.badge.archived')} </span>}
              {page.published !== null
                ? t('wiki.status.published', { revision: page.published.revision, person: name(page.published.publishedBy),
                  time: formatDateTime(page.published.publishedAt, language) })
                : t('wiki.status.unpublished')}
              {drafts && draft !== null && draft.changed && page.published !== null && ` ${t('wiki.status.draftNewer', { revision: draft.revision })}`}
            </p>
            <NoticeView notice={notice} />
            {mode !== 'edit' && (
              <div className="actions" data-testid="wiki-actions">
                {mode !== 'read' && <button type="button" className="secondary" onClick={() => setMode('read')}>{t('wiki.action.read')}</button>}
                {page.access.edit && !page.archived && draft !== null && (
                  <button type="button" onClick={() => { setNotice(null); setMode('edit'); }} data-testid="wiki-edit-open">{t('wiki.action.edit')}</button>
                )}
                {page.access.publish && !page.archived && draft !== null && (page.published === null || draft.changed) && (
                  <button type="button" onClick={() => setMode('publish')} data-testid="wiki-publish">{t('wiki.action.publish')}</button>
                )}
                {page.access.publish && !page.archived && page.published !== null && (
                  <button type="button" className="secondary" disabled={busy} data-testid="wiki-withdraw"
                    onClick={() => void act(() => withdrawWikiPage(api, page.id), 'wiki.withdrawn', 'wiki.withdrawConfirm')}>{t('wiki.action.withdraw')}</button>
                )}
                {drafts && <button type="button" className="secondary" onClick={() => setMode('history')} data-testid="wiki-history-open">{t('wiki.action.history')}</button>}
                {page.access.edit && !page.archived && roomBelow(depth) && (
                  <button type="button" className="secondary" onClick={() => setMode('create')} data-testid="wiki-child">{t('wiki.action.child')}</button>
                )}
                {page.access.edit && !page.archived && (
                  <button type="button" className="secondary" onClick={() => setMode('move')} data-testid="wiki-move-open">{t('wiki.action.move')}</button>
                )}
                {page.access.publish && !page.archived && (
                  <button type="button" className="secondary danger" disabled={busy} data-testid="wiki-archive"
                    onClick={() => void act(() => archiveWikiPage(api, page.id, page.version), 'wiki.archived', 'wiki.archiveConfirm')}>{t('wiki.action.archive')}</button>
                )}
                {page.access.publish && page.archived && (
                  <button type="button" disabled={busy} data-testid="wiki-unarchive"
                    onClick={() => void act(() => unarchiveWikiPage(api, page.id, page.version), 'wiki.unarchived', null)}>{t('wiki.action.unarchive')}</button>
                )}
              </div>
            )}
            {mode === 'edit' && draft !== null && (
              <EditorPanel page={{ ...page, draft }} links={flat.filter((row) => row.item.id !== page.id).map((row) => row.item)}
                onSaved={(saved) => { replace({ value: saved, etag: null }); onTreeChanged(); }} onClose={() => setMode('read')} />
            )}
            {mode === 'publish' && draft !== null && (
              <PublishDialog page={{ ...page, draft }} projectName={projectName} onCancel={() => setMode('read')}
                onDone={(published) => changed(published, t('wiki.published', { revision: draft.revision }))} />
            )}
            {mode === 'move' && <MoveForm page={page} tree={nodes} onCancel={() => setMode('read')} onDone={(moved) => changed(moved, t('wiki.moved'))} />}
            {mode === 'create' && <CreateForm projectId={projectId} tree={flat} parentId={page.id} onCancel={() => setMode('read')} />}
            {mode === 'history' && <HistoryPanel page={page} onRestored={(restored) => changed(restored, null, 'history')} />}
            {(mode === 'read' || mode === 'publish' || mode === 'move' || mode === 'create') && (
              drafts && draft !== null && (page.published === null || draft.changed)
                ? (
                  <section className="stack" data-testid="wiki-draft-view">
                    <p className="mode-notice">{t('wiki.draftShown', { revision: draft.revision })}</p>
                    <Content html={draft.html} testId="wiki-content" />
                  </section>
                )
                : page.published !== null
                  ? <Content html={page.published.html} testId="wiki-content" />
                  : <EmptyText text={t('wiki.empty')} />
            )}
          </article>
        );
      }}
    </LoadedView>
  );
}

// ---------------------------------------------------------------- the wiki of a project and the choice of projects

function ProjectWiki({ projectId, pageId }: { readonly projectId: string; readonly pageId: string | null }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [archived, setArchived] = useState(false);
  const [projects] = useLoad(() => listWikiProjects(api), [api]);
  // The tree loads again with every page shown, so a page created or changed elsewhere appears.
  const [tree, retry] = useLoad(() => readWikiTree(api, projectId), [api, projectId, pageId]);
  const [archive, retryArchive] = useLoad(() => (archived ? readWikiTree(api, projectId, true)
    : Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })), [api, projectId, archived]);
  const project = projects.state === 'ok' ? projects.value.find((item) => item.projectId === projectId) : undefined;
  const projectName = project === undefined ? '' : `${project.key} · ${project.name}`;
  const refresh = () => {
    retry();
    retryArchive();
  };

  return (
    <div className="stack">
      <PageHead title={t('wiki.title')} intro={projectName === '' ? t('wiki.intro') : projectName}>
        <a className="button secondary" href={hrefFor('wiki')}>{t('wiki.allProjects')}</a>
      </PageHead>
      <LoadedView loaded={tree} retry={retry}>
        {(value) => {
          const nodes = buildTree(value.pages, language);
          const drafts = value.access.edit || value.access.publish;
          return (
            <div className="wiki-layout">
              <nav className="wiki-tree card" aria-label={t('wiki.tree')} data-testid="wiki-tree">
                <a href={hrefFor({ name: 'wikiPage', projectId, pageId: null })} aria-current={pageId === null ? 'page' : undefined}>{t('wiki.home')}</a>
                {nodes.length === 0 ? <EmptyText text={t(drafts ? 'wiki.tree.emptyEditor' : 'wiki.tree.empty')} /> : (
                  <TreeView nodes={nodes} projectId={projectId} current={pageId} drafts={drafts} />
                )}
                {value.access.publish && (
                  <div className="stack">
                    <label className="check">
                      <input type="checkbox" checked={archived} onChange={(event) => setArchived(event.target.checked)} data-testid="wiki-show-archived" />
                      {' '}{t('wiki.showArchived')}
                    </label>
                    {archived && (
                      <LoadedView loaded={archive} retry={retryArchive}>
                        {(list) => <ArchiveList pages={list?.pages ?? []} projectId={projectId} />}
                      </LoadedView>
                    )}
                  </div>
                )}
              </nav>
              <div className="wiki-main">
                {pageId === null
                  ? (
                    <div className="stack">
                      <p className="hint">{t(nodes.length === 0 ? 'wiki.start.empty' : 'wiki.start.choose')}</p>
                      {value.access.edit && <CreateForm projectId={projectId} tree={flatten(nodes)} parentId={null} />}
                    </div>
                  )
                  : <PageView key={pageId} pageId={pageId} projectId={projectId} projectName={projectName} tree={value} onTreeChanged={refresh} />}
              </div>
            </div>
          );
        }}
      </LoadedView>
    </div>
  );
}

function ArchiveList({ pages, projectId }: { readonly pages: readonly WikiTreeItem[]; readonly projectId: string }) {
  const { t } = useI18n();
  if (pages.length === 0) return <EmptyText text={t('wiki.archive.empty')} />;
  return (
    <ul className="wiki-tree-level" data-testid="wiki-archive-list">
      {pages.map((item) => (
        <li key={item.id} data-testid="wiki-archived-item">
          <a href={hrefFor({ name: 'wikiPage', projectId, pageId: item.id })}>{item.title}</a>
        </li>
      ))}
    </ul>
  );
}

function ProjectList({ projects }: { readonly projects: readonly WikiProject[] }) {
  const { t } = useI18n();
  if (projects.length === 0) return <EmptyText text={t('wiki.noProjects')} />;
  return (
    <ul className="list" data-testid="wiki-projects">
      {projects.map((project) => (
        <li key={project.projectId} className="card" data-testid="wiki-project">
          <a href={hrefFor({ name: 'wikiPage', projectId: project.projectId, pageId: null })}>{project.key} · {project.name}</a>
        </li>
      ))}
    </ul>
  );
}

function ProjectChoice() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, retry] = useLoad(() => listWikiProjects(api), [api]);
  return (
    <div className="stack">
      <PageHead title={t('wiki.title')} intro={t('wiki.intro')} />
      <LoadedView loaded={projects} retry={retry}>
        {(list) => <ProjectList projects={list} />}
      </LoadedView>
    </div>
  );
}

export function WikiPage({ projectId, pageId }: { readonly projectId: string | null; readonly pageId: string | null }) {
  return projectId === null ? <ProjectChoice /> : <ProjectWiki projectId={projectId} pageId={pageId} />;
}
