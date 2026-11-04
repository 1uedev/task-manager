import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-069: time entries at a ticket and on the page "Zeiten" with the week of the person and the evaluation, after the
// actions time.book, time.read and time.manage, against the in-memory backend that follows the contract.

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

// A group of its own with the given time actions for the given accounts, as an administration would grant them.
function grant(backend, accounts, actions) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000000071${String(backend.state.groups.length).padStart(2, '0')}`, name: `Zeiten ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })), fields: [] });
}

async function withApp({ prepare, as = BEN, hash = '#/time' }, body) {
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

const ticketHash = (backend, number) => `#/projects/${backend.project.id}/tickets/${number}`;
const today = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

describe('time entries', () => {
  test('without a time action the ticket shows no times and the page offers nothing', async () => {
    await withApp({ hash: '#/time' }, async (page, backend) => {
      await page.getByText('Du darfst in keinem Projekt Zeiten buchen oder lesen.').waitFor();
      await page.goto(`${base()}${ticketHash(backend, 1)}`);
      await page.getByRole('heading', { name: 'Anmeldung prüfen' }).waitFor();
      await page.getByRole('heading', { name: 'Historie' }).waitFor();
      assert.equal(await page.getByTestId('ticket-time').count(), 0);
    });
  });

  test('a person books, corrects and cancels their own time at a ticket', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.ben], ['time.book']), hash: '' }, async (page, backend) => {
      await page.goto(`${base()}${ticketHash(backend, 1)}`);
      const panel = page.getByTestId('ticket-time');
      await panel.getByText('Auf dieses Ticket ist noch keine Zeit gebucht.').waitFor();
      await panel.getByText('Zeit auf dieses Ticket buchen').click();
      const form = panel.getByTestId('ticket-time-form');
      assert.equal(await form.getByLabel('Person').count(), 0, 'time.book alone books only for oneself');

      await form.getByLabel('Dauer').fill('15');
      await form.getByLabel('Beschreibung').fill('Anmeldung nachgestellt');
      await form.getByRole('button', { name: 'Buchen' }).click();
      await form.getByText('Bitte eine Dauer zwischen 1 Minute und 24 Stunden angeben, zum Beispiel 1:15.').waitFor();
      assert.equal(backend.state.time.length, 0, 'a bare number is refused before anything is sent');

      await form.getByLabel('Dauer').fill('1:15');
      await form.getByText('Erkannt: 1 Std. 15 Min.').waitFor();
      await form.getByRole('button', { name: 'Buchen' }).click();
      await form.getByText('Die Zeit ist gebucht.').waitFor();
      await panel.locator('[data-testid="time-total"][data-minutes="75"]').waitFor();
      assert.equal(backend.state.time[0].ticketId, backend.state.tickets[0].id);
      assert.equal(backend.state.time[0].personId, backend.ben.id);

      const entry = panel.getByTestId('time-entry');
      await entry.getByRole('button', { name: 'Korrigieren' }).click();
      const correct = entry.getByTestId('time-correct');
      await correct.getByText('Optional bei eigenen Einträgen.').waitFor();
      await correct.getByLabel('Dauer').fill('1h 30m');
      await correct.getByRole('button', { name: 'Korrektur speichern' }).click();
      await panel.locator('[data-testid="time-total"][data-minutes="90"]').waitFor();

      await panel.getByTestId('time-entry').getByRole('button', { name: 'Verlauf' }).click();
      const history = panel.getByTestId('time-history');
      await history.locator('li[data-kind="corrected"]').waitFor();
      assert.match(await history.locator('li[data-kind="created"]').innerText(), /Gebucht von Ben Leser.*1 Std\. 15 Min\./);
      await history.getByRole('button', { name: 'Verlauf schließen' }).click();

      await panel.getByTestId('time-entry').getByRole('button', { name: 'Stornieren' }).click();
      await panel.getByText('Ein stornierter Eintrag zählt in keiner Summe mehr, bleibt lesbar und kann nicht zurückgenommen werden.').waitFor();
      await panel.getByRole('button', { name: 'Eintrag stornieren' }).click();
      await panel.getByText('Auf dieses Ticket ist noch keine Zeit gebucht.').waitFor();
      assert.notEqual(backend.state.time[0].cancelledAt, null);
      assert.deepEqual(backend.state.time[0].revisions.map((revision) => revision.kind), ['created', 'corrected', 'cancelled']);
    });
  });

  test('a changed entry is not overwritten', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.ben], ['time.book']), hash: '' }, async (page, backend) => {
      await page.goto(`${base()}${ticketHash(backend, 1)}`);
      const panel = page.getByTestId('ticket-time');
      await panel.getByText('Zeit auf dieses Ticket buchen').click();
      const form = panel.getByTestId('ticket-time-form');
      await form.getByLabel('Dauer').fill('45m');
      await form.getByLabel('Beschreibung').fill('Prüfung');
      await form.getByRole('button', { name: 'Buchen' }).click();
      await panel.locator('[data-testid="time-total"][data-minutes="45"]').waitFor();
      backend.state.timeConflict = true;
      await panel.getByTestId('time-entry').getByRole('button', { name: 'Korrigieren' }).click();
      const correct = panel.getByTestId('time-correct');
      await correct.getByLabel('Dauer').fill('50m');
      await correct.getByRole('button', { name: 'Korrektur speichern' }).click();
      await correct.getByText('Der Eintrag wurde inzwischen geändert. Bitte neu laden und erneut versuchen.').waitFor();
      assert.equal(backend.state.time[0].minutes, 45);
    });
  });

  test('my week sums my own entries per day and the booking form names a future date', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.ben], ['time.book']) }, async (page, backend) => {
      const week = page.getByTestId('time-week');
      await week.locator('[data-testid="time-week-total"][data-minutes="0"]').waitFor();
      await week.getByText('Zeit buchen').click();
      const form = week.getByTestId('time-form');
      await form.getByLabel('Ticketnummer').fill('2');
      await form.getByLabel('Dauer').fill('2 h');
      await form.getByLabel('Beschreibung').fill('Suche getestet');
      await form.getByLabel('Datum').fill('2099-01-01');
      await form.getByRole('button', { name: 'Buchen' }).click();
      await form.getByText('Zeiten in der Zukunft können nicht gebucht werden.').waitFor();

      await form.getByLabel('Datum').fill(today());
      await form.getByRole('button', { name: 'Buchen' }).click();
      await form.getByText('Die Zeit ist gebucht.').waitFor();
      await week.locator('[data-testid="time-week-total"][data-minutes="120"]').waitFor();
      await week.locator(`[data-testid="time-days"] li[data-day="${today()}"][data-minutes="120"]`).waitFor();
      assert.match(await week.getByTestId('time-entry').innerText(), /PLAT-2 Suche nach Tags/);

      await week.getByRole('button', { name: 'Vorige Woche' }).click();
      await week.locator('[data-testid="time-week-total"][data-minutes="0"]').waitFor();
      await week.getByRole('button', { name: 'Diese Woche' }).click();
      await week.locator('[data-testid="time-week-total"][data-minutes="120"]').waitFor();
      assert.equal(backend.state.time.length, 1);
    });
  });

  test('time.manage books for a member, a foreign change needs a reason and the evaluation sums and exports', async () => {
    await withApp({ as: ANNA, prepare: (backend) => {
      grant(backend, [backend.anna], ['time.manage']);
      grant(backend, [backend.ben], ['time.book']);
    }, hash: '' }, async (page, backend) => {
      await page.goto(`${base()}${ticketHash(backend, 1)}`);
      const panel = page.getByTestId('ticket-time');
      await panel.getByText('Zeit auf dieses Ticket buchen').click();
      const form = panel.getByTestId('ticket-time-form');
      await form.getByLabel('Person').selectOption({ label: 'Ben Leser' });
      await form.getByLabel('Dauer').fill('1,5 h');
      await form.getByLabel('Beschreibung').fill('Für Ben nachgetragen');
      await form.getByRole('button', { name: 'Buchen' }).click();
      await form.getByText('Die Zeit ist gebucht.').waitFor();
      await form.getByLabel('Dauer').fill('30m');
      await form.getByLabel('Beschreibung').fill('Eigene Prüfung');
      await form.getByRole('button', { name: 'Buchen' }).click();
      await panel.locator('[data-testid="time-total"][data-minutes="120"]').waitFor();
      assert.deepEqual(backend.state.time.map((entry) => entry.personId).sort(), [backend.anna.id, backend.ben.id].sort());

      const foreign = panel.locator(`[data-testid="time-entry"][data-entry="${backend.state.time.find((entry) => entry.personId === backend.ben.id).id}"]`);
      await foreign.getByRole('button', { name: 'Korrigieren' }).click();
      const correct = foreign.getByTestId('time-correct');
      await correct.getByText('Bei Einträgen anderer Personen ist ein Grund nötig.').waitFor();
      await correct.getByLabel('Dauer').fill('1:00');
      await correct.getByRole('button', { name: 'Korrektur speichern' }).click();
      await correct.getByText('Bitte einen Grund angeben.').waitFor();
      await correct.getByLabel('Grund').fill('Pause abgezogen');
      await correct.getByRole('button', { name: 'Korrektur speichern' }).click();
      await panel.locator('[data-testid="time-total"][data-minutes="90"]').waitFor();
      assert.equal(backend.state.time.find((entry) => entry.personId === backend.ben.id).revisions.at(-1).reason, 'Pause abgezogen');

      await page.goto(`${base()}#/time`);
      const evaluation = page.getByTestId('time-evaluation');
      await evaluation.getByLabel('Projekt').selectOption({ label: 'PLAT · Plattform' });
      await evaluation.getByLabel('Person').waitFor();
      await evaluation.getByLabel('Von').fill('2026-01-01');
      await evaluation.getByLabel('Bis').fill(today());
      await evaluation.getByRole('button', { name: 'Anzeigen' }).click();
      await evaluation.locator('[data-testid="time-total"][data-minutes="90"]').waitFor();
      assert.deepEqual(await evaluation.locator('[data-testid="time-per-person"] li').evaluateAll((items) => items.map((item) => Number(item.dataset.minutes)).sort((a, b) => a - b)), [30, 60]);
      const href = await evaluation.getByTestId('time-export').getAttribute('href');
      assert.match(href, /^\/api\/v1\/time\/export\?/);
      const query = new URLSearchParams(href.split('?')[1]);
      assert.equal(query.get('project'), backend.project.id);
      assert.equal(query.get('from'), '2026-01-01');
      assert.equal(query.get('to'), today());

      await evaluation.getByLabel('Person').selectOption({ label: 'Ben Leser' });
      await evaluation.getByRole('button', { name: 'Anzeigen' }).click();
      await evaluation.locator('[data-testid="time-total"][data-minutes="60"]').waitFor();
      assert.equal(new URLSearchParams((await evaluation.getByTestId('time-export').getAttribute('href')).split('?')[1]).get('person'), backend.ben.id);
    });
  });
});
