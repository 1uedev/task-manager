import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend, newTag } from './backend.mjs';
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
      // With the keyboard: the button disappears with the overview, the focus goes to the search field of the area (TASK-110, F6).
      await form.getByLabel('Suchtext').fill('anderes');
      await tickets.getByRole('button', { name: 'Alle anzeigen: Tickets' }).focus();
      await page.keyboard.press('Enter');
      assert.equal(await page.getByRole('radio', { name: 'Tickets' }).isChecked(), true);
      const area = page.getByRole('search', { name: 'Tickets durchsuchen' });
      assert.equal(await area.getByLabel('Suchtext').inputValue(), 'nordlicht', 'the text that was searched, not the one typed since');
      await page.waitForFunction(() => document.activeElement?.closest('form[role=search]')?.getAttribute('aria-label') === 'Tickets durchsuchen');
      assert.equal(await area.getByLabel('Suchtext').evaluate((element) => element === document.activeElement), true);
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

// TASK-110: the findings F1 to F4 of the Codex review of TASK-104 at the quick search, F2 at the ticket page and F7 at the stand in.
describe('the quick search after the review of TASK-104 (TASK-110)', () => {
  test('F1: unsaved input asks first, and declining keeps the page, the input and the text of the quick search', async () => {
    await withApp({ hash: (backend) => `#/projects/${backend.project.id}/tickets/1` }, async (page) => {
      await page.waitForSelector('[data-testid=history]');
      await page.getByLabel(/^Titel/).fill('Noch nicht gespeichert');
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('plat-2');
      let asked = null;
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await quick.press('Enter');
      await page.waitForFunction(() => document.querySelector('#quick-search')?.getAttribute('aria-busy') === 'false');
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?');
      assert.match(page.url(), /tickets\/1$/);
      assert.equal(await page.getByLabel(/^Titel/).inputValue(), 'Noch nicht gespeichert');
      assert.equal(await quick.inputValue(), 'plat-2');

      page.once('dialog', (dialog) => void dialog.accept());
      await quick.press('Enter');
      await page.waitForSelector('h1:has-text("PLAT-2")');
      assert.match(page.url(), /tickets\/2$/);
      assert.equal(await quick.inputValue(), '');
    });
  });

  test('F3: a later change of the page drops the outstanding answer of the quick search', async () => {
    await withApp({ hash: '#/start' }, async (page, backend) => {
      let release;
      backend.state.hold = { '/api/v1/search/all': new Promise((resolve) => { release = resolve; }) };
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('plat-1');
      await quick.press('Enter');
      await page.waitForFunction(() => document.querySelector('#quick-search')?.getAttribute('aria-busy') === 'true');
      await page.locator('nav a[href="#/projects"]').first().click();
      await page.waitForFunction(() => location.hash === '#/projects');
      backend.state.hold = {};
      release();
      await page.waitForFunction(() => document.querySelector('#quick-search')?.getAttribute('aria-busy') === 'false');
      await page.waitForTimeout(500);
      assert.equal(new URL(page.url()).hash, '#/projects', 'the late answer does not open the ticket');
      assert.equal(await page.locator('main h1').innerText(), 'Projekte');
    });
  });

  test('F4: the same address opens "Alle" again with the text of the quick search', async () => {
    await withApp({ prepare: polarlicht, hash: '#/search/all?q=polarlicht' }, async (page) => {
      await page.getByTestId('overview').waitFor();
      const form = page.getByRole('search', { name: 'Alles durchsuchen' });
      await form.getByLabel('Suchtext').fill('tags');
      await form.getByRole('button', { name: 'Suchen' }).click();
      await page.waitForFunction(() => /PLAT-2/.test(document.querySelector('[data-testid=overview-tickets]')?.textContent ?? ''));
      await page.getByRole('radio', { name: 'Tickets' }).check();
      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('polarlicht');
      await quick.press('Enter');
      await page.waitForFunction(() => document.querySelector('input[name=search-area][value=all]')?.checked === true);
      assert.equal(new URL(page.url()).hash, '#/search/all?q=polarlicht');
      assert.equal(await page.getByRole('search', { name: 'Alles durchsuchen' }).getByLabel('Suchtext').inputValue(), 'polarlicht');
      await page.waitForFunction(() => /Polarlicht prüfen/.test(document.querySelector('[data-testid=overview-tickets]')?.textContent ?? '')
        && !/PLAT-2/.test(document.querySelector('[data-testid=overview-tickets]')?.textContent ?? ''));
      assert.equal(await quick.inputValue(), '');
    });
  });

  test('F2: who may read a ticket but not its project opens the ticket with the data it allows', async () => {
    const ticketOnly = (backend) => {
      backend.state.groups.find((group) => group.name === 'Lesen').actions = [{ action: 'ticket.read', projectId: null }];
    };
    await withApp({ as: BEN, hash: '#/start', prepare: ticketOnly }, async (page, backend) => {
      // The stand in answers like the server: the ticket 200, the project 404, no project area and no project as direct hit.
      assert.equal(backend.getCall(backend.ben, `/projects/${backend.project.id}/tickets/1`).status, 200);
      assert.equal(backend.getCall(backend.ben, `/projects/${backend.project.id}`).status, 404);
      const overview = backend.getCall(backend.ben, '/search/all', 'q=PLAT').body;
      assert.equal(overview.projects, null);
      assert.equal(overview.direct, null);

      const quick = page.getByRole('searchbox', { name: 'Schnellsuche' });
      await quick.fill('plat-1');
      await quick.press('Enter');
      await page.waitForSelector('h1:has-text("PLAT-1 · Anmeldung prüfen")');
      assert.match(page.url(), new RegExp(`#/projects/${backend.project.id}/tickets/1$`));
      await page.getByTestId('ticket-project-hidden').waitFor();
      assert.equal(await page.locator('.breadcrumb').innerText(), 'PLAT');
      assert.equal(await page.locator('.breadcrumb a').count(), 0, 'no link to a project the person may not read');
      assert.match(await page.locator('[data-field=status]').innerText(), /new/);
      assert.equal(await page.getByRole('alert').count(), 0);
    });
  });
});

