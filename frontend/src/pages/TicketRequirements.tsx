import { useRef, useState, type FormEvent } from 'react';
import {
  linkRequirementTicket, listRequirements, readTicketRequirements, unlinkRequirementTicket, type Project, type RequirementItem, type Ticket,
} from '../api/product.ts';
import { hrefFor } from '../app/route.ts';
import { useLoad } from '../app/hooks.ts';
import { EmptyText, NoticeView, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// The answer of every change of links.
type LinkAnswer = ReturnType<typeof linkRequirementTicket>;

const PROBLEMS: Readonly<Record<string, MessageKey>> = {
  'requirement.already_linked': 'requirements.links.error.alreadyLinked',
  'requirement.links_too_many': 'requirements.links.error.tooMany',
  'requirement.discarded': 'requirements.links.error.discarded',
  'requirement.link_not_found': 'requirements.links.error.notFound',
  'requirement.not_found': 'requirements.error.notFound',
  'auth.forbidden': 'requirements.error.forbidden',
};

// The field "Anforderungen" of a ticket (TASK-097): the linked requirements the person may read with the revision the link
// refers to and the hint on a newer one. Without requirement.read the panel is not shown at all. Linking from here uses the same
// endpoint as the requirement and leaves the version of the ticket alone; only the history changes.
export function TicketRequirements({ project, ticket, round, onChanged }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly round: number;
  readonly onChanged: () => void;
}) {
  const { t, language: ui } = useI18n();
  const language = ui === 'en' ? 'en' : 'de';
  const { api } = useBrand();
  const problemText = useProblemText();
  const [view, reload] = useLoad(() => readTicketRequirements(api, project.id, ticket.number), [api, project.id, ticket.number, round]);
  const [query, setQuery] = useState('');
  const [found, setFound] = useState<readonly RequirementItem[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  // Only the answer of the latest search counts.
  const search = useRef(0);

  if (view.state !== 'ok' || !view.value.read) return null;
  const value = view.value;
  const linked = new Set(value.requirements.map((item) => item.requirementId));

  async function find(event: FormEvent) {
    event.preventDefault();
    const request = ++search.current;
    const text = query.trim();
    const result = await listRequirements(api, project.id, { status: null, priority: null, owner: null, text: text === '' ? null : text });
    if (request !== search.current) return;
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problemText(result.problem, PROBLEMS) });
    setFound(result.data.value.items);
  }

  async function act(call: () => LinkAnswer, success: MessageKey) {
    setBusy(true);
    setNotice(null);
    const result = await call();
    setBusy(false);
    reload();
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problemText(result.problem, PROBLEMS) });
    setNotice({ kind: 'success', text: t(success) });
    onChanged();
  }

  return (
    <section aria-labelledby="ticket-requirements" className="panel" data-testid="ticket-requirements">
      <h2 id="ticket-requirements">{t('ticketRequirements.title')}</h2>
      <NoticeView notice={notice} />
      {value.requirements.length === 0 ? <EmptyText text={t('ticketRequirements.empty')} /> : (
        <ul className="list">
          {value.requirements.map((item) => (
            <li key={item.requirementId} data-testid="ticket-requirement" data-key={item.key} data-revision={item.revision}>
              <a href={hrefFor({ name: 'requirementPage', projectId: project.id, requirementId: item.requirementId })}>{item.key}</a>{' '}
              {item.title ?? <span className="hint">{t('requirements.hidden')}</span>}
              {' · '}{t('ticketRequirements.revision', { revision: item.revision })}
              {item.newerRevision !== null && (
                <span className="badge" data-testid="ticket-requirement-newer"> {t('requirements.links.newer', { revision: item.newerRevision })}</span>
              )}
              {value.edit && (
                <>
                  {' '}
                  <button type="button" className="secondary danger" disabled={busy} data-testid="ticket-requirement-unlink"
                    aria-label={t('requirements.links.unlinkAria', { name: item.key })}
                    onClick={() => void act(() => unlinkRequirementTicket(api, item.requirementId, ticket.number, language), 'requirements.links.unlinked')}>
                    {t('requirements.links.unlink')}
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {value.edit && (
        <form className="stack" onSubmit={(event) => void find(event)} data-testid="ticket-requirement-search">
          <TextField label={t('ticketRequirements.search')} value={query} onChange={setQuery} maxLength={200} hint={t('ticketRequirements.searchHint')} />
          <div className="actions"><button type="submit" className="secondary">{t('ticketRequirements.searchSubmit')}</button></div>
          {found !== null && (found.filter((item) => !linked.has(item.id)).length === 0
            ? <p className="hint">{t('ticketRequirements.noResults')}</p> : (
            <ul className="list" data-testid="ticket-requirement-results">
              {found.filter((item) => !linked.has(item.id)).map((item) => (
                <li key={item.id}>
                  {item.key} {item.title ?? t('requirements.hidden')}{' '}
                  <button type="button" disabled={busy} aria-label={t('ticketRequirements.linkAria', { key: item.key })}
                    onClick={() => void act(() => linkRequirementTicket(api, item.id, ticket.number, language), 'requirements.links.linked')}>
                    {t('ticketRequirements.link')}
                  </button>
                </li>
              ))}
            </ul>
          ))}
        </form>
      )}
    </section>
  );
}
