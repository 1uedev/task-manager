import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-104: the overview "Alle" with five hits per area and "Alle anzeigen", and the quick search at the top of the
// application with "/" and the direct opening of an exact key, against the in-memory backend that follows the contract.

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

const ALL = ['title', 'description', 'owner', 'priority', 'criteria'];

function grant(backend, accounts, actions, requirementFields = []) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000001040${String(backend.state.groups.length).padStart(2, '0')}`, name: `Suche ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })),
    fields: requirementFields.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'read', effect: 'allow' })) });
}

const doc = (text) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] });

/** One word in a ticket, the project, a published wiki page, a wiki draft and a current requirement. */
function polarlicht(backend) {
  grant(backend, [backend.anna], ['wiki.read', 'wiki.edit', 'requirement.read'], ALL);
  backend.state.tickets[0].values.title = 'Polarlicht prüfen';
  backend.project.description = 'Polarlicht Projekt';
  backend.addWikiPage(backend.anna, 'Polarlicht Handbuch', doc('Veröffentlicht'), { publish: true });
  backend.addWikiPage(backend.anna, 'Polarlicht Entwurf', doc('Noch offen'));
  backend.addRequirement(backend.anna, 'Polarlicht anzeigen', { criteria: ['Sichtbar'] });
}

async function withApp({ prepare, as = ANNA, hash = '#/search' }, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${typeof hash === 'function' ? hash(backend) : hash}`,
    { setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('the overview "Alle" (TASK-104)', () => {
  test('one word is found in a ticket, a project, a published wiki page and a current requirement, never in a draft', async () => {
    await withApp({ prepare: polarlicht }, async (page) => {
      assert.equal(await page.getByRole('radio', { name: 'Alle' }).isChecked(), true, '"Alle" is the first tab');
      const form = page.getByRole('search', { name: 'Alles durchsuchen' });
      await form.getByLabel('Suchtext').fill('polarlicht');
      await form.getByRole('button', { name: 'Suchen' }).click();
      await page.getByTestId('overview').waitFor();
      assert.match(await page.getByTestId('overview-tickets').innerText(), /PLAT-1\s+Polarlicht prüfen/);
      assert.match(await page.getByTestId('overview-projects').innerText(), /Plattform/);
      const wiki = await page.getByTestId('overview-wiki').innerText();
      assert.match(wiki, /Polarlicht Handbuch/);
      assert.doesNotMatch(wiki, /Polarlicht Entwurf/, 'the overview searches published pages only');
      assert.match(await page.getByTestId('overview-requirements').innerText(), /ANF-1[\s\S]*Polarlicht anzeigen/);
      assert.equal(await page.getByRole('button', { name: /^Alle anzeigen/ }).count(), 0, 'no area has more than five hits');
    });
  });

  test('areas without a right do not appear', async () => {
    await withApp({ as: BEN, prepare: polarlicht }, async (page) => {
      const form = page.getByRole('search', { name: 'Alles durchsuchen' });
      await form.getByLabel('Suchtext').fill('polarlicht');
      await form.getByRole('button', { name: 'Suchen' }).click();
      await page.getByTestId('overview').waitFor();
      assert.match(await page.getByTestId('overview-tickets').innerText(), /PLAT-1/);
      assert.equal(await page.getByTestId('overview-wiki').count(), 0);
      assert.equal(await page.getByTestId('overview-requirements').count(), 0);
    });
  });

  test('more than five hits offer "Alle anzeigen", which opens the area with the same text and its own paging', async () => {
    await withApp({
      prepare: (backend) => {
        // Seven tickets with the word: the two of the stand in and five more.
        const [first] = backend.state.tickets;
        for (let index = 0; index < 5; index += 1) {
          backend.state.tickets.push({ ...first, id: `00000000-0000-4000-8000-0000001041${String(index).padStart(2, '0')}`, number: 3 + index,
            key: `PLAT-${3 + index}`, values: { ...first.values } });
        }
        for (const ticket of backend.state.tickets) ticket.values = { ...ticket.values, title: `Nordlicht ${ticket.number}` };
      },
    }, async (page) => {
      const form = page.getByRole('search', { name: 'Alles durchsuchen' });
      await form.getByLabel('Suchtext').fill('nordlicht');
      await form.getByRole('button', { name: 'Suchen' }).click();
      const tickets = page.getByTestId('overview-tickets');
      await tickets.waitFor();
      assert.match(await tickets.getByRole('heading').innerText(), /^7 Tickets/);
      assert.equal(await tickets.locator('li').count(), 5);
      await tickets.getByRole('button', { name: 'Alle anzeigen: Tickets' }).click();
      assert.equal(await page.getByRole('radio', { name: 'Tickets' }).isChecked(), true);
      const area = page.getByRole('search', { name: 'Tickets durchsuchen' });
      assert.equal(await area.getByLabel('Suchtext').inputValue(), 'nordlicht');
      await page.waitForFunction(() => document.querySelectorAll('[data-testid=ticket-hits] li').length === 7);
    });
  });
});

describe('the quick search (TASK-104)', () => {
  test('"/" focuses the field, but not while typing in another field', async () => {
    await withApp({ hash: '#/search/tickets' }, async (page) => {
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await page.getByRole('search', { name: 'Tickets durchsuchen' }).getByLabel('Suchtext').fill('a/b');
      assert.equal(await page.getByRole('search', { name: 'Tickets durchsuchen' }).getByLabel('Suchtext').inputValue(), 'a/b');
      assert.equal(await quick.evaluate((element) => element === document.activeElement), false);
      await page.locator('main h1').click();
      await page.keyboard.press('/');
      assert.equal(await quick.evaluate((element) => element === document.activeElement), true);
      assert.equal(await quick.inputValue(), '', 'the slash is not typed into the field');
    });
  });

  test('an exact ticket, requirement or project key opens the object directly', async () => {
    let requirement;
    await withApp({
      hash: '#/start',
      prepare: (backend) => {
        polarlicht(backend);
        requirement = backend.state.requirements.items[0];
      },
    }, async (page, backend) => {
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('plat-2');
      await quick.press('Enter');
      await page.waitForSelector('h1:has-text("PLAT-2")');
      assert.match(page.url(), new RegExp(`#/projects/${backend.project.id}/tickets/2$`));

      await quick.fill('PLAT ANF-1');
      await quick.press('Enter');
      await page.getByTestId('requirement-title').waitFor();
      assert.match(page.url(), new RegExp(`#/requirements/${backend.project.id}/${requirement.id}$`));

      await quick.fill('PLAT');
      await quick.press('Enter');
      await page.waitForFunction((id) => location.hash === `#/projects/${id}`, backend.project.id);
    });
  });

  test('a key the person may not read and any other text open "Alle" with the text, without a hint', async () => {
    await withApp({ as: BEN, hash: '#/start', prepare: polarlicht }, async (page) => {
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('PLAT ANF-1');
      await quick.press('Enter');
      await page.waitForFunction(() => location.hash === '#/search/all?q=PLAT%20ANF-1');
      await page.getByTestId('overview').waitFor();
      assert.equal(await page.getByRole('radio', { name: 'Alle' }).isChecked(), true);
      assert.equal(await page.getByRole('search', { name: 'Alles durchsuchen' }).getByLabel('Suchtext').inputValue(), 'PLAT ANF-1');
      assert.equal(await page.getByTestId('overview-requirements').count(), 0, 'no area and no hint for the hidden requirement');

      await quick.fill('polarlicht');
      await quick.press('Enter');
      await page.waitForFunction(() => location.hash === '#/search/all?q=polarlicht');
      await page.waitForFunction(() => /PLAT-1/.test(document.querySelector('[data-testid=overview-tickets]')?.textContent ?? ''));
    });
  });
});
