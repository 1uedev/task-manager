import { useEffect, useRef, useState, type ReactNode } from 'react';
import { EditorContent, useEditor, useEditorState, type Editor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { TableKit } from '@tiptap/extension-table';
import { Fragment, Slice, type Node as PmNode } from '@tiptap/pm/model';
import type { WikiDocument } from '../api/product.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// The visual editor of a wiki page (TASK-093, DEC-123): Tiptap 3 with exactly the elements of the confirmed rules
// (paragraphs, headings 1 to 3, bold, italic, inline code, lists, tables with a header row, links, images with
// alternative text, quotes, code blocks and lines). The server checks every saved document again and renders the HTML
// readers see; the editor only keeps a person from producing what the server would refuse.

const IMAGE_PATH = '/api/v1/wiki/images/';

/** Whether a link target is allowed: https, http, mailto or a wiki page of the same project as `wiki:<id>`. */
export function allowedHref(href: string): boolean {
  return /^(https?:\/\/[^\s]+|mailto:[^\s]+|wiki:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.test(href.trim());
}

const DROPPED = 'script, style, iframe, object, embed, video, audio, svg, canvas, form, input, button, select, textarea, frame, frameset';

const isWikiImage = (src: string) => {
  const origin = globalThis.location?.origin ?? '';
  const path = origin !== '' && src.startsWith(origin) ? src.slice(origin.length) : src;
  return path.startsWith(IMAGE_PATH);
};

/**
 * Counts what pasting drops from HTML: embedded content and images from elsewhere. Everything else the editor does not
 * know becomes text by itself because its schema has no place for it.
 */
export function countDropped(html: string): number {
  const body = new DOMParser().parseFromString(html, 'text/html').body;
  const embedded = body.querySelectorAll(DROPPED).length;
  const images = Array.from(body.querySelectorAll('img')).filter((image) => !isWikiImage(image.getAttribute('src') ?? '')).length;
  return embedded + images;
}

/** Removes images from elsewhere from pasted content; they are never stored (rule 16). */
function withoutForeignImages(fragment: Fragment): Fragment {
  const kept: PmNode[] = [];
  fragment.forEach((node) => {
    if (node.type.name === 'image' && !isWikiImage(String(node.attrs.src ?? ''))) return;
    kept.push(node.isLeaf ? node : node.copy(withoutForeignImages(node.content)));
  });
  return Fragment.fromArray(kept);
}

export function useWikiEditor(content: WikiDocument, editable: boolean, onPasteRemoved: (count: number) => void): Editor | null {
  const removed = useRef(onPasteRemoved);
  removed.current = onPasteRemoved;
  return useEditor({
    editable,
    content,
    immediatelyRender: true,
    shouldRerenderOnTransaction: false,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        strike: false,
        underline: false,
        // No empty paragraph added at the end by itself: what is saved is what the person wrote.
        trailingNode: false,
        link: {
          openOnClick: false,
          autolink: false,
          linkOnPaste: true,
          isAllowedUri: (url) => allowedHref(url),
          HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: null },
        },
      }),
      Image.configure({ inline: false, allowBase64: false }),
      TableKit.configure({ table: { resizable: false } }),
    ],
    editorProps: {
      attributes: { class: 'wiki-content wiki-editor-surface', 'data-testid': 'wiki-editor', role: 'textbox', 'aria-multiline': 'true' },
      transformPastedHTML: (html) => {
        const dropped = countDropped(html);
        if (dropped > 0) removed.current(dropped);
        return html;
      },
      transformPasted: (slice) => new Slice(withoutForeignImages(slice.content), slice.openStart, slice.openEnd),
    },
  });
}

function ToolButton({ label, active = false, disabled = false, onClick, children }: {
  readonly label: string;
  readonly active?: boolean;
  readonly disabled?: boolean;
  readonly onClick: () => void;
  readonly children: ReactNode;
}) {
  return (
    <button type="button" className={`tool${active ? ' active' : ''}`} aria-label={label} title={label} aria-pressed={active}
      disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={onClick}>
      {children}
    </button>
  );
}

