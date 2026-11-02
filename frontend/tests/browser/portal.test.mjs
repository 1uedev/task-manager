import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend, newTag } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-075: portal accounts in the account administration and the reading view of a portal account, against the in-memory
// backend that follows the contract. The rules themselves are tested against the real backend (PortalAccountsApiTest).

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
const NORDWERK = '00000000-0000-4000-8000-00000000c001';
const SUEDTECHNIK = '00000000-0000-4000-8000-00000000c003';

function withCustomers(backend) {
  const stamp = { version: 1, createdAt: '2026-11-02T08:00:00Z', createdBy: backend.anna.id, updatedAt: '2026-11-02T08:00:00Z', updatedBy: backend.anna.id };
  backend.state.helpdesk.customers.push({ id: NORDWERK, name: 'Nordwerk', projects: [backend.project.id], ...stamp });
  backend.state.helpdesk.customers.push({ id: SUEDTECHNIK, name: 'Südtechnik', projects: [backend.project.id], ...stamp });
  backend.state.helpdesk.contacts.push({ id: '00000000-0000-4000-8000-00000000c002', customerId: NORDWERK, name: 'Lea Lang', addresses: ['lea@nordwerk.example'], ...stamp });
  const [first, second] = backend.state.tickets;
  backend.state.helpdesk.ticketCustomers.set(first.id, { customerId: NORDWERK, contactId: null });
  backend.state.helpdesk.ticketCustomers.set(second.id, { customerId: SUEDTECHNIK, contactId: null });
}

async function session(backend, as, hash) {
  return open(browser, `${base()}${hash}`, { locale: 'de-DE', setup: (context, page) => backend.install(context, page, { as }) });
}

