import { useRef, useState, type FormEvent } from 'react';
import {
  linkRequirementPage, linkRequirementTicket, readRequirementLinks, readWikiTree, switchRequirementTicket, unlinkRequirementPage,
  unlinkRequirementTicket, type Requirement,
} from '../api/product.ts';
import { hrefFor } from '../app/route.ts';
import { useLoad } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// The answer of every change of links.
type LinkAnswer = ReturnType<typeof readRequirementLinks>;

const PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'requirement.already_linked': 'requirements.links.error.alreadyLinked',
  'requirement.links_too_many': 'requirements.links.error.tooMany',
  'requirement.discarded': 'requirements.links.error.discarded',
  'requirement.version_conflict': 'requirements.links.error.stale',
  'requirement.link_not_found': 'requirements.links.error.notFound',
  'requirement.not_found': 'requirements.error.notFound',
  'auth.forbidden': 'requirements.error.forbidden',
};

const VIOLATIONS: Readonly<Record<string, MessageKey>> = {
  'ticket.unknown': 'requirements.links.error.ticketUnknown',
  'number.invalid': 'requirements.links.error.number',
  'page.unknown': 'requirements.links.error.pageUnknown',
};

// The links of a requirement with tickets and wiki pages of its project (TASK-097). The server decides what is shown: tickets
// only with ticket.read and their title and status with their read rules, pages with their published title only for readers of
// the wiki. A link names no version, so it is sent without If-Match and never touches the requirement form.
export function RequirementLinksPanel({ requirement }: { readonly requirement: Requirement }) {
  const { t, language: ui } = useI18n();
  const language = ui === 'en' ? 'en' : 'de';
  const { api } = useBrand();
  const problemText = useProblemText();
  const [links, retry, replace] = useLoad(() => readRequirementLinks(api, requirement.id, language),
    [api, requirement.id, requirement.revision, language]);
  const [pages] = useLoad(() => readWikiTree(api, requirement.projectId, language), [api, requirement.projectId, language]);
  const [number, setNumber] = useState('');
  const [numberError, setNumberError] = useState<string | null>(null);
  const [page, setPage] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  // Only the answer of the latest change counts; an older one that arrives late is dropped.
  const request = useRef(0);

  async function change(call: () => LinkAnswer, success: string, onOk?: () => void) {
    const ticket = ++request.current;
    setBusy(true);
    setNotice(null);
    const result = await call();
    if (ticket !== request.current) return;
    setBusy(false);
    if (result.kind !== 'ok') {
      const violation = (result.problem.violations ?? []).find((code) => VIOLATIONS[code] !== undefined);
      const text = violation !== undefined ? t(VIOLATIONS[violation] as MessageKey) : problemText(result.problem, PROBLEMS);
      if (violation === 'ticket.unknown' || violation === 'number.invalid') {
        setNumberError(text);
      } else {
        setNotice({ kind: 'info', text });
      }
      if (result.problem.status === 404 || result.problem.status === 409 || result.problem.status === 412) retry();
      return;
    }
    replace(result.data);
    setNotice({ kind: 'success', text: success });
    onOk?.();
  }

  function linkTicket(event: FormEvent) {
    event.preventDefault();
    setNumberError(null);
    const value = Number(number.trim());
    if (!Number.isInteger(value) || value < 1) return setNumberError(t('requirements.links.error.number'));
    void change(() => linkRequirementTicket(api, requirement.id, value, language), t('requirements.links.linked'), () => setNumber(''));
  }

  function linkPage(event: FormEvent) {
    event.preventDefault();
    if (page === '') return;
    void change(() => linkRequirementPage(api, requirement.id, page, language), t('requirements.links.linked'), () => setPage(''));
  }

  return (
    <section className="stack" data-testid="requirement-links" aria-labelledby="requirement-links-title">
      <h2 id="requirement-links-title">{t('requirements.links.title')}</h2>
      <NoticeView notice={notice} />
      <LoadedView loaded={links} retry={retry}>
        {(value) => {
          const linkedPages = new Set(value.pages.map((item) => item.pageId));
          const choices = pages.state === 'ok'
            ? pages.value.pages.filter((item) => item.published && !item.archived && !linkedPages.has(item.id)) : [];
          return (
            <>
              <section className="card stack" aria-labelledby="requirement-links-tickets">
                <h3 id="requirement-links-tickets">{t('requirements.links.tickets')}</h3>
                {!value.ticketsReadable ? <p className="hint">{t('requirements.links.ticketsHidden')}</p> : (
                  <>
                    <p data-testid="requirement-links-summary">
                      {value.summary.tickets === 1
                        ? t('requirements.links.summaryOne', { done: value.summary.done })
                        : t('requirements.links.summary', { count: value.summary.tickets, done: value.summary.done })}
                    </p>
                    <p className="hint">{t('requirements.links.neverApproves')}</p>
                    {value.tickets.length === 0 ? <EmptyText text={t('requirements.links.ticketsEmpty')} /> : (
                      <ul className="list" data-testid="requirement-ticket-links">
                        {value.tickets.map((link) => (
                          <li key={link.ticketId} className="card" data-testid="requirement-ticket-link" data-number={link.number}
                            data-revision={link.revision} data-done={link.done ? 'true' : 'false'}>
                            <p>
                              <a href={hrefFor({ name: 'ticket', projectId: requirement.projectId, number: link.number })}>{link.key}</a>{' '}
                              {link.title ?? <span className="hint">{t('requirements.hidden')}</span>}
                              {' · '}
                              <span data-testid="requirement-ticket-status">
                                {link.statusName ?? link.status ?? t('requirements.links.statusHidden')}
                                {link.done && <> ({t('requirements.links.done')})</>}
                              </span>
                              {' · '}{t('requirements.links.revision', { revision: link.revision })}
                            </p>
                            {link.newerRevision !== null && (
                              <p className="mode-notice" data-testid="requirement-ticket-newer">{t('requirements.links.newer', { revision: link.newerRevision })}</p>
                            )}
                            {value.edit && (
                              <div className="actions">
                                {link.newerRevision !== null && (
                                  <button type="button" className="secondary" disabled={busy} data-testid="requirement-ticket-switch"
                                    aria-label={t('requirements.links.switchAria', { key: link.key, revision: link.newerRevision })}
                                    onClick={() => void change(() => switchRequirementTicket(api, requirement.id, link.number, value.revision, language),
                                      t('requirements.links.switched', { revision: value.revision }))}>
                                    {t('requirements.links.switch', { revision: link.newerRevision })}
                                  </button>
                                )}
                                <button type="button" className="secondary danger" disabled={busy} data-testid="requirement-ticket-unlink"
                                  aria-label={t('requirements.links.unlinkAria', { name: link.key })}
                                  onClick={() => void change(() => unlinkRequirementTicket(api, requirement.id, link.number, language),
                                    t('requirements.links.unlinked'))}>
                                  {t('requirements.links.unlink')}
                                </button>
                              </div>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                    {value.edit && (
                      <form className="inline-form" onSubmit={linkTicket} data-testid="requirement-ticket-link-form">
                        <TextField label={t('requirements.links.ticketNumber')} value={number} onChange={setNumber} error={numberError} disabled={busy} />
                        <div className="actions"><button type="submit" disabled={busy}>{t('requirements.links.linkTicket')}</button></div>
                      </form>
                    )}
                  </>
                )}
              </section>
              <section className="card stack" aria-labelledby="requirement-links-pages">
                <h3 id="requirement-links-pages">{t('requirements.links.pages')}</h3>
                {value.pages.length === 0 ? <EmptyText text={t('requirements.links.pagesEmpty')} /> : (
                  <ul className="list" data-testid="requirement-page-links">
                    {value.pages.map((link) => (
                      <li key={link.pageId} data-testid="requirement-page-link" data-available={link.available ? 'true' : 'false'}>
                        {link.title !== null
                          ? <a href={hrefFor({ name: 'wikiPage', projectId: requirement.projectId, pageId: link.pageId })}>{link.title}</a>
                          : <span className="hint">{t('requirements.links.pageUnavailable')}</span>}
                        {value.edit && (
                          <>
                            {' '}
                            <button type="button" className="secondary danger" disabled={busy} data-testid="requirement-page-unlink"
                              aria-label={t('requirements.links.unlinkAria', { name: link.title ?? t('requirements.links.pageUnavailable') })}
                              onClick={() => void change(() => unlinkRequirementPage(api, requirement.id, link.pageId, language),
                                t('requirements.links.unlinked'))}>
                              {t('requirements.links.unlink')}
                            </button>
                          </>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
                {value.edit && pages.state === 'ok' && (choices.length === 0 ? <p className="hint">{t('requirements.links.noPages')}</p> : (
                  <form className="inline-form" onSubmit={linkPage} data-testid="requirement-page-link-form">
                    <SelectField label={t('requirements.links.page')} value={page} onChange={setPage} disabled={busy}
                      options={[{ value: '', label: t('requirements.links.pageChoose') }, ...choices.map((item) => ({ value: item.id, label: item.title }))]} />
                    <div className="actions"><button type="submit" disabled={busy || page === ''}>{t('requirements.links.linkPage')}</button></div>
                  </form>
                ))}
              </section>
            </>
          );
        }}
      </LoadedView>
    </section>
  );
}
