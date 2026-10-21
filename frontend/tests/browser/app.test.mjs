import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, NORA, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-030: the continuous interface against an in-memory backend that follows the contracts. Two accounts with
// different rights, a first login, version conflicts, unsaved input, rights withdrawn while a page is open and an
// ended session. The real backend run is documented separately in docs/BROWSER_CHECK_TASK-030.md.

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

// localhost instead of 127.0.0.1, so that the browser accepts the __Host- cookie on plain HTTP.
const base = () => server.url.replace('127.0.0.1', 'localhost');

async function start({ backend = createBackend(), as = null, locale = 'de-DE', viewport, hash = '' } = {}) {
  const session = await open(browser, `${base()}${hash}`, {
    locale, ...(viewport ? { viewport } : {}),
    setup: (context, page) => backend.install(context, page, { as }),
  });
  if (as !== null && !as.pending) await session.page.waitForSelector('.shell main h1');
  return { ...session, backend };
}

async function withApp(options, body) {
  const session = await start(options);
  try {
    await body(session.page, session.backend, session.context);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const heading = (page) => page.locator('main h1').innerText();
const focusedText = (page) => page.evaluate(() => document.activeElement?.textContent?.trim() ?? '');
const ticketHash = (backend, number) => `#/projects/${backend.project.id}/tickets/${number}`;
const save = (page) => page.getByRole('button', { name: 'Änderungen speichern' });

describe('login', () => {
  test('the login page follows the browser language and the choice is remembered', async () => {
    await withApp({ locale: 'en-US' }, async (page) => {
      assert.equal(await heading(page), 'Sign in');
      assert.equal(await page.locator('html').getAttribute('lang'), 'en');
      assert.equal(await page.title(), 'Sign in · Task Manager');
      await page.locator('#language').selectOption('de');
      assert.equal(await heading(page), 'Anmelden');
      await page.reload();
      await page.waitForSelector('main h1');
      assert.equal(await heading(page), 'Anmelden');
      const stored = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage } }));
      assert.deepEqual(stored, { local: { 'taskmanager.language': 'de' }, session: {} }, 'no session data in browser storage');
    });
  });

  test('wrong credentials are reported, a correct login with the keyboard opens the start page', async () => {
    await withApp({}, async (page, backend) => {
      await page.getByLabel(/^Anmeldename/).fill(ANNA.username);
      await page.getByLabel(/^Passwort/).fill('falsch');
      await page.keyboard.press('Enter');
      assert.equal(await page.getByTestId('login-error').innerText(), 'Anmeldename oder Passwort stimmen nicht.');
      await page.getByLabel(/^Passwort/).fill(ANNA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('.shell');
      assert.equal(await heading(page), 'Willkommen, Anna Admin');
      assert.equal(await page.getByTestId('who').innerText(), 'Anna Admin');
      const login = backend.writes().find((item) => item.path === '/api/v1/session');
      assert.equal(login.csrf, 'csrf-token-for-browser-tests-0001', 'the login sends the CSRF token');
      assert.equal(JSON.stringify(await page.evaluate(() => ({ ...localStorage }))).includes(ANNA.password), false);
    });
  });

  test('the first login asks for an own password before anything else', async () => {
    await withApp({}, async (page) => {
      await page.getByLabel(/^Anmeldename/).fill(NORA.username);
      await page.getByLabel(/^Passwort/).fill(NORA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('h1:has-text("Eigenes Passwort festlegen")');
      assert.equal(await page.locator('.shell').count(), 0, 'no navigation before the password is changed');
      await page.getByLabel(/^Einmalpasswort/).fill(NORA.password);
      await page.getByLabel(/^Neues Passwort \(/).fill('kurz');
      await page.getByLabel(/^Neues Passwort wiederholen/).fill('anders');
      await page.getByRole('button', { name: 'Passwort speichern' }).click();
      assert.match(await page.locator('main').innerText(), /Bitte mindestens 12 Zeichen verwenden\./);
      assert.match(await page.locator('main').innerText(), /Die beiden Eingaben stimmen nicht überein\./);
      await page.getByLabel(/^Neues Passwort \(/).fill('Nora Passwort 2026');
      await page.getByLabel(/^Neues Passwort wiederholen/).fill('Nora Passwort 2026');
      await page.getByRole('button', { name: 'Passwort speichern' }).click();
      await page.waitForSelector('.shell');
      assert.equal(await heading(page), 'Willkommen, Nora Neu');
    });
  });

  test('signing out returns to the login with a note', async () => {
    await withApp({ as: ANNA }, async (page) => {
      await page.getByRole('button', { name: 'Abmelden' }).click();
      await page.waitForSelector('h1:has-text("Anmelden")');
      assert.equal(await page.getByRole('status').innerText(), 'Sie sind abgemeldet.');
    });
  });

  test('a session the server ended leads back to the login on the next request', async () => {
    await withApp({ as: BEN }, async (page, backend) => {
      backend.deactivate(backend.ben);
      await page.getByRole('link', { name: 'Projekte', exact: true }).click();
      await page.waitForSelector('h1:has-text("Anmelden")');
      assert.equal(await page.getByRole('status').innerText(), 'Ihre Sitzung ist beendet. Bitte melden Sie sich erneut an.');
    });
  });
});

describe('keyboard navigation', () => {
  test('skip link first, links with Enter, current page marked and focus on the new heading', async () => {
    await withApp({ as: ANNA }, async (page) => {
      await page.keyboard.press('Tab');
      assert.equal(await focusedText(page), 'Zum Inhalt springen');
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(() => document.activeElement?.id), 'main');
      await page.getByRole('link', { name: 'Start' }).focus();
      await page.keyboard.press('Tab');
      assert.equal(await focusedText(page), 'Projekte');
      await page.keyboard.press('Enter');
      await page.waitForSelector('h1:has-text("Projekte")');
      await page.waitForFunction(() => document.activeElement?.tagName === 'H1');
      assert.equal(await page.getByRole('link', { name: 'Projekte' }).getAttribute('aria-current'), 'page');
      assert.equal(await page.title(), 'Projekte · Task Manager');
      for (const role of ['banner', 'navigation', 'main', 'contentinfo']) assert.ok(await page.getByRole(role).count() >= 1, role);
      await page.getByRole('link', { name: 'Suche' }).focus();
      await page.keyboard.press('Shift+Tab');
      await page.keyboard.press('Tab');
      const outline = await page.evaluate(() => {
        const style = getComputedStyle(document.activeElement);
        return { width: parseFloat(style.outlineWidth), style: style.outlineStyle };
      });
      assert.ok(outline.width >= 2 && outline.style !== 'none', JSON.stringify(outline));
    });
  });
});

describe('two accounts with different rights', () => {
  test('the administration sees its pages and can edit, a reader only reads', async () => {
    const backend = createBackend();
    const anna = await start({ backend, as: ANNA, hash: ticketHash(backend, 1) });
    const ben = await start({ backend, as: BEN, hash: ticketHash(backend, 1) });
    try {
      for (const page of [anna.page, ben.page]) await page.waitForSelector('[data-testid=history]');
      assert.deepEqual(await anna.page.locator('.sidebar nav ul').nth(1).locator('a').allInnerTexts(), ['Konten', 'Gruppen und Rechte', 'Erscheinungsbild']);
      assert.deepEqual(await ben.page.locator('.sidebar nav ul').nth(1).locator('a').allInnerTexts(), ['Erscheinungsbild']);
      assert.equal(await anna.page.getByLabel(/^Beschreibung/).inputValue(), 'Ablauf mit synthetischen Konten.');
      assert.equal(await ben.page.getByLabel(/^Titel/).count(), 0, 'no input for a reader');
      assert.match(await ben.page.locator('main').innerText(), /Beschreibung: für Sie verborgen/);
      assert.doesNotMatch(await ben.page.locator('main').innerText(), /Ablauf mit synthetischen Konten/, 'hidden content is not shown, not even in the history');
      assert.match(await ben.page.locator('main').innerText(), /Sie dürfen dieses Ticket lesen, aber nicht ändern\./);
      await ben.page.goto(`${base()}#/projects/${backend.project.id}/tickets`);
      await ben.page.waitForSelector('[data-testid=ticket-table], .empty');
      // The project answer names no project actions, so the form is offered and the server refusal is explained.
      await ben.page.locator('summary', { hasText: 'Neues Ticket' }).click();
      await ben.page.getByLabel(/^Titel/).fill('Nicht erlaubt');
      await ben.page.getByRole('button', { name: 'Ticket anlegen' }).click();
      await ben.page.getByText('Sie dürfen in diesem Projekt keine Tickets anlegen.').waitFor();
      assert.equal(backend.state.tickets.length, 2, 'nothing was created');
      await ben.page.getByLabel(/^Titel/).fill('');
      await ben.page.goto(`${base()}#/accounts`);
      await ben.page.waitForSelector('main h1');
      assert.match(await ben.page.locator('main').innerText(), /kein|nicht/i);
      assert.equal(backend.state.requests.filter((item) => item.user === 'ben' && item.path.startsWith('/api/v1/accounts')).length, 0, 'the page does not even ask');
      for (const session of [anna, ben]) assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), []);
    } finally {
      await anna.context.close();
      await ben.context.close();
    }
  });

  test('a ticket created by the administration appears in the list and the search of the reader', async () => {
    const backend = createBackend();
    const anna = await start({ backend, as: ANNA, hash: `#/projects/${backend.project.id}/tickets` });
    const ben = await start({ backend, as: BEN, hash: '#/search' });
    try {
      await anna.page.locator('summary', { hasText: 'Neues Ticket' }).click();
      await anna.page.getByLabel(/^Titel/).fill('Tastaturbedienung prüfen');
      await anna.page.getByRole('button', { name: 'Ticket anlegen' }).click();
      await anna.page.waitForSelector('h1:has-text("PLAT-3")');
      await ben.page.getByLabel(/^Suchtext/).fill('tastatur');
      await ben.page.getByRole('button', { name: 'Suchen' }).click();
      await ben.page.waitForSelector('[data-testid=ticket-hits]');
      assert.match(await ben.page.getByTestId('ticket-hits').innerText(), /PLAT-3\s+Tastaturbedienung prüfen/);
    } finally {
      await anna.context.close();
      await ben.context.close();
    }
  });
});