async function finish(opened) {
  try {
    assert.deepEqual(opened.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await opened.context.close();
  }
}

describe('portal accounts', () => {
  test('the administration creates a portal account with organization, contact and project', async () => {
    const backend = createBackend();
    withCustomers(backend);
    const admin = await session(backend, ANNA, '#/accounts');
    const page = admin.page;
    const panel = page.getByTestId('portal-accounts');
    await panel.getByText('Neues Portalkonto anlegen').click();
    const form = panel.getByTestId('portal-account-form');
    await form.getByLabel('Anmeldename').fill('lea.lang');
    await form.getByLabel('Kundenorganisation').selectOption({ label: 'Nordwerk' });
    await form.getByLabel('Kontakt').selectOption({ label: 'Lea Lang' });
    await form.getByLabel('PLAT Plattform').check();
    await form.getByRole('button', { name: 'Portalkonto anlegen' }).click();
    await panel.getByTestId('portal-one-time').waitFor();
    const created = backend.state.accounts.find((item) => item.username === 'lea.lang');
    assert.equal(created.kind, 'portal');
    assert.equal(created.organizationId, NORDWERK);
    assert.equal(created.contactId, '00000000-0000-4000-8000-00000000c002');
    assert.match(await panel.getByTestId('portal-account-table').locator('tr', { hasText: 'lea.lang' }).innerText(), /Nordwerk[\s\S]*Lea Lang[\s\S]*PLAT/);
    // The list of all accounts marks it, and the choice of assignees leaves it out.
    assert.match(await page.getByTestId('account-table').locator('tr', { hasText: 'lea.lang' }).innerText(), /Portalkonto/);
    await finish(admin);
  });

  test('a refused contact is explained and nothing is created', async () => {
    const backend = createBackend();
    withCustomers(backend);
    backend.state.helpdesk.contacts.push({ ...backend.state.helpdesk.contacts[0], id: '00000000-0000-4000-8000-00000000c004', customerId: NORDWERK, name: 'Alter Kontakt' });
    const admin = await session(backend, ANNA, '#/accounts');
    const panel = admin.page.getByTestId('portal-accounts');
    await panel.getByText('Neues Portalkonto anlegen').click();
    const form = panel.getByTestId('portal-account-form');
    await form.getByLabel('Anmeldename').fill('kunde.zwei');
    await form.getByLabel('Kundenorganisation').selectOption({ label: 'Nordwerk' });
    await form.getByLabel('Kontakt').selectOption({ label: 'Alter Kontakt' });
    // The contact moved to another organization in the meantime.
    backend.state.helpdesk.contacts.find((entry) => entry.name === 'Alter Kontakt').customerId = SUEDTECHNIK;
    await form.getByRole('button', { name: 'Portalkonto anlegen' }).click();
    // The reason is named for itself (TASK-078, H3).
    await form.getByRole('alert').filter({ hasText: 'Der gewählte Kontakt gehört nicht mehr zu dieser Organisation.' }).waitFor();
    assert.equal(backend.state.accounts.some((item) => item.username === 'kunde.zwei'), false);
    await finish(admin);
  });

  test('a portal account sees only the requests of its organization and no internal navigation', async () => {
    const backend = createBackend();
    withCustomers(backend);
    const admin = await session(backend, ANNA, '#/accounts');
    const panel = admin.page.getByTestId('portal-accounts');
    await panel.getByText('Neues Portalkonto anlegen').click();
    const form = panel.getByTestId('portal-account-form');
    await form.getByLabel('Anmeldename').fill('lea.lang');
    await form.getByLabel('Kundenorganisation').selectOption({ label: 'Nordwerk' });
    await form.getByLabel('PLAT Plattform').check();
    await form.getByRole('button', { name: 'Portalkonto anlegen' }).click();
    await panel.getByTestId('portal-one-time').waitFor();
    await finish(admin);

    const lea = backend.state.accounts.find((item) => item.username === 'lea.lang');
    lea.passwordChangeRequired = false;
    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    await page.getByRole('heading', { name: 'Ihre Anfragen' }).waitFor();
    assert.equal(await page.locator('.sidebar').count(), 0, 'no internal navigation');
    const rows = page.getByTestId('portal-tickets').locator('tbody tr');
    assert.equal(await rows.count(), 1);
    assert.match(await rows.first().innerText(), /Anmeldung prüfen/);
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    await page.getByTestId('portal-ticket').waitFor();
    assert.match(await page.getByTestId('portal-ticket').innerText(), /Ablauf mit synthetischen Konten/);
    await page.getByRole('button', { name: 'Zurück zur Übersicht' }).click();
    await page.getByTestId('portal-tickets').waitFor();
    // The portal never asks for the account directory of the staff.
    assert.equal(backend.state.requests.some((item) => item.user === 'lea.lang' && item.path.includes('/directory/accounts')), false);
    await finish(portal);
  });
});

// TASK-076: the conversation of the portal. Lea Lang of Nordwerk is a portal account in the group "Portal"; the type "Aufgabe"
// of the project is offered in the portal; Anna answers as support.
function withLea(backend) {
  withCustomers(backend);
  const lea = { id: '00000000-0000-4000-8000-0000000000e1', username: 'lea.lang', displayName: 'Lea Lang', password: 'Portal-Passwort-1', active: true,
    passwordChangeRequired: false, createdAt: '2026-11-02T08:00:00Z', kind: 'portal', organizationId: NORDWERK, contactId: '00000000-0000-4000-8000-00000000c002' };
  backend.state.accounts.push(lea);
  backend.state.members.get(backend.project.id).push({ accountId: lea.id, addedAt: '2026-11-02T08:00:00Z' });
  const rule = (field, operation) => ({ resourceType: 'ticket', field, projectId: null, operation, effect: 'allow' });
  backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000000e2', name: 'Portal', purpose: 'portal', members: [lea.id],
    actions: ['ticket.read', 'ticket.create', 'portal.answer'].map((action) => ({ action, projectId: null })),
    fields: [...['title', 'description', 'type', 'status', 'attachments'].map((field) => rule(field, 'read')), ...['title', 'description', 'attachments'].map((field) => rule(field, 'write'))] });
  backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000000e3', name: 'Support', members: [backend.anna.id],
    actions: [{ action: 'helpdesk.reply', projectId: null }], fields: ['customer', 'contact', 'mail'].map((field) => rule(field, 'read')) });
  backend.state.configurations.get(backend.project.id).configuration.ticketTypes[0].portal = true;
  return lea;
}

