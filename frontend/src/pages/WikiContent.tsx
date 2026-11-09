import { createElement, Fragment, type ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider.tsx';

// Shows the HTML the server rendered for a wiki revision (TASK-093, DEC-123) without handing markup to the browser:
// the HTML is parsed into an inert document and only the elements and attributes of the confirmed element list become
// React elements. Anything else contributes its text only. The server already builds this HTML from the checked
// document; the second list here keeps a mistake on either side from running anything in the page.

const ID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const WIKI_LINK = new RegExp(`^#/wiki/${ID}/${ID}$`);
const IMAGE = new RegExp(`^/api/v1/wiki/images/${ID}$`);
const EXTERNAL = /^(https?:\/\/|mailto:)/i;
const PLAIN = new Set(['p', 'h1', 'h2', 'h3', 'strong', 'em', 'code', 'pre', 'ul', 'li', 'table', 'tbody', 'tr', 'blockquote']);
const VOID = new Set(['hr', 'br']);

function count(value: string | null): number | undefined {
  if (value === null || !/^[1-9][0-9]{0,3}$/.test(value)) return undefined;
  return Number(value);
}

function convert(node: ChildNode, key: number, unavailable: string): ReactNode {
  if (node.nodeType === 3) return node.textContent;
  if (node.nodeType !== 1) return null;
  const element = node as Element;
  const tag = element.tagName.toLowerCase();
  const children = () => Array.from(element.childNodes).map((child, index) => convert(child, index, unavailable));
  if (tag === 'span' && element.getAttribute('data-wiki-unavailable') === 'true') {
    return <span key={key} className="wiki-unavailable" data-wiki-unavailable="true">{unavailable}</span>;
  }
  if (VOID.has(tag)) return createElement(tag, { key });
  if (tag === 'img') {
    const src = element.getAttribute('src') ?? '';
    return IMAGE.test(src) ? <img key={key} src={src} alt={element.getAttribute('alt') ?? ''} loading="lazy" /> : null;
  }
  if (tag === 'a') {
    const href = element.getAttribute('href') ?? '';
    if (WIKI_LINK.test(href)) return <a key={key} href={href} data-wiki-page={element.getAttribute('data-wiki-page') ?? undefined}>{children()}</a>;
    if (EXTERNAL.test(href)) return <a key={key} href={href} rel="noopener noreferrer nofollow" target="_blank">{children()}</a>;
    return <Fragment key={key}>{children()}</Fragment>;
  }
  if (tag === 'ol') return <ol key={key} start={count(element.getAttribute('start'))}>{children()}</ol>;
  if (tag === 'th' || tag === 'td') {
    return createElement(tag, { key, colSpan: count(element.getAttribute('colspan')), rowSpan: count(element.getAttribute('rowspan')) }, ...children());
  }
  if (tag === 'code') {
    const language = /^language-[A-Za-z0-9_+#.-]{1,40}$/.exec(element.getAttribute('class') ?? '');
    return <code key={key} className={language === null ? undefined : language[0]}>{children()}</code>;
  }
  if (PLAIN.has(tag)) return createElement(tag, { key }, ...children());
  return <Fragment key={key}>{children()}</Fragment>;
}

/** The nodes of server HTML as React elements of the allowed list. */
export function wikiNodes(html: string, unavailable: string): ReactNode[] {
  const body = new DOMParser().parseFromString(`<!doctype html><body>${html}`, 'text/html').body;
  return Array.from(body.childNodes).map((child, index) => convert(child, index, unavailable));
}

export function WikiContent({ html, testId }: { readonly html: string; readonly testId: string }) {
  const { t } = useI18n();
  return <div className="wiki-content" data-testid={testId}>{wikiNodes(html, t('wiki.unavailable'))}</div>;
}
