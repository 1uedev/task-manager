import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const dist = resolve(fileURLToPath(new URL('../../dist/', import.meta.url)));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };

// Serves the production build. Every unknown path answers with index.html like the later single page hosting.
export async function startServer() {
  const server = createServer(async (request, response) => {
    let path;
    try {
      path = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
    } catch {
      response.writeHead(400).end();
      return;
    }
    const file = path === '/' || extname(path) === '' ? 'index.html' : path.slice(1);
    // The resolved path must stay inside dist, whatever the request contains or the platform uses as separator.
    const target = resolve(dist, file);
    if (target !== dist && !target.startsWith(dist + sep)) {
      response.writeHead(404).end();
      return;
    }
    try {
      const body = await readFile(target);
      response.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
      response.end(body);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  return { url: `http://127.0.0.1:${port}/`, close: () => new Promise((resolve) => server.close(resolve)) };
}

// TASKMANAGER_BROWSER points to a Chromium based executable; otherwise the installed Chrome is used.
export async function launch() {
  const executablePath = process.env.TASKMANAGER_BROWSER;
  return chromium.launch(executablePath ? { executablePath } : { channel: 'chrome' });
}

// Opens a page and records everything the application writes to the console or throws.
// `setup` runs before the first navigation, for example to answer API requests in place of the backend.
export async function open(browser, url, { locale = 'de-DE', viewport = { width: 1100, height: 800 }, colorScheme = 'light', setup } = {}) {
  const context = await browser.newContext({ locale, viewport, colorScheme });
  const page = await context.newPage();
  if (setup) await setup(context, page);
  const problems = [];
  page.on('console', (message) => {
    if (['warning', 'error'].includes(message.type())) problems.push(`${message.type()}: ${message.text()}`);
  });
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`));
  await page.goto(url);
  await page.waitForSelector('main h1');
  return { context, page, problems };
}