describe('portal conversation', () => {
  test('a portal account creates a request with a file and writes in its conversation', async () => {
    const backend = createBackend();
    withLea(backend);
    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    await page.getByRole('button', { name: 'Neue Anfrage' }).click();
    const form = page.getByTestId('portal-create');
    // Only the offered type is in the choice.
    assert.deepEqual(await form.getByLabel('Typ').locator('option').allInnerTexts(), ['Aufgabe']);
    await form.getByLabel('Titel').fill('Drucker defekt');
    await form.getByLabel('Beschreibung').fill('Seit heute Morgen');
    await form.getByTestId('portal-files').setInputFiles({ name: 'foto.png', mimeType: 'image/png', buffer: Buffer.from('bild') });
    await form.getByRole('button', { name: 'Anfrage senden' }).click();
    await page.getByTestId('portal-ticket').waitFor();
    assert.match(await page.locator('[data-field="status"]').innerText(), /Neu/);
    assert.match(await page.getByTestId('portal-attachments').innerText(), /foto\.png/);
    const created = backend.state.tickets.at(-1);
    assert.deepEqual(backend.state.helpdesk.ticketCustomers.get(created.id), { customerId: NORDWERK, contactId: '00000000-0000-4000-8000-00000000c002' });

    const post = page.getByTestId('portal-post');
    await post.getByLabel('Nachricht').fill('Hier noch ein Foto.');
    await post.getByTestId('portal-files').setInputFiles({ name: 'zweites.png', mimeType: 'image/png', buffer: Buffer.from('bild2') });
    await post.getByRole('button', { name: 'Nachricht senden' }).click();
    await post.getByText('Ihre Nachricht ist angekommen.').waitFor();
    const entry = page.getByTestId('portal-conversation').locator(':scope > li').last();
    assert.match(await entry.innerText(), /Sie[\s\S]*Hier noch ein Foto\.[\s\S]*zweites\.png/);
    assert.equal(backend.state.helpdesk.posts.at(-1).attachmentIds.length, 1);
    await finish(portal);
  });

  test('the support answers in the portal and the start page marks the new answer until it is opened', async () => {
    const backend = createBackend();
    withLea(backend);
    const ticket = backend.state.tickets[0];
    const admin = await session(backend, ANNA, `#/projects/${backend.project.id}/tickets/${ticket.number}`);
    const helpdesk = admin.page.getByTestId('ticket-helpdesk');
    await helpdesk.getByRole('button', { name: 'Im Portal antworten' }).click();
    await helpdesk.getByTestId('portal-answer').getByLabel('Text').fill('Ein Techniker kommt morgen.');
    await helpdesk.getByRole('button', { name: 'Im Portal veröffentlichen' }).click();
    await helpdesk.getByText('Die Antwort steht im Portal.').waitFor();
    assert.match(await helpdesk.getByTestId('ticket-portal-posts').innerText(), /Support im Portal[\s\S]*Ein Techniker kommt morgen\./);
    await finish(admin);

    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    const row = page.getByTestId('portal-tickets').locator('tr', { hasText: 'Anmeldung prüfen' });
    await row.getByTestId('portal-unread').waitFor();
    await row.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    const conversation = page.getByTestId('portal-conversation');
    await conversation.waitFor();
    assert.match(await conversation.innerText(), /Support[\s\S]*Ein Techniker kommt morgen\./);
    await page.getByRole('button', { name: 'Zurück zur Übersicht' }).click();
    await page.getByTestId('portal-tickets').waitFor();
    assert.equal(await page.getByTestId('portal-unread').count(), 0, 'opened, so no longer new');
    await finish(portal);
  });

  test('an internal attachment reaches the portal only after it is released, and the list filters', async () => {
    const backend = createBackend();
    withLea(backend);
    const ticket = backend.state.tickets[0];
    backend.state.attachments.push({ id: '00000000-0000-4000-8000-0000000000f1', ticket: ticket.id, name: 'handbuch.pdf', size: 4, sha256: 'a'.repeat(64),
      uploadedAt: '2026-11-02T08:00:00Z', uploadedBy: backend.anna.id, content: Buffer.from('%PDF'), removed: false, visibility: 'internal' });
    const portalBefore = await session(backend, { username: 'lea.lang' }, '#/');
    await portalBefore.page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    await portalBefore.page.getByTestId('portal-conversation').or(portalBefore.page.getByText('Noch keine Nachrichten.')).first().waitFor();
    assert.equal(await portalBefore.page.getByTestId('portal-attachments').count(), 0);
    await finish(portalBefore);

    const admin = await session(backend, ANNA, `#/projects/${backend.project.id}/tickets/${ticket.number}`);
    await admin.page.getByTestId('attachment-visibility').filter({ hasText: 'Intern' }).waitFor();
    await admin.page.getByRole('button', { name: 'handbuch.pdf für Kunden freigeben' }).click();
    await admin.page.getByText('handbuch.pdf ist jetzt im Portal sichtbar.').waitFor();
    await finish(admin);

    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    await page.getByLabel('Offen oder erledigt').selectOption({ label: 'Erledigt' });
    await page.getByRole('button', { name: 'Suchen' }).click();
    await page.getByText('Noch keine Anfragen.').waitFor();
    await page.getByLabel('Offen oder erledigt').selectOption({ label: 'Offen' });
    await page.getByLabel('Text').fill('anmeldung');
    await page.getByRole('button', { name: 'Suchen' }).click();
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    assert.match(await page.getByTestId('portal-attachments').innerText(), /handbuch\.pdf/);
    await finish(portal);
  });
});

