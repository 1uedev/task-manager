import { useState, type FormEvent } from 'react';
import {
  createSlaCalendar, createSlaRule, listSlaCalendars, listSlaRules, readSlaOptions, removeSlaRule, SLA_PRIORITIES, updateSlaCalendar, updateSlaRule,
  type SlaBudgets, type SlaCalendar, type SlaCalendarRequest, type SlaOptions, type SlaRule,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard, type Loaded } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

export const SLA_RIGHT = 'sla.manage';

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const;
const known = (key: string): key is MessageKey => Object.hasOwn(de, key);

type Span = SlaCalendarRequest['week'][number]['hours'][number];

/** Business hours as a person writes them: "08:00-12:00, 13:00-17:00". Anything unreadable goes to the server as it is, which names it. */
export function parseSpans(text: string): Span[] {
  return text.split(/[,;]/).map((part) => part.trim()).filter((part) => part !== '').map((part) => {
    const [from = '', to = ''] = part.split(/\s*[-–]\s*/);
    return { from, to };
  });
}

export const spansText = (spans: readonly Span[]) => spans.map((span) => `${span.from}-${span.to}`).join(', ');

/** One exception per line: a date, then its hours; a date alone closes the day. */
export function parseExceptions(text: string): NonNullable<SlaCalendarRequest['exceptions']> {
  return text.split('\n').map((line) => line.trim()).filter((line) => line !== '').map((line) => {
    const [date = '', ...rest] = line.split(/\s+/);
    return { date, hours: parseSpans(rest.join(' ')) };
  });
}

/** The hours per day as text; a new calendar starts with Monday to Friday from eight to five. */
function weekTexts(calendar: SlaCalendar | null): Record<number, string> {
  const texts: Record<number, string> = {};
  for (const day of DAYS) {
    const hours = calendar?.week.find((item) => item.day === day)?.hours ?? [];
    texts[day] = calendar === null ? (day <= 5 ? '08:00-17:00' : '') : spansText(hours);
  }
  return texts;
}

const exceptionsText = (exceptions: SlaCalendarRequest['exceptions']) =>
  (exceptions ?? []).map((item) => (item.hours.length === 0 ? item.date : `${item.date} ${spansText(item.hours)}`)).join('\n');

// The reasons of a refused form, each with its own text; unknown reasons fall back to the general one.
function useReasons(): (problem: ProblemInfo) => string {
  const { t } = useI18n();
  const text = useProblemText();
  return (problem) => {
    const reasons = (problem.violations ?? []).map((reason) => (known(`sla.violation.${reason}`) ? t(`sla.violation.${reason}` as MessageKey) : null))
      .filter((reason): reason is string => reason !== null);
    if (reasons.length > 0) return reasons.join(' ');
    return text(problem, {
      'sla.calendar_name_taken': 'sla.error.calendarTaken',
      'sla.rule_conflict': 'sla.error.ruleConflict',
      'sla.version_conflict': 'sla.error.changed',
    });
  };
}

// Business hours calendars and SLA rules (TASK-068). A change applies to cycles that start afterwards; running cycles keep
// the versions they began with.
export function SlaPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [calendars, reloadCalendars] = useLoad(() => listSlaCalendars(api), [api]);
  return (
    <>
      <PageHead title={t('sla.title')} intro={t('sla.intro')} />
      <Calendars loaded={calendars} reload={reloadCalendars} />
      <LoadedView loaded={calendars} retry={reloadCalendars}>
        {(list) => <Rules calendars={list} />}
      </LoadedView>
    </>
  );
}

// ---------------------------------------------------------------- calendars

