import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { auditContrast } from './audit.mjs';
import { ANNA, BEN, NORA, TOKEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-036: light and dark surfaces, the company accent from the server, and drag and drop with its keyboard
// alternative. The backend is answered by page.route, so these tests check what the interface sends and shows.

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

const PNG = Buffer.from('89504e470d0a1a0a0000000d4948445200000001000000010806000000' + '1f15c4890000000d49444154789c6360f8cfc0f01f0005000201' + 'e2d1b0a50000000049454e44ae426082', 'hex');
// Every page of the interface, with ids of the synthetic backend where a page needs one.
const ROUTES = (backend) => ({
  start: '#/start', projects: '#/projects', tickets: `#/projects/${backend.project.id}`, members: `#/projects/${backend.project.id}/members`,
  tags: `#/projects/${backend.project.id}/tags`, settings: `#/projects/${backend.project.id}/settings`, ticket: `#/projects/${backend.project.id}/tickets/1`,
  search: '#/search', accounts: '#/accounts', groups: '#/groups', group: `#/groups/${backend.group('Lesen').id}`, board: '#/board', appearance: '#/appearance',
});

// The company color of these tests is blue, so that a page still showing the default orange stands out.
const fake = (options = {}) => createBackend({ accent: '#2563eb', ...options });

async function withPage(options, body) {
  const backend = options.backend === undefined ? fake() : options.backend;
  const as = options.as === undefined ? ANNA : options.as;
  // localhost instead of 127.0.0.1, so that the browser accepts the __Host- cookie on plain HTTP.
  const session = await open(browser, `${base()}${options.hash ?? ''}`, {
    ...options,
    ...(backend ? { setup: (context, page) => backend.install(context, page, { as }) } : {}),
  });
  try {
    if (backend && as) await session.page.waitForSelector('.shell main h1');
    await body(session.page, session.context);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const base = () => server.url.replace('127.0.0.1', 'localhost');
const accent = (page) => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--brand-primary').trim());
// The accent follows a choice after React committed the next render; wait for it instead of reading at once.
async function accentBecomes(page, expected, message) {
  try {
    await page.waitForFunction((color) => getComputedStyle(document.documentElement).getPropertyValue('--brand-primary').trim() === color, expected, { timeout: 3000 });
  } catch {
    assert.equal(await accent(page), expected, message);
  }
}
const live = (page) => page.evaluate(() => [...document.querySelectorAll('[aria-live]')].map((element) => element.textContent.trim()).filter(Boolean).join(' | '));

describe('light and dark', () => {
  test('the display follows the system until a choice is made, and the choice is remembered', async () => {
    await withPage({ colorScheme: 'dark' }, async (page) => {
      assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'dark');
      assert.equal(await page.getByRole('button', { name: 'Wie das System' }).getAttribute('aria-pressed'), 'true');
      await page.getByRole('button', { name: 'Hell' }).click();
      assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'light');
      await page.reload();
      await page.waitForSelector('main h1');
      assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'light');
      assert.equal(await page.getByRole('button', { name: 'Hell' }).getAttribute('aria-pressed'), 'true');
    });
  });

  // F4 of the TASK-096 review: with every navigation entry the sidebar scrolls inside itself in a low window, so language and
  // display can be brought fully into view and used.
  for (const height of [800, 600, 480]) {
    test(`the display choice stays reachable in a window of 1100 by ${height} pixels`, async () => {
      await withPage({ viewport: { width: 1100, height } }, async (page) => {
        const light = page.getByRole('button', { name: 'Hell' });
        await light.click();
        assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'light');
        await light.scrollIntoViewIfNeeded();
        const box = await light.boundingBox();
        assert.ok(box !== null && box.y >= 0 && box.y + box.height <= height + 1, `the button lies inside the window: ${JSON.stringify(box)}`);
        await page.getByLabel('Sprache').selectOption('en');
        await page.getByRole('button', { name: 'Light' }).waitFor();
      });
    });
  }

  for (const colorScheme of ['light', 'dark']) {
    for (const route of Object.keys(ROUTES(fake()))) {
      test(`every visible text on ${route} reaches its contrast in ${colorScheme}`, async () => {
        const backend = fake();
        await withPage({ colorScheme, backend, hash: ROUTES(backend)[route] }, async (page) => {
          await page.waitForTimeout(300); // let loading and color transitions finish
          const { checked, failures } = await auditContrast(page);
          assert.ok(checked > 10, `only ${checked} texts`);
          assert.deepEqual(failures, []);
        });
      });
    }
    test(`every visible text on the login reaches its contrast in ${colorScheme}`, async () => {
      await withPage({ colorScheme, as: null }, async (page) => {
        await page.getByRole('button', { name: 'Anmelden' }).click();
        await page.waitForTimeout(300);
        const { checked, failures } = await auditContrast(page);
        assert.ok(checked > 5, `only ${checked} texts`);
        assert.deepEqual(failures, []);
      });
    });
  }

  test('in dark mode hints, errors, borders and focus stay readable', async () => {
    const backend = fake();
    await withPage({ colorScheme: 'dark', backend, hash: ROUTES(backend).ticket }, async (page) => {
      await page.waitForSelector('[data-testid=history]');
      await page.keyboard.press('Shift'); // keyboard modality, so that :focus-visible applies
      await page.getByLabel(/^Titel/).focus();
      const measured = await page.evaluate(() => {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = 1;
        const context = canvas.getContext('2d', { willReadFrequently: true });
        const rgb = (value) => { context.clearRect(0, 0, 1, 1); context.fillStyle = value; context.fillRect(0, 0, 1, 1); return [...context.getImageData(0, 0, 1, 1).data].slice(0, 3); };
        const light = (value) => {
          const [r, g, b] = rgb(value).map((v) => { const c = v / 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; });
          return 0.2126 * r + 0.7152 * g + 0.0722 * b;
        };
        const ratio = (a, b) => { const x = light(a); const y = light(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
        const input = document.querySelector('.panel input');
        const surface = getComputedStyle(input.closest('.panel')).backgroundColor;
        const header = document.querySelector('header');
        const language = document.querySelector('#language');
        language.focus();
        const headerFocus = { visible: language.matches(':focus-visible'), ratio: ratio(getComputedStyle(language).outlineColor, getComputedStyle(header).backgroundColor) };
        input.focus();
        return {
          border: ratio(getComputedStyle(input).borderTopColor, surface),
          focus: ratio(getComputedStyle(input).outlineColor, surface),
          focusVisible: input.matches(':focus-visible'),
          headerFocus,
        };
      });
      for (const name of ['border', 'focus']) assert.ok(measured[name] >= 3, `${name} ${measured[name]}`);
      assert.equal(measured.focusVisible, true, 'the focus ring was actually measured');
      assert.equal(measured.headerFocus.visible, true);
      assert.ok(measured.headerFocus.ratio >= 3, `header focus ${measured.headerFocus.ratio}`);
    });
  });
});

describe('company appearance', () => {
  test('without a server the default orange is used and the interface says the server is not reachable', async () => {
    await withPage({ backend: null, hash: '#/appearance' }, async (page) => {
      await page.waitForSelector('h1:has-text("Server nicht erreichbar")');
      assert.equal(await accent(page), '#f97316');
    });
  });

  test('before the login the login page shows the company name, color and logo and reads nothing else', async () => {
    const backend = fake({ brandName: 'Nordlicht Service' });
    backend.state.appearance.logo = { contentType: 'image/png', size: 67, url: '/api/v1/appearance/logo?v=3' };
    await withPage({ backend, as: null }, async (page) => {
      await page.getByRole('heading', { name: 'Anmelden' }).waitFor();
      await accentBecomes(page, '#2563eb', 'the login page carries the company color');
      await page.waitForSelector('header img.brand-logo');
      assert.equal(await page.locator('header img.brand-logo').getAttribute('src'), '/api/v1/appearance/public/logo');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Nordlicht Service');
      assert.equal(await page.title(), 'Anmelden · Nordlicht Service');
      assert.deepEqual([...new Set(backend.state.requests.map((item) => item.path))].sort(),
        ['/api/v1/appearance/public', '/api/v1/appearance/public/logo', '/api/v1/session']);
    });
  });

  test('when the public appearance cannot be read the login page keeps the default', async () => {
    const backend = fake({ brandName: 'Nordlicht Service' });
    backend.state.publicAppearanceDown = true;
    await withPage({ backend, as: null }, async (page) => {
      await page.getByRole('heading', { name: 'Anmelden' }).waitFor();
      await page.waitForFunction(() => performance.getEntriesByType('resource').some((entry) => entry.name.endsWith('/api/v1/appearance/public')));
      assert.equal(await accent(page), '#f97316');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Task Manager');
    });
  });

  test('the password change and the page after logging out show the company appearance', async () => {
    const backend = fake({ brandName: 'Nordlicht Service' });
    await withPage({ backend, as: null }, async (page) => {
      await page.getByLabel(/^Anmeldename/).fill(NORA.username);
      await page.getByLabel(/^Passwort/).fill(NORA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('h1:has-text("Eigenes Passwort festlegen")');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Nordlicht Service');
      await accentBecomes(page, '#2563eb', 'the password change carries the company color');
      await page.getByLabel(/^Einmalpasswort/).fill(NORA.password);
      await page.getByLabel(/^Neues Passwort \(/).fill('Nora Passwort 2026');
      await page.getByLabel(/^Neues Passwort wiederholen/).fill('Nora Passwort 2026');
      await page.getByRole('button', { name: 'Passwort speichern' }).click();
      await page.waitForSelector('.shell main h1');
      await page.getByRole('button', { name: 'Abmelden' }).click();
      await page.getByRole('heading', { name: 'Anmelden' }).waitFor();
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Nordlicht Service');
      await accentBecomes(page, '#2563eb', 'the page after logging out carries the company color');
    });
  });

  test('the saved company color applies to everyone at start', async () => {
    await withPage({ as: BEN }, async (page) => {
      await page.waitForFunction(() => getComputedStyle(document.documentElement).getPropertyValue('--brand-primary').trim() === '#2563eb');
      assert.match(await page.getByTestId('brand-current').innerText(), /#2563EB/);
    });
  });

  test('a person without the right to change the appearance gets a preview that is never sent', async () => {
    const server = fake();
    await withPage({ backend: server, as: BEN, hash: '#/appearance' }, async (page) => {
      await page.getByTestId('appearance-mode').waitFor();
      assert.match(await page.getByTestId('appearance-mode').innerText(), /Nur Konten mit dem Recht dazu/);
      await page.getByRole('button', { name: 'Grün' }).click();
      await accentBecomes(page, '#16a34a', 'the preview shows everywhere');
      assert.equal(await page.getByRole('button', { name: 'Für alle speichern' }).isDisabled(), true);
      await page.getByRole('link', { name: 'Start' }).click();
      await page.waitForSelector('h1:has-text("Willkommen, Ben Leser")');
      await accentBecomes(page, '#2563eb', 'leaving the page shows the saved color again');
      assert.deepEqual(server.writes(), []);
    });
  });

  test('a holder of the right saves a color with the version and the CSRF token', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      await page.getByRole('button', { name: 'Grün' }).click();
      assert.equal(await page.getByRole('button', { name: 'Grün' }).getAttribute('aria-pressed'), 'true');
      await page.getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      const [write] = server.writes();
      assert.deepEqual({ method: write.method, path: write.path, ifMatch: write.ifMatch, csrf: write.csrf }, { method: 'PUT', path: '/api/v1/appearance', ifMatch: '"3"', csrf: TOKEN });
      assert.deepEqual(JSON.parse(write.body.toString()), { accentColor: '#16a34a' });
      assert.equal(await page.getByRole('button', { name: 'Für alle speichern' }).isDisabled(), true);
      await page.getByRole('link', { name: 'Start' }).click();
      await accentBecomes(page, '#16a34a');
    });
  });

  test('a typed color is checked before it is shown', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Farbwert');
      await field.fill('#12');
      assert.equal(await field.getAttribute('aria-invalid'), 'true');
      await page.getByText('Bitte einen Farbwert mit # und sechs Stellen eingeben').waitFor();
      await field.fill('#7C3AED');
      assert.equal(await field.getAttribute('aria-invalid'), null);
      await accentBecomes(page, '#7c3aed');
      assert.match(await page.getByTestId('contrast').innerText(), /Kontrast \d+,\d zu 1/);
    });
  });

  test('a color typed before the saved one arrives is kept', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      let arrived;
      const requested = new Promise((resolve) => { arrived = resolve; });
      let release;
      const held = new Promise((resolve) => { release = resolve; });
      await page.route((url) => url.pathname === '/api/v1/appearance', async (route) => {
        if (route.request().method() === 'GET') {
          arrived();
          await held;
        }
        await route.fallback();
      });
      await page.reload();
      await requested;
      const field = page.getByLabel('Farbwert');
      await field.fill('#12');
      release();
      await page.waitForTimeout(300);
      assert.equal(await field.inputValue(), '#12', 'the late saved color does not overwrite what was typed');
      assert.equal(await field.getAttribute('aria-invalid'), 'true');
    });
  });

  test('an invalid typed color cannot save the last valid one', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      // Saving opens once the session and the saved appearance are loaded; wait for them instead of racing the load.
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      await page.getByRole('button', { name: 'Für alle speichern' }).waitFor();
      const field = page.getByLabel('Farbwert');
      const save = page.getByRole('button', { name: 'Für alle speichern' });
      await field.fill('#7C3AED');
      assert.equal(await save.isDisabled(), false);
      await field.fill('#12');
      assert.equal(await save.isDisabled(), true);
      await save.click({ force: true }).catch(() => {});
      assert.equal(server.state.requests.filter((item) => item.method === 'PUT').length, 0);
    });
  });

  test('a concurrent change is reported and the current state can be loaded', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      server.state.appearance.version = 9; // someone else saved meanwhile
      server.state.appearance.accent = '#e11d48';
      await page.getByRole('button', { name: 'Grün' }).click();
      await page.getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#E11D48');
    });
  });

  // TASK-087, finding R1: loading the current version after a conflict keeps the unsaved input of the other parts.
  test('after a conflict on the name the current version is loaded and the unsaved color stays', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      await field.fill('Ungespeicherter Name');
      await page.getByRole('button', { name: 'Violett' }).click();
      server.state.appearance.version = 9; // someone else saved another color and name meanwhile
      server.state.appearance.accent = '#16a34a';
      server.state.appearance.name = 'Fremder Name';
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Fremder Name');
      await accentBecomes(page, '#7c3aed', 'the unsaved color stays');
      assert.equal(await page.getByRole('button', { name: 'Violett' }).getAttribute('aria-pressed'), 'true');

      await field.fill('Zweiter Entwurf');
      server.state.appearance.version = 10;
      server.state.appearance.accent = '#e11d48';
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#E11D48');
      assert.equal(await field.inputValue(), 'Zweiter Entwurf', 'the unsaved name stays');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      assert.equal(server.state.appearance.name, 'Zweiter Entwurf');
    });
  });

  // TASK-089, finding S1: "Aktuellen Stand laden" takes exactly its own answer, also with the same version, and a failed
  // read keeps every input, offers the same step again and leaves nothing behind for a later change.
  test('loading after a conflict ends with the same version too and leaves nothing behind for a later save', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      await field.fill('Erster Entwurf');
      await page.getByRole('button', { name: 'Violett' }).click();
      server.state.appearance.version = 9;
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      server.state.appearance.version = 3; // the read answers with the version the page already has
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
      assert.equal(await field.inputValue(), 'Nordlicht', 'the failed part takes the saved name');
      await accentBecomes(page, '#7c3aed', 'the unsaved color stays');

      await field.fill('Zweiter Entwurf');
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      assert.equal(await field.inputValue(), 'Zweiter Entwurf', 'a later save of another part keeps the new name');
    });
  });

  test('a failed load after a conflict keeps every input and can be tried again', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      await field.fill('Ungespeichert');
      await page.getByRole('button', { name: 'Violett' }).click();
      server.state.appearance.version = 9;
      server.state.appearance.name = 'Fremder Name';
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      server.state.appearanceReadDown = true;
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      const retry = page.getByRole('button', { name: 'Erneut versuchen' });
      await retry.waitFor();
      assert.equal(await field.inputValue(), 'Ungespeichert', 'the name stays after the failed read');
      await accentBecomes(page, '#7c3aed', 'and so does the color');
      assert.equal(await page.getByRole('button', { name: 'Namen speichern' }).isEnabled(), true, 'the page stays usable');

      server.state.appearanceReadDown = false;
      await retry.click();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
      assert.equal(await field.inputValue(), 'Fremder Name', 'the repeated step takes the saved name');
      await accentBecomes(page, '#7c3aed', 'the color stays');
      await page.getByRole('button', { name: 'Namen speichern' }).waitFor();
      await field.fill('Letzter Entwurf');
      await page.getByRole('button', { name: 'Violett' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      assert.equal(await field.inputValue(), 'Letzter Entwurf', 'nothing of the failed read acts later');
    });
  });

  // TASK-090, T1 and T2 of docs/REVIEW_TASK-089_PR-263.md: the answer of "Aktuellen Stand laden" is held back and given
  // out only later with the then old version 9. It takes nothing from a page that was left, never takes back a newer
  // version, and no logo can be dropped while it is pending.
  async function holdNextRead(page, server, method = 'GET', change = () => {}) {
    let armed = false;
    let arrived;
    let release;
    const requested = new Promise((resolve) => { arrived = resolve; });
    const held = new Promise((resolve) => { release = resolve; });
    await page.route((url) => url.pathname === '/api/v1/appearance', async (route) => {
      if (!armed || route.request().method() !== method) return route.fallback();
      armed = false;
      change(server.state.appearance);
      const a = server.state.appearance;
      const body = { name: a.name, defaultName: 'Task Manager', accentColor: a.accent, defaultAccentColor: '#f97316',
        logo: a.logo && (({ content, ...shown }) => shown)(a.logo), version: a.version, updatedAt: '2026-10-19T06:00:00Z' };
      const etag = `"${a.version}"`;
      arrived();
      await held;
      await route.fulfill({ status: 200, contentType: 'application/json', headers: { ETag: etag }, body: JSON.stringify(body) });
    });
    return { arm: () => { armed = true; }, requested, release: () => release() };
  }

  test('a late answer of a page that was left takes nothing and keeps the newer saved version', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const hold = await holdNextRead(page, server);
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      server.state.appearance.version = 9;
      server.state.appearance.name = 'Alter Servername';
      await field.fill('Erster Entwurf');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      hold.arm();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await hold.requested;

      await page.evaluate(() => { location.hash = '#/start'; });
      await field.waitFor({ state: 'detached' });
      await page.evaluate(() => { location.hash = '#/appearance'; });
      await field.waitFor();
      await field.fill('Neu gespeichert');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
      await field.fill('Neu gespeichert');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      assert.equal(server.state.appearance.version, 10);

      hold.release();
      await page.waitForTimeout(300);
      assert.equal(await field.inputValue(), 'Neu gespeichert', 'the old answer does not take back the name');
      await page.getByRole('button', { name: 'Violett' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      assert.equal(server.writes().at(-1).ifMatch, '"10"', 'the next change is sent against the newer version');
      assert.equal(server.state.appearance.name, 'Neu gespeichert');
    });
  });

  // TASK-090, finding B1 of docs/REVIEW_TASK-090_PR-268.md: a save that succeeds after the page was left still reaches
  // the shared appearance, so the page shows it again and the next change is sent against its version.
  test('a save whose answer arrives after leaving the page is kept for the next visit', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const hold = await holdNextRead(page, server, 'PUT', (a) => { a.name = 'Gespeichert'; a.version += 1; });
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      await field.fill('Gespeichert');
      hold.arm();
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await hold.requested;
      await page.evaluate(() => { location.hash = '#/start'; });
      await field.waitFor({ state: 'detached' });
      hold.release();
      await page.waitForTimeout(300);
      await page.evaluate(() => { location.hash = '#/appearance'; });
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Gespeichert');
      await page.getByRole('button', { name: 'Violett' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      assert.equal(server.writes().at(-1).ifMatch, '"4"', 'the next change is sent against the saved version');
      assert.equal(server.state.appearance.version, 5);
    });
  });

  test('no logo can be dropped while the current version is loading and the old answer takes nothing back', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const hold = await holdNextRead(page, server);
      const drop = () => page.getByTestId('logo-dropzone').evaluate((zone, bytes) => {
        const transfer = new DataTransfer();
        transfer.items.add(new File([new Uint8Array(bytes)], 'logo.png', { type: 'image/png' }));
        zone.dispatchEvent(new DragEvent('dragover', { dataTransfer: transfer, bubbles: true, cancelable: true }));
        zone.dispatchEvent(new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true }));
      }, [...PNG]);
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      server.state.appearance.version = 9;
      await page.getByLabel('Name des Unternehmens').fill('Erster Entwurf');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      hold.arm();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await hold.requested;
      const logoWrites = () => server.writes().filter((item) => item.path === '/api/v1/appearance/logo').length;

      await drop();
      await page.waitForTimeout(300);
      assert.equal(logoWrites(), 0, 'a drop during the read sends nothing');
      assert.equal(await page.getByRole('button', { name: 'Namen speichern' }).isDisabled(), true, 'the page stays locked');

      hold.release();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
      await drop();
      await page.getByText('Das Logo ist für alle gespeichert.').waitFor();
      assert.deepEqual(server.writes().filter((item) => item.path === '/api/v1/appearance/logo').map((item) => item.ifMatch), ['"9"']);
      assert.equal(await page.locator('header img.brand-logo').getAttribute('src'), '/api/v1/appearance/logo?v=10');
      await page.getByRole('button', { name: 'Logo entfernen' }).click();
      await page.getByText('Das Logo wurde entfernt.').waitFor();
      assert.equal(server.writes().at(-1).ifMatch, '"10"', 'the logo keeps the version it was saved with');
    });
  });

  // TASK-089, finding S2 and hint H2: the stand in answers wrong JSON types and logo types like the server.
  test('the stand in refuses wrong JSON types and logo types like the server', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      const send = (path, type, body, etag = '"3"') => page.evaluate(async ({ path, type, body, etag, token }) => {
        const answer = await fetch(path, { method: 'PUT', headers: { 'Content-Type': type, 'If-Match': etag, 'X-CSRF-Token': token },
          body: typeof body === 'string' ? body : new Uint8Array(body) });
        return [answer.status, (await answer.json()).code ?? null];
      }, { path, type, body, etag, token: TOKEN });
      for (const body of ['{"name":{}}', '{"name":["A"]}', '{"accentColor":["#f97316"]}', '[]']) {
        assert.deepEqual(await send('/api/v1/appearance', 'application/json', body), [400, 'request.invalid'], body);
      }
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', 'null'), [400, 'appearance.change_empty']);
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', '{"accentColor":42}'), [400, 'appearance.color_invalid']);
      assert.deepEqual(await send('/api/v1/appearance/logo', 'image/jpeg', [...PNG]), [415, 'appearance.logo_type_invalid']);
      assert.deepEqual(await send('/api/v1/appearance/logo', 'image/png', [1, 2, 3]), [415, 'appearance.logo_type_invalid']);
      // TASK-090, hint 2: the type is named before a stale version, and a truth value as color is no color.
      assert.deepEqual(await send('/api/v1/appearance/logo', 'image/jpeg', [...PNG], '"1"'), [415, 'appearance.logo_type_invalid']);
      assert.deepEqual(await send('/api/v1/appearance/logo', 'image/png', [1, 2, 3], '"1"'), [415, 'appearance.logo_type_invalid']);
      assert.deepEqual(await send('/api/v1/appearance/logo', 'image/png', [...PNG], '"1"'), [412, 'appearance.version_conflict']);
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', '{"accentColor":true}'), [400, 'appearance.color_invalid']);
      assert.equal(server.state.appearance.version, 3, 'nothing was stored');
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', '{"name":42}'), [200, null]);
      assert.equal(server.state.appearance.name, '42');
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', '{"name":true}', '"4"'), [200, null]);
      assert.equal(server.state.appearance.name, 'true');
      assert.deepEqual(await send('/api/v1/appearance', 'application/json', '{"name":false}', '"5"'), [200, null]);
      assert.equal(server.state.appearance.name, 'false');
    });
  });

  test('a logo dropped on the area is uploaded as image bytes and shown in the sidebar', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      await page.getByTestId('logo-dropzone').evaluate((zone, bytes) => {
        const transfer = new DataTransfer();
        transfer.items.add(new File([new Uint8Array(bytes)], 'logo.png', { type: 'image/png' }));
        zone.dispatchEvent(new DragEvent('dragover', { dataTransfer: transfer, bubbles: true, cancelable: true }));
        zone.dispatchEvent(new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true }));
      }, [...PNG]);
      await page.getByText('Das Logo ist für alle gespeichert.').waitFor();
      const [write] = server.writes();
      assert.deepEqual({ path: write.path, ifMatch: write.ifMatch, csrf: write.csrf, type: write.type }, { path: '/api/v1/appearance/logo', ifMatch: '"3"', csrf: TOKEN, type: 'image/png' });
      assert.deepEqual([...write.body], [...PNG]);
      assert.equal(await page.locator('header img.brand-logo').getAttribute('src'), '/api/v1/appearance/logo?v=4');
      await page.getByRole('button', { name: 'Logo entfernen' }).click();
      await page.getByText('Das Logo wurde entfernt.').waitFor();
      assert.equal(await page.locator('header img').count(), 0);
    });
  });

  test('a file that is not PNG, JPEG or WebP is refused before anything is sent', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.waitForFunction(() => document.querySelector('[data-testid="preview-color"]')?.textContent === '#2563EB');
      await page.getByTestId('logo-file').setInputFiles({ name: 'logo.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>') });
      await page.getByText('Diese Datei ist kein PNG, JPEG oder WebP.').waitFor();
      assert.deepEqual(server.writes(), []);
    });
  });
});