describe('changes', () => {
  test('a concurrent change is reported, the input is kept and can be saved on the new version', async () => {
    const backend = createBackend();
    const first = await start({ backend, as: ANNA, hash: ticketHash(backend, 1) });
    const second = await start({ backend, as: ANNA, hash: ticketHash(backend, 1) });
    try {
      await first.page.getByLabel(/^Titel/).fill('Anmeldung gründlich prüfen');
      await save(first.page).click();
      await first.page.getByText('Gespeichert.').waitFor();
      await second.page.getByLabel(/^Status/).selectOption({ label: 'In Arbeit' });
      await save(second.page).click();
      await second.page.getByTestId('ticket-conflict').waitFor();
      await second.page.getByText('Version 2, zuletzt geändert').waitFor();
      assert.equal(await second.page.getByLabel(/^Titel/).inputValue(), 'Anmeldung gründlich prüfen', 'the other change is loaded');
      assert.equal(await second.page.getByLabel(/^Status/).inputValue(), 'in_progress', 'the own input is kept');
      await save(second.page).click();
      await second.page.getByText('Gespeichert.').waitFor();
      const ticket = backend.state.tickets[0];
      assert.deepEqual([ticket.version, ticket.values.title, ticket.values.status], [3, 'Anmeldung gründlich prüfen', 'in_progress']);
      const patches = backend.writes().filter((item) => item.method === 'PATCH');
      assert.deepEqual(patches.map((item) => item.ifMatch), ['"1"', '"1"', '"2"'], 'every change names the version it is based on');
      assert.deepEqual(JSON.parse(patches[2].body.toString()), { status: 'in_progress' }, 'only changed fields are sent');
    } finally {
      await first.context.close();
      await second.context.close();
    }
  });

  test('unsaved input asks before leaving and stays when the person declines', async () => {
    const backend = createBackend();
    await withApp({ backend, as: ANNA, hash: ticketHash(backend, 1) }, async (page) => {
      await page.waitForSelector('[data-testid=history]');
      await page.getByLabel(/^Titel/).fill('Noch nicht gespeichert');
      let asked = null;
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?');
      assert.match(page.url(), /tickets\/1$/);
      assert.equal(await page.getByLabel(/^Titel/).inputValue(), 'Noch nicht gespeichert');
      page.once('dialog', (dialog) => void dialog.accept());
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForSelector('h1:has-text("Suche")');
      assert.equal(backend.writes().filter((item) => item.method === 'PATCH').length, 0);
    });
  });

  test('a right withdrawn in another browser takes effect at the next save without losing the page', async () => {
    const backend = createBackend();
    const reader = backend.group('Lesen');
    reader.actions.push({ action: 'ticket.edit', projectId: null });
    reader.fields.push({ resourceType: 'ticket', field: 'title', projectId: null, operation: 'write', effect: 'allow' });
    const anna = await start({ backend, as: ANNA, hash: `#/groups/${reader.id}` });
    const ben = await start({ backend, as: BEN, hash: ticketHash(backend, 1) });
    try {
      await ben.page.getByLabel(/^Titel/).fill('Ben ändert den Titel');
      const box = anna.page.getByRole('checkbox', { name: /^Tickets bearbeiten/ });
      await box.click();
      await anna.page.getByText('Tickets bearbeiten entzogen.').waitFor();
      assert.equal(await box.isChecked(), false);
      await save(ben.page).click();
      await ben.page.getByText(/Sie dürfen eines der geänderten Felder nicht mehr ändern\./).waitFor();
      assert.equal(await ben.page.getByLabel(/^Titel/).count(), 0, 'the page shows the current rights: read only');
      assert.equal(backend.state.tickets[0].values.title, 'Anmeldung prüfen', 'nothing was written');
    } finally {
      await anna.context.close();
      await ben.context.close();
    }
  });
});