describe('the search of the stand in answers like the server (TASK-110, F7)', () => {
  const search = (backend, area, query) => backend.getCall(backend.anna, `/search/${area}`, query);

  test('only project.read or project.manage in a member project or projects.manage shows a project', () => {
    const backend = createBackend();
    backend.state.groups.find((group) => group.name === 'Lesen').actions = [{ action: 'ticket.read', projectId: null }];
    assert.equal(backend.getCall(backend.ben, '/projects').body.length, 0);
    assert.equal(backend.getCall(backend.ben, '/search/projects', 'q=plat').body.total, 0);
    backend.state.groups.find((group) => group.name === 'Lesen').actions.push({ action: 'project.manage', projectId: null });
    assert.equal(backend.getCall(backend.ben, '/search/projects', 'q=plat').body.total, 1);
  });

  test('a normalized ticket key finds its ticket first, a text without words finds nothing', () => {
    const backend = createBackend();
    for (const q of ['ＰＬＡＴ-０２', 'plat-002', ' PLAT-2 ']) {
      const found = search(backend, 'tickets', `q=${encodeURIComponent(q)}`).body;
      assert.deepEqual(found.items.map((hit) => hit.key), ['PLAT-2'], q);
      assert.equal(search(backend, 'all', `q=${encodeURIComponent(q)}`).body.direct?.key, 'PLAT-2', q);
    }
    assert.equal(search(backend, 'tickets', 'q=plat').body.total, 0, 'the key is not a word of the ticket');
    for (const area of ['tickets', 'projects']) assert.equal(search(backend, area, `q=${encodeURIComponent('!')}`).body.total, 0, area);
    assert.equal(search(backend, 'tickets', 'q=').body.total, 2, 'no text finds every readable ticket');
  });

  test('the ticket filters and the readable fields decide like the server', () => {
    const backend = createBackend();
    backend.state.tickets[1].values.status = 'in_progress';
    assert.deepEqual(search(backend, 'tickets', 'status=in_progress').body.items.map((hit) => hit.key), ['PLAT-2']);
    assert.deepEqual(search(backend, 'tickets', 'type=task&q=pr%C3%BCfen').body.items.map((hit) => hit.key), ['PLAT-1']);
    assert.equal(search(backend, 'tickets', 'project=00000000-0000-4000-8000-000000000999').status, 400);
    // Ben may not read descriptions: a word only there finds nothing, and so does a filter on a field he may not read.
    assert.equal(backend.getCall(backend.ben, '/search/tickets', 'q=synthetischen').body.total, 0);
    assert.equal(backend.getCall(backend.anna, '/search/tickets', 'q=synthetischen').body.total, 1);
    backend.state.groups.find((group) => group.name === 'Lesen').fields = backend.state.groups.find((group) => group.name === 'Lesen').fields
      .filter((rule) => rule.field !== 'status');
    assert.equal(backend.getCall(backend.ben, '/search/tickets', 'status=new').body.total, 0);
  });

  test('the project search finds the names of the tags of a project and the normalized key', () => {
    const backend = createBackend();
    const tag = newTag('Leuchtturm', null, backend.anna);
    backend.state.tags.push(tag);
    backend.state.projectTags.push({ project: backend.project.id, tag: tag.id });
    assert.deepEqual(search(backend, 'projects', 'q=leucht').body.items.map((hit) => hit.key), ['PLAT']);
    assert.equal(search(backend, 'projects', `q=${encodeURIComponent('ＰＬＡＴ')}`).body.total, 1);
  });
});
