import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend, newTag } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-072: the page "Tags" for holders of tags.manage with description, color, archive and merge, the suggestions while
// typing at a ticket and a project, and the ticket history that names merged tags, against the in-memory backend that
// follows contracts/tags.openapi.yaml.

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
const TAG_GROUP = '00000000-0000-4000-8000-00000000a072';

// Anna gets tags.manage for the company catalog and every project, in a group of its own.
function withTagRights(backend) {
  backend.state.groups.push({ id: TAG_GROUP, name: 'Tagpflege', members: [backend.anna.id], actions: [{ action: 'tags.manage', projectId: null }], fields: [] });
}

async function withApp({ prepare = withTagRights, as = ANNA, hash = '#/tags', colorScheme = 'light' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { colorScheme, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const companyTag = (backend, name, extra = {}) => {
  const tag = newTag(name, null, backend.anna, extra);
  backend.state.tags.push(tag);
  return tag;
};

describe('tag catalog care', () => {
  test('without tags.manage there is no entry and the page is refused', async () => {
    await withApp({ prepare: null, as: BEN }, async (page) => {
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'Tags' }).count(), 0);
      await page.getByText('Sie haben für diese Aktion keine Berechtigung.').first().waitFor();
    });
  });

  test('a tag gets a description and a color, both shown with the name', async () => {
    await withApp({}, async (page, backend) => {
      const tag = companyTag(backend, 'Kunde Nord');
      await page.getByRole('navigation').getByRole('link', { name: 'Tags' }).click();
      await page.getByRole('button', { name: 'Kunde Nord bearbeiten' }).click();
      const form = page.getByRole('form', { name: 'Tag Kunde Nord bearbeiten' });
      await form.getByLabel('Beschreibung').fill('Anfragen aus dem Norden');
      await form.getByLabel('Farbe').selectOption({ label: 'Petrol' });
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Tag Kunde Nord gespeichert.').waitFor();
      assert.equal(tag.color, 'teal');
      assert.equal(tag.description, 'Anfragen aus dem Norden');
      assert.equal(tag.version, 2);
      const row = page.getByTestId('catalog-company').locator('li', { hasText: 'Kunde Nord' });
      await row.getByText('Anfragen aus dem Norden').waitFor();
      assert.equal(await row.locator('.tag-dot.tag-color-teal').count(), 1);
      assert.match(await row.locator('.usage').innerText(), /0 Tickets · 0 Projekte, die Sie sehen/);
    });
  });

  test('a stale version names the conflict, archived tags show only on request', async () => {
    await withApp({}, async (page, backend) => {
      const tag = companyTag(backend, 'Alt');
      await page.getByRole('button', { name: 'Alt bearbeiten' }).click();
      tag.version = 5;
      const form = page.getByRole('form', { name: 'Tag Alt bearbeiten' });
      await form.getByLabel('Archiviert').check();
      await form.getByRole('button', { name: 'Speichern' }).click();
      await form.getByText('Der Tag wurde inzwischen geändert.', { exact: false }).waitFor();
      tag.version = 1;
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Tag Alt gespeichert.').waitFor();
      await page.getByText('Noch keine Tags.').waitFor();
      await page.getByLabel('Archivierte zeigen').check();
      await page.getByTestId('catalog-company').getByText('archiviert').waitFor();
    });
  });

  test('merging asks for a target, shows the preview and moves the assignments', async () => {
    await withApp({}, async (page, backend) => {
      const source = companyTag(backend, 'Rechnung');
      const target = companyTag(backend, 'Abrechnung', { color: 'blue' });
      const [first, second] = backend.state.tickets;
      backend.state.ticketTags.push({ ticket: first.id, tag: source.id }, { ticket: second.id, tag: source.id }, { ticket: second.id, tag: target.id });
      await page.getByRole('button', { name: 'Rechnung zusammenführen', exact: true }).click();
      const group = page.getByRole('group', { name: 'Tag Rechnung zusammenführen' });
      const picker = group.getByRole('combobox', { name: 'Ziel' });
      await picker.fill('abr');
      const option = group.getByRole('option', { name: 'Abrechnung' });
      await option.waitFor();
      assert.equal(await group.getByRole('option', { name: 'Rechnung', exact: true }).count(), 0, 'the source is no target');
      await picker.press('Enter');
      const preview = page.getByTestId('merge-preview');
      await preview.getByText('„Rechnung“ wird in „Abrechnung“ zusammengeführt. Betroffen sind 2 Tickets und 0 Projekte, die Sie sehen.').waitFor();
      await preview.getByText('Zuordnungen an Tickets, die Sie nicht sehen, werden ebenfalls umgestellt.').waitFor();
      await preview.getByRole('button', { name: 'In Abrechnung zusammenführen' }).click();
      await page.getByText('Rechnung wurde in Abrechnung zusammengeführt.').waitFor();
      assert.equal(source.mergedInto, target.id);
      assert.deepEqual(backend.state.ticketTags.map((entry) => entry.tag), [target.id, target.id], 'one assignment per ticket');
      const list = page.getByTestId('catalog-company');
      await list.getByText('Rechnung', { exact: true }).waitFor({ state: 'detached' });
      await list.locator('li', { hasText: 'Abrechnung' }).getByText('2 Tickets · 0 Projekte, die Sie sehen').waitFor();
      const merges = backend.state.requests.filter((request) => request.path === `/api/v1/tags/${source.id}/merge` && request.method === 'POST');
      assert.equal(merges.length, 1);
    });
  });

  test('the dark display keeps the tag readable with its color mark', async () => {
    await withApp({ colorScheme: 'dark' }, async (page, backend) => {
      companyTag(backend, 'Dringend', { color: 'red' });
      const label = page.getByTestId('catalog-company').locator('.tag-label', { hasText: 'Dringend' });
      await label.waitFor();
      const dot = await label.locator('.tag-dot').evaluate((node) => getComputedStyle(node).backgroundColor);
      assert.equal(dot, 'rgb(248, 113, 113)', 'the lighter red of the dark palette');
    });
  });
});

