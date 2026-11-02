import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend } from './backend.mjs';
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
    await form.getByRole('alert').waitFor();
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
