import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// Review probes for TASK-090 / PR #268 (not part of the product test suite). Same stand in as design.test.mjs.
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
const base = () => server.url.replace('127.0.0.1', 'localhost');
const fake = (options = {}) => createBackend({ accent: '#2563eb', ...options });
async function withPage(backend, body) {
  const session = await open(browser, `${base()}#/appearance`, { setup: (context, page) => backend.install(context, page, { as: ANNA }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page);
  } finally {
    await session.context.close();
  }
}
const body = (a) => ({ name: a.name, defaultName: 'Task Manager', accentColor: a.accent, defaultAccentColor: '#f97316',
  logo: a.logo && (({ content, ...shown }) => shown)(a.logo), version: a.version, updatedAt: '2026-10-19T06:00:00Z' });

// Holds the next request of the method; `change` may change the state like the server would before the answer is held.
async function holdNext(page, server, method, path, change = () => {}) {
  let armed = false;
  let arrived;
  let release;
  const requested = new Promise((resolve) => { arrived = resolve; });
  const held = new Promise((resolve) => { release = resolve; });
  await page.route((url) => url.pathname === path, async (route) => {
    if (!armed || route.request().method() !== method) return route.fallback();
    armed = false;
    change(server.state.appearance);
    const answer = body(server.state.appearance);
    arrived();
    await held;
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { ETag: `"${answer.version}"` }, body: JSON.stringify(answer) });
  });
  return { arm: () => { armed = true; }, requested, release: () => release() };
}
const ready = (page) => page.waitForFunction(() => document.querySelector('[data-testid="brand-name-input"]')?.value === 'Nordlicht');
const conflict = async (page) => {
  await page.getByLabel('Name des Unternehmens').fill('Erster Entwurf');
  await page.getByRole('button', { name: 'Namen speichern' }).click();
  await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
};
const logoWrites = (server) => server.writes().filter((item) => item.path === '/api/v1/appearance/logo').length;

describe('TASK-090 probes', () => {
  test('P1 a file chosen while the current version is loading sends nothing', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage(server, async (page) => {
      const hold = await holdNext(page, server, 'GET', '/api/v1/appearance');
      await ready(page);
      server.state.appearance.version = 9;
      await conflict(page);
      hold.arm();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await hold.requested;
      await page.getByTestId('logo-file').setInputFiles({ name: 'logo.png', mimeType: 'image/png', buffer: PNG });
      await page.waitForTimeout(300);
      assert.equal(logoWrites(server), 0);
      hold.release();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
    });
  });

  test('P2 a drop while a name is being saved sends nothing', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage(server, async (page) => {
      const hold = await holdNext(page, server, 'PUT', '/api/v1/appearance', (a) => { a.name = 'Gehalten'; a.version += 1; });
      await ready(page);
      await page.getByLabel('Name des Unternehmens').fill('Gehalten');
      hold.arm();
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await hold.requested;
      await page.getByTestId('logo-dropzone').evaluate((zone, bytes) => {
        const transfer = new DataTransfer();
        transfer.items.add(new File([new Uint8Array(bytes)], 'logo.png', { type: 'image/png' }));
        zone.dispatchEvent(new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true }));
      }, [...PNG]);
      await page.waitForTimeout(300);
      assert.equal(logoWrites(server), 0);
      hold.release();
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
    });
  });

  test('P3 two quick Enter presses on a save button send one change', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage(server, async (page) => {
      await ready(page);
      await page.getByLabel('Name des Unternehmens').fill('Schnell');
      await page.getByRole('button', { name: 'Namen speichern' }).focus();
      await page.keyboard.press('Enter');
      await page.keyboard.press('Enter');
      await page.getByText('Der Name ist für alle gespeichert.').waitFor();
      await page.waitForTimeout(300);
      assert.equal(server.writes().filter((item) => item.path === '/api/v1/appearance').length, 1);
    });
  });

  test('P4 a late answer of a left page keeps a newer saved color', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage(server, async (page) => {
      const hold = await holdNext(page, server, 'GET', '/api/v1/appearance');
      await ready(page);
      server.state.appearance.version = 9;
      await conflict(page);
      hold.arm();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await hold.requested;
      await page.evaluate(() => { location.hash = '#/start'; });
      await page.getByLabel('Name des Unternehmens').waitFor({ state: 'detached' });
      await page.evaluate(() => { location.hash = '#/appearance'; });
      await page.getByRole('button', { name: 'Violett' }).click();
      const save = page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' });
      await save.click();
      await page.getByText('Der Eintrag wurde zwischenzeitlich geändert.').waitFor();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.getByText('Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.').waitFor();
      await page.getByRole('button', { name: 'Violett' }).click();
      await save.click();
      await page.getByText('Die Akzentfarbe ist für alle gespeichert.').waitFor();
      const saved = server.state.appearance.accent;
      hold.release();
      await page.waitForTimeout(400);
      assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--brand-primary').trim()), saved);
    });
  });

  test('P5 a successful save whose answer arrives after leaving the page', async () => {
    const server = fake({ brandName: 'Nordlicht' });
    await withPage(server, async (page) => {
      const hold = await holdNext(page, server, 'PUT', '/api/v1/appearance', (a) => { a.name = 'Gespeichert'; a.version += 1; });
      await ready(page);
      const before = server.state.appearance.version;
      await page.getByLabel('Name des Unternehmens').fill('Gespeichert');
      hold.arm();
      await page.getByRole('button', { name: 'Namen speichern' }).click();
      await hold.requested;
      await page.evaluate(() => { location.hash = '#/start'; });
      await page.getByLabel('Name des Unternehmens').waitFor({ state: 'detached' });
      hold.release();
      await page.waitForTimeout(400);
      await page.evaluate(() => { location.hash = '#/appearance'; });
      await page.getByLabel('Name des Unternehmens').waitFor();
      const field = await page.getByLabel('Name des Unternehmens').inputValue();
      await page.getByRole('button', { name: 'Violett' }).click();
      await page.locator('section[aria-labelledby="accent-heading"]').getByRole('button', { name: 'Für alle speichern' }).click();
      await page.waitForTimeout(400);
      const last = server.writes().at(-1);
      console.log(JSON.stringify({ serverName: server.state.appearance.name, serverVersion: server.state.appearance.version, before, field, nextIfMatch: last.ifMatch }));
    });
  });
});
