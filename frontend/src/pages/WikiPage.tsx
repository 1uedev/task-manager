import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import type { Editor } from '@tiptap/react';
import {
  addWikiLanguage, archiveWikiPage, createWikiPage, listWikiProjects, moveWikiPage, publishWikiPage, readWikiHistory, readWikiPage, readWikiRevision, readWikiTree,
  restoreWikiRevision, saveWikiDraft, unarchiveWikiPage, uploadWikiImage, withdrawWikiPage,
  type WikiDocument, type WikiLanguage, type WikiPage as Page, type WikiProject, type WikiRevisionContent, type WikiTree, type WikiTreeItem,
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
import { DiffView } from './WikiDiffView.tsx';
import { WikiContent } from './WikiContent.tsx';
import { useWikiEditor, WikiEditorContent, WikiToolbar } from './WikiEditor.tsx';

// The wiki of a project (TASK-093, DEC-123). The server decides who sees what: readers get the published revision only,
// people with `wiki.edit` the shared draft and the history, people with `wiki.publish` publication, archive and restore.
// The interface offers only what `access` allows and shows a refusal of the server as it comes. Language versions (TASK-094,
// DEC-124): a page is asked for in the language of the interface; the server answers with the version it may show and says
// when that is another one. Every call of a page names the language of the version shown.

const MAX_DEPTH = 6;

/** Whether a page at this depth may still get a subpage. */
const roomBelow = (depth: number) => MAX_DEPTH - depth > 0;

const VIOLATION: Record<string, MessageKey> = {
  'title.required': 'wiki.error.title', 'title.too_long': 'wiki.error.titleLong', 'title.invalid': 'wiki.error.title',
  'content.too_large': 'wiki.error.tooLarge', 'content.too_many_images': 'wiki.error.tooManyImages', 'content.too_deep': 'wiki.error.tooDeepContent',
  'content.invalid': 'wiki.error.content', 'content.unsupported': 'wiki.error.content', 'image.external': 'wiki.error.externalImage',
  'image.unknown': 'wiki.error.unknownImage', 'link.invalid': 'wiki.error.link', 'link.unknown_page': 'wiki.error.linkPage',
  'parent.unknown': 'wiki.error.parent', 'parent.too_deep': 'wiki.error.tooDeep', 'parent.cycle': 'wiki.error.cycle',
  'source_revision.invalid': 'wiki.error.sourceRevision',
};

const SPECIFIC: Record<string, MessageKey> = {
  'wiki.version_conflict': 'wiki.error.conflict', 'wiki.busy': 'wiki.error.busy', 'wiki.page_archived': 'wiki.error.archived',
  'wiki.page_not_archived': 'wiki.error.notArchived', 'wiki.not_published': 'wiki.error.notPublished', 'wiki.page_not_found': 'wiki.error.notFound',
  'auth.forbidden': 'wiki.error.forbidden', 'image.too_large': 'wiki.error.imageTooLarge', 'image.type_unsupported': 'wiki.error.imageType',
  'image.empty': 'wiki.error.imageEmpty', 'image.storage_unavailable': 'wiki.error.imageStorage',
  'wiki.language_exists': 'wiki.error.languageExists', 'wiki.language_not_found': 'wiki.error.languageMissing',
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

const LANGUAGES: readonly WikiLanguage[] = ['de', 'en'];

/** The language of the interface as a language of the wiki. */
const wikiLanguage = (language: string): WikiLanguage => (language === 'en' ? 'en' : 'de');

const languageName = (language: string): MessageKey => (language === 'en' ? 'wiki.lang.en' : 'wiki.lang.de');

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
          <a href={hrefFor({ name: 'wikiPage', projectId, pageId: node.item.id })} aria-current={node.item.id === current ? 'page' : undefined}
            lang={node.item.language}>
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
  const { t, language } = useI18n();
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
    const result = await createWikiPage(api, projectId, title, parent === '' ? null : parent, wikiLanguage(language));
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
  // A translation whose source was published again can be marked as matched to that revision with the next save.
  const [aligned, setAligned] = useState(false);
  const sourcePublished = page.translation?.outdated === true ? page.translation.sourcePublishedRevision : null;
  const editor = useWikiEditor(page.draft.content, true, (count) => setPasteNotice(t('wiki.paste.removed', { count })));
  // The editor content of the last confirmed revision; as state, so the first comparison after opening already uses it.
  const [baseline, setBaseline] = useState<string | null>(null);
  useEffect(() => {
    if (editor !== null) setBaseline(JSON.stringify(editor.getJSON()));
  }, [editor]);
  useEffect(() => {
    if (editor === null) return;
    const update = () => setVersion((value) => value + 1);
    editor.on('update', update);
    return () => {
      editor.off('update', update);
    };
  }, [editor]);
  const dirty = useMemo(() => editor !== null && baseline !== null
    && (aligned || title !== base.title || JSON.stringify(editor.getJSON()) !== baseline), [editor, baseline, aligned, title, base.title, version]);
  useUnsavedGuard(dirty);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (editor === null) return;
    if (title.trim() === '') return setTitleError(t('wiki.error.title'));
    setTitleError(null);
    setBusy(true);
    setNotice(null);
    const sent = JSON.stringify(editor.getJSON());
    const content = asDocument(editor.getJSON());
    const result = await saveWikiDraft(api, page.id, page.language, base.revision, title, content, aligned ? sourcePublished : null);
    if (result.kind === 'ok') {
      setBusy(false);
      const saved = result.data.value;
      if (saved.draft !== null) {
        setBase(saved.draft);
        setTitle(saved.draft.title);
        if (JSON.stringify(editor.getJSON()) === sent) {
          // Nothing typed meanwhile: the editor shows the document as the server stored it.
          editor.commands.setContent(saved.draft.content, { emitUpdate: false });
          setBaseline(JSON.stringify(editor.getJSON()));
        } else {
          // Typed while the request ran: that input stays in the editor and remains unsaved against what was sent.
          setBaseline(sent);
        }
        setVersion((value) => value + 1);
      }
      setConflict(null);
      setAligned(false);
      setNotice({ kind: 'success', text: t('wiki.saved', { revision: saved.draft?.revision ?? base.revision }) });
      onSaved(saved);
      return;
    }
    if (result.problem.status === 412) {
      // Someone saved meanwhile. The own input stays; the newer draft appears beside it with the differences.
      const newer = await readWikiPage(api, page.id, page.language);
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
    setBaseline(JSON.stringify(editor.getJSON()));
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
      <div lang={page.language}><WikiEditorContent editor={editor} /></div>
      {sourcePublished !== null && (
        <label className="check" data-testid="wiki-align">
          <input type="checkbox" checked={aligned} onChange={(event) => setAligned(event.target.checked)} disabled={busy} />
          {' '}{t('wiki.translation.align', { revision: sourcePublished })}
        </label>
      )}
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
    const result = await publishWikiPage(api, page.id, page.language, page.draft.revision);
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
        <dd data-testid="wiki-publish-language">{t(languageName(page.language))}</dd>
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
  const [history, retry] = useLoad(() => readWikiHistory(api, page.id, page.language),
    [api, page.id, page.language, page.draft?.revision, page.published?.revision]);
  const [shown, setShown] = useState<WikiRevisionContent | null>(null);
  const [left, setLeft] = useState('');
  const [right, setRight] = useState('');
  const [compared, setCompared] = useState<{ readonly left: number; readonly right: number; readonly entries: DiffEntry[] } | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  // R1 of the follow up review: only the answer of the latest request to show or compare counts.
  const shownRequest = useRef(0);
  const comparedRequest = useRef(0);

  async function show(revision: number) {
    const request = ++shownRequest.current;
    const result = await readWikiRevision(api, page.id, page.language, revision);
    if (request !== shownRequest.current) return;
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setShown(result.data.value);
  }

  async function compare(event: FormEvent) {
    event.preventDefault();
    if (left === '' || right === '') return;
    const request = ++comparedRequest.current;
    const [a, b] = await Promise.all([readWikiRevision(api, page.id, page.language, Number(left)),
      readWikiRevision(api, page.id, page.language, Number(right))]);
    if (request !== comparedRequest.current) return;
    if (a.kind !== 'ok') return setNotice({ kind: 'info', text: problem(a.problem) });
    if (b.kind !== 'ok') return setNotice({ kind: 'info', text: problem(b.problem) });
    setCompared({ left: Number(left), right: Number(right), entries: diffBlocks(blocksOf(a.data.value.content), blocksOf(b.data.value.content)) });
  }

  async function restore(revision: number) {
    if (page.draft === null) return;
    if (!globalThis.confirm(t('wiki.history.restoreConfirm', { revision }))) return;
    setBusy(true);
    const result = await restoreWikiRevision(api, page.id, page.language, revision, page.draft.revision);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    shownRequest.current++;
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
                      {revision.sourceRevision !== null && <span className="hint"> {t('wiki.history.translates', { revision: revision.sourceRevision })}</span>}
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
                  <div lang={shown.language}><Content html={shown.html} testId="wiki-revision-content" /></div>
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
                      <DiffView entries={compared.entries} testId="wiki-compare-diff" lang={page.language} />
                    </>
                  )}
                </form>
              )}
              <h3>{t('wiki.history.events')}</h3>
              <ul className="list" data-testid="wiki-events">
                {value.events.map((event, index) => (
                  <li key={index} data-kind={event.kind}>
                    {t(`wiki.event.${event.kind}` as MessageKey, { revision: event.revision ?? '' })}
                    {event.language !== null && <span className="badge"> {event.language.toUpperCase()}</span>}{' · '}{name(event.actorId)}{' · '}
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
    const result = await moveWikiPage(api, page.id, page.language, page.version, parent === '' ? null : parent);
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

// ---------------------------------------------------------------- language versions

/** The versions of the page, the choice between them and adding the missing one. */
function LanguageBar({ page, onChoose, onAdded }: {
  readonly page: Page;
  readonly onChoose: (language: WikiLanguage) => void;
  readonly onAdded: (page: Page) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useWikiProblem();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  // H2 of the review: the add button disappears, so the focus moves to the button of the added version.
  const [focusOn, setFocusOn] = useState<WikiLanguage | null>(null);
  const group = useRef<HTMLDivElement>(null);
  const known = new Set(page.languages.map((item) => item.language));
  const missing = page.access.edit && !page.archived ? LANGUAGES.filter((code) => !known.has(code)) : [];
  useEffect(() => {
    if (focusOn === null || focusOn !== page.language) return;
    group.current?.querySelector<HTMLButtonElement>(`button[data-language="${focusOn}"]`)?.focus();
    setFocusOn(null);
  }, [focusOn, page.language]);

  async function add(language: WikiLanguage) {
    setBusy(true);
    const result = await addWikiLanguage(api, page.id, language);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setFocusOn(language);
    onAdded(result.data.value);
  }

  if (page.languages.length < 2 && missing.length === 0) return null;
  return (
    <div className="stack" data-testid="wiki-languages">
      <div className="actions" role="group" aria-label={t('wiki.languages')} ref={group}>
        {page.languages.map((item) => (
          <button key={item.language} type="button" className={item.language === page.language ? undefined : 'secondary'}
            aria-pressed={item.language === page.language} data-language={item.language} onClick={() => onChoose(item.language)}>
            {t(languageName(item.language))}
            {item.language === page.sourceLanguage && page.languages.length > 1 && ` · ${t('wiki.language.source')}`}
            {!item.published && ` · ${t('wiki.badge.draft')}`}
          </button>
        ))}
        {missing.map((code) => (
          <button key={code} type="button" className="secondary" disabled={busy} onClick={() => void add(code)} data-testid="wiki-language-add">
            {t('wiki.language.add', { language: t(languageName(code)) })}
          </button>
        ))}
      </div>
      <NoticeView notice={notice} />
    </div>
  );
}

/** The compared source revisions: their pair, a changed title and the changed blocks. */
type SourceDiff = {
  readonly pair: string;
  readonly title: { readonly before: string; readonly after: string } | null;
  readonly entries: readonly DiffEntry[];
};

/**
 * For people with drafts: the source was published again after the revision this translation translates, with the changes
 * of title and content (F3 of the review). A comparison belongs to its pair of revisions; when the pair changes it is
 * dropped, and a late answer of an older pair is not taken (F4).
 */
function SourceChange({ page }: { readonly page: Page }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useWikiProblem();
  const from = page.translation?.sourceRevision ?? null;
  const to = page.translation?.sourcePublishedRevision ?? null;
  const source = page.sourceLanguage;
  const pair = `${page.id} ${source ?? ''} ${from ?? ''} ${to ?? ''}`;
  const current = useRef(pair);
  current.current = pair;
  const [diff, setDiff] = useState<SourceDiff | null>(null);
  const [notice, setNotice] = useState<{ readonly pair: string; readonly notice: Notice } | null>(null);
  const result = useRef<HTMLDivElement>(null);
  const shown = diff !== null && diff.pair === pair ? diff : null;
  useEffect(() => {
    if (shown !== null) result.current?.focus();
  }, [shown]);
  if (from === null || to === null || source === null) return null;

  async function compare(asked: string, language: WikiLanguage, left: number, right: number) {
    const [a, b] = await Promise.all([readWikiRevision(api, page.id, language, left), readWikiRevision(api, page.id, language, right)]);
    if (current.current !== asked) return;
    if (a.kind !== 'ok') return setNotice({ pair: asked, notice: { kind: 'info', text: problem(a.problem) } });
    if (b.kind !== 'ok') return setNotice({ pair: asked, notice: { kind: 'info', text: problem(b.problem) } });
    const before = a.data.value.title;
    const after = b.data.value.title;
    setDiff({ pair: asked, title: before === after ? null : { before, after },
      entries: diffBlocks(blocksOf(a.data.value.content), blocksOf(b.data.value.content)) });
  }

  const contentChanged = shown !== null && shown.entries.some((entry) => entry.op !== 'same');
  return (
    <section className="card stack" data-testid="wiki-source-changed" aria-labelledby="wiki-source-changed-title">
      <h3 id="wiki-source-changed-title">{t('wiki.translation.changed')}</h3>
      <p>{t('wiki.translation.changedText', { from, to, language: t(languageName(source)) })}</p>
      <NoticeView notice={notice !== null && notice.pair === pair ? notice.notice : null} />
      {shown === null
        ? (
          <div className="actions">
            <button type="button" className="secondary" onClick={() => void compare(pair, source, from, to)}>{t('wiki.translation.compare')}</button>
          </div>
        )
        : (
          <div className="stack" ref={result} tabIndex={-1} data-testid="wiki-source-result" aria-label={t('wiki.translation.result', { from, to })}>
            {shown.title !== null && (
              <dl className="wiki-source-title" data-testid="wiki-source-title">
                <dt>{t('wiki.translation.titleBefore')}</dt>
                <dd lang={source}>{shown.title.before}</dd>
                <dt>{t('wiki.translation.titleAfter')}</dt>
                <dd lang={source}>{shown.title.after}</dd>
              </dl>
            )}
            {shown.title !== null && !contentChanged
              ? <p className="hint" data-testid="wiki-source-diff-none">{t('wiki.translation.contentSame')}</p>
              : <DiffView entries={shown.entries} testId="wiki-source-diff" lang={source} />}
          </div>
        )}
    </section>
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
  // The language asked for (F2 of the review): the version the person chose on this page, else the current language of the
  // interface. While the editor is open the version stays fixed, so that a change of the interface language keeps the input.
  const [chosen, setChosen] = useState<{ readonly pageId: string; readonly language: WikiLanguage } | null>(null);
  const [pinned, setPinned] = useState<WikiLanguage | null>(null);
  const wanted = pinned ?? (chosen !== null && chosen.pageId === pageId ? chosen.language : wikiLanguage(language));
  const [loaded, retry, replace] = useLoad(() => readWikiPage(api, pageId, wanted), [api, pageId, wanted]);
  const [mode, setMode] = useState<Mode>('read');
  useEffect(() => {
    if (mode !== 'edit') setPinned(null);
  }, [mode]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const nodes = useMemo(() => buildTree(tree.pages, language), [tree.pages, language]);
  const flat = useMemo(() => flatten(nodes), [nodes]);
  const depth = flat.find((row) => row.item.id === pageId)?.depth ?? 1;

  function changed(page: Page, text: string | null, next: Mode = 'read') {
    if (page.language !== wanted) setChosen({ pageId: page.id, language: page.language });
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
            <h2 className="wiki-title" data-testid="wiki-title" lang={page.language}>{shownTitle}</h2>
            {mode !== 'edit' && (
              <LanguageBar page={page} onChoose={(code) => { setNotice(null); setMode('read'); setChosen({ pageId: page.id, language: code }); }}
                onAdded={(added) => changed(added, t('wiki.language.added', { language: t(languageName(added.language)) }))} />
            )}
            {page.fallback && (
              <p className="mode-notice" data-testid="wiki-fallback" lang={language}>
                {t('wiki.language.fallback', { wanted: t(languageName(wanted)), shown: t(languageName(page.language)) })}
              </p>
            )}
            {page.translation?.outdated === true && !drafts && page.sourceLanguage !== null && (
              <p className="hint" data-testid="wiki-outdated">{t('wiki.translation.outdatedReader', { language: t(languageName(page.sourceLanguage)) })}</p>
            )}
            {page.translation?.outdated === true && drafts && mode !== 'edit' && <SourceChange page={page} />}
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
                  <button type="button" onClick={() => { setNotice(null); setPinned(page.language); setMode('edit'); }} data-testid="wiki-edit-open">{t('wiki.action.edit')}</button>
                )}
                {page.access.publish && !page.archived && draft !== null && (page.published === null || draft.changed) && (
                  <button type="button" onClick={() => setMode('publish')} data-testid="wiki-publish">{t('wiki.action.publish')}</button>
                )}
                {page.access.publish && !page.archived && page.published !== null && (
                  <button type="button" className="secondary" disabled={busy} data-testid="wiki-withdraw"
                    onClick={() => void act(() => withdrawWikiPage(api, page.id, page.language), 'wiki.withdrawn', 'wiki.withdrawConfirm')}>{t('wiki.action.withdraw')}</button>
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
                    onClick={() => void act(() => archiveWikiPage(api, page.id, page.language, page.version), 'wiki.archived', 'wiki.archiveConfirm')}>{t('wiki.action.archive')}</button>
                )}
                {page.access.publish && page.archived && (
                  <button type="button" disabled={busy} data-testid="wiki-unarchive"
                    onClick={() => void act(() => unarchiveWikiPage(api, page.id, page.language, page.version), 'wiki.unarchived', null)}>{t('wiki.action.unarchive')}</button>
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
            {/* R1 of the follow up review: the history belongs to one page and language version. A change of the version gives a
                new panel, so a shown revision, a comparison and late answers of the former version are dropped. */}
            {mode === 'history' && (
              <HistoryPanel key={`${page.id} ${page.language}`} page={page} onRestored={(restored) => changed(restored, null, 'history')} />
            )}
            {(mode === 'read' || mode === 'publish' || mode === 'move' || mode === 'create') && (
              drafts && draft !== null && (page.published === null || draft.changed)
                ? (
                  <section className="stack" data-testid="wiki-draft-view">
                    <p className="mode-notice">{t('wiki.draftShown', { revision: draft.revision })}</p>
                    <div lang={page.language}><Content html={draft.html} testId="wiki-content" /></div>
                  </section>
                )
                : page.published !== null
                  ? <div lang={page.language}><Content html={page.published.html} testId="wiki-content" /></div>
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
  const [tree, retry] = useLoad(() => readWikiTree(api, projectId, wikiLanguage(language)), [api, projectId, pageId, language]);
  const [archive, retryArchive] = useLoad(() => (archived ? readWikiTree(api, projectId, wikiLanguage(language), true)
    : Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })), [api, projectId, archived, language]);
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
