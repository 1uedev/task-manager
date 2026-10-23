import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-045: the state of the one time password, the display name, the end of a waiting time and the log of the account
// administration, against the in-memory backend that follows the contract.

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

async function withApp(prepare, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}#/accounts`, { locale: 'de-DE', setup: (context, page) => backend.install(context, page, { as: ANNA }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await session.page.getByTestId('account-table').waitFor();
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('account administration', () => {
  test('the list says what became of the one time password', async () => {
    await withApp(null, async (page) => {
      const nora = page.locator('tr', { hasText: 'nora' });
      assert.match(await nora.innerText(), /Einmalpasswort offen bis/);
      assert.match(await page.locator('tr', { hasText: 'anna' }).innerText(), /Aktiv/);
    });
  });

  test('the display name is changed in the row and shows in the log', async () => {
    await withApp(null, async (page, backend) => {
      await page.getByRole('button', { name: 'Anzeigename von ben ändern' }).click();
      await page.getByLabel('Anzeigename von ben').fill('Benedikt Leser');
      await page.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Der Anzeigename von ben ist geändert.').waitFor();
      assert.equal(backend.state.accounts.find((item) => item.username === 'ben').displayName, 'Benedikt Leser');
      assert.match(await page.locator('tr', { hasText: 'ben' }).innerText(), /Benedikt Leser/);
      await page.getByTestId('accounts-audit').getByText('Anzeigename von ben geändert').waitFor();
    });
  });

  test('an unsaved display name asks before the page is left', async () => {
    await withApp(null, async (page) => {
      await page.getByRole('button', { name: 'Anzeigename von ben ändern' }).click();
      await page.getByLabel('Anzeigename von ben').fill('Halbfertig');
      let asked = '';
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?');
    });
  });

  test('a running waiting time is shown and can be ended', async () => {
    await withApp((backend) => { backend.ben.delayedUntil = '2026-10-23T20:00:00Z'; }, async (page, backend) => {
      assert.match(await page.locator('tr', { hasText: 'ben' }).innerText(), /Wartezeit nach Fehlversuchen bis/);
      await page.getByRole('button', { name: 'Wartezeit von ben aufheben' }).click();
      await page.getByText('Die Wartezeit von ben ist aufgehoben; die nächste Anmeldung zählt von vorn.').waitFor();
      assert.equal(await page.getByRole('button', { name: 'Wartezeit von ben aufheben' }).count(), 0);
      assert.doesNotMatch(await page.locator('tr', { hasText: 'ben' }).innerText(), /Wartezeit/);
      assert.equal(backend.ben.delayedUntil, null);
      await page.getByTestId('accounts-audit').getByText('Wartezeit von ben aufgehoben').waitFor();
    });
  });

  test('the log pages back to its first entry', async () => {
    await withApp((backend) => {
      for (let i = 0; i < 55; i += 1) {
        backend.state.accountAudit.push({ id: i + 1, occurredAt: '2026-10-22T08:00:00Z', actor: 'anna', event: 'account.created', accountId: backend.ben.id, detail: null });
      }
    }, async (page) => {
      const items = page.getByTestId('accounts-audit').locator('li');
      await items.first().waitFor();
      assert.equal(await items.count(), 50);
      await page.getByRole('button', { name: 'Ältere Einträge laden' }).click();
      await page.getByText('Das sind alle Einträge.').waitFor();
      assert.equal(await items.count(), 55);
    });
  });
});