// TASK-078: findings of the review of TASK-075.
describe('portal review findings', () => {
  test('the portal reaches more than one page of requests and shows each key once', async () => {
    const backend = createBackend();
    withLea(backend);
    for (let index = 0; index < 50; index += 1) {
      const ticket = backend.addTicket(`Anfrage ${index + 3}`, null, backend.anna);
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: NORDWERK, contactId: null });
    }
    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    const rows = page.getByTestId('portal-tickets').locator('tbody tr');
    await rows.first().waitFor();
    assert.equal(await rows.count(), 50, 'the first page');
    assert.match(await rows.first().innerText(), /^PLAT-52\b/);
    assert.doesNotMatch(await page.getByTestId('portal-tickets').innerText(), /PLAT-\d+-\d+/, 'no number twice');
    await page.getByTestId('portal-more').click();
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).waitFor();
    assert.equal(await rows.count(), 51, 'the oldest request on the second page');
    assert.equal(await page.getByTestId('portal-more').count(), 0, 'no further page');
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    await page.getByTestId('portal-ticket').waitFor();
    assert.match(await page.locator('main h1').innerText(), /^PLAT-1 Anmeldung prüfen$/);
    await finish(portal);
  });

  test('a portal account is never offered as assignee or person', async () => {
    const backend = createBackend();
    withLea(backend);
    backend.state.configurations.get(backend.project.id).configuration.customFields = [
      { key: 'reviewer', name: 'Prüfer', type: 'user', required: false, active: true }];
    const admin = await session(backend, ANNA, `#/projects/${backend.project.id}/tickets/1`);
    const page = admin.page;
    const assignee = page.getByLabel('Zuständig');
    await assignee.waitFor();
    const offered = await assignee.locator('option').allInnerTexts();
    assert.ok(offered.length > 1, 'staff is offered');
    assert.equal(offered.some((text) => /Lea Lang/.test(text)), false, 'not on the ticket');
    await page.goto(`${base()}#/projects/${backend.project.id}/tickets`);
    await page.locator('summary', { hasText: 'Neues Ticket' }).click();
    const create = page.getByLabel('Zuständig');
    await create.waitFor();
    assert.equal((await create.locator('option').allInnerTexts()).some((text) => /Lea Lang/.test(text)), false, 'not in a new ticket');
    const reviewer = page.getByLabel(/^Prüfer/);
    assert.ok((await reviewer.locator('option').count()) > 1, 'staff is offered for the person field');
    assert.equal((await reviewer.locator('option').allInnerTexts()).some((text) => /Lea Lang/.test(text)), false, 'not for a person field');
    await finish(admin);
  });
});

// TASK-077: tags in the portal. Lea sees only tags visible to customers; with ticket.edit, the write rule for tags and tag.create
// in a group of her own she sets, removes and creates tags; the support releases a tag of Nordwerk for all customers.
function withLeaTags(backend, { write = false } = {}) {
  const lea = withLea(backend);
  const portal = backend.state.groups.find((group) => group.purpose === 'portal');
  portal.fields.push({ resourceType: 'ticket', field: 'tags', projectId: null, operation: 'read', effect: 'allow' });
  if (write) {
    backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000000e4', name: 'Portal Tags', members: [lea.id],
      actions: ['ticket.edit', 'tag.create'].map((action) => ({ action, projectId: null })),
      fields: [{ resourceType: 'ticket', field: 'tags', projectId: null, operation: 'write', effect: 'allow' }] });
  }
  const tag = (name, visibility, extra = {}) => {
    const created = newTag(name, backend.project.id, backend.anna, { visibility, ...extra });
    backend.state.tags.push(created);
    return created;
  };
  const internal = tag('Eskalation intern', 'internal');
  const shown = tag('Wartung', 'customer');
  const [first] = backend.state.tickets;
  backend.state.ticketTags.push({ ticket: first.id, tag: internal.id }, { ticket: first.id, tag: shown.id });
  const other = backend.addTicket('Zweite Anfrage', null, backend.anna);
  backend.state.helpdesk.ticketCustomers.set(other.id, { customerId: NORDWERK, contactId: null });
  return { lea, internal, shown, first };
}