describe('administration', () => {
  test('a new account gets a one time password that is shown once', async () => {
    await withApp({ as: ANNA, hash: '#/accounts' }, async (page, backend) => {
      await page.locator('summary', { hasText: 'Neues Konto anlegen' }).click();
      await page.getByLabel(/^Anmeldename/).fill('dora');
      await page.getByLabel(/^Anzeigename/).fill('Dora Demo');
      await page.getByRole('button', { name: 'Konto anlegen' }).click();
      await page.getByTestId('one-time').waitFor();
      const created = backend.state.accounts.find((item) => item.username === 'dora');
      assert.equal(await page.locator('[data-testid=one-time] code').innerText(), created.password);
      assert.match(await page.locator('table').innerText(), /dora\s+Dora Demo\s+Aktiv, Passwortwechsel offen/);
      await page.goto(`${base()}#/start`);
      await page.goto(`${base()}#/accounts`);
      await page.waitForSelector('table');
      assert.equal(await page.getByTestId('one-time').count(), 0, 'not shown again');
    });
  });

  test('the last administration cannot be removed by deactivating its account', async () => {
    await withApp({ as: ANNA, hash: '#/accounts' }, async (page) => {
      await page.getByRole('button', { name: 'anna deaktivieren' }).click();
      await page.getByText('Das letzte aktive Konto der Verwaltung kann nicht deaktiviert werden.').waitFor();
    });
  });

  test('groups take members, actions per scope and field rules', async () => {
    await withApp({ as: ANNA, hash: '#/groups' }, async (page, backend) => {
      await page.getByLabel(/^Neue Gruppe/).fill('Entwicklung');
      await page.getByRole('button', { name: 'Gruppe anlegen' }).click();
      await page.waitForSelector('h1:has-text("Entwicklung")');
      const select = page.getByLabel('Konto aufnehmen');
      await select.selectOption({ label: 'Ben Leser' });
      await page.getByRole('button', { name: 'Aufnehmen' }).click();
      await page.getByText('Ben Leser ist jetzt in der Gruppe.').waitFor();
      await page.getByLabel(/^Geltungsbereich/).selectOption({ label: 'PLAT · Plattform' });
      assert.equal(await page.getByRole('checkbox', { name: /^Konten verwalten/ }).count(), 0, 'company actions only without a project');
      await page.getByRole('checkbox', { name: /^Tickets bearbeiten/ }).click();
      await page.getByText('Tickets bearbeiten gewährt.').waitFor();
      await page.getByLabel('Schreiben von Titel').selectOption('allow');
      await page.getByText('Feldregel gespeichert.').waitFor();
      const group = backend.group('Entwicklung');
      assert.deepEqual(group.members, [backend.ben.id]);
      assert.deepEqual(group.actions, [{ action: 'ticket.edit', projectId: backend.project.id }]);
      assert.deepEqual(group.fields, [{ resourceType: 'ticket', field: 'title', projectId: backend.project.id, operation: 'write', effect: 'allow' }]);
    });
  });
});

describe('layout', () => {
  const routes = (backend) => ['#/start', '#/projects', `#/projects/${backend.project.id}/tickets`, `#/projects/${backend.project.id}/members`,
    `#/projects/${backend.project.id}/settings`, ticketHash(backend, 1), '#/search', '#/accounts', '#/groups', '#/appearance', '#/board'];
  for (const locale of ['de-DE', 'en-US']) {
    test(`no horizontal scrolling at 320 px width on every page in ${locale}`, async () => {
      const backend = createBackend();
      await withApp({ backend, as: ANNA, locale, viewport: { width: 320, height: 640 } }, async (page) => {
        for (const hash of routes(backend)) {
          await page.goto(`${base()}${hash}`);
          await page.waitForSelector('main h1');
          await page.waitForTimeout(150);
          const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
          assert.ok(overflow <= 0, `${hash}: overflow ${overflow}px`);
        }
      });
    });
  }

  test('the login page fits 320 px too', async () => {
    await withApp({ viewport: { width: 320, height: 640 } }, async (page) => {
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      assert.ok(overflow <= 0, `overflow ${overflow}px`);
    });
  });
});