function Calendars({ loaded, reload }: { readonly loaded: Loaded<SlaCalendar[]>; readonly reload: () => void }) {
  const { t } = useI18n();
  const [editing, setEditing] = useState<SlaCalendar | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const dayText = (calendar: SlaCalendar) => DAYS.map((day) => {
    const hours = calendar.week.find((item) => item.day === day)?.hours ?? [];
    return hours.length === 0 ? null : `${t(`sla.dayShort.${day}` as MessageKey)} ${spansText(hours)}`;
  }).filter((part) => part !== null).join('; ');
  return (
    <section aria-labelledby="sla-calendars" className="panel" data-testid="sla-calendars">
      <h2 id="sla-calendars">{t('sla.calendars')}</h2>
      <p className="hint">{t('sla.calendarsIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={loaded} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('sla.calendarsEmpty')} /> : (
          <ul className="plain-list">
            {list.map((calendar) => (
              <li key={calendar.id} data-calendar={calendar.name}>
                <strong>{calendar.name}</strong>{' '}
                <span className="hint">{`${calendar.timeZone}, ${dayText(calendar)}`}
                  {(calendar.exceptions ?? []).length > 0 ? `, ${t('sla.exceptionCount', { count: (calendar.exceptions ?? []).length })}` : ''}</span>{' '}
                <button type="button" className="link-button" onClick={() => setEditing(calendar)}>{t('sla.edit')}</button>
              </li>
            ))}
          </ul>
        )}
      </LoadedView>
      {editing === null
        ? <p className="actions"><button type="button" onClick={() => setEditing('new')} disabled={loaded.state !== 'ok'}>{t('sla.addCalendar')}</button></p>
        : <CalendarForm key={editing === 'new' ? 'new' : editing.id} calendar={editing === 'new' ? null : editing}
            onDone={(saved) => {
              setEditing(null);
              if (saved !== null) {
                setNotice({ kind: 'success', text: t('sla.saved', { name: saved.name }) });
                reload();
              }
            }} />}
    </section>
  );
}

function CalendarForm({ calendar, onDone }: { readonly calendar: SlaCalendar | null; readonly onDone: (saved: SlaCalendar | null) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const initialWeek = weekTexts(calendar);
  const [name, setName] = useState(calendar?.name ?? '');
  const [timeZone, setTimeZone] = useState(calendar?.timeZone ?? 'Europe/Berlin');
  const [week, setWeek] = useState(initialWeek);
  const [exceptions, setExceptions] = useState(exceptionsText(calendar?.exceptions ?? []));
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  useUnsavedGuard(name !== (calendar?.name ?? '') || DAYS.some((day) => week[day] !== initialWeek[day]) || exceptions !== exceptionsText(calendar?.exceptions ?? []));

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    const body: SlaCalendarRequest = {
      name: name.trim(), timeZone: timeZone.trim(),
      week: DAYS.map((day) => ({ day, hours: parseSpans(week[day] ?? '') })).filter((item) => item.hours.length > 0),
      exceptions: parseExceptions(exceptions),
    };
    setBusy(true);
    const result = calendar === null ? await createSlaCalendar(api, body) : await updateSlaCalendar(api, calendar.id, calendar.version, body);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid="calendar-form">
      <h3>{calendar === null ? t('sla.addCalendar') : t('sla.editCalendar', { name: calendar.name })}</h3>
      <TextField label={t('sla.name')} value={name} onChange={setName} required maxLength={80} />
      <TextField label={t('sla.timeZone')} hint={t('sla.timeZoneHint')} value={timeZone} onChange={setTimeZone} required maxLength={64} autoComplete="off" />
      <fieldset>
        <legend>{t('sla.week')}</legend>
        <p className="hint">{t('sla.weekHint')}</p>
        {DAYS.map((day) => (
          <TextField key={day} label={t(`sla.day.${day}` as MessageKey)} value={week[day] ?? ''} maxLength={200} autoComplete="off"
            onChange={(value) => setWeek((current) => ({ ...current, [day]: value }))} />
        ))}
      </fieldset>
      <TextField label={t('sla.exceptions')} hint={t('sla.exceptionsHint')} value={exceptions} onChange={setExceptions} multiline maxLength={20000} />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('sla.save')}</button>
        <button type="button" className="secondary" onClick={() => onDone(null)}>{t('sla.cancel')}</button>
      </p>
      <NoticeView notice={notice} />
    </form>
  );
}

// ---------------------------------------------------------------- rules

