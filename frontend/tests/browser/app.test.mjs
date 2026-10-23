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

  test('signing out before the own password is set still lets another account log in', async () => {
    await withApp({}, async (page) => {
      await page.getByLabel(/^Anmeldename/).fill(NORA.username);
      await page.getByLabel(/^Passwort/).fill(NORA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('h1:has-text("Eigenes Passwort festlegen")');
      await page.getByRole('button', { name: 'Abmelden' }).click();
      await page.waitForSelector('h1:has-text("Anmelden")');
      await page.getByLabel(/^Anmeldename/).fill(ANNA.username);
      await page.getByLabel(/^Passwort/).fill(ANNA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('.shell');
      assert.equal(await heading(page), 'Willkommen, Anna Admin');
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
      // Declining puts the ticket address back after the hash change; on a slow machine that can take longer than the pause.
      await page.waitForURL(/tickets\/1$/, { timeout: 5000 });
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

describe('review findings of TASK-030', () => {
  // F1: a field read right withdrawn while the ticket is open; the version stays the same, the history must follow anyway.
  test('after a refused save the history no longer shows a field that became hidden', async () => {
    const backend = createBackend();
    const reader = backend.group('Lesen');
    reader.actions.push({ action: 'ticket.edit', projectId: null });
    for (const operation of ['read', 'write']) reader.fields.push({ resourceType: 'ticket', field: 'description', projectId: null, operation, effect: 'allow' });
    await withApp({ backend, as: BEN, hash: ticketHash(backend, 1) }, async (page) => {
      const history = page.getByTestId('history');
      await history.getByText(/Ablauf mit synthetischen Konten\./).waitFor();
      await page.getByLabel(/^Beschreibung/).fill('Ben ergänzt die Beschreibung');
      reader.fields = reader.fields.filter((rule) => rule.field !== 'description');
      await save(page).click();
      await page.getByText(/Sie dürfen eines der geänderten Felder nicht mehr ändern\./).waitFor();
      await page.getByText('Beschreibung: für Sie verborgen').waitFor();
      await history.waitFor();
      assert.doesNotMatch(await page.locator('main').innerText(), /Ablauf mit synthetischen Konten/, 'no hidden value is left on the page');
      assert.equal(backend.state.tickets[0].version, 1, 'the version did not change');
    });
  });

  // F2: input typed while the answer to a save is outstanding is not part of it and stays unsaved.
  test('input typed while a save is running is kept as unsaved', async () => {
    const backend = createBackend();
    await withApp({ backend, as: ANNA, hash: ticketHash(backend, 1) }, async (page) => {
      let arrived;
      const patchArrived = new Promise((resolve) => { arrived = resolve; });
      let release;
      const answer = new Promise((resolve) => { release = resolve; });
      await page.route('**/api/v1/projects/*/tickets/1', async (route) => {
        if (route.request().method() === 'PATCH') {
          arrived();
          await answer;
        }
        await route.fallback();
      });
      await page.getByLabel(/^Titel/).fill('Gesendeter Titel');
      await save(page).click();
      await patchArrived;
      await page.getByLabel(/^Beschreibung/).fill('Während des Speicherns getippt');
      release();
      await page.getByText('Gespeichert.').waitFor();
      assert.equal(await page.getByLabel(/^Titel/).inputValue(), 'Gesendeter Titel');
      assert.equal(await page.getByLabel(/^Beschreibung/).inputValue(), 'Während des Speicherns getippt', 'the later input is kept');
      assert.equal(await save(page).isEnabled(), true, 'and can still be saved');
      assert.equal(backend.state.tickets[0].values.description, 'Ablauf mit synthetischen Konten.', 'it was not sent');
      let asked = null;
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?', 'it counts as unsaved');
    });
  });

  // F3: the forms that create a project, an account or a group ask before their input is left behind.
  for (const { hash, open, fill, submit, done } of [
    { hash: '#/projects', open: 'Neues Projekt anlegen', fill: [[/^Projektschlüssel/, 'DRAFT'], [/^Name/, 'Entwurf']], submit: 'Projekt anlegen', done: 'h1:has-text("Entwurf")' },
    { hash: '#/accounts', open: 'Neues Konto anlegen', fill: [[/^Anmeldename/, 'dora']], submit: 'Konto anlegen', done: '[data-testid=one-time]' },
    { hash: '#/groups', open: null, fill: [[/^Neue Gruppe/, 'Entwurf']], submit: 'Gruppe anlegen', done: 'h1:has-text("Entwurf")' },
  ]) {
    test(`the create form on ${hash} asks before its input is left behind`, async () => {
      await withApp({ as: ANNA, hash }, async (page) => {
        if (open) await page.locator('summary', { hasText: open }).click();
        for (const [label, value] of fill) await page.getByLabel(label).fill(value);
        const asked = [];
        page.on('dialog', (dialog) => {
          asked.push(dialog.message());
          void dialog.dismiss();
        });
        await page.getByRole('link', { name: 'Suche' }).click();
        await page.waitForTimeout(300);
        assert.deepEqual(asked, ['Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?']);
        assert.ok(page.url().endsWith(hash), 'the page stays');
        assert.equal(await page.getByLabel(fill[0][0]).inputValue(), fill[0][1], 'with the input');
        await page.getByRole('button', { name: submit }).click();
        await page.waitForSelector(done);
        await page.getByRole('link', { name: 'Suche' }).click();
        await page.waitForSelector('h1:has-text("Suche")');
        assert.equal(asked.length, 1, 'after a successful creation nothing is asked');
      });
    });
  }

  // F6: a refusal reads the rights of the session again, so the navigation no longer offers what was withdrawn.
  test('after a refused save the administration links follow the withdrawn rights', async () => {
    const backend = createBackend();
    await withApp({ backend, as: ANNA, hash: ticketHash(backend, 1) }, async (page) => {
      const nav = page.locator('.sidebar');
      await nav.getByRole('link', { name: 'Gruppen und Rechte' }).waitFor();
      await page.getByLabel(/^Titel/).fill('Anna ändert den Titel');
      backend.group('Unternehmensverwaltung').members = [];
      backend.group('Lesen').members.push(backend.anna.id);
      await save(page).click();
      await page.getByText(/Ticket und Rechte sind neu geladen/).waitFor();
      assert.equal(await nav.getByRole('link', { name: 'Gruppen und Rechte' }).count(), 0);
      assert.equal(await nav.getByRole('link', { name: 'Konten' }).count(), 0);
      assert.equal(await page.getByLabel(/^Titel/).count(), 0, 'the ticket is read only now');
    });
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
      // The list reloads after the creation; wait for the new row instead of reading the table while it is still the old one.
      await page.locator('table tbody tr', { hasText: 'Dora Demo' }).waitFor();
      assert.match(await page.locator('table').innerText(), /dora\s+Dora Demo\s+Einmalpasswort offen bis/);
      await page.goto(`${base()}#/start`);
      await page.goto(`${base()}#/accounts`);
      await page.waitForSelector('table');
      assert.equal(await page.getByTestId('one-time').count(), 0, 'not shown again');
    });
  });

  test('the last administration cannot be removed by deactivating its account', async () => {
    await withApp({ as: ANNA, hash: '#/accounts' }, async (page) => {
      await page.getByRole('button', { name: 'anna deaktivieren' }).click();
      await page.getByText('Das letzte aktive Konto der Verwaltung und das letzte aktive lokale Konto mit den Rechten für Gruppen und Konten können nicht deaktiviert werden.').waitFor();
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

describe('rights core hardening (TASK-043)', () => {
  const grantAudit = (backend) => backend.group('Unternehmensverwaltung').actions.push({ action: 'permissions.audit.read', projectId: null });
  const seedAudit = (backend, count) => {
    for (let version = 1; version <= count; version += 1) {
      backend.state.audit.push({ id: `00000000-0000-4000-8000-${String(version).padStart(12, '0')}`, actorId: backend.anna.id,
        occurredAt: '2026-10-22T08:00:00Z', operation: 'action.changed', groupId: backend.group('Lesen').id, subjectId: null, projectId: null,
        action: 'ticket.read', resourceType: null, field: null, effect: version % 2 ? 'allow' : 'remove', previousEffect: version % 2 || version < 3 ? null : 'allow',
        // The two oldest entries stand for entries from before TASK-043, which do not know their previous state.
        previousRecorded: version >= 3, rightsVersion: version });
    }
  };

  test('the change log pages beyond a hundred entries and shows what a change replaced', async () => {
    const backend = createBackend();
    grantAudit(backend);
    seedAudit(backend, 120);
    await withApp({ backend, as: ANNA, hash: '#/groups' }, async (page) => {
      const rows = page.locator('[data-testid=audit-log] tbody tr');
      await page.waitForFunction(() => document.querySelectorAll('[data-testid=audit-log] tbody tr').length === 50);
      assert.match(await rows.first().innerText(), /Anna.*Lesen.*Tickets lesen entzogen.*Erlaubt/s);
      for (const expected of [100, 120]) {
        await page.getByRole('button', { name: 'Ältere Einträge laden' }).click();
        await page.waitForFunction((count) => document.querySelectorAll('[data-testid=audit-log] tbody tr').length === count, expected);
      }
      await page.getByText('Das sind alle Einträge.').waitFor();
      assert.equal(await rows.last().getAttribute('data-version'), '1');
      assert.match(await page.locator('[data-testid=audit-log] tbody tr[data-version="3"]').innerText(), /Tickets lesen gewährt\s+Nichts/);
      // Finding F4 of the TASK-043 review: an old entry without its previous state does not claim there was nothing.
      assert.match(await rows.last().innerText(), /Tickets lesen gewährt\s+Nicht erfasst/);
      assert.match(await page.locator('[data-testid=audit-log] tbody tr[data-version="2"]').innerText(), /Tickets lesen entzogen\s+Nicht erfasst/);
    });
  });

  test('without the log right the log says which right it needs', async () => {
    await withApp({ as: ANNA, hash: '#/groups' }, async (page) => {
      await page.getByText('Das Protokoll sieht nur, wer das Recht „Rechteprotokoll lesen“ hat.').waitFor();
    });
  });

  test('a group name that exists already, in any case, is refused with its own message', async () => {
    await withApp({ as: ANNA, hash: '#/groups' }, async (page, backend) => {
      await page.getByLabel(/^Neue Gruppe/).fill('lesen');
      await page.getByRole('button', { name: 'Gruppe anlegen' }).click();
      await page.getByText('Eine Gruppe mit diesem Namen gibt es schon.').waitFor();
      assert.equal(backend.state.groups.length, 2);
    });
  });

  test('a project administration grants in its project only what it holds itself', async () => {
    const backend = createBackend();
    backend.state.groups.push({ id: '11111111-1111-4111-8111-111111111111', name: 'Projektleitung', members: [backend.ben.id],
      actions: [{ action: 'permissions.manage', projectId: backend.project.id }], fields: [] });
    await withApp({ backend, as: BEN, hash: `#/projects/${backend.project.id}/rights` }, async (page) => {
      await page.getByLabel(/^Gruppe/).selectOption({ label: 'Lesen' });
      await page.locator('[data-testid=project-rights-actions] code').first().waitFor();
      const offered = await page.locator('[data-testid=project-rights-actions] code').allInnerTexts();
      assert.deepEqual(offered, ['project.read', 'ticket.read'], 'only the project actions Ben holds');
      await page.getByText('Keine Feldregeln, die Sie hier vergeben dürfen.').waitFor();
      await page.getByRole('checkbox', { name: /^Tickets lesen/ }).click();
      await page.getByText('Gespeichert. Die Änderung gilt sofort.').waitFor();
      assert.deepEqual(backend.group('Lesen').actions.filter((rule) => rule.projectId === backend.project.id), [{ action: 'ticket.read', projectId: backend.project.id }]);
      // The tab reloads the rights after the change; the box follows the answer of the server.
      await page.locator('[data-testid=project-rights-actions] input:checked').waitFor();
      assert.equal(await page.getByRole('link', { name: 'Gruppen und Rechte' }).count(), 0, 'no company administration');
    });
  });

  test('without project administration there is no rights tab', async () => {
    await withApp({ as: BEN, hash: `#/projects` }, async (page, backend) => {
      await page.goto(`${base()}#/projects/${backend.project.id}`);
      await page.getByRole('link', { name: 'Mitglieder' }).waitFor();
      assert.equal(await page.locator('nav.tabs a', { hasText: 'Rechte' }).count(), 0);
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

  for (const withLogo of [false, true]) {
    test(`at 320 px the brand ${withLogo ? 'logo' : 'name'} stays clear of the language and theme controls`, async () => {
      for (const locale of ['de-DE', 'en-US']) {
        const backend = createBackend();
        if (withLogo) backend.state.appearance.logo = { contentType: 'image/png', size: 67, url: '/api/v1/appearance/logo?v=3' };
        await withApp({ backend, as: ANNA, locale, viewport: { width: 320, height: 640 } }, async (page) => {
          await page.waitForSelector(withLogo ? '.brand-logo' : '.brand-mark');
          await page.waitForTimeout(150);
          const overlap = await page.evaluate(() => {
            const box = (element) => element.getBoundingClientRect();
            const brand = box(document.querySelector('.brand'));
            const footer = box(document.querySelector('.sidebar-footer'));
            // What the brand shows is clipped to the brand box; an element of one pixel is visually hidden.
            return [...document.querySelectorAll('.brand > *')].map(box).filter((r) => r.width > 1 && r.height > 1)
              .map((r) => ({ left: Math.max(r.left, brand.left), right: Math.min(r.right, brand.right), top: Math.max(r.top, brand.top), bottom: Math.min(r.bottom, brand.bottom) }))
              .some((r) => r.left < footer.right && footer.left < r.right && r.top < footer.bottom && footer.top < r.bottom && r.right > r.left);
          });
          assert.equal(overlap, false, locale);
          assert.equal(await page.getByTestId('brand-name').textContent(), 'Task Manager');
          // Finding F3 of the TASK-043 review: the name stays in the accessibility tree, with or without a logo.
          assert.equal(await page.locator('.brand').ariaSnapshot(), '- text: Task Manager', locale);
        });
      }
    });
  }

  test('the login page fits 320 px too', async () => {
    await withApp({ viewport: { width: 320, height: 640 } }, async (page) => {
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      assert.ok(overflow <= 0, `overflow ${overflow}px`);
    });
  });
});