describe('drag and drop', () => {
  test('navigation entries move with Alt and arrow keys, are announced, keep focus and are remembered', async () => {
    await withPage({}, async (page) => {
      await page.getByRole('link', { name: 'Board' }).focus();
      await page.keyboard.press('Alt+ArrowUp');
      await page.keyboard.press('Alt+ArrowUp');
      await page.keyboard.press('Alt+ArrowUp');
      const order = () => page.locator('.nav-main li').evaluateAll((items) => items.map((item) => item.dataset.route));
      assert.deepEqual(await order(), ['start', 'board', 'projects', 'search', 'inbox', 'escalations', 'time', 'wiki', 'requirements']);
      assert.match(await live(page), /Board steht jetzt an Position 2 von 9/);
      assert.equal(await page.evaluate(() => document.activeElement?.textContent?.trim()), 'Board');
      await page.reload();
      await page.waitForSelector('.shell main h1');
      assert.deepEqual(await order(), ['start', 'board', 'projects', 'search', 'inbox', 'escalations', 'time', 'wiki', 'requirements']);
    });
  });

  test('navigation entries can be dragged with the mouse', async () => {
    await withPage({}, async (page) => {
      await page.dragAndDrop('.nav-main li[data-route="search"]', '.nav-main li[data-route="start"]');
      assert.deepEqual(await page.locator('.nav-main li').evaluateAll((items) => items.map((item) => item.dataset.route)), ['search', 'start', 'projects', 'inbox', 'board', 'escalations', 'time', 'wiki', 'requirements']);
    });
  });

  test('start page cards move with the handle and the keyboard', async () => {
    await withPage({}, async (page) => {
      await page.getByRole('button', { name: 'Unternehmensauftritt verschieben' }).focus();
      await page.keyboard.press('Alt+ArrowLeft');
      assert.deepEqual(await page.locator('.card-grid > section').evaluateAll((items) => items.map((item) => item.dataset.card)), ['projects', 'brand', 'search']);
      assert.match(await live(page), /Unternehmensauftritt steht jetzt an Position 2 von 3/);
    });
  });

  test('board cards change column with the keyboard and the mouse, and the preview says it is sample data', async () => {
    await withPage({ hash: '#/board' }, async (page) => {
      await page.getByText('Vorschau mit Beispieldaten').waitFor();
      const column = (id) => page.locator(`[data-card="${id}"]`).evaluate((card) => card.closest('[data-column]').dataset.column);
      await page.locator('[data-card="c1"]').focus();
      await page.keyboard.press('Alt+ArrowRight');
      assert.equal(await column('c1'), 'doing');
      assert.equal(await page.evaluate(() => document.activeElement?.dataset.card), 'c1', 'focus follows the card');
      assert.match(await live(page), /Anmeldeseite gestalten steht jetzt in In Arbeit an Position 1 von 3/);
      await page.keyboard.press('Alt+ArrowDown');
      assert.deepEqual(await page.locator('[data-column="doing"] [data-card]').evaluateAll((cards) => cards.map((card) => card.dataset.card)), ['c2', 'c1', 'c5']);
      await page.dragAndDrop('[data-card="c6"]', '[data-column="review"] header');
      assert.equal(await column('c6'), 'review');
    });
  });
});

