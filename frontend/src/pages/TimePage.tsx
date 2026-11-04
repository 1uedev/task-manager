import { useState, type FormEvent } from 'react';
import {
  bookTime, cancelTime, correctTime, listTimeEntries, listTimePeople, listTimeProjects, readTimeEntry, timeExportHref,
  type TimeEntry, type TimeEntryList, type TimeFilter, type TimeInput, type TimeProject,
} from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { durationInput, formatHours, formatMinutes, parseDuration } from '../app/duration.ts';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { useAccountName, useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { formatDate, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// Time tracking (TASK-069, DEC-099). The server decides what is visible and changeable: `time.book` for own entries,
// `time.read` for everybody's, `time.manage` for booking and changing for others. The interface only offers what the
// answers allow (`access`, `changeable`) and shows a refusal of the server as it comes.

const VIOLATION: Record<string, MessageKey> = {
  'date.required': 'time.error.date', 'date.invalid': 'time.error.date', 'date.future': 'time.error.future',
  'minutes.invalid': 'time.error.duration', 'description.required': 'time.error.description', 'description.too_long': 'time.error.descriptionLong',
  'ticket.unknown': 'time.error.ticket', 'person.not_member': 'time.error.person', 'reason.required': 'time.error.reason',
  'reason.too_long': 'time.error.reasonLong', 'to.before_from': 'time.error.period',
};

/** Today as a calendar date of the browser. */
export function today(): string {
  const now = new Date();
  return isoDate(now.getFullYear(), now.getMonth(), now.getDate());
}

function isoDate(year: number, month: number, day: number): string {
  const date = new Date(Date.UTC(year, month, day));
  return date.toISOString().slice(0, 10);
}

/** The Monday of the week of a date and the six days after it. */
export function weekOf(date: string): string[] {
  const [year, month, day] = date.split('-').map(Number) as [number, number, number];
  const start = new Date(Date.UTC(year, month - 1, day));
  const offset = (start.getUTCDay() + 6) % 7;
  return Array.from({ length: 7 }, (_, index) => isoDate(year, month - 1, day - offset + index));
}

function shift(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number) as [number, number, number];
  return isoDate(year, month - 1, day + days);
}

function useViolationText() {
  const { t } = useI18n();
  const text = useProblemText();
  return (problem: Parameters<ReturnType<typeof useProblemText>>[0]) => {
    const known = (problem.violations ?? []).map((violation) => VIOLATION[violation]).filter((key): key is MessageKey => key !== undefined);
    if (problem.status === 400 && known.length > 0) return known.map((key) => t(key)).join(' ');
    return text(problem, { 'time.version_conflict': 'time.error.conflict', 'time.entry_cancelled': 'time.error.cancelled', 'auth.forbidden': 'time.error.forbidden',
      'time.export_too_large': 'time.error.tooLarge' });
  };
}

// ---------------------------------------------------------------- the form of a booking or a correction

interface Submit {
  (projectId: string, input: TimeInput, person: string | null, reason: string | null): Promise<Notice | null>;
}

function EntryForm({ initial, projects, fixedProject, fixedTicket, people, correcting, onSubmit, onCancel, testId }: {
  readonly initial: { readonly projectId: string; readonly date: string; readonly minutes: number | null; readonly description: string; readonly ticket: number | null };
  readonly projects: readonly TimeProject[];
  readonly fixedProject: boolean;
  readonly fixedTicket: boolean;
  readonly people: ((projectId: string) => readonly string[]) | null;
  readonly correcting: { readonly reasonRequired: boolean } | null;
  readonly onSubmit: Submit;
  readonly onCancel?: () => void;
  readonly testId: string;
}) {
  const { t, language } = useI18n();
  const name = useAccountName();
  const [projectId, setProjectId] = useState(initial.projectId);
  const [date, setDate] = useState(initial.date);
  const [duration, setDuration] = useState(initial.minutes === null ? '' : durationInput(initial.minutes));
  const [description, setDescription] = useState(initial.description);
  const [ticket, setTicket] = useState(initial.ticket === null ? '' : String(initial.ticket));
  const [person, setPerson] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [errors, setErrors] = useState<Readonly<Record<string, string>>>({});
  const minutes = parseDuration(duration, language);
  const project = projects.find((item) => item.projectId === projectId);
  const others = project?.access.manage && people !== null ? people(projectId) : [];

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (date === '') found.date = t('time.error.date');
    if (minutes === null) found.duration = t('time.error.duration');
    if (description.trim() === '') found.description = t('time.error.description');
    if (ticket !== '' && !/^[1-9][0-9]{0,8}$/.test(ticket)) found.ticket = t('time.error.ticket');
    if (correcting?.reasonRequired && reason.trim() === '') found.reason = t('time.error.reason');
    setErrors(found);
    if (Object.keys(found).length > 0 || minutes === null) return;
    setBusy(true);
    const result = await onSubmit(projectId, { date, minutes, description, ticketNumber: ticket === '' ? null : Number(ticket) }, person === '' ? null : person,
      reason.trim() === '' ? null : reason);
    setBusy(false);
    setNotice(result);
    if (result?.kind === 'success' && correcting === null) {
      setDuration('');
      setDescription('');
      // The next booking is the person's own again, so no second entry lands on someone else by mistake.
      setPerson('');
      if (!fixedTicket) setTicket('');
    }
  }

  return (
    <form className="stack" onSubmit={(event) => void submit(event)} noValidate data-testid={testId}>
      {!fixedProject && (
        <SelectField label={t('time.project')} value={projectId} onChange={setProjectId} disabled={busy}
          options={projects.filter((item) => item.access.book || item.access.manage).map((item) => ({ value: item.projectId, label: `${item.key} · ${item.name}` }))} />
      )}
      {correcting === null && others.length > 0 && (
        <SelectField label={t('time.person')} value={person} onChange={setPerson} disabled={busy}
          options={[{ value: '', label: t('time.personSelf') }, ...others.map((id) => ({ value: id, label: name(id) }))]} />
      )}
      <TextField label={t('time.date')} type="date" value={date} onChange={setDate} required error={errors.date ?? null} disabled={busy} />
      <TextField label={t('time.duration')} value={duration} onChange={setDuration} required error={errors.duration ?? null} disabled={busy}
        hint={minutes === null ? t('time.durationHint') : t('time.durationParsed', { duration: formatMinutes(minutes, language) })} />
      {!fixedTicket && (
        <TextField label={t('time.ticket')} value={ticket} onChange={setTicket} error={errors.ticket ?? null} disabled={busy} hint={t('time.ticketHint')} />
      )}
      <TextField label={t('time.description')} value={description} onChange={setDescription} required maxLength={1000} multiline
        error={errors.description ?? null} disabled={busy} />
      {correcting !== null && (
        <TextField label={t('time.reason')} value={reason} onChange={setReason} required={correcting.reasonRequired} maxLength={500}
          error={errors.reason ?? null} disabled={busy} hint={correcting.reasonRequired ? t('time.reasonForeign') : t('time.reasonOwn')} />
      )}
      <NoticeView notice={notice} />
      <div className="actions">
        <button type="submit" disabled={busy}>{t(correcting === null ? 'time.book' : 'time.saveCorrection')}</button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>{t('time.discard')}</button>}
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- one entry with correction, cancellation and history

function EntryItem({ entry, projects, showProject, onChanged }: {
  readonly entry: TimeEntry;
  readonly projects: readonly TimeProject[];
  readonly showProject: boolean;
  readonly onChanged: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const violation = useViolationText();
  const [mode, setMode] = useState<'view' | 'correct' | 'cancel' | 'history'>('view');
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [history] = useLoad(() => (mode === 'history' ? readTimeEntry(api, entry.id)
    : Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })), [api, entry.id, mode, entry.version]);
  const ticketText = entry.ticket === null ? null
    : `${entry.projectKey}-${entry.ticket.number}${entry.ticket.title ? ` ${entry.ticket.title}` : ''}`;

  async function cancel(event: FormEvent) {
    event.preventDefault();
    if (!entry.own && reason.trim() === '') return setNotice({ kind: 'info', text: t('time.error.reason') });
    setBusy(true);
    const result = await cancelTime(api, entry.id, entry.version, reason.trim() === '' ? null : reason);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: violation(result.problem) });
    setMode('view');
    onChanged();
  }

  return (
    <li className="card" data-testid="time-entry" data-entry={entry.id} data-minutes={entry.minutes} data-cancelled={entry.cancelled ? 'true' : 'false'}>
      <p>
        <strong>{formatMinutes(entry.minutes, language)}</strong>{' · '}{formatDate(entry.date, language)}{' · '}{name(entry.personId)}
        {showProject && ` · ${entry.projectKey}`}
        {entry.cancelled && <span className="badge" data-testid="time-cancelled"> {t('time.cancelled')}</span>}
      </p>
      {ticketText !== null && entry.ticket !== null && (
        <p className="hint"><a href={hrefFor({ name: 'ticket', projectId: entry.projectId, number: entry.ticket.number })}>{ticketText}</a></p>
      )}
      <p className="pre-wrap">{entry.description}</p>
      <NoticeView notice={notice} />
      {mode === 'view' && (
        <div className="actions">
          {entry.changeable && <button type="button" className="link-button" onClick={() => { setNotice(null); setMode('correct'); }}>{t('time.correct')}</button>}
          {entry.changeable && <button type="button" className="link-button" onClick={() => { setNotice(null); setReason(''); setMode('cancel'); }}>{t('time.cancel')}</button>}
          <button type="button" className="link-button" onClick={() => setMode('history')}>{t('time.history')}</button>
        </div>
      )}
      {mode === 'correct' && (
        <EntryForm testId="time-correct" projects={projects} fixedProject fixedTicket={false} people={null}
          initial={{ projectId: entry.projectId, date: entry.date, minutes: entry.minutes, description: entry.description, ticket: entry.ticket?.number ?? null }}
          correcting={{ reasonRequired: !entry.own }} onCancel={() => setMode('view')}
          onSubmit={async (_project, input, _person, why) => {
            const result = await correctTime(api, entry.id, entry.version, input, why);
            if (result.kind !== 'ok') return { kind: 'info', text: violation(result.problem) };
            setMode('view');
            onChanged();
            return null;
          }} />
      )}
      {mode === 'cancel' && (
        <form className="stack" onSubmit={(event) => void cancel(event)} noValidate data-testid="time-cancel">
          <TextField label={t('time.reason')} value={reason} onChange={setReason} required={!entry.own} maxLength={500} disabled={busy}
            hint={entry.own ? t('time.reasonOwn') : t('time.reasonForeign')} />
          <p className="hint">{t('time.cancelHint')}</p>
          <div className="actions">
            <button type="submit" className="danger" disabled={busy}>{t('time.cancelConfirm')}</button>
            <button type="button" className="secondary" onClick={() => setMode('view')} disabled={busy}>{t('time.discard')}</button>
          </div>
        </form>
      )}
      {mode === 'history' && (
        <div data-testid="time-history">
          <LoadedView loaded={history} retry={() => setMode('history')}>
            {(detail) => detail === null ? null : (
              <ol className="history">
                {detail.revisions.map((revision) => (
                  <li key={revision.revision} data-kind={revision.kind}>
                    {t(`time.revision.${revision.kind}` as MessageKey, { by: name(revision.actorId), date: formatDateTime(revision.occurredAt, language) })}
                    {': '}{formatMinutes(revision.minutes, language)}, {formatDate(revision.date, language)}, {revision.description}
                    {revision.ticket && `, ${entry.projectKey}-${revision.ticket.number}`}
                    {revision.reason && ` (${t('time.reasonShown', { reason: revision.reason })})`}
                  </li>
                ))}
              </ol>
            )}
          </LoadedView>
          <button type="button" className="link-button" onClick={() => setMode('view')}>{t('time.closeHistory')}</button>
        </div>
      )}
    </li>
  );
}