export interface LinkChoice {
  readonly id: string;
  readonly title: string;
}

/** The tool bar above the editor. Every tool has a name in both languages and works by keyboard. */
export function WikiToolbar({ editor, pages, onImage, disabled }: {
  readonly editor: Editor;
  readonly pages: readonly LinkChoice[];
  readonly onImage: () => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive('bold'), italic: e.isActive('italic'), code: e.isActive('code'), h1: e.isActive('heading', { level: 1 }),
      h2: e.isActive('heading', { level: 2 }), h3: e.isActive('heading', { level: 3 }), bullet: e.isActive('bulletList'),
      ordered: e.isActive('orderedList'), quote: e.isActive('blockquote'), codeBlock: e.isActive('codeBlock'), link: e.isActive('link'),
      table: e.isActive('table'), href: String(e.getAttributes('link').href ?? ''), canUndo: e.can().undo(), canRedo: e.can().redo(),
    }),
  });
  const [linkOpen, setLinkOpen] = useState(false);
  const [href, setHref] = useState('');
  const [linkError, setLinkError] = useState(false);
  const chain = () => editor.chain().focus();
  const tool = (key: MessageKey) => t(key);

  function openLink() {
    setHref(state.href);
    setLinkError(false);
    setLinkOpen(true);
  }

  function applyLink(target: string) {
    if (!allowedHref(target)) {
      setLinkError(true);
      return;
    }
    if (editor.state.selection.empty && !state.link) {
      const text = target.startsWith('wiki:') ? (pages.find((page) => `wiki:${page.id}` === target)?.title ?? target) : target;
      chain().insertContent({ type: 'text', text, marks: [{ type: 'link', attrs: { href: target } }] }).run();
    } else {
      chain().extendMarkRange('link').setLink({ href: target }).run();
    }
    setLinkOpen(false);
  }

  return (
    <div className="wiki-toolbar-wrap">
      <div className="wiki-toolbar" role="toolbar" aria-label={t('wiki.tool.bar')}>
        <ToolButton label={tool('wiki.tool.paragraph')} disabled={disabled} onClick={() => chain().setParagraph().run()}>¶</ToolButton>
        <ToolButton label={tool('wiki.tool.h1')} active={state.h1} disabled={disabled} onClick={() => chain().toggleHeading({ level: 1 }).run()}>H1</ToolButton>
        <ToolButton label={tool('wiki.tool.h2')} active={state.h2} disabled={disabled} onClick={() => chain().toggleHeading({ level: 2 }).run()}>H2</ToolButton>
        <ToolButton label={tool('wiki.tool.h3')} active={state.h3} disabled={disabled} onClick={() => chain().toggleHeading({ level: 3 }).run()}>H3</ToolButton>
        <span className="tool-gap" aria-hidden="true" />
        <ToolButton label={tool('wiki.tool.bold')} active={state.bold} disabled={disabled} onClick={() => chain().toggleBold().run()}><strong>B</strong></ToolButton>
        <ToolButton label={tool('wiki.tool.italic')} active={state.italic} disabled={disabled} onClick={() => chain().toggleItalic().run()}><em>I</em></ToolButton>
        <ToolButton label={tool('wiki.tool.code')} active={state.code} disabled={disabled} onClick={() => chain().toggleCode().run()}>{'</>'}</ToolButton>
        <ToolButton label={tool('wiki.tool.link')} active={state.link} disabled={disabled} onClick={openLink}>🔗</ToolButton>
        <span className="tool-gap" aria-hidden="true" />
        <ToolButton label={tool('wiki.tool.bullet')} active={state.bullet} disabled={disabled} onClick={() => chain().toggleBulletList().run()}>•</ToolButton>
        <ToolButton label={tool('wiki.tool.ordered')} active={state.ordered} disabled={disabled} onClick={() => chain().toggleOrderedList().run()}>1.</ToolButton>
        <ToolButton label={tool('wiki.tool.quote')} active={state.quote} disabled={disabled} onClick={() => chain().toggleBlockquote().run()}>❝</ToolButton>
        <ToolButton label={tool('wiki.tool.codeBlock')} active={state.codeBlock} disabled={disabled} onClick={() => chain().toggleCodeBlock().run()}>{'{ }'}</ToolButton>
        <ToolButton label={tool('wiki.tool.rule')} disabled={disabled} onClick={() => chain().setHorizontalRule().run()}>―</ToolButton>
        <ToolButton label={tool('wiki.tool.image')} disabled={disabled} onClick={onImage}>🖼</ToolButton>
        <ToolButton label={tool('wiki.tool.table')} disabled={disabled || state.table}
          onClick={() => chain().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()}>▦</ToolButton>
        {state.table && (
          <>
            <ToolButton label={tool('wiki.tool.addRow')} disabled={disabled} onClick={() => chain().addRowAfter().run()}>+≡</ToolButton>
            <ToolButton label={tool('wiki.tool.addColumn')} disabled={disabled} onClick={() => chain().addColumnAfter().run()}>+‖</ToolButton>
            <ToolButton label={tool('wiki.tool.deleteRow')} disabled={disabled} onClick={() => chain().deleteRow().run()}>−≡</ToolButton>
            <ToolButton label={tool('wiki.tool.deleteColumn')} disabled={disabled} onClick={() => chain().deleteColumn().run()}>−‖</ToolButton>
            <ToolButton label={tool('wiki.tool.deleteTable')} disabled={disabled} onClick={() => chain().deleteTable().run()}>✕▦</ToolButton>
          </>
        )}
        <span className="tool-gap" aria-hidden="true" />
        <ToolButton label={tool('wiki.tool.undo')} disabled={disabled || !state.canUndo} onClick={() => chain().undo().run()}>↶</ToolButton>
        <ToolButton label={tool('wiki.tool.redo')} disabled={disabled || !state.canRedo} onClick={() => chain().redo().run()}>↷</ToolButton>
      </div>
      {linkOpen && (
        <div className="wiki-link-form card" data-testid="wiki-link-form">
          <div className="field">
            <label htmlFor="wiki-link-href">{t('wiki.link.address')}</label>
            <p className="hint" id="wiki-link-hint">{t('wiki.link.hint')}</p>
            <input id="wiki-link-href" value={href} onChange={(event) => setHref(event.target.value)} aria-describedby="wiki-link-hint"
              aria-invalid={linkError ? true : undefined} autoFocus
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  applyLink(href);
                } else if (event.key === 'Escape') {
                  setLinkOpen(false);
                  editor.commands.focus();
                }
              }} />
            {linkError && <p className="field-error">{t('wiki.link.invalid')}</p>}
          </div>
          {pages.length > 0 && (
            <div className="field">
              <label htmlFor="wiki-link-page">{t('wiki.link.page')}</label>
              <select id="wiki-link-page" value="" onChange={(event) => { if (event.target.value !== '') applyLink(`wiki:${event.target.value}`); }}>
                <option value="">{t('wiki.link.choosePage')}</option>
                {pages.map((page) => <option key={page.id} value={page.id}>{page.title}</option>)}
              </select>
            </div>
          )}
          <div className="actions">
            <button type="button" onClick={() => applyLink(href)}>{t('wiki.link.apply')}</button>
            {state.link && (
              <button type="button" className="secondary" onClick={() => { chain().extendMarkRange('link').unsetLink().run(); setLinkOpen(false); }}>
                {t('wiki.link.remove')}
              </button>
            )}
            <button type="button" className="secondary" onClick={() => { setLinkOpen(false); editor.commands.focus(); }}>{t('wiki.cancel')}</button>
          </div>
        </div>
      )}
    </div>
  );
}

/** The editor surface. */
export function WikiEditorContent({ editor }: { readonly editor: Editor }) {
  const { t } = useI18n();
  useEffect(() => {
    editor.view.dom.setAttribute('aria-label', t('wiki.editor.label'));
  }, [editor, t]);
  return <EditorContent editor={editor} />;
}
