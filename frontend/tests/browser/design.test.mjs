import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { auditContrast } from './audit.mjs';
import { ANNA, BEN, TOKEN, createBackend } from './backend.mjs';
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

  test('before the login the default orange is used and nothing of the company is read', async () => {
    const backend = fake();
    await withPage({ backend, as: null }, async (page) => {
      assert.equal(await accent(page), '#f97316');
      assert.deepEqual([...new Set(backend.state.requests.map((item) => item.path))], ['/api/v1/session']);
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

  test('an invalid typed color cannot save the last valid one', async () => {
    const server = fake();
    await withPage({ backend: server, hash: '#/appearance' }, async (page) => {
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
      assert.deepEqual(await order(), ['start', 'board', 'projects', 'search', 'inbox']);
      assert.match(await live(page), /Board steht jetzt an Position 2 von 5/);
      assert.equal(await page.evaluate(() => document.activeElement?.textContent?.trim()), 'Board');
      await page.reload();
      await page.waitForSelector('.shell main h1');
      assert.deepEqual(await order(), ['start', 'board', 'projects', 'search', 'inbox']);
    });
  });

  test('navigation entries can be dragged with the mouse', async () => {
    await withPage({}, async (page) => {
      await page.dragAndDrop('.nav-main li[data-route="search"]', '.nav-main li[data-route="start"]');
      assert.deepEqual(await page.locator('.nav-main li').evaluateAll((items) => items.map((item) => item.dataset.route)), ['search', 'start', 'projects', 'inbox', 'board']);
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