function Rules({ calendars }: { readonly calendars: readonly SlaCalendar[] }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const [rules, reload] = useLoad(() => listSlaRules(api), [api]);
  const [options] = useLoad(() => readSlaOptions(api), [api]);
  const [editing, setEditing] = useState<SlaRule | 'new' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const choices = options.state === 'ok' ? options.value : null;
  const projectName = (id: string) => {
    const project = choices?.projects.find((item) => item.id === id);
    return project === undefined ? id.slice(0, 8) : `${project.key} ${project.name}`;
  };
  const scope = (rule: SlaRule) => {
    const customer = rule.customerId ? choices?.customers.find((item) => item.id === rule.customerId)?.name ?? rule.customerId.slice(0, 8) : null;
    const type = rule.ticketType ? choices?.projects.find((item) => item.id === rule.projectId)?.ticketTypes.find((item) => item.key === rule.ticketType)?.name
      ?? rule.ticketType : null;
    if (customer === null) return t('sla.scopeAll');
    return type === null ? t('sla.scopeCustomer', { customer }) : t('sla.scopeCustomerType', { customer, type });
  };

  async function remove(rule: SlaRule) {
    if (!globalThis.confirm(t('sla.removeConfirm', { name: rule.name }))) return;
    setNotice(null);
    const result = await removeSlaRule(api, rule.id, rule.version);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    setNotice({ kind: 'success', text: t('sla.removed', { name: rule.name }) });
    reload();
  }

  return (
    <section aria-labelledby="sla-rules" className="panel" data-testid="sla-rules">
      <h2 id="sla-rules">{t('sla.rules')}</h2>
      <p className="hint">{t('sla.rulesIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={rules} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('sla.rulesEmpty')} /> : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">{t('sla.name')}</th><th scope="col">{t('sla.project')}</th><th scope="col">{t('sla.scope')}</th>
                  <th scope="col">{t('sla.calendar')}</th><th scope="col">{t('sla.budgets')}</th><th scope="col">{t('sla.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {list.map((rule) => (
                  <tr key={rule.id} data-rule={rule.name}>
                    <td>{rule.name}</td>
                    <td>{projectName(rule.projectId)}</td>
                    <td>{scope(rule)}</td>
                    <td>{calendars.find((item) => item.id === rule.calendarId)?.name ?? rule.calendarId.slice(0, 8)}</td>
                    <td>
                      {SLA_PRIORITIES.map((priority) => (
                        <p key={priority} className="hint">{t('sla.budgetLine', { priority: t(`priority.${priority}` as MessageKey),
                          first: rule.budgets[priority].firstResponse, resolution: rule.budgets[priority].resolution })}</p>
                      ))}
                    </td>
                    <td className="row-actions">
                      <button type="button" className="secondary" onClick={() => setEditing(rule)} disabled={choices === null}>{t('sla.edit')}</button>
                      <button type="button" className="secondary" onClick={() => void remove(rule)}>{t('sla.remove')}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
      {editing === null
        ? <p className="actions"><button type="button" onClick={() => setEditing('new')} disabled={choices === null || calendars.length === 0}>{t('sla.addRule')}</button></p>
        : choices !== null && <RuleForm key={editing === 'new' ? 'new' : editing.id} rule={editing === 'new' ? null : editing} options={choices} calendars={calendars}
            onDone={(saved) => {
              setEditing(null);
              if (saved !== null) {
                setNotice({ kind: 'success', text: t('sla.saved', { name: saved.name }) });
                reload();
              }
            }} />}
      {calendars.length === 0 && <p className="hint">{t('sla.needCalendar')}</p>}
    </section>
  );
}

const DEFAULT_BUDGETS: SlaBudgets = {
  low: { firstResponse: 1440, resolution: 7200 },
  normal: { firstResponse: 480, resolution: 2400 },
  high: { firstResponse: 120, resolution: 960 },
  critical: { firstResponse: 30, resolution: 240 },
};

function RuleForm({ rule, options, calendars, onDone }: {
  readonly rule: SlaRule | null;
  readonly options: SlaOptions;
  readonly calendars: readonly SlaCalendar[];
  readonly onDone: (saved: SlaRule | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reasons = useReasons();
  const [name, setName] = useState(rule?.name ?? '');
  const [projectId, setProjectId] = useState(rule?.projectId ?? options.projects[0]?.id ?? '');
  const [customerId, setCustomerId] = useState(rule?.customerId ?? '');
  const [ticketType, setTicketType] = useState(rule?.ticketType ?? '');
  const [calendarId, setCalendarId] = useState(rule?.calendarId ?? calendars[0]?.id ?? '');
  const initial = Object.fromEntries(SLA_PRIORITIES.map((priority) => {
    const value = (rule?.budgets ?? DEFAULT_BUDGETS)[priority];
    return [priority, { firstResponse: String(value.firstResponse), resolution: String(value.resolution) }];
  })) as Record<string, { firstResponse: string; resolution: string }>;
  const [budgets, setBudgets] = useState(initial);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  useUnsavedGuard(name !== (rule?.name ?? ''));
  const project = options.projects.find((item) => item.id === projectId);
  const customers = options.customers.filter((customer) => customer.projects.includes(projectId));
  const minutes = (value: string | undefined) => (/^[0-9]{1,6}$/.test((value ?? '').trim()) ? Number((value ?? '').trim()) : -1);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    const values = Object.fromEntries(SLA_PRIORITIES.map((priority) => [priority, {
      firstResponse: minutes(budgets[priority]?.firstResponse), resolution: minutes(budgets[priority]?.resolution),
    }])) as SlaBudgets;
    const body = {
      name: name.trim(), projectId, customerId: customerId === '' ? null : customerId,
      ticketType: customerId === '' || ticketType === '' ? null : ticketType, calendarId, budgets: values,
    };
    setBusy(true);
    const result = rule === null ? await createSlaRule(api, body) : await updateSlaRule(api, rule.id, rule.version, body);
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: reasons(result.problem) });
    onDone(result.data.value);
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate data-testid="rule-form">
      <h3>{rule === null ? t('sla.addRule') : t('sla.editRule', { name: rule.name })}</h3>
      <TextField label={t('sla.name')} value={name} onChange={setName} required maxLength={80} />
      <SelectField label={t('sla.project')} value={projectId} disabled={rule !== null}
        onChange={(value) => { setProjectId(value); setCustomerId(''); setTicketType(''); }}
        options={options.projects.map((item) => ({ value: item.id, label: `${item.key} ${item.name}` }))}
        {...(rule !== null ? { hint: t('sla.projectFixed') } : {})} />
      <SelectField label={t('sla.customer')} value={customerId} onChange={(value) => { setCustomerId(value); if (value === '') setTicketType(''); }}
        options={[{ value: '', label: t('sla.allCustomers') }, ...customers.map((item) => ({ value: item.id, label: item.name }))]} />
      {customerId !== '' && (
        <SelectField label={t('sla.ticketType')} value={ticketType} onChange={setTicketType}
          options={[{ value: '', label: t('sla.allTypes') }, ...(project?.ticketTypes ?? []).map((item) => ({ value: item.key, label: item.name }))]} />
      )}
      <SelectField label={t('sla.calendar')} value={calendarId} onChange={setCalendarId}
        options={calendars.map((item) => ({ value: item.id, label: item.name }))} />
      <fieldset>
        <legend>{t('sla.budgets')}</legend>
        <p className="hint">{t('sla.budgetsHint')}</p>
        <div className="table-scroll">
          <table>
            <thead>
              <tr><th scope="col">{t('field.priority')}</th><th scope="col">{t('sla.firstResponse')}</th><th scope="col">{t('sla.resolution')}</th></tr>
            </thead>
            <tbody>
              {SLA_PRIORITIES.map((priority) => (
                <tr key={priority} data-priority={priority}>
                  <th scope="row">{t(`priority.${priority}` as MessageKey)}</th>
                  {(['firstResponse', 'resolution'] as const).map((kind) => (
                    <td key={kind}>
                      <input type="text" inputMode="numeric" maxLength={6} value={budgets[priority]?.[kind] ?? ''}
                        aria-label={t('sla.budgetField', { priority: t(`priority.${priority}` as MessageKey), kind: t(kind === 'firstResponse' ? 'sla.firstResponse' : 'sla.resolution') })}
                        onChange={(event) => {
                          const value = event.target.value;
                          setBudgets((current) => ({ ...current, [priority]: { ...(current[priority] ?? { firstResponse: '', resolution: '' }), [kind]: value } }));
                        }} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </fieldset>
      <p className="actions">
        <button type="submit" disabled={busy}>{t('sla.save')}</button>
        <button type="button" className="secondary" onClick={() => onDone(null)}>{t('sla.cancel')}</button>
      </p>
      <NoticeView notice={notice} />
    </form>
  );
}
