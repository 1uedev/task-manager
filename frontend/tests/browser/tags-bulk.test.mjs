import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend, newTag } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-073: the tag filter (all, any, none, without tags) and the bulk change of tags in the search and in the ticket list
// of a project, with selection, check before the change and the counted result, against the in-memory backend that follows
// contracts/tags.openapi.yaml and contracts/search.openapi.yaml.

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

async function withApp({ as = ANNA, hash = '#/start', prepare = null } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const companyTag = (backend, name) => {
  const tag = newTag(name, null, backend.anna);
  backend.state.tags.push(tag);
  return tag;
};

async function pickTag(scope, label, typed, option) {
  const picker = scope.getByRole('combobox', { name: label });
  await picker.fill(typed);
  await scope.getByRole('option', { name: option, exact: true }).click();
}

// Holds the answers of the ticket searches that match until `release` lets them through, then gives the page a moment.
async function holdSearch(page, matches) {
  let open;
  const gate = new Promise((resolve) => { open = resolve; });
  let asked;
  const first = new Promise((resolve) => { asked = resolve; });
  const pending = [];
  await page.route((url) => url.pathname === '/api/v1/search/tickets' && matches(url), async (route) => {
    pending.push(page.waitForEvent('requestfinished', (request) => request === route.request()));
    asked();
    await gate;
    await route.fallback();
  });
  return {
    asked: first,
    async release() {
      open();
      await Promise.all(pending);
      await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 300)));
    },
  };
}

