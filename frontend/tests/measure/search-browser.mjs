// Browser measurement of the search (TASK-104, DEC-134, rule 7): from the trigger (the click on "Suchen" or Enter in the
// quick search) until the first page of the answer is visible, 200 repetitions per class against a running installation
// with the reference data set. Each repetition opens its area anew, so that no earlier answer can count as the new one.
// The time is taken in this process around the click and the wait for the visible result; it includes the protocol of the
// browser automation and is an upper bound of what a person sees. Only synthetic reference accounts are used.
//
//   TASK_MANAGER_URL=http://localhost:8080 TASK_MANAGER_DEMO_PASSWORD=... TASKMANAGER_BROWSER=/path/to/chromium \
//     npm run measure:search -- --repetitions 200 --user ref001 --out ../demo-results
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { performance } from 'node:perf_hooks';
import { parseArgs } from 'node:util';
import { chromium } from 'playwright-core';

const { values: options } = parseArgs({
  options: {
    repetitions: { type: 'string', default: '200' },
    user: { type: 'string', default: 'ref001' },
    out: { type: 'string', default: process.env.TASK_MANAGER_RESULTS ?? 'results' },
    warmup: { type: 'string', default: '10' },
  },
});
const base = (process.env.TASK_MANAGER_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const password = process.env.TASK_MANAGER_DEMO_PASSWORD ?? '';
if (password.length < 12) throw new Error('TASK_MANAGER_DEMO_PASSWORD must hold the password of the reference accounts');
const repetitions = Number(options.repetitions);
const warmup = Number(options.warmup);
const TARGET_MS = 1000;

// The vocabulary of tools/demo/demo.py: frequent topic words, so that most searches find something like real ones.
const TOPIC = ['abrechnung', 'anmeldung', 'archiv', 'auftrag', 'auswertung', 'bericht', 'bestellung', 'datenbank', 'dokument', 'drucker',
  'export', 'filter', 'formular', 'freigabe', 'import', 'kalender', 'kasse', 'katalog', 'konto', 'lager'];

let seed = 104;
const random = () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};
const pick = (list) => list[Math.floor(random() * list.length)];

const visibleResult = 'main [aria-live=polite] :is(ul.item-list li, p.empty)';

/** The classes: where the search starts, how it is triggered and what shows its first page. */
function classes(keys) {
  const area = (name, hash, form) => ({
    name,
    async prepare(page) {
      await page.goto(`${base}/#/start`);
      await page.goto(`${base}/${hash}`);
      await page.getByRole('search', { name: form }).getByLabel('Suchtext').fill(pick(TOPIC));
    },
    trigger: (page) => page.getByRole('search', { name: form }).getByRole('button', { name: 'Suchen' }).click(),
    visible: (page) => page.waitForSelector(hash === '#/search/all' ? '[data-testid=overview]' : visibleResult),
  });
  return [
    area('Tickets ein Wort', '#/search/tickets', 'Tickets durchsuchen'),
    area('Wiki ein Wort', '#/search/wiki', 'Wiki durchsuchen'),
    area('Anforderung ein Wort', '#/search/requirements', 'Anforderungen durchsuchen'),
    area('Übersicht Alle', '#/search/all', 'Alles durchsuchen'),
    {
      name: 'Schnellsuche mit Kennung',
      key: null,
      async prepare(page) {
        await page.goto(`${base}/#/start`);
        this.key = pick(keys);
        await page.getByRole('searchbox', { name: 'Schnellsuche' }).fill(this.key);
      },
      trigger: (page) => page.getByRole('searchbox', { name: 'Schnellsuche' }).press('Enter'),
      // The ticket page with its key in the heading is the first page of a direct hit.
      visible(page) {
        return page.waitForFunction((key) => document.querySelector('main h1')?.textContent?.includes(key), this.key);
      },
    },
  ];
}

function percentile(values, share) {
  const ordered = [...values].sort((a, b) => a - b);
  return ordered.length === 0 ? null : ordered[Math.max(0, Math.ceil(share * ordered.length) - 1)];
}

const round = (value) => (value === null ? null : Math.round(value * 10) / 10);

const executablePath = process.env.TASKMANAGER_BROWSER;
const browser = await chromium.launch(executablePath ? { executablePath } : { channel: 'chrome' });
const context = await browser.newContext({ locale: 'de-DE', viewport: { width: 1280, height: 900 } });
const page = await context.newPage();
const problems = [];
page.on('pageerror', (error) => problems.push(error.message));
try {
  await page.goto(`${base}/`);
  await page.getByLabel(/^Anmeldename/).fill(options.user);
  await page.getByLabel(/^Passwort/).fill(password);
  await page.getByRole('button', { name: 'Anmelden' }).click();
  await page.waitForSelector('.shell main h1');

  // Keys of tickets the person reads, from the first page of a search through the session of the page.
  const keys = await page.evaluate(async () => {
    const found = await fetch('/api/v1/search/tickets?limit=50', { credentials: 'include' }).then((response) => response.json());
    return found.items.map((item) => item.key);
  });
  const rows = [];
  for (const measured of classes(keys)) {
    const times = [];
    let failures = 0;
    for (let index = 0; index < warmup + repetitions; index += 1) {
      await measured.prepare(page);
      const started = performance.now();
      try {
        await measured.trigger(page);
        await measured.visible(page);
      } catch (error) {
        failures += 1;
        if (failures <= 3) console.error(`${measured.name}: ${error.message.split('\n')[0]}`);
        continue;
      }
      if (index >= warmup) times.push(performance.now() - started);
    }
    const p95 = percentile(times, 0.95);
    rows.push({ class: measured.name, samples: times.length, failures, p50_ms: round(percentile(times, 0.5)), p95_ms: round(p95),
      p99_ms: round(percentile(times, 0.99)), max_ms: round(times.length ? Math.max(...times) : null), target_p95_ms: TARGET_MS,
      met: p95 === null ? null : p95 <= TARGET_MS });
    console.log(`${measured.name}: ${times.length} values, p95 ${round(p95)} ms`);
  }
  const result = { started: new Date().toISOString(), server: base, user: options.user, repetitions, warmup,
    browser: browser.version(), problems, classes: rows };
  await mkdir(options.out, { recursive: true });
  const stamp = result.started.replace(/[-:]/g, '').replace('T', '-').slice(0, 15);
  await writeFile(join(options.out, `browser-${stamp}.json`), `${JSON.stringify(result, null, 2)}\n`);
  const table = ['| Messklasse | Messwerte | Fehler | p50 ms | p95 ms | p99 ms | Ziel p95 ms | erfüllt |', '|---|---:|---:|---:|---:|---:|---:|---|',
    ...rows.map((row) => `| ${row.class} | ${row.samples} | ${row.failures} | ${row.p50_ms} | ${row.p95_ms} | ${row.p99_ms} | ${row.target_p95_ms} | ${row.met === null ? '' : row.met ? 'ja' : 'nein'} |`)];
  await writeFile(join(options.out, `browser-${stamp}.md`), `Browsermessung ${result.started}: ${options.user}, ${repetitions} Wiederholungen je Klasse nach ${warmup} zum Aufwärmen, ${result.browser}\n\n${table.join('\n')}\n`);
  console.log(table.join('\n'));
} finally {
  await browser.close();
}
