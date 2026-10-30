import { useState } from 'react';
import { readTicketSla, setTicketPriority, SLA_PRIORITIES, type Project, type SlaClock, type SlaPriority, type Ticket } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { NoticeView, SelectField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

const known = (key: string): key is MessageKey => Object.hasOwn(de, key);

/** A duration in business seconds as hours and minutes, without its sign. */
export function durationParts(seconds: number): { hours: number; minutes: number } {
  const minutes = Math.floor(Math.abs(seconds) / 60);
  return { hours: Math.floor(minutes / 60), minutes: minutes % 60 };
}

// The priority and the deadlines of a ticket (TASK-068). The server decides what is shown: the priority with the read rule
// of `priority`, the deadlines only with the read rules of `priority` and `customer`; the choice only with the write rule.
export function TicketSla({ project, ticket, round, onChanged }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly round: number;
  readonly onChanged: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [view, reload, replace] = useLoad(() => readTicketSla(api, project.id, ticket.number), [api, project.id, ticket.number, round]);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  if (view.state !== 'ok') return null;
  const value = view.value;
  if (!value.readable.includes('priority')) return null;
  const writable = value.writable.includes('priority');
  // Times in the zone of the calendar of the cycle, which the cycle line names (rule 14).
  const date = (at: string | null | undefined, timeZone: string) => formatDateTime(at ?? '', language, timeZone);
  const rest = (seconds: number) => t('ticketSla.duration', durationParts(seconds));

  async function choose(priority: SlaPriority) {
    setNotice(null);
    setBusy(true);
    const result = await setTicketPriority(api, project.id, ticket.number, priority);
    setBusy(false);
    setEditing(false);
    if (result.kind !== 'ok') {
      setNotice({ kind: 'info', text: text(result.problem, { 'auth.forbidden': 'ticketSla.error.forbidden' }) });
      reload();
      return;
    }
    replace(result.data);
    setNotice({ kind: 'success', text: t('ticketSla.saved') });
    onChanged();
  }

  const clock = (label: MessageKey, item: SlaClock, zone: string) => {
    let line: string;
    if (item.state === 'running') {
      line = item.breached ? t('ticketSla.clock.runningBreached', { date: date(item.breachedAt ?? item.dueAt, zone), rest: rest(item.remainingSeconds) })
        : t('ticketSla.clock.running', { date: date(item.dueAt, zone), rest: rest(item.remainingSeconds) });
    } else if (item.state === 'paused') {
      line = t('ticketSla.clock.paused', { date: date(item.pausedAt, zone), rest: rest(item.remainingSeconds) });
    } else if (item.state === 'met') {
      line = item.breachedAt ? t('ticketSla.clock.metBreached', { date: date(item.metAt, zone), breached: date(item.breachedAt, zone) }) : t('ticketSla.clock.met', { date: date(item.metAt, zone) });
    } else {
      line = t('ticketSla.clock.cancelled');
    }
    return (
      <p data-clock={item.kind} data-state={item.state} data-breached={item.breached ? 'true' : 'false'} className={item.breached ? 'field-error' : undefined}>
        <span className="label-small">{t(label)}</span> {line}
      </p>
    );
  };

  return (
    <section aria-labelledby="ticket-sla" className="panel" data-testid="ticket-sla">
      <h2 id="ticket-sla">{t('ticketSla.title')}</h2>
      {!editing ? (
        <p data-testid="ticket-priority"><span className="label-small">{t('field.priority')}</span>{' '}
          {value.priority ? t(`priority.${value.priority}` as MessageKey) : t('field.hidden')}{' '}
          {writable && <button type="button" className="link-button" onClick={() => setEditing(true)}>{t('ticketSla.change')}</button>}
        </p>
      ) : (
        <SelectField label={t('field.priority')} value={value.priority ?? 'normal'} disabled={busy}
          onChange={(next) => void choose(next as SlaPriority)}
          options={SLA_PRIORITIES.map((priority) => ({ value: priority, label: t(`priority.${priority}` as MessageKey) }))} />
      )}
      <NoticeView notice={notice} />
      {value.state === null || value.state === undefined
        ? <p className="hint">{t('ticketSla.hidden')}</p>
        : value.state === 'no_customer'
        ? <p className="hint" data-testid="sla-state">{t('ticketSla.noCustomer')}</p>
        : value.state === 'no_rule'
        ? <p className="hint" data-testid="sla-state">{t('ticketSla.noRule')}</p>
        : (
          <ol className="plain-list" data-testid="sla-cycles">
            {(value.cycles ?? []).map((cycle) => (
              <li key={cycle.number} data-cycle={cycle.number}>
                <p className="hint">{t('ticketSla.cycle', { number: cycle.number, date: date(cycle.startedAt, cycle.timeZone), rule: cycle.ruleName, calendar: cycle.calendarName,
                  zone: cycle.timeZone })}</p>
                {cycle.outcome && (
                  <p>{t(`ticketSla.outcome.${cycle.outcome}` as MessageKey, { date: date(cycle.closedAt, cycle.timeZone) })}
                    {cycle.reason && known(`ticketSla.reason.${cycle.reason}`) ? `, ${t(`ticketSla.reason.${cycle.reason}` as MessageKey)}` : ''}</p>
                )}
                {clock('sla.firstResponse', cycle.firstResponse, cycle.timeZone)}
                {clock('sla.resolution', cycle.resolution, cycle.timeZone)}
              </li>
            ))}
          </ol>
        )}
    </section>
  );
}
