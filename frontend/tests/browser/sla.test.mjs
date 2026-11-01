import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-068: calendars and SLA rules for holders of sla.manage, and the priority and deadlines of a ticket after its field
// rules; TASK-070: the escalation stages of a rule, the list "SLA Eskalationen" and the hint at the ticket, against the
// in-memory backend that follows the contract.

let server;
let browser;
before(async () => {
  server = await startServer();
  browser = await launch();
});
after(async () => {
  await browser?.close();
  await server?.close();
});

const base = () => server.url.replace('127.0.0.1', 'localhost');
const SLA_GROUP = '00000000-0000-4000-8000-00000000a10a';

// Anna gets sla.manage and the rules of the field priority in a group of their own, as an administration would grant them.
function withSlaRights(backend) {
  backend.state.groups.push({ id: SLA_GROUP, name: 'Servicefristen', members: [backend.anna.id], actions: [{ action: 'sla.manage', projectId: null }],
    fields: ['priority', 'customer'].flatMap((field) => ['read', 'write'].map((operation) => ({ resourceType: 'ticket', field, projectId: null, operation, effect: 'allow' }))) });
}

// A cycle as the server reports it, with reached stages for the given groups.
const cycle = (escalations, extra = {}) => ({
  number: 1, ruleId: '00000000-0000-4000-8000-00000000e001', ruleName: 'Standard', ruleVersion: 1, calendarId: '00000000-0000-4000-8000-00000000e002',
  calendarName: 'Bürozeiten', calendarVersion: 1, timeZone: 'Europe/Berlin', priority: 'normal', startedAt: '2026-10-30T08:00:00Z', outcome: null, reason: null, closedAt: null,
  firstResponse: { kind: 'first_response', state: 'running', breached: false, budgetSeconds: 3600, remainingSeconds: 600, dueAt: '2026-10-30T09:00:00Z',
    pausedAt: null, metAt: null, breachedAt: null },
  resolution: { kind: 'resolution', state: 'running', breached: false, budgetSeconds: 28800, remainingSeconds: 25000, dueAt: '2026-10-30T16:00:00Z',
    pausedAt: null, metAt: null, breachedAt: null },
  escalations, ...extra,
});
const stage = (groupId, percent, extra = {}) => ({ kind: 'first_response', percent, groupId, groupName: 'Servicefristen', thresholdAt: '2026-10-30T08:48:00Z',
  reachedAt: '2026-10-30T08:48:30Z', ...extra });

