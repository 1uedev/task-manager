import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { chromium } from 'playwright-core';

const dist = new URL('../../dist/', import.meta.url).pathname;
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };

// Serves the production build. Every unknown path answers with index.html like the later single page hosting.
export async function startServer() {
  const server = createServer(async (request, response) => {
    const path = normalize(new URL(request.url ?? '/', 'http://localhost').pathname).replace(/^(\.\.[/\\])+/, '');
    const file = path === '/' || extname(path) === '' ? 'index.html' : path.slice(1);
    try {
      const body = await readFile(join(dist, file));
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
export async function open(browser, url, { locale = 'de-DE', viewport = { width: 1100, height: 800 } } = {}) {
  const context = await browser.newContext({ locale, viewport });
  const page = await context.newPage();
  const problems = [];
  page.on('console', (message) => {
    if (['warning', 'error'].includes(message.type())) problems.push(`${message.type()}: ${message.text()}`);
  });
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`));
  await page.goto(url);
  await page.waitForSelector('main h1');
  return { context, page, problems };
}
