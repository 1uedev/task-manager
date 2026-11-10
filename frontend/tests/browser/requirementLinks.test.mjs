import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-097: links of requirements with tickets and wiki pages of the same project, the revision a ticket refers to with the
// hint and the explicit switch, the summary of done tickets that never approves, the field "Anforderungen" at the ticket and
// the entries in both histories, against the in-memory backend that follows the contract.

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
const ALL = ['criteria', 'description', 'owner', 'priority', 'title'];

function grant(backend, accounts, actions, { read = ALL, write = ALL } = {}) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000000097${String(backend.state.groups.length).padStart(2, '0')}`, name: `Verknüpfungen ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })),
    fields: [...read.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'read', effect: 'allow' })),
      ...write.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'write', effect: 'allow' }))] });
}

async function withApp({ prepare, as = ANNA, hash, locale = 'de-DE' }, body) {
  const backend = createBackend();
  const prepared = prepare?.(backend);
  const session = await open(browser, `${base()}${hash(backend, prepared)}`, { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend, prepared);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const detailHash = (backend, item) => `#/requirements/${backend.project.id}/${item.id}`;
const ticketHash = (backend, number) => `#/projects/${backend.project.id}/tickets/${number}`;
const lastWrite = (backend, pattern) => backend.state.requests.findLast((item) => item.method !== 'GET' && pattern.test(item.path));
const bodyOf = (request) => JSON.parse(request.body.toString('utf8'));

/** Opens revision 2 of an approved requirement as the server does after a content change. */
function openRevision(backend, item) {
  const current = backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id && entry.revision === item.revision);
  backend.state.requirements.revisions.push({ ...current, revision: item.revision + 1, title: `${current.title} (überarbeitet)`, approvedAt: null, approvedBy: null });
  Object.assign(item, { revision: item.revision + 1, status: 'draft', version: item.version + 1 });
}

describe('requirement links', () => {
  test('a ticket keeps its revision until it is switched, and a done ticket counts but never approves', async () => {
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['requirement.read', 'requirement.edit', 'requirement.approve', 'wiki.read']);
        return backend.addRequirement(backend.anna, 'Kunden sehen den Stand', { criteria: ['Liste zeigt den Status'], status: 'approved' });
      },
      hash: detailHash,
    }, async (page, backend, item) => {
      const links = page.getByTestId('requirement-links');
      await links.getByTestId('requirement-links-summary').waitFor();
      assert.equal(await links.getByTestId('requirement-links-summary').textContent(), '0 Tickets, davon 0 erledigt');
      await links.getByLabel('Ticketnummer').fill('9');
      await links.getByRole('button', { name: 'Ticket verknüpfen' }).click();
      await links.getByText('Ein Ticket mit dieser Nummer gibt es in diesem Projekt nicht oder du darfst es nicht sehen.').waitFor();
      await links.getByLabel('Ticketnummer').fill('1');
      await links.getByRole('button', { name: 'Ticket verknüpfen' }).click();
      const link = links.getByTestId('requirement-ticket-link');
      await link.waitFor();
      assert.match(await link.textContent(), /Anmeldung prüfen · Neu · bezieht sich auf Revision 1/);
      assert.equal(await links.getByTestId('requirement-links-summary').textContent(), '1 Ticket, davon 0 erledigt');
      const linked = lastWrite(backend, /\/tickets$/);
      assert.deepEqual(bodyOf(linked), { number: 1 });
      assert.equal(linked.ifMatch, null, 'a link names no version');
      assert.equal(item.version, 1, 'linking keeps the version of the requirement');
      assert.equal(backend.state.tickets[0].version, 1, 'linking keeps the version of the ticket');

      // A newer revision: the hint names it until the explicit switch binds the ticket to it.
      openRevision(backend, item);
      await page.reload();
      await link.waitFor();
      assert.equal(await page.getByTestId('requirement-ticket-newer').textContent(), 'Neuere Revision 2 vorhanden');
      await page.getByRole('button', { name: `${backend.state.tickets[0].key} auf Revision 2 umstellen` }).click();
      await page.getByText('Auf Revision 2 umgestellt.').waitFor();
      assert.equal(await page.getByTestId('requirement-ticket-newer').count(), 0);
      assert.deepEqual(bodyOf(lastWrite(backend, /\/switch$/)), { revision: 2 });

      // F-14: the ticket is done; the summary counts it, the requirement keeps status and approval.
      backend.state.tickets[0].values.status = 'done';
      await page.reload();
      await link.waitFor();
      assert.equal(await links.getByTestId('requirement-links-summary').textContent(), '1 Ticket, davon 1 erledigt');
      assert.equal(await page.getByTestId('requirement').getAttribute('data-status'), 'draft');
      assert.equal(item.approvedRevision, 1);

      await page.getByRole('button', { name: `Verknüpfung mit ${backend.state.tickets[0].key} lösen` }).click();
      await page.getByText('Noch keine Tickets verknüpft.').waitFor();

      await page.getByTestId('requirement-history-open').click();
      const events = page.getByTestId('requirement-events');
      await events.getByText(`Ticket ${backend.state.tickets[0].key} mit Revision 1 verknüpft`).waitFor();
      await events.getByText(`Ticket ${backend.state.tickets[0].key} von Revision 1 auf Revision 2 umgestellt`).waitFor();
      await events.getByText(`Verknüpfung mit Ticket ${backend.state.tickets[0].key} gelöst`).waitFor();
    });
  });

  test('wiki pages show their published title to wiki readers and are not available to others', async () => {
    let pages;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['requirement.read', 'requirement.edit', 'wiki.read']);
        pages = [backend.addWikiPage(backend.anna, 'Portalhandbuch', { type: 'doc', content: [{ type: 'paragraph' }] }, { publish: true }),
          backend.addWikiPage(backend.anna, 'Entwurf', { type: 'doc', content: [{ type: 'paragraph' }] })];
        return backend.addRequirement(backend.anna, 'Handbuch verlinken', { criteria: ['Seite ist verknüpft'] });
      },
      hash: detailHash,
    }, async (page, backend, item) => {
      const form = page.getByTestId('requirement-page-link-form');
      await form.waitFor();
      const options = await form.getByLabel('Wikiseite').locator('option').allTextContents();
      assert.deepEqual(options, ['Seite wählen', 'Portalhandbuch'], 'only published pages are offered');
      await form.getByLabel('Wikiseite').selectOption(pages[0].id);
      await form.getByRole('button', { name: 'Seite verknüpfen' }).click();
      const link = page.getByTestId('requirement-page-link');
      await link.getByRole('link', { name: 'Portalhandbuch' }).waitFor();
      assert.equal(await link.getByRole('link').getAttribute('href'), `#/wiki/${backend.project.id}/${pages[0].id}`);
      assert.equal(backend.state.requirements.pageLinks.length, 1);
      assert.equal(item.version, 1);
    });

    // Ben may read requirements, but neither the wiki nor the tickets: the page has no title, tickets are not shown or counted.
    await withApp({
      as: BEN,
      prepare: (backend) => {
        grant(backend, [backend.ben], ['requirement.read']);
        backend.state.groups[1].members = backend.state.groups[1].members.filter((id) => id !== backend.ben.id);
        const shown = backend.addWikiPage(backend.anna, 'Portalhandbuch', { type: 'doc', content: [{ type: 'paragraph' }] }, { publish: true });
        const item = backend.addRequirement(backend.anna, 'Handbuch verlinken', { criteria: ['Seite ist verknüpft'] });
        backend.state.requirements.pageLinks.push({ requirementId: item.id, pageId: shown.id, linkedAt: new Date().toISOString(), linkedBy: backend.anna.id });
        backend.state.requirements.ticketLinks.push({ requirementId: item.id, ticketId: backend.state.tickets[0].id, revision: 1, linkedAt: new Date().toISOString(),
          linkedBy: backend.anna.id });
        return item;
      },
      hash: detailHash,
    }, async (page) => {
      const links = page.getByTestId('requirement-links');
      await links.getByText('Seite nicht verfügbar').waitFor();
      await links.getByText('Die Tickets dieses Projekts darfst du nicht sehen.').waitFor();
      assert.equal(await links.getByTestId('requirement-ticket-link').count(), 0);
      assert.equal(await links.getByTestId('requirement-links-summary').count(), 0);
      assert.equal(await links.getByRole('button').count(), 0, 'no changes without requirement.edit');
    });
  });

  test('the ticket shows its requirements with the revision hint and links one from the ticket side', async () => {
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['requirement.read', 'requirement.edit']);
        const linked = backend.addRequirement(backend.anna, 'Anmeldung mit Firmenkonto', { criteria: ['Konto meldet sich an'], status: 'approved' });
        backend.addRequirement(backend.anna, 'Sitzung endet nach 24 Stunden', { criteria: ['Abmeldung nach Ablauf'] });
        backend.state.requirements.ticketLinks.push({ requirementId: linked.id, ticketId: backend.state.tickets[0].id, revision: 1, linkedAt: new Date().toISOString(),
          linkedBy: backend.anna.id });
        openRevision(backend, linked);
        return linked;
      },
      hash: (backend) => ticketHash(backend, 1),
    }, async (page, backend) => {
      const panel = page.getByTestId('ticket-requirements');
      const first = panel.getByTestId('ticket-requirement').first();
      await first.waitFor();
      assert.match(await first.textContent(), /ANF-1 Anmeldung mit Firmenkonto \(überarbeitet\) · Revision 1/);
      assert.equal(await panel.getByTestId('ticket-requirement-newer').textContent(), ' Neuere Revision 2 vorhanden');
      const search = panel.getByTestId('ticket-requirement-search');
      await search.getByLabel('Anforderung suchen').fill('Sitzung');
      await search.getByRole('button', { name: 'Suchen' }).click();
      await search.getByRole('button', { name: 'ANF-2 verknüpfen' }).click();
      await panel.getByText('Verknüpft.').waitFor();
      await panel.locator('[data-key="ANF-2"]').waitFor();
      assert.equal(backend.state.tickets[0].version, 1, 'the ticket keeps its version');
      const history = page.getByTestId('history');
      await history.getByText('Anforderung ANF-2 mit Revision 1 verknüpft').waitFor();
      await panel.getByRole('button', { name: 'Verknüpfung mit ANF-2 lösen' }).click();
      await history.getByText('Verknüpfung mit Anforderung ANF-2 gelöst').waitFor();
      assert.equal(await panel.locator('[data-key="ANF-2"]').count(), 0);
    });

    // Without requirement.read the ticket shows no requirements panel and no entry of the links in its history.
    await withApp({
      as: BEN,
      prepare: (backend) => {
        const linked = backend.addRequirement(backend.anna, 'Anmeldung mit Firmenkonto', { criteria: ['Konto meldet sich an'] });
        backend.state.requirements.ticketLinks.push({ requirementId: linked.id, ticketId: backend.state.tickets[0].id, revision: 1, linkedAt: new Date().toISOString(),
          linkedBy: backend.anna.id });
        backend.state.history.push({ ticket: backend.state.tickets[0].id, version: 1, occurredAt: new Date().toISOString(), actor: backend.anna.id,
          changes: [{ field: 'requirements', from: null, to: 'ANF-1:1' }] });
      },
      hash: (backend) => ticketHash(backend, 1),
    }, async (page) => {
      await page.getByTestId('history').waitFor();
      assert.equal(await page.getByTestId('ticket-requirements').count(), 0);
      assert.equal(await page.getByText('Anforderung ANF-1 mit Revision 1 verknüpft').count(), 0);
    });
  });

  test('the links work in English as well', async () => {
    await withApp({
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.anna], ['requirement.read', 'requirement.edit']);
        const item = backend.addRequirement(backend.anna, 'Sign in', { criteria: ['Works'] });
        backend.state.requirements.ticketLinks.push({ requirementId: item.id, ticketId: backend.state.tickets[0].id, revision: 1, linkedAt: new Date().toISOString(),
          linkedBy: backend.anna.id });
        return item;
      },
      hash: detailHash,
    }, async (page) => {
      const links = page.getByTestId('requirement-links');
      await links.getByText('1 ticket, 0 of them done').waitFor();
      await links.getByText('refers to revision 1', { exact: false }).waitFor();
      await links.getByRole('button', { name: 'Link ticket' }).waitFor();
    });
  });
});