async function withApp({ prepare = withSlaRights, as = ANNA, hash = '#/sla' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('service deadlines', () => {
  test('without sla.manage there is no entry and nothing is asked', async () => {
    await withApp({ prepare: null }, async (page, backend) => {
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'Servicefristen' }).count(), 0);
      assert.equal(backend.state.requests.filter((request) => request.path.startsWith('/api/v1/sla')).length, 0);
    });
  });

  test('a calendar and a rule are created, a refusal names its reason', async () => {
    await withApp({ prepare: (backend) => {
      withSlaRights(backend);
      backend.state.helpdesk.customers.push({ id: '00000000-0000-4000-8000-00000000c001', name: 'Nordwerk', projects: [backend.project.id], version: 1 });
    } }, async (page, backend) => {
      await page.getByRole('navigation').getByRole('link', { name: 'Servicefristen' }).waitFor();
      await page.getByRole('button', { name: 'Regel anlegen' }).waitFor();
      assert.equal(await page.getByRole('button', { name: 'Regel anlegen' }).isDisabled(), true, 'a rule needs a calendar first');

      await page.getByRole('button', { name: 'Kalender anlegen' }).click();
      const calendarForm = page.getByTestId('calendar-form');
      await calendarForm.getByLabel('Name').fill('Bürozeiten');
      await calendarForm.getByLabel('Samstag').fill('10:00-xx');
      await calendarForm.getByRole('button', { name: 'Speichern' }).click();
      await calendarForm.getByText('Eine Zeitspanne der Woche ist ungültig.').waitFor();
      await calendarForm.getByLabel('Samstag').fill('');
      await calendarForm.getByLabel('Ausnahmen').fill('2026-12-24 08:00-12:00\n2026-12-25');
      await calendarForm.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Bürozeiten wurde gespeichert.').waitFor();
      const calendar = backend.state.sla.calendars[0];
      assert.equal(calendar.timeZone, 'Europe/Berlin');
      assert.deepEqual(calendar.week.map((day) => day.day), [1, 2, 3, 4, 5]);
      assert.deepEqual(calendar.week[0].hours, [{ from: '08:00', to: '17:00' }]);
      assert.deepEqual(calendar.exceptions, [{ date: '2026-12-24', hours: [{ from: '08:00', to: '12:00' }] }, { date: '2026-12-25', hours: [] }]);

      await page.getByRole('button', { name: 'Regel anlegen' }).click();
      const ruleForm = page.getByTestId('rule-form');
      await ruleForm.getByLabel('Name').fill('Nordwerk Standard');
      await ruleForm.getByLabel('Kunde').selectOption({ label: 'Nordwerk' });
      await ruleForm.getByLabel('Tickettyp').selectOption({ label: 'Fehler' });
      await ruleForm.getByLabel('Kritisch, Erste Antwort').fill('0');
      await ruleForm.getByRole('button', { name: 'Speichern' }).click();
      await ruleForm.getByText('Fristen müssen ganze Minuten zwischen 1 und 525600 sein.').waitFor();
      await ruleForm.getByLabel('Kritisch, Erste Antwort').fill('15');
      await ruleForm.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Nordwerk Standard wurde gespeichert.').waitFor();
      const rule = backend.state.sla.rules[0];
      assert.equal(rule.ticketType, 'bug');
      assert.equal(rule.calendarId, calendar.id);
      assert.deepEqual(rule.budgets.critical, { firstResponse: 15, resolution: 240 });
      await page.locator('[data-rule="Nordwerk Standard"]').getByText('Kunde Nordwerk, Typ Fehler').waitFor();
    });
  });

  test('the ticket shows priority and deadlines and the priority can be changed', async () => {
    await withApp({ hash: '#/start' }, async (page, backend) => {
      const ticket = backend.state.tickets[0];
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: '00000000-0000-4000-8000-00000000c001', contactId: null });
      backend.addCycle(ticket, {
        number: 1, ruleId: '00000000-0000-4000-8000-00000000e001', ruleName: 'Standard', ruleVersion: 1, calendarId: '00000000-0000-4000-8000-00000000e002',
        calendarName: 'Bürozeiten', calendarVersion: 1, timeZone: 'Europe/Berlin', priority: 'normal', startedAt: '2026-10-30T08:00:00Z', outcome: null, reason: null, closedAt: null,
        firstResponse: { kind: 'first_response', state: 'running', breached: true, budgetSeconds: 3600, remainingSeconds: -1800, dueAt: '2026-10-30T09:00:00Z',
          pausedAt: null, metAt: null, breachedAt: '2026-10-30T09:00:00Z' },
        resolution: { kind: 'resolution', state: 'paused', breached: false, budgetSeconds: 28800, remainingSeconds: 19800, dueAt: null,
          pausedAt: '2026-10-30T09:30:00Z', metAt: null, breachedAt: null },
      });
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets/1`; }, backend.project.id);
      const panel = page.getByTestId('ticket-sla');
      await panel.waitFor();
      await panel.getByText('Normal').waitFor();
      const first = panel.locator('[data-clock="first_response"]');
      assert.equal(await first.getAttribute('data-breached'), 'true');
      assert.match(await first.innerText(), /überschritten seit .*, 0 h 30 min drüber/);
      assert.match(await panel.locator('[data-clock="resolution"]').innerText(), /pausiert seit .*, Rest 5 h 30 min/);

      await panel.getByRole('button', { name: 'Ändern' }).click();
      await panel.getByLabel('Priorität').selectOption('critical');
      await panel.getByText('Die Priorität wurde gespeichert.').waitFor();
      assert.equal(backend.state.sla.priorities.get(ticket.id), 'critical');
      await page.getByTestId('history').getByText('Priorität: Normal wird Kritisch').waitFor();
    });
  });

  test('without the read rule of customer only the priority shows, without priority nothing', async () => {
    await withApp({ prepare: (backend) => {
      backend.state.groups.find((group) => group.name === 'Lesen').fields.push({ resourceType: 'ticket', field: 'priority', projectId: null, operation: 'read', effect: 'allow' });
    }, as: BEN, hash: '#/projects' }, async (page, backend) => {
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets/1`; }, backend.project.id);
      const panel = page.getByTestId('ticket-sla');
      await panel.waitFor();
      await panel.getByText('Die Fristen sind für Sie verborgen.').waitFor();
      assert.equal(await panel.getByRole('button', { name: 'Ändern' }).count(), 0, 'no change without the write rule');
      backend.state.groups.find((group) => group.name === 'Lesen').fields.pop();
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets/2`; }, backend.project.id);
      await page.getByRole('heading', { level: 1, name: /Suche nach Tags/ }).waitFor();
      assert.equal(await page.getByTestId('ticket-sla').count(), 0);
    });
  });
  test('a new rule proposes the stages 80 and 100 percent, a refused stage names its reason', async () => {
    await withApp({}, async (page, backend) => {
      backend.state.sla.calendars.push({ id: '00000000-0000-4000-8000-00000000e002', name: 'Bürozeiten', timeZone: 'Europe/Berlin',
        week: [{ day: 1, hours: [{ from: '08:00', to: '17:00' }] }], exceptions: [], version: 1 });
      await page.reload();
      await page.getByRole('button', { name: 'Regel anlegen' }).click();
      const editor = page.getByTestId('stage-editor');
      await editor.waitFor();
      assert.equal(await editor.locator('[data-stage]').count(), 4);
      assert.equal(await editor.getByLabel('Prozent der Stufe 1').inputValue(), '80');
      assert.equal(await editor.getByLabel('Prozent der Stufe 2').inputValue(), '100');
      assert.equal(await editor.getByLabel('Uhr der Stufe 3').inputValue(), 'resolution');
      const groups = await editor.getByLabel('Gruppe der Stufe 1').locator('option').allInnerTexts();
      assert.deepEqual(groups, ['Lesen', 'Servicefristen', 'Unternehmensverwaltung'], 'the groups of the rights administration');

      const form = page.getByTestId('rule-form');
      await form.getByLabel('Name').fill('Mit Stufen');
      await editor.getByLabel('Prozent der Stufe 1').fill('0');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await form.getByText('Jede Stufe braucht eine Uhr und einen Anteil von 1 bis 1000 Prozent.').waitFor();
      await editor.getByLabel('Prozent der Stufe 1').fill('90');
      await editor.getByLabel('Gruppe der Stufe 2').selectOption({ label: 'Servicefristen' });
      await editor.getByRole('button', { name: 'Stufe 4 entfernen' }).click();
      await editor.getByRole('button', { name: 'Stufe 3 entfernen' }).click();
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Mit Stufen wurde gespeichert.').waitFor();
      const rule = backend.state.sla.rules[0];
      assert.deepEqual(rule.escalations, [{ clock: 'first_response', percent: 90, groupId: backend.group('Lesen').id },
        { clock: 'first_response', percent: 100, groupId: SLA_GROUP }]);
      await page.locator('[data-rule="Mit Stufen"]').getByText('Erste Antwort 100 %, Servicefristen').waitFor();

      await page.locator('[data-rule="Mit Stufen"]').getByRole('button', { name: 'Bearbeiten' }).click();
      assert.equal(await page.getByTestId('stage-editor').locator('[data-stage]').count(), 2, 'an existing rule keeps its stages');
    });
  });

  test('the list shows the reached stages of the own groups and leads to the ticket with its hint', async () => {
    await withApp({ hash: '#/start', prepare: (backend) => {
      withSlaRights(backend);
      const ticket = backend.state.tickets[0];
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: '00000000-0000-4000-8000-00000000c001', contactId: null });
      backend.addCycle(ticket, cycle([stage(SLA_GROUP, 80), stage(backend.group('Lesen').id, 100, { groupName: 'Lesen' })]));
    } }, async (page, backend) => {
      await page.getByRole('navigation').getByRole('link', { name: 'SLA Eskalationen' }).click();
      const list = page.getByTestId('escalation-list');
      await list.waitFor();
      assert.equal(await list.locator('li').count(), 1, 'only the stages of groups of the person');
      await list.getByText('Erste Antwort: 80 % der Frist erreicht, Gruppe Servicefristen').waitFor();
      await list.getByRole('link', { name: `${backend.project.key}-1` }).click();
      const hints = page.getByTestId('sla-escalations');
      await hints.waitFor();
      assert.equal(await hints.locator('li').count(), 1);
      assert.match(await hints.innerText(), /Eskalation Erste Antwort: 80 % der Frist erreicht am .*, Gruppe Servicefristen/);
    });
  });

  test('without a group with stages or without the read rules the list is empty', async () => {
    await withApp({ as: BEN, hash: '#/escalations', prepare: (backend) => {
      const ticket = backend.state.tickets[0];
      backend.addCycle(ticket, cycle([stage(backend.group('Lesen').id, 80, { groupName: 'Lesen' })]));
    } }, async (page) => {
      await page.getByText('Keine Eskalationen für Ihre Gruppen.').waitFor();
      assert.equal(await page.getByTestId('escalation-list').count(), 0, 'Ben is in the group but may not read customer and priority');
    });
  });
  test('the list shows at most 200 entries and a stage without clock is refused like on the server', async () => {
    await withApp({ hash: '#/escalations', prepare: (backend) => {
      withSlaRights(backend);
      const ticket = backend.state.tickets[0];
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: '00000000-0000-4000-8000-00000000c001', contactId: null });
      const stages = Array.from({ length: 201 }, (_, index) => stage(SLA_GROUP, index + 1, { reachedAt: new Date(Date.UTC(2026, 9, 30, 8, index)).toISOString() }));
      backend.addCycle(ticket, cycle(stages));
    } }, async (page, backend) => {
      const list = page.getByTestId('escalation-list');
      await list.waitFor();
      assert.equal(await list.locator('li').count(), 200);
      assert.equal(await list.locator('li').first().getAttribute('data-percent'), '201', 'newest first');
      const refused = await page.evaluate(async (groupId) => {
        const csrf = document.cookie.split('; ').find((item) => item.startsWith('__Host-tm-csrf='))?.split('=')[1] ?? '';
        const response = await fetch('/api/v1/sla/rules', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
          body: JSON.stringify({ name: 'Ohne Uhr', projectId: 'x', calendarId: 'x', budgets: {}, escalations: [{ clock: null, percent: 80, groupId }] }) });
        return { status: response.status, body: await response.json() };
      }, SLA_GROUP);
      assert.equal(refused.status, 400);
      assert.ok(refused.body.violations.includes('escalations.invalid'), JSON.stringify(refused.body));
      assert.ok(backend.state.sla.rules.length === 0);
    });
  });
});