describe('tag suggestions and history', () => {
  test('a ticket takes a suggested tag by keyboard and creates a new one in the project', async () => {
    await withApp({ prepare: null, hash: '#/start' }, async (page, backend) => {
      companyTag(backend, 'Sicherheitslücke');
      companyTag(backend, 'Alt archiviert', { archived: true });
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets/1`; }, backend.project.id);
      const section = page.getByRole('region', { name: 'Tags' });
      const picker = section.getByRole('combobox', { name: 'Tag zuordnen' });
      await picker.fill('sich');
      await section.getByRole('option', { name: 'Sicherheit', exact: true }).waitFor();
      await section.getByRole('option', { name: 'Sicherheitslücke (Unternehmen)' }).waitFor();
      assert.equal(await section.getByRole('option', { name: /archiviert/ }).count(), 0);
      await picker.press('ArrowDown');
      await picker.press('Enter');
      await section.getByTestId('ticket-tags').getByText('Sicherheitslücke').waitFor();

      await picker.fill('Neu im Team');
      const option = section.getByRole('option', { name: 'Neuen Tag „Neu im Team“ im Projekt anlegen' });
      await option.click();
      await section.getByTestId('ticket-tags').getByText('Neu im Team').waitFor();
      const created = backend.state.tags.find((tag) => tag.name === 'Neu im Team');
      assert.equal(created.projectId, backend.project.id);
      await picker.fill('Sicherheitslücke');
      await section.getByText('Keine passenden Tags.').waitFor();
      await picker.press('Escape');
      assert.equal(await section.getByRole('listbox').isHidden(), true);
    });
  });

  test('the history names a merged tag with its target', async () => {
    await withApp({ prepare: null, hash: '#/start' }, async (page, backend) => {
      const target = companyTag(backend, 'Kundenwunsch');
      const source = companyTag(backend, 'Wunsch', { mergedInto: target.id, version: 2 });
      const ticket = backend.state.tickets[0];
      backend.state.ticketTags.push({ ticket: ticket.id, tag: target.id });
      backend.state.history.push({ ticket: ticket.id, version: 1, occurredAt: '2026-10-21T09:00:00.000Z', actor: backend.anna.id,
        changes: [{ field: 'tags', from: null, to: source.id }] });
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tickets/1`; }, backend.project.id);
      await page.getByText('Wunsch (zusammengeführt in Kundenwunsch)', { exact: false }).waitFor();
      const lookup = backend.state.requests.find((request) => request.path === `/api/v1/projects/${backend.project.id}/catalog/lookup`);
      assert.ok(lookup, 'the names come from the lookup, not from the whole catalog');
    });
  });

  test('a project takes tags of the company catalog through the suggestions', async () => {
    await withApp({ prepare: null, hash: '#/start' }, async (page, backend) => {
      companyTag(backend, 'Plattformteam');
      await page.evaluate((id) => { location.hash = `#/projects/${id}/tags`; }, backend.project.id);
      const section = page.getByRole('region', { name: 'Tags am Projekt' });
      const picker = section.getByRole('combobox', { name: 'Tag zuordnen' });
      await picker.fill('plat');
      await section.getByRole('option', { name: 'Plattformteam' }).click();
      await section.getByTestId('project-assigned-tags').getByText('Plattformteam').waitFor();
      assert.equal(await section.getByRole('option', { name: 'Sicherheit' }).count(), 0, 'project tags do not go on projects');
      await section.getByRole('button', { name: 'Tag Plattformteam entfernen' }).click();
      await section.getByText('Keine Tags zugeordnet.').waitFor();
      assert.equal(backend.state.projectTags.length, 0);
    });
  });
});
