import { useEffect, useRef, useState, type FormEvent } from 'react';
import { searchAll } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { navigateAsked, type Route } from './route.ts';

// The quick search at the top of the application (TASK-104, rule 2). "/" outside a field moves the focus here. Enter asks the
// overview: a key that names exactly one object the person may read (PLAT-12, HELP ANF-17, a project key) opens it directly,
// anything else opens "Alle" with the text, without saying whether such an object exists. Opening asks first when the page
// holds unsaved input, and a change of the page while the answer is outstanding drops it (TASK-110).
export function QuickSearch() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const field = useRef<HTMLInputElement>(null);
  const latest = useRef(0);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
      const target = event.target as HTMLElement | null;
      if (target !== null && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) return;
      event.preventDefault();
      field.current?.focus();
      field.current?.select();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  // The person went elsewhere while the answer was outstanding: it no longer decides where the application goes.
  useEffect(() => {
    const onChange = () => {
      latest.current += 1;
      setBusy(false);
    };
    globalThis.addEventListener('hashchange', onChange);
    return () => globalThis.removeEventListener('hashchange', onChange);
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const text = q.trim().slice(0, 200);
    const ticket = ++latest.current;
    setBusy(true);
    const result = text === '' ? null : await searchAll(api, text, language);
    if (ticket !== latest.current) return;
    setBusy(false);
    const direct = result?.kind === 'ok' ? result.data.value.direct : null;
    let target: Route;
    if (direct === null) {
      target = { name: 'searchArea', area: 'all', q: text };
    } else if (direct.kind === 'ticket' && direct.number !== null) {
      target = { name: 'ticket', projectId: direct.projectId, number: direct.number };
    } else if (direct.kind === 'requirement') {
      target = { name: 'requirementPage', projectId: direct.projectId, requirementId: direct.id };
    } else {
      target = { name: 'project', id: direct.id, tab: 'tickets' };
    }
    // When the page holds unsaved input and the person stays, the text stays in the field.
    if (navigateAsked(target)) setQ('');
  }

  return (
    <form className="quick-search" role="search" aria-label={t('quickSearch.label')} onSubmit={(event) => void submit(event)}>
      <label className="visually-hidden" htmlFor="quick-search">{t('quickSearch.label')}</label>
      <input id="quick-search" ref={field} type="search" value={q} maxLength={200} placeholder={t('quickSearch.placeholder')}
        aria-describedby="quick-search-hint" aria-keyshortcuts="/" aria-busy={busy} autoComplete="off"
        onChange={(event) => setQ(event.target.value)} />
      <span id="quick-search-hint" className="visually-hidden">{t('quickSearch.hint')}</span>
      <kbd aria-hidden="true">/</kbd>
    </form>
  );
}
