import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-044: the configuration editor with draft and publication, custom fields in the ticket, the required fields of a step
// and the assignment of tickets whose status goes away. Against the in-memory backend that follows the contracts; the rules
// themselves are tested against the real backend in ProjectConfigurationApiTest.

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

async function withApp(hash, body) {
  const backend = createBackend();
  const session = await open(browser, `${base()}${hash(backend)}`, {
    locale: 'de-DE', setup: (context, page) => backend.install(context, page, { as: ANNA }),
  });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const settings = (backend) => `#/projects/${backend.project.id}/settings`;

describe('configuration editor', () => {
  test('a custom field is drafted, kept over a reload, published and then required for a step', async () => {
    await withApp(settings, async (page, backend) => {
      const fields = page.getByTestId('custom-fields');
      await fields.getByLabel('Neues Feld').fill('Interne Ursache');
      await fields.getByRole('button', { name: 'Hinzufügen' }).click();
      const steps = page.getByTestId('transitions').first();
      await steps.locator('li', { hasText: 'In Arbeit nach Erledigt' }).getByLabel('Interne Ursache').check();
      await page.getByRole('button', { name: 'Entwurf speichern' }).click();
      await page.getByText('Entwurf gespeichert.').waitFor();
      assert.ok(backend.state.drafts.get(backend.project.id), 'the draft is on the server');
      assert.equal(backend.state.configurations.get(backend.project.id).version, 1, 'and not in force');

      await page.reload();
      await page.getByText('Entwurf auf Grundlage der veröffentlichten Version 1.').waitFor();
      assert.equal(await page.getByTestId('custom-field-interne_ursache').count(), 1, 'the draft is back after a reload');

      await page.getByRole('button', { name: 'Veröffentlichen' }).click();
      await page.getByText('Version 2 ist veröffentlicht und gilt jetzt für alle Tickets.').waitFor();
      assert.equal(backend.state.drafts.size, 0, 'publishing consumes the draft');
      // The type of a published field can no longer be changed.
      await page.getByText('Typ: Kurztext (nicht mehr änderbar)').waitFor();

      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/1`);
      await page.waitForSelector('[data-field="custom.interne_ursache"]');
      const status = page.getByLabel('Status', { exact: true });
      await status.selectOption({ label: 'In Arbeit' });
      await page.getByRole('button', { name: 'Änderungen speichern' }).click();
      await page.getByText('Gespeichert.').waitFor();
      await status.selectOption({ label: 'Erledigt' });
      await page.getByTestId('transition-requires').getByText('Für diesen Statuswechsel nötig: Interne Ursache').waitFor();
      await page.getByRole('button', { name: 'Änderungen speichern' }).click();
      await page.getByText('Interne Ursache: wird für diesen Statuswechsel benötigt.').waitFor();
      assert.equal(backend.state.tickets[0].values.status, 'in_progress', 'the refused step changed nothing');

      await page.getByLabel('Interne Ursache', { exact: true }).fill('Treiber veraltet');
      await page.getByRole('button', { name: 'Änderungen speichern' }).click();
      await page.getByText('Gespeichert.').waitFor();
      assert.equal(backend.state.tickets[0].values.status, 'done');
      assert.equal(backend.state.tickets[0].custom.interne_ursache, 'Treiber veraltet');
      await page.getByTestId('history').getByText('Interne Ursache: Leer wird Treiber veraltet').waitFor();
    });
  });

  test('a status that tickets use goes only with an assignment, which moves them', async () => {
    await withApp(settings, async (page, backend) => {
      const statuses = page.locator('fieldset', { has: page.locator('legend', { hasText: /^Status$/ }) });
      await statuses.getByRole('button', { name: 'Neu entfernen' }).click();
      await page.getByRole('button', { name: 'Veröffentlichen' }).click();
      const panel = page.getByTestId('mapping-panel');
      await panel.waitFor();
      assert.equal(backend.state.configurations.get(backend.project.id).version, 1, 'nothing is published without the assignment');
      await panel.getByLabel('Status Neu (2 Tickets) wechselt zu').selectOption({ label: 'Erledigt' });
      await panel.getByRole('button', { name: 'Zuordnung bestätigen und veröffentlichen' }).click();
      await page.getByText('Version 2 ist veröffentlicht und gilt jetzt für alle Tickets.').waitFor();
      assert.deepEqual(backend.state.tickets.map((ticket) => ticket.values.status), ['done', 'done']);
      assert.ok(backend.state.history.some((entry) => entry.changes.some((change) => change.field === 'status' && change.from === 'new' && change.to === 'done')));
    });
  });

  test('a draft that starts from an older version cannot be saved over a newer one', async () => {
    await withApp(settings, async (page, backend) => {
      // Someone else published meanwhile.
      backend.state.configurations.get(backend.project.id).version = 2;
      await page.getByTestId('configuration-editor').getByLabel('Neuer Tickettyp').fill('Epos');
      await page.getByTestId('configuration-editor').getByRole('button', { name: 'Hinzufügen' }).first().click();
      await page.getByRole('button', { name: 'Entwurf speichern' }).click();
      await page.getByText('Seit diesem Entwurf wurde eine neuere Version veröffentlicht.', { exact: false }).waitFor();
      assert.equal(backend.state.drafts.size, 0);
    });
  });
});