function Sums({ list, projects }: { readonly list: TimeEntryList; readonly projects: readonly TimeProject[] }) {
  const { t, language } = useI18n();
  const name = useAccountName();
  const many = new Set(list.perTicket.map((sum) => sum.projectId)).size > 1 || projects.length > 1;
  return (
    <div className="sums" data-testid="time-sums">
      <p data-testid="time-total" data-minutes={list.totalMinutes}>
        {t('time.total', { duration: formatMinutes(list.totalMinutes, language), hours: formatHours(list.totalMinutes, language), count: list.count })}
      </p>
      {list.truncated && <p className="hint">{t('time.truncated')}</p>}
      {list.perPerson.length > 1 && (
        <>
          <h3>{t('time.perPerson')}</h3>
          <ul className="plain-list" data-testid="time-per-person">
            {list.perPerson.map((sum) => <li key={sum.personId} data-minutes={sum.minutes}>{name(sum.personId)}: {formatMinutes(sum.minutes, language)}</li>)}
          </ul>
        </>
      )}
      {list.perTicket.length > 1 && (
        <>
          <h3>{t('time.perTicket')}</h3>
          <ul className="plain-list" data-testid="time-per-ticket">
            {list.perTicket.map((sum) => (
              <li key={`${sum.projectId}#${sum.ticket?.number ?? ''}`} data-minutes={sum.minutes}>
                {sum.ticket === null ? `${many ? `${sum.projectKey} · ` : ''}${t('time.noTicket')}` : `${sum.projectKey}-${sum.ticket.number}${sum.ticket.title ? ` ${sum.ticket.title}` : ''}`}
                {': '}{formatMinutes(sum.minutes, language)}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- the panel at a ticket

export function TicketTime({ projectId, ticketNumber, round }: { readonly projectId: string; readonly ticketNumber: number; readonly round: number }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const violation = useViolationText();
  const [changes, setChanges] = useState(0);
  const [projects] = useLoad(() => listTimeProjects(api), [api, round]);
  const project = projects.state === 'ok' ? projects.value.find((item) => item.projectId === projectId) : undefined;
  const [entries, reload] = useLoad(() => (project === undefined ? Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })
    : listTimeEntries(api, { project: projectId, ticket: ticketNumber })), [api, projectId, ticketNumber, project === undefined, round, changes]);
  const [people] = useLoad(() => (project?.access.manage ? listTimePeople(api, projectId)
    : Promise.resolve({ kind: 'ok' as const, data: { value: [] as string[], etag: null } })), [api, projectId, project?.access.manage]);
  if (project === undefined) return null;
  const changed = () => setChanges((value) => value + 1);

  return (
    <section aria-labelledby="ticket-time" className="panel" data-testid="ticket-time">
      <h2 id="ticket-time">{t('time.ticketTitle')}</h2>
      <LoadedView loaded={entries} retry={reload}>
        {(list) => list === null ? null : (
          <>
            <Sums list={list} projects={[project]} />
            {list.entries.length === 0 ? <EmptyText text={t('time.ticketEmpty')} /> : (
              <ul className="item-list">
                {list.entries.map((entry) => <EntryItem key={`${entry.id}/${entry.version}`} entry={entry} projects={[project]} showProject={false} onChanged={changed} />)}
              </ul>
            )}
          </>
        )}
      </LoadedView>
      {(project.access.book || project.access.manage) && (
        <details>
          <summary>{t('time.bookOnTicket')}</summary>
          <EntryForm testId="ticket-time-form" projects={[project]} fixedProject fixedTicket correcting={null}
            people={() => (people.state === 'ok' ? people.value : [])}
            initial={{ projectId, date: today(), minutes: null, description: '', ticket: ticketNumber }}
            onSubmit={async (id, input, person) => {
              const result = await bookTime(api, id, input, person);
              if (result.kind !== 'ok') return { kind: 'info', text: violation(result.problem) };
              changed();
              return { kind: 'success', text: t('time.booked') };
            }} />
        </details>
      )}
    </section>
  );
}

// ---------------------------------------------------------------- the page "Zeiten": my week and the evaluation

export function TimePage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, reload] = useLoad(() => listTimeProjects(api), [api]);
  return (
    <>
      <PageHead title={t('time.title')} intro={t('time.intro')} />
      <LoadedView loaded={projects} retry={reload}>
        {(list) => (
          list.length === 0 ? <EmptyText text={t('time.noProjects')} /> : (
          <>
            <MyWeek projects={list} />
            <Evaluation projects={list} />
          </>
        ))}
      </LoadedView>
    </>
  );
}

function MyWeek({ projects }: { readonly projects: readonly TimeProject[] }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const { view, directory } = useSession();
  const violation = useViolationText();
  const [anchor, setAnchor] = useState(today());
  const [changes, setChanges] = useState(0);
  const days = weekOf(anchor);
  const username = view.state === 'ready' ? view.session.username : null;
  const me = [...directory.values()].find((entry) => entry.username === username)?.id;
  const filter: TimeFilter = { from: days[0] ?? anchor, to: days[6] ?? anchor, ...(me === undefined ? {} : { person: me }) };
  const [entries, reload] = useLoad(() => listTimeEntries(api, filter), [api, filter.from, filter.to, me, changes]);
  const bookable = projects.filter((item) => item.access.book || item.access.manage);
  const changed = () => setChanges((value) => value + 1);

  return (
    <section aria-labelledby="time-week" className="panel" data-testid="time-week">
      <h2 id="time-week">{t('time.myWeek')}</h2>
      <div className="actions" role="group" aria-label={t('time.weekNavigation')}>
        <button type="button" className="secondary" onClick={() => setAnchor(shift(anchor, -7))}>{t('time.previousWeek')}</button>
        <button type="button" className="secondary" onClick={() => setAnchor(today())}>{t('time.thisWeek')}</button>
        <button type="button" className="secondary" onClick={() => setAnchor(shift(anchor, 7))}>{t('time.nextWeek')}</button>
      </div>
      <p className="hint" data-testid="time-week-range">{t('time.weekRange', { from: formatDate(days[0] ?? anchor, language), to: formatDate(days[6] ?? anchor, language) })}</p>
      <LoadedView loaded={entries} retry={reload}>
        {(list) => {
          const own = list.entries.filter((entry) => entry.own && !entry.cancelled);
          const week = own.reduce((sum, entry) => sum + entry.minutes, 0);
          return (
            <>
              <p data-testid="time-week-total" data-minutes={week}>{t('time.weekTotal', { duration: formatMinutes(week, language), hours: formatHours(week, language) })}</p>
              <ol className="plain-list" data-testid="time-days">
                {days.map((day) => {
                  const ofDay = own.filter((entry) => entry.date === day);
                  const sum = ofDay.reduce((total, entry) => total + entry.minutes, 0);
                  return (
                    <li key={day} data-day={day} data-minutes={sum}>
                      <h3>{formatDate(day, language)}{sum > 0 && ` · ${formatMinutes(sum, language)}`}</h3>
                      {ofDay.length > 0 && (
                        <ul className="item-list">
                          {ofDay.map((entry) => <EntryItem key={`${entry.id}/${entry.version}`} entry={entry} projects={projects} showProject onChanged={changed} />)}
                        </ul>
                      )}
                    </li>
                  );
                })}
              </ol>
            </>
          );
        }}
      </LoadedView>
      {bookable.length > 0 && bookable[0] !== undefined && (
        <details>
          <summary>{t('time.bookNew')}</summary>
          <EntryForm testId="time-form" projects={bookable} fixedProject={false} fixedTicket={false} people={null} correcting={null}
            initial={{ projectId: bookable[0].projectId, date: today(), minutes: null, description: '', ticket: null }}
            onSubmit={async (projectId, input) => {
              const result = await bookTime(api, projectId, input, null);
              if (result.kind !== 'ok') return { kind: 'info', text: violation(result.problem) };
              setAnchor(input.date);
              changed();
              return { kind: 'success', text: t('time.booked') };
            }} />
        </details>
      )}
    </section>
  );
}

function Evaluation({ projects }: { readonly projects: readonly TimeProject[] }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const violation = useViolationText();
  const days = weekOf(today());
  const [project, setProject] = useState('');
  const [person, setPerson] = useState('');
  const [ticket, setTicket] = useState('');
  const [from, setFrom] = useState(`${today().slice(0, 7)}-01`);
  const [to, setTo] = useState(days[6] ?? today());
  const [cancelled, setCancelled] = useState(false);
  const [applied, setApplied] = useState<TimeFilter | null>(null);
  const [changes, setChanges] = useState(0);
  const selected = projects.find((item) => item.projectId === project);
  const readsAll = selected !== undefined && (selected.access.read || selected.access.manage);
  const [people] = useLoad(() => (selected !== undefined && readsAll ? listTimePeople(api, selected.projectId)
    : Promise.resolve({ kind: 'ok' as const, data: { value: [] as string[], etag: null } })), [api, selected?.projectId, readsAll]);
  const [entries, reload] = useLoad(() => (applied === null ? Promise.resolve({ kind: 'ok' as const, data: { value: null, etag: null } })
    : listTimeEntries(api, applied)), [api, JSON.stringify(applied), changes]);

  function show(event: FormEvent) {
    event.preventDefault();
    setApplied({
      ...(project === '' ? {} : { project }),
      ...(person === '' || !readsAll ? {} : { person }),
      ...(ticket === '' || project === '' || !/^[1-9][0-9]{0,8}$/.test(ticket) ? {} : { ticket: Number(ticket) }),
      ...(from === '' ? {} : { from }),
      ...(to === '' ? {} : { to }),
      ...(cancelled ? { cancelled: true } : {}),
    });
  }

  return (
    <section aria-labelledby="time-evaluation" className="panel" data-testid="time-evaluation">
      <h2 id="time-evaluation">{t('time.evaluation')}</h2>
      <p className="hint">{t('time.evaluationHint')}</p>
      <form className="stack" onSubmit={show} noValidate data-testid="time-filter">
        <SelectField label={t('time.project')} value={project} onChange={(next) => { setProject(next); setPerson(''); }}
          options={[{ value: '', label: t('time.allProjects') }, ...projects.map((item) => ({ value: item.projectId, label: `${item.key} · ${item.name}` }))]} />
        {readsAll && people.state === 'ok' && (
          <SelectField label={t('time.person')} value={person} onChange={setPerson}
            options={[{ value: '', label: t('time.allPeople') }, ...people.value.map((id) => ({ value: id, label: name(id) }))]} />
        )}
        {project !== '' && <TextField label={t('time.ticket')} value={ticket} onChange={setTicket} hint={t('time.ticketHint')} />}
        <TextField label={t('time.from')} type="date" value={from} onChange={setFrom} />
        <TextField label={t('time.to')} type="date" value={to} onChange={setTo} />
        <label className="check"><input type="checkbox" checked={cancelled} onChange={(event) => setCancelled(event.target.checked)} /> {t('time.showCancelled')}</label>
        <div className="actions">
          <button type="submit">{t('time.show')}</button>
          {applied !== null && <a className="button secondary" href={timeExportHref(applied)} download data-testid="time-export">{t('time.export')}</a>}
        </div>
      </form>
      {applied !== null && (
        <LoadedView loaded={entries} retry={reload}>
          {(list) => list === null ? null : (
            <>
              <Sums list={list} projects={projects} />
              {list.entries.length === 0 ? <EmptyText text={t('time.empty')} /> : (
                <ul className="item-list" data-testid="time-entries">
                  {list.entries.map((entry) => (
                    <EntryItem key={`${entry.id}/${entry.version}`} entry={entry} projects={projects} showProject onChanged={() => setChanges((value) => value + 1)} />
                  ))}
                </ul>
              )}
            </>
          )}
        </LoadedView>
      )}
      {entries.state === 'problem' && <p className="field-error">{violation(entries.problem)}</p>}
    </section>
  );
}