describe('tag filter and bulk change', () => {
  test('the search filters by any of the tags and adds a tag to the selected tickets after a check', async () => {
    await withApp({ hash: '#/search' }, async (page, backend) => {
      const urgent = companyTag(backend, 'Dringend');
      const customer = companyTag(backend, 'Kunde');
      const [first, second] = backend.state.tickets;
      backend.state.ticketTags.push({ ticket: first.id, tag: urgent.id });
      const filter = page.getByTestId('tag-filter');
      await filter.getByLabel('Art des Filters').selectOption({ label: 'Mindestens einer der Tags' });
      await pickTag(filter, 'Tag für den Filter', 'dri', 'Dringend');
      await filter.getByLabel('Gewählte Tags').getByText('Dringend').waitFor();
      await page.getByRole('button', { name: 'Suchen' }).click();
      const hits = page.getByTestId('ticket-hits');
      await hits.getByText('PLAT-1').waitFor();
      assert.equal(await hits.locator('li').count(), 1, 'only the ticket with the tag');
      const request = backend.state.requests.findLast((item) => item.path === '/api/v1/search/tickets');
      assert.match(request.query, /tagMatch=any/);

      await filter.getByLabel('Art des Filters').selectOption({ label: 'Keiner der gewählten Tags' });
      await page.getByRole('button', { name: 'Suchen' }).click();
      await hits.getByText('PLAT-2').waitFor();
      assert.equal(await hits.locator('li').count(), 1);

      await filter.getByRole('button', { name: 'Tag Dringend entfernen' }).click();
      await page.getByRole('button', { name: 'Suchen' }).click();
      await hits.getByText('PLAT-1').waitFor();
      await page.getByRole('checkbox', { name: 'Alle angezeigten Tickets auswählen' }).first().check();
      await page.getByText('2 Tickets ausgewählt').waitFor();
      const bulk = page.getByTestId('tag-bulk');
      await bulk.getByRole('heading', { name: 'Tags an 2 ausgewählten Tickets ändern' }).waitFor();
      await pickTag(bulk, 'Tag wählen', 'kun', 'Kunde');
      await bulk.getByRole('button', { name: 'Änderung prüfen' }).click();
      const preview = bulk.getByTestId('tag-bulk-preview');
      await preview.getByText('„Kunde“ wird an 2 Tickets hinzugefügt.').waitFor();
      assert.equal(backend.state.requests.filter((item) => item.path === '/api/v1/tags/bulk').length, 0, 'nothing changes before the confirmation');
      await preview.getByRole('button', { name: 'Änderung ausführen' }).click();
      await bulk.getByTestId('tag-bulk-result').getByText('Geändert: 2, schon so: 0, nicht passend: 0, abgelehnt: 0.').waitFor();
      assert.deepEqual(backend.state.ticketTags.filter((entry) => entry.tag === customer.id).map((entry) => entry.ticket).sort(), [first.id, second.id].sort());
    });
  });

  test('the ticket list of a project shows tickets without tags and names a refused ticket with its reason', async () => {
    await withApp({}, async (page, backend) => {
      const [first, second] = backend.state.tickets;
      const project = backend.state.tags.find((tag) => tag.name === 'Sicherheit');
      for (let i = 0; i < 30; i++) {
        const tag = companyTag(backend, `Voll ${i}`);
        backend.state.ticketTags.push({ ticket: second.id, tag: tag.id });
      }
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets`; }, backend.project.id);
      const table = page.getByTestId('ticket-table');
      await table.getByText('PLAT-2').waitFor();
      await page.getByTestId('tag-filter').getByLabel('Art des Filters').selectOption({ label: 'Ohne sichtbare Tags' });
      await page.getByText('Gefiltert nach Tags', { exact: false }).waitFor();
      await table.getByText('PLAT-2').waitFor({ state: 'detached' });
      assert.equal(await table.locator('tbody tr').count(), 1, 'only the ticket without tags');
      await table.getByRole('checkbox', { name: 'Ticket PLAT-1 auswählen' }).check();

      await page.getByTestId('tag-filter').getByLabel('Art des Filters').selectOption({ label: 'Alle gewählten Tags' });
      await table.getByText('PLAT-2').waitFor();
      await table.getByRole('checkbox', { name: 'Ticket PLAT-2 auswählen' }).check();
      await page.getByText('2 Tickets ausgewählt').waitFor();
      const bulk = page.getByTestId('tag-bulk');
      await pickTag(bulk, 'Tag wählen', 'sich', 'Sicherheit');
      await bulk.getByRole('button', { name: 'Änderung prüfen' }).click();
      await bulk.getByRole('button', { name: 'Änderung ausführen' }).click();
      const result = bulk.getByTestId('tag-bulk-result');
      await result.getByText('Geändert: 1, schon so: 0, nicht passend: 0, abgelehnt: 1.').waitFor();
      await result.getByText('PLAT-2: Das Ticket hätte mehr als 30 Tags.').waitFor();
      assert.ok(backend.state.ticketTags.some((entry) => entry.ticket === first.id && entry.tag === project.id));
      assert.ok(!backend.state.ticketTags.some((entry) => entry.ticket === second.id && entry.tag === project.id));
    });
  });

  test('a reader without the write rule gets every ticket refused with the reason and nothing changes', async () => {
    await withApp({ as: BEN, hash: '#/search' }, async (page, backend) => {
      companyTag(backend, 'Kunde');
      await page.getByRole('button', { name: 'Suchen' }).click();
      await page.getByTestId('ticket-hits').getByText('PLAT-2').waitFor();
      await page.getByRole('checkbox', { name: 'Ticket PLAT-2 auswählen' }).check();
      const bulk = page.getByTestId('tag-bulk');
      await pickTag(bulk, 'Tag wählen', 'kun', 'Kunde');
      await bulk.getByRole('button', { name: 'Änderung prüfen' }).click();
      await bulk.getByRole('button', { name: 'Änderung ausführen' }).click();
      const result = bulk.getByTestId('tag-bulk-result');
      await result.getByText('Geändert: 0, schon so: 0, nicht passend: 0, abgelehnt: 1.').waitFor();
      await result.getByText('PLAT-2: Sie dürfen die Tags dieses Tickets nicht ändern.').waitFor();
      assert.equal(backend.state.ticketTags.length, 0);
      await page.getByRole('button', { name: 'Auswahl aufheben' }).click();
      assert.equal(await page.getByTestId('tag-bulk').count(), 0);
    });
  });
  test('archived tags can be chosen for a filter and a removal, not for adding', async () => {
    await withApp({ hash: '#/search', prepare: (backend) => {
      const old = newTag('Altbestand', null, backend.anna, { archived: true });
      backend.state.tags.push(old);
      backend.state.ticketTags.push({ ticket: backend.state.tickets[0].id, tag: old.id });
    } }, async (page, backend) => {
      const old = backend.state.tags.find((tag) => tag.name === 'Altbestand');
      const filter = page.getByTestId('tag-filter');
      await pickTag(filter, 'Tag für den Filter', 'altb', 'Altbestand (archiviert)');
      await page.getByRole('button', { name: 'Suchen' }).click();
      const hits = page.getByTestId('ticket-hits');
      await hits.getByText('PLAT-1').waitFor();
      assert.equal(await hits.locator('li').count(), 1, 'the archived tag filters');

      await page.getByRole('checkbox', { name: 'Ticket PLAT-1 auswählen' }).check();
      const bulk = page.getByTestId('tag-bulk');
      const picker = bulk.getByRole('combobox', { name: 'Tag wählen' });
      await picker.fill('altb');
      await bulk.getByText('Keine passenden Tags').waitFor();
      assert.equal(await bulk.getByRole('listbox', { name: 'Tag wählen' }).getByRole('option').count(), 0, 'adding offers no archived tag');
      await picker.fill('');
      await bulk.getByLabel('Aktion').selectOption({ label: 'Tags entfernen' });
      await pickTag(bulk, 'Tag wählen', 'altb', 'Altbestand (archiviert)');
      await bulk.getByRole('button', { name: 'Änderung prüfen' }).click();
      await bulk.getByRole('button', { name: 'Änderung ausführen' }).click();
      await bulk.getByTestId('tag-bulk-result').getByText('Geändert: 1, schon so: 0, nicht passend: 0, abgelehnt: 0.').waitFor();
      assert.ok(!backend.state.ticketTags.some((entry) => entry.tag === old.id), 'the archived tag is removed');
    });
  });

  test('a late answer of an earlier search does not replace the newer one, and a reload repeats the search sent', async () => {
    await withApp({ hash: '#/search' }, async (page, backend) => {
      const urgent = companyTag(backend, 'Dringend');
      const customer = companyTag(backend, 'Kunde');
      const [first] = backend.state.tickets;
      backend.state.ticketTags.push({ ticket: first.id, tag: urgent.id });
      const filter = page.getByTestId('tag-filter');
      await filter.getByLabel('Art des Filters').selectOption({ label: 'Mindestens einer der Tags' });
      await pickTag(filter, 'Tag für den Filter', 'dri', 'Dringend');
      const held = await holdSearch(page, (url) => url.searchParams.get('tagMatch') === 'any');
      await page.getByRole('button', { name: 'Suchen' }).click();
      await held.asked;
      await filter.getByLabel('Art des Filters').selectOption({ label: 'Keiner der gewählten Tags' });
      await page.getByRole('button', { name: 'Suchen' }).click();
      const hits = page.getByTestId('ticket-hits');
      await hits.getByText('PLAT-2').waitFor();
      await held.release();
      assert.equal(await hits.getByText('PLAT-1').count(), 0, 'the late answer for "any" is dropped');
      assert.equal(await hits.locator('li').count(), 1);

      // The text field changes without a new search; the reload after the bulk change repeats the search that was sent.
      await page.getByLabel('Suchtext').fill('etwas anderes');
      await page.getByRole('checkbox', { name: 'Ticket PLAT-2 auswählen' }).check();
      const bulk = page.getByTestId('tag-bulk');
      await pickTag(bulk, 'Tag wählen', 'kun', 'Kunde');
      await bulk.getByRole('button', { name: 'Änderung prüfen' }).click();
      const before = backend.state.requests.filter((item) => item.path === '/api/v1/search/tickets').length;
      await bulk.getByRole('button', { name: 'Änderung ausführen' }).click();
      await bulk.getByTestId('tag-bulk-result').getByText('Geändert: 1, schon so: 0, nicht passend: 0, abgelehnt: 0.').waitFor();
      await hits.getByText('PLAT-2').waitFor();
      const searches = backend.state.requests.filter((item) => item.path === '/api/v1/search/tickets');
      assert.ok(searches.length > before, 'the list is reloaded');
      const reload = searches.at(-1);
      assert.match(reload.query, /tagMatch=none/);
      assert.doesNotMatch(reload.query, /anderes/, 'the unsent text is not searched');
      assert.ok(backend.state.ticketTags.some((entry) => entry.tag === customer.id));
    });
  });

  test('the stand-in checks the bulk body and the tags of a search like the server', async () => {
    await withApp({ hash: '#/search' }, async (page) => {
      const answer = await page.evaluate(async () => {
        const send = (path, init) => fetch(path, { credentials: 'include', ...init }).then(async (response) => ({ status: response.status, body: await response.json() }));
        const json = (body) => ({ method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': 'csrf-token-for-browser-tests-0001' }, body: JSON.stringify(body) });
        return {
          empty: await send('/api/v1/tags/bulk', json({ action: 'add', tags: [], tickets: [] })),
          extra: await send('/api/v1/tags/bulk', json({ action: 'add', tags: ['00000000-0000-4000-8000-999999999999'], tickets: ['00000000-0000-4000-8000-999999999998'], more: 1 })),
          unknown: await send('/api/v1/search/tickets?tag=00000000-0000-4000-8000-999999999999', {}),
        };
      });
      assert.equal(answer.empty.status, 400);
      assert.deepEqual(answer.empty.body.violations, ['tags.invalid', 'tickets.invalid']);
      assert.equal(answer.extra.status, 400);
      assert.deepEqual(answer.extra.body.violations, ['fields.unknown']);
      assert.equal(answer.unknown.status, 400);
      assert.deepEqual(answer.unknown.body.violations, ['tag.unknown']);
    });
  });
});
