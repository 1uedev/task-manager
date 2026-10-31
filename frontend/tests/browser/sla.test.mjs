import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-068: calendars and SLA rules for holders of sla.manage, and the priority and deadlines of a ticket after its field
// rules, against the in-memory backend that follows the contract.

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
});