describe('portal tags', () => {
  test('the portal shows only tags visible to customers and filters by them', async () => {
    const backend = createBackend();
    withLeaTags(backend);
    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    const rows = page.getByTestId('portal-tickets').locator('tbody tr');
    await rows.first().waitFor();
    assert.equal(await rows.count(), 2);
    const filter = page.getByLabel('Tag', { exact: true });
    assert.deepEqual(await filter.locator('option').allInnerTexts(), ['Alle Tags', 'Wartung'], 'no internal tag offered');
    await filter.selectOption({ label: 'Wartung' });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="portal-tickets"] tbody tr').length === 1);
    assert.match(await rows.first().innerText(), /Anmeldung prüfen/);
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    const tags = page.getByTestId('portal-tags');
    await tags.getByText('Wartung').waitFor();
    assert.doesNotMatch(await tags.innerText(), /Eskalation/, 'the internal tag stays hidden');
    assert.equal(await page.getByTestId('portal-tag-form').count(), 0, 'the group "Portal" writes no tags');
    await finish(portal);
  });

  test('an authorized portal account creates, sets and removes tags, internal ones stay', async () => {
    const backend = createBackend();
    const { internal, first } = withLeaTags(backend, { write: true });
    const portal = await session(backend, { username: 'lea.lang' }, '#/');
    const page = portal.page;
    await page.getByRole('button', { name: 'Anmeldung prüfen' }).click();
    const tags = page.getByTestId('portal-tags');
    const form = tags.getByTestId('portal-tag-form');
    await form.getByLabel('Tag hinzufügen').fill('Rückruf');
    await form.getByRole('button', { name: '„Rückruf“ anlegen' }).click();
    await tags.getByText('Tag „Rückruf“ hinzugefügt.').waitFor();
    await tags.getByTestId('portal-tag-list').getByText('Rückruf').waitFor();
    const created = backend.state.tags.find((tag) => tag.name === 'Rückruf');
    assert.equal(created.visibility, 'customer');
    assert.equal(created.organizationId, NORDWERK);
    await tags.getByRole('button', { name: 'Tag Wartung entfernen' }).click();
    await tags.getByText('Tag „Wartung“ entfernt.').waitFor();
    // A suggestion sets an existing tag with one click.
    await form.getByRole('button', { name: 'Wartung' }).click();
    await tags.getByText('Tag „Wartung“ hinzugefügt.').waitFor();
    const held = backend.state.ticketTags.filter((entry) => entry.ticket === first.id).map((entry) => entry.tag);
    assert.ok(held.includes(internal.id), 'the internal tag stays at the request');
    assert.equal(held.length, 3);
    assert.doesNotMatch(await tags.innerText(), /Eskalation/);
    await finish(portal);
  });

  test('the tag care shows the visibility and releases a tag of one organization for all customers', async () => {
    const backend = createBackend();
    withLeaTags(backend);
    backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000000e5', name: 'Tagpflege', members: [backend.anna.id], actions: [{ action: 'tags.manage', projectId: null }], fields: [] });
    const own = newTag('Rückruf', backend.project.id, backend.anna, { visibility: 'customer', organizationId: NORDWERK });
    backend.state.tags.push(own);
    const admin = await session(backend, ANNA, `#/projects/${backend.project.id}/tags`);
    const page = admin.page;
    const catalog = page.getByTestId('catalog-project');
    await catalog.waitFor();
    assert.match(await catalog.locator('li', { hasText: 'Rückruf' }).innerText(), /Kunde, eine Organisation/);
    assert.match(await catalog.locator('li', { hasText: 'Wartung' }).innerText(), /Kunde/);
    assert.doesNotMatch(await catalog.locator('li', { hasText: 'Eskalation intern' }).innerText(), /Kunde/);
    await page.getByRole('button', { name: 'Rückruf bearbeiten' }).click();
    const form = page.getByRole('form', { name: 'Tag Rückruf bearbeiten' });
    await form.getByTestId('tag-release').check();
    await form.getByRole('button', { name: 'Speichern' }).click();
    await page.getByText('Tag Rückruf gespeichert.').waitFor();
    assert.equal(own.organizationId, null);
    assert.equal(own.visibility, 'customer');
    // An internal tag becomes visible to customers.
    await page.getByRole('button', { name: 'Eskalation intern bearbeiten' }).click();
    const second = page.getByRole('form', { name: 'Tag Eskalation intern bearbeiten' });
    await second.getByLabel('Sichtbarkeit').selectOption({ label: 'Kunde (im Portal sichtbar)' });
    await second.getByRole('button', { name: 'Speichern' }).click();
    await page.getByText('Tag Eskalation intern gespeichert.').waitFor();
    assert.equal(backend.state.tags.find((tag) => tag.name === 'Eskalation intern').visibility, 'customer');
    await finish(admin);
  });
});