// TASK-081: the name of the company, resetting everything and the preview of every area.
describe('company name and preview', () => {
  test('a holder of the right previews and saves the name, which reaches the head and every preview', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Task Manager');
      await field.fill('  Nordlicht Service  ');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name"]')?.textContent === 'Nordlicht Service');
      for (const area of ['preview-app', 'preview-login', 'preview-portal', 'preview-mail-de', 'preview-mail-en']) {
        assert.match(await page.getByTestId(area).innerText(), /Nordlicht Service/, area);
      }
      assert.match(await page.getByTestId('preview-portal').innerText(), /Kundenportal/);
      assert.match(await page.getByTestId('preview-mail-de').innerText(), /Guten Tag Erika Musterfrau/);
      assert.match(await page.getByTestId('preview-mail-en').innerText(), /Hello Jane Example/);
      assert.equal(await page.getByTestId('preview-mail-en').getAttribute('lang'), 'en');
      assert.deepEqual(server.writes(), [], 'nothing is sent while typing');

      await field.fill('');
      assert.equal(await field.getAttribute('aria-invalid'), 'true');
      await page.getByText('Bitte 1 bis 80 Zeichen ohne Zeilenumbruch eingeben.').waitFor();
      assert.equal(await page.getByRole('button', { name: 'Namen speichern' }).isDisabled(), true);
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name"]')?.textContent === 'Task Manager');

      await field.fill('Nordlicht Service');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      const [write] = server.writes();
      assert.deepEqual({ method: write.method, path: write.path, ifMatch: write.ifMatch, csrf: write.csrf }, { method: 'PUT', path: '/api/v1/appearance', ifMatch: '"3"', csrf: TOKEN });
      assert.deepEqual(JSON.parse(write.body.toString()), { name: 'Nordlicht Service' });
      await page.getByRole('link', { name: 'Start' }).click();
      await page.waitForSelector('h1:has-text("Willkommen")');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Nordlicht Service');
      assert.match(await page.title(), / · Nordlicht Service$/);
    });
  });

  test('resetting everything asks once more and puts name, color and logo back', async () => {
    const server = fake({ brandName: 'Nordlicht Service' });
    server.state.appearance.logo = { contentType: 'image/png', size: 67, url: '/api/v1/appearance/logo?v=3' };
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      await page.getByRole('heading', { name: 'Alles zurücksetzen' }).waitFor();
      await page.getByRole('button', { name: 'Alles zurücksetzen' }).click();
      assert.deepEqual(server.writes(), [], 'the first click only asks');
      await page.getByRole('button', { name: 'Abbrechen' }).click();
      await page.getByRole('button', { name: 'Alles zurücksetzen' }).click();
      await page.getByRole('button', { name: 'Ja, alles zurücksetzen' }).click();
      await page.getByText('Name, Akzentfarbe und Logo sind auf den Standard zurückgesetzt.').waitFor();
      const [write] = server.writes();
      assert.deepEqual({ method: write.method, path: write.path, ifMatch: write.ifMatch, csrf: write.csrf }, { method: 'DELETE', path: '/api/v1/appearance', ifMatch: '"3"', csrf: TOKEN });
      await accentBecomes(page, '#f97316');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Task Manager');
      assert.equal(await page.locator('header img.brand-logo').count(), 0);
      assert.equal(await page.getByLabel('Name des Unternehmens').inputValue(), 'Task Manager');
    });
  });

  // TASK-087, finding F2 of docs/REVIEW_TASK-081_PR-243.md: saving one part keeps the unsaved input of the others.
  test('saving the color keeps an unsaved name and saving the name keeps an unsaved color', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await accentBecomes(page, '#2563eb');
      await field.fill('Entwurf GmbH');
      await page.getByRole('button', { name: 'Grün' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      assert.equal(await field.inputValue(), 'Entwurf GmbH', 'the unsaved name stays');
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Entwurf GmbH', 'and so does its preview');
      assert.deepEqual(JSON.parse(server.writes()[0].body.toString()), { accentColor: '#16a34a' });

      await page.getByRole('button', { name: 'Violett' }).click();
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      assert.deepEqual(JSON.parse(server.writes()[1].body.toString()), { name: 'Entwurf GmbH' });
      await accentBecomes(page, '#7c3aed', 'the unsaved color stays');
      assert.equal(await page.getByRole('button', { name: 'Violett' }).getAttribute('aria-pressed'), 'true');

      await field.fill('Noch ein Entwurf');
      await page.getByTestId('logo-file').setInputFiles({ name: 'logo.png', mimeType: 'image/png', buffer: PNG });
      await page.getByText('Das Logo ist für alle gespeichert.').waitFor();
      assert.equal(await field.inputValue(), 'Noch ein Entwurf', 'a logo keeps the unsaved name');
      await accentBecomes(page, '#7c3aed', 'and the unsaved color');
    });
  });

  // TASK-087, finding F1: protected spaces at the edges leave the name before it is sent, as on the server.
  test('protected spaces at the edges of the name are removed before saving', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Task Manager');
      await field.fill('\u00a0\u202fNordlicht\u00a0GmbH\u2007');
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      assert.deepEqual(JSON.parse(server.writes()[0].body.toString()), { name: 'Nordlicht\u00a0GmbH' });
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Nordlicht\u00a0GmbH');
      await field.fill('\u00a0');
      assert.equal(await field.getAttribute('aria-invalid'), 'true');
      assert.equal(await page.getByRole('button', { name: 'Namen speichern' }).isDisabled(), true);
    });
  });

  // TASK-087, finding F3: the keyboard focus lands on the outcome of a step and returns to the start of a cancelled question.
  test('after saving the focus is on the confirmation and after cancelling the question on the reset button', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const field = page.getByLabel('Name des Unternehmens');
      await page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
      await field.fill('Nordlicht Service');
      await page.getByRole('button', { name: 'Namen speichern' }).focus();
      await page.keyboard.press('Enter');
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-testid') === 'appearance-saved');

      await page.getByRole('button', { name: 'Grün' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).focus();
      await page.keyboard.press('Enter');
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-testid') === 'appearance-saved');

      await page.getByRole('button', { name: 'Alles zurücksetzen' }).focus();
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Abbrechen' }).focus();
      await page.keyboard.press('Enter');
      await page.waitForFunction(() => document.activeElement?.textContent === 'Alles zurücksetzen' && document.activeElement.tagName === 'BUTTON');

      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Ja, alles zurücksetzen' }).focus();
      await page.keyboard.press('Enter');
      await page.getByText('Name, Akzentfarbe und Logo sind auf den Standard zurückgesetzt.').waitFor();
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-testid') === 'appearance-saved');
    });
  });

  // TASK-087, finding F4: the name is visible beside the logo and at phone width, not only for screen readers.
  for (const width of [1100, 320]) {
    for (const withLogo of [true, false]) {
      test(`the name is visible in the head at ${width} px ${withLogo ? 'with' : 'without'} a logo, before and after the login`, async () => {
        const backend = fake({ brandName: 'Nordlicht Service' });
        if (withLogo) backend.state.appearance.logo = { contentType: 'image/png', size: 67, url: '/api/v1/appearance/logo?v=3' };
        for (const as of [null, ANNA]) {
          await withPage({ backend, as, viewport: { width, height: 800 } }, async (page) => {
            await page.waitForFunction(() => document.querySelector('[data-testid="brand-name"]')?.textContent === 'Nordlicht Service');
            if (withLogo) await page.waitForSelector('header img.brand-logo');
            const shown = await page.evaluate(() => {
              const name = document.querySelector('[data-testid="brand-name"]');
              const box = name.getBoundingClientRect();
              const style = getComputedStyle(name);
              const others = [...document.querySelectorAll('.preferences, .sidebar nav')]
                .map((element) => element.getBoundingClientRect())
                .filter((other) => other.width > 0 && !(other.right <= box.left || other.left >= box.right || other.bottom <= box.top || other.top >= box.bottom));
              return { width: box.width, height: box.height, clip: style.clip, inside: box.left >= 0 && box.right <= window.innerWidth + 0.5, covered: others.length,
                scroll: document.documentElement.scrollWidth <= window.innerWidth };
            });
            assert.ok(shown.width > 40 && shown.height > 10, JSON.stringify(shown));
            assert.notEqual(shown.clip, 'rect(0px, 0px, 0px, 0px)');
            assert.ok(shown.inside, JSON.stringify(shown));
            assert.equal(shown.covered, 0, 'nothing lies over the name');
            assert.ok(shown.scroll, 'no horizontal scrolling');
          });
        }
      });
    }
  }

  test('the mail template is edited per language with a preview of synthetic data, checked by the server and reset', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
      const section = page.getByTestId('mail-template');
      await section.getByRole('heading', { name: 'Mailvorlage „Antwort an Kunden“' }).waitFor();
      await section.getByText('Es gilt die eingebaute Vorlage dieser Sprache.').waitFor();
      const preview = section.getByTestId('mail-template-preview');
      await preview.locator('[data-part="greeting"]').getByText('Guten Tag Erika Beispiel,').waitFor();
      assert.match(await preview.locator('[data-part="signature"]').textContent(), /^Viele Grüße\nMax Support\nNordlicht$/);
      assert.equal(await preview.getAttribute('lang'), 'de');

      // A mistyped placeholder is named in the preview and refused when saving.
      await section.getByTestId('mail-part-signature').fill('Gruß\n{{sender.nme}}');
      await section.getByTestId('mail-preview-problem').getByText('Unbekannter Platzhalter: Deutsch, Signatur: {{sender.nme}}').waitFor();
      await section.getByRole('button', { name: 'Vorlage speichern' }).click();
      await section.getByTestId('mail-template-problem').getByText('Unbekannter Platzhalter: Deutsch, Signatur: {{sender.nme}}').waitFor();
      assert.deepEqual(server.writes().map((write) => write.method), ['PUT']);
      assert.equal(server.state.mailTemplate.version, 1, 'nothing refused is stored');

      await section.getByTestId('mail-part-signature').fill('Gruß\n{{sender.name}}');
      await section.getByTestId('mail-language-en').click();
      assert.equal(await section.getByTestId('mail-language-en').getAttribute('aria-pressed'), 'true');
      await section.getByTestId('mail-part-greeting').fill('Dear {{contact.name}}');
      await section.getByTestId('mail-address').fill('https://support.example.com');
      await preview.locator('[data-part="greeting"]').getByText('Dear Alex Example').waitFor();
      assert.equal(await preview.getAttribute('lang'), 'en');
      assert.match(await preview.locator('[data-part="footer"]').textContent(), /customer portal: https:\/\/support\.example\.com$/);
      await section.getByRole('button', { name: 'Vorlage speichern' }).click();
      await section.getByTestId('mail-template-saved').getByText('Die Mailvorlage ist gespeichert. Neue Antworten nutzen sie ab jetzt.').waitFor();
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'mail-template-saved', 'the focus is not lost after a save');
      const saved = server.writes().at(-1);
      assert.deepEqual({ method: saved.method, path: saved.path, ifMatch: saved.ifMatch, csrf: saved.csrf },
        { method: 'PUT', path: '/api/v1/appearance/mail-template', ifMatch: '"1"', csrf: TOKEN });
      const body = JSON.parse(saved.body.toString());
      assert.equal(body.applicationUrl, 'https://support.example.com');
      assert.equal(body.de.signature, 'Gruß\n{{sender.name}}');
      assert.equal(body.en.greeting, 'Dear {{contact.name}}');
      await section.getByText('Gespeicherte Vorlage dieser Sprache.').waitFor();

      // Back to the built-in template after one more question.
      await section.getByRole('button', { name: 'Eingebaute Vorlage verwenden' }).click();
      await section.getByRole('button', { name: 'Ja, eingebaute Vorlage verwenden' }).click();
      await section.getByText('Beide Sprachen nutzen wieder die eingebaute Vorlage.').waitFor();
      assert.equal(server.writes().at(-1).method, 'DELETE');
      assert.equal(await section.getByTestId('mail-part-greeting').inputValue(), 'Guten Tag {{contact.name}},');
    });
  });

  test('the mail template follows the unsaved name and color of the page and stays usable at 320 pixels', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance', viewport: { width: 320, height: 900 } }, async (page) => {
      const preview = page.getByTestId('mail-template-preview');
      await preview.waitFor();
      await page.getByLabel('Name des Unternehmens').fill('Südlicht');
      await preview.locator('.mail-name').getByText('Südlicht').waitFor();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      assert.equal(overflow, 0, 'no horizontal scrolling');
    });
  });

  test('a person without the right sees no reset and cannot save a name', async () => {
    const server = fake();
    await withPage({ backend: server, as: BEN, hash: '#/appearance' }, async (page) => {
      await page.getByTestId('appearance-mode').waitFor();
      assert.equal(await page.getByRole('heading', { name: 'Alles zurücksetzen' }).count(), 0);
      assert.equal(await page.getByTestId('mail-template').count(), 0, 'no mail template without the right');
      await page.getByLabel('Name des Unternehmens').fill('Vorschau');
      assert.equal(await page.getByRole('button', { name: 'Namen speichern' }).isDisabled(), true);
      assert.deepEqual(server.writes(), []);
    });
  });
});
