import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend, MAIL_INTAKE } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-057: the helpdesk against the in-memory backend that follows the contract. Mailboxes, customers and contacts need
// helpdesk.manage; the review queue shows a person the mails of projects in which they hold helpdesk.review; customer,
// contact and mails of a ticket follow their field rules.

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
const SECRET = 'Postfach-Geheim-2026';

function withHelpdeskRight(backend) {
  backend.state.groups.push({ id: '00000000-0000-4000-8000-00000000eeee', name: 'Helpdesk', members: [backend.anna.id], actions: [{ action: 'helpdesk.manage', projectId: null }], fields: [] });
}

// Ben reviews the mails of the project and may read and write customer and contact; mail he may only read.
function withReviewer(backend, fields = ['customer', 'contact', 'mail']) {
  backend.state.groups.push({ id: '00000000-0000-4000-8000-00000000dddd', name: 'Support', members: [backend.ben.id],
    actions: ['helpdesk.review', 'ticket.edit', 'ticket.read', 'project.read'].map((action) => ({ action, projectId: backend.project.id })),
    fields: [...fields.map((field) => ({ resourceType: 'ticket', field, projectId: backend.project.id, operation: 'read', effect: 'allow' })),
      ...fields.filter((field) => field !== 'mail').map((field) => ({ resourceType: 'ticket', field, projectId: backend.project.id, operation: 'write', effect: 'allow' }))] });
}

function withCustomer(backend) {
  const customer = { id: '00000000-0000-4000-8000-00000000c001', name: 'Nordwerk', projects: [backend.project.id], version: 1, createdAt: '2026-10-28T08:00:00Z',
    createdBy: backend.anna.id, updatedAt: '2026-10-28T08:00:00Z', updatedBy: backend.anna.id };
  const contact = { id: '00000000-0000-4000-8000-00000000c002', customerId: customer.id, name: 'Lea Lang', addresses: ['lea@nordwerk.example'], version: 1,
    createdAt: '2026-10-28T08:00:00Z', createdBy: backend.anna.id, updatedAt: '2026-10-28T08:00:00Z', updatedBy: backend.anna.id };
  backend.state.helpdesk.customers.push(customer);
  backend.state.helpdesk.contacts.push(contact);
  return { customer, contact };
}

async function withApp({ prepare = withHelpdeskRight, as = ANNA, locale = 'de-DE', hash = '#/helpdesk' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('helpdesk administration', () => {
  test('without helpdesk.manage there is no entry and the page refuses', async () => {
    await withApp({ prepare: null }, async (page, backend) => {
      await page.getByText('Sie haben für diese Aktion keine Berechtigung.').first().waitFor();
      await page.getByRole('navigation').getByRole('link').first().waitFor();
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'Helpdesk' }).count(), 0);
      assert.equal(backend.state.requests.filter((request) => request.path.startsWith('/api/v1/helpdesk/')
        && !request.path.startsWith('/api/v1/helpdesk/inbox')).length, 0, 'the page asks nothing it may not');
    });
  });

  test('customers, contacts and a mailbox are set up and the password is never shown again', async () => {
    await withApp({}, async (page, backend) => {
      await page.getByRole('navigation').getByRole('link', { name: 'Helpdesk' }).waitFor();
      await page.getByRole('heading', { name: 'Helpdesk', level: 1 }).waitFor();

      await page.getByRole('button', { name: 'Kunden anlegen' }).click();
      const customerForm = page.getByTestId('customer-form');
      await customerForm.getByLabel(/^Name/).fill('Nordwerk');
      await customerForm.getByLabel('PLAT Plattform').check();
      await customerForm.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Nordwerk wurde gespeichert.').waitFor();
      assert.deepEqual(backend.state.helpdesk.customers[0].projects, [backend.project.id]);

      await page.getByRole('button', { name: 'Kontakt anlegen' }).click();
      const contactForm = page.getByTestId('contact-form');
      await contactForm.getByLabel(/^Name/).fill('Lea Lang');
      await contactForm.getByLabel('Mailadressen').fill('Lea@Nordwerk.example\nlea.lang@nordwerk.example');
      await contactForm.getByRole('button', { name: 'Speichern' }).click();
      await page.locator('tr[data-contact="Lea Lang"]').getByText('lea@nordwerk.example, lea.lang@nordwerk.example').waitFor();

      // The same address for a second contact is refused with its reason.
      await page.getByRole('button', { name: 'Kontakt anlegen' }).click();
      await page.getByTestId('contact-form').getByLabel(/^Name/).fill('Zweite Lea');
      await page.getByTestId('contact-form').getByLabel('Mailadressen').fill('lea@nordwerk.example');
      await page.getByTestId('contact-form').getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Eine der Adressen gehört bereits zu einem anderen Kontakt.').waitFor();
      await page.getByTestId('contact-form').getByRole('button', { name: 'Abbrechen' }).click();

      await page.getByRole('button', { name: 'Postfach hinzufügen' }).click();
      const form = page.getByTestId('mailbox-form');
      await form.getByLabel(/^Name/).fill('Support');
      await form.getByLabel('Tickettyp neuer Tickets').selectOption('bug');
      await form.getByLabel('IMAP Server').fill('imap.firma.example');
      await form.getByLabel('Benutzername').fill('support@firma.example');
      await form.getByLabel('Passwort').fill(SECRET);
      await form.getByLabel('Kennung des eigenen Empfangsservers (authserv-id)').fill('mx.firma.example');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Support wurde gespeichert.').waitFor();
      const row = page.locator('tr[data-mailbox="Support"]');
      await row.getByText('Passwort gespeichert').waitFor();
      await row.getByText('Noch nicht abgerufen').waitFor();
      const created = backend.writes().find((request) => request.path === '/api/v1/helpdesk/mailboxes');
      const sent = JSON.parse(created.body.toString('utf8'));
      assert.equal(sent.password, SECRET);
      assert.equal(sent.ticketType, 'bug');
      assert.equal(sent.imapPort, 993);
      assert.equal(sent.imapSecurity, 'tls');
      assert.ok(!(await page.content()).includes(SECRET), 'the password is nowhere on the page');

      // Editing without a new password keeps it; the test names the result.
      await row.getByRole('button', { name: 'Bearbeiten' }).click();
      assert.equal(await page.getByTestId('mailbox-form').getByLabel('Passwort', { exact: true }).inputValue(), '');
      await page.getByTestId('mailbox-form').getByLabel(/^Name/).fill('Support DE');
      await page.getByTestId('mailbox-form').getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Support DE wurde gespeichert.').waitFor();
      const update = backend.writes().find((request) => request.method === 'PUT' && request.path.startsWith('/api/v1/helpdesk/mailboxes/'));
      assert.equal(update.ifMatch, '"1"');
      assert.ok(!('password' in JSON.parse(update.body.toString('utf8'))), 'an empty field keeps the stored password');
      assert.equal(backend.state.helpdesk.mailboxes[0].password, SECRET);
      await page.locator('tr[data-mailbox="Support DE"]').getByRole('button', { name: 'Verbindung prüfen' }).click();
      await page.getByText('Die Verbindung zu Support DE funktioniert.').waitFor();
      assert.ok(backend.writes().every((request) => request.csrf !== null), 'every change carries the CSRF token');
    });
  });

  test('a failed fetch, a missing key and a refused form are explained', async () => {
    await withApp({ prepare: (backend) => {
      withHelpdeskRight(backend);
      backend.state.helpdesk.secretKey = false;
      backend.state.helpdesk.mailboxes.push({ id: '00000000-0000-4000-8000-00000000b001', name: 'Alt', enabled: true, projectId: backend.project.id, ticketType: 'task',
        imapHost: 'imap.firma.example', imapPort: 993, imapSecurity: 'tls', username: 'alt', password: 'falsch', folder: 'INBOX', smtpHost: null, smtpPort: null,
        smtpSecurity: null, address: null, authservId: null, maxBytes: 25000000, version: 3, updatedAt: '2026-10-28T08:00:00Z', updatedBy: backend.anna.id,
        status: { polledAt: '2026-10-28T09:00:00Z', succeededAt: null, errorCode: 'helpdesk.authentication_failed', errorAt: '2026-10-28T09:00:00Z',
          notice: 'helpdesk.uidvalidity_changed', noticeAt: '2026-10-28T08:00:00Z' } });
    } }, async (page) => {
      await page.getByTestId('helpdesk-key-missing').waitFor();
      const row = page.locator('tr[data-mailbox="Alt"]');
      await row.getByText('Anmeldung am Postfach abgelehnt', { exact: false }).waitFor();
      await row.getByText('Der Ordner wurde auf dem Server neu aufgebaut', { exact: false }).waitFor();
      await row.getByRole('button', { name: 'Verbindung prüfen' }).click();
      await page.getByText('Die Verbindung zu Alt gelingt nicht: Anmeldung am Postfach abgelehnt').waitFor();
      await page.getByRole('button', { name: 'Postfach hinzufügen' }).click();
      await page.getByTestId('mailbox-form').getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Der Name fehlt oder ist zu lang.', { exact: false }).waitFor();
    });
  });
});

describe('review queue', () => {
  test('a reviewer opens a mail, picks the contact and creates the ticket', async () => {
    await withApp({ as: BEN, hash: '#/inbox', prepare: (backend) => {
      withReviewer(backend);
      withCustomer(backend);
      backend.addMail({ fromAddress: 'lea@nordwerk.example', fromName: 'Lea Lang', reason: 'sender_unconfirmed', subject: 'Drucker defekt',
        attachments: [{ name: 'tool.exe', size: 5, attachmentId: null, refusal: null }] });
      backend.addMail({ subject: 'Werbung', reason: 'sender_unknown' });
    } }, async (page, backend) => {
      await page.getByRole('heading', { name: 'Prüfwarteschlange', level: 1 }).waitFor();
      await page.getByTestId('inbox-summary').getByText('2 warten auf Prüfung, 0 automatische Mails').waitFor();
      const first = backend.state.helpdesk.messages[0];
      const item = page.locator(`li[data-message="${first.id}"]`);
      await item.getByText('Grund: Absender nicht vom eigenen Mailserver bestätigt').waitFor();
      await item.getByRole('button', { name: 'Drucker defekt' }).click();
      await item.getByTestId('inbox-body').getByText('der Drucker im zweiten Stock druckt nicht.', { exact: false }).waitFor();
      await item.getByText('Datei tool.exe', { exact: false }).waitFor();
      await item.getByLabel('Kontakt des Tickets').selectOption({ label: 'Lea Lang (Nordwerk) (Absender)' });
      await item.getByRole('button', { name: 'Ticket anlegen' }).click();
      await page.getByText('Ticket 3 wurde angelegt.').waitFor();
      const write = backend.writes().find((request) => request.path.endsWith('/ticket'));
      assert.equal(write.ifMatch, '"1"');
      assert.equal(JSON.parse(write.body.toString('utf8')).contactId, '00000000-0000-4000-8000-00000000c002');
      assert.equal(first.state, 'ticket');
      await page.getByLabel('Anzeigen').selectOption('ticket');
      await page.getByRole('link', { name: 'Zu Ticket 3' }).click();
      await page.getByTestId('ticket-customer').getByText('Nordwerk').waitFor();
      await page.getByTestId('ticket-contact').getByText('Lea Lang (lea@nordwerk.example)').waitFor();
      await page.getByTestId('history').getByText('Mail von lea@nordwerk.example eingegangen: Drucker defekt').waitFor();
    });
  });

  test('discarding needs a reason and a mail processed by somebody else is explained', async () => {
    await withApp({ as: BEN, hash: '#/inbox', prepare: (backend) => {
      withReviewer(backend);
      backend.addMail({ subject: 'Spam' });
      backend.addMail({ subject: 'Schon erledigt' });
    } }, async (page, backend) => {
      const [spam, done] = backend.state.helpdesk.messages;
      const item = page.locator(`li[data-message="${spam.id}"]`);
      await item.getByRole('button', { name: 'Spam' }).click();
      await item.getByRole('button', { name: 'Verwerfen' }).click();
      await item.getByRole('button', { name: 'Endgültig verwerfen' }).click();
      await item.getByText('Bitte geben Sie eine Begründung mit höchstens 500 Zeichen an.').waitFor();
      assert.equal(spam.state, 'review');
      await item.getByLabel('Begründung').fill('Werbung ohne Bezug');
      await item.getByRole('button', { name: 'Endgültig verwerfen' }).click();
      await page.getByText('Die Mail wurde verworfen.').waitFor();
      assert.equal(spam.discardReason, 'Werbung ohne Bezug');
      // The list is read again after the notice; wait for it instead of counting at once.
      await page.locator(`li[data-message="${spam.id}"]`).waitFor({ state: 'detached' });

      const other = page.locator(`li[data-message="${done.id}"]`);
      await other.getByRole('button', { name: 'Schon erledigt' }).click();
      await other.getByRole('button', { name: 'Ticket anlegen' }).waitFor();
      done.state = 'discarded';
      await other.getByRole('button', { name: 'Ticket anlegen' }).click();
      await other.getByText('Diese Mail wurde inzwischen bearbeitet.').waitFor();
    });
  });

  test('who also manages the helpdesk adds the unknown sender as a contact and uses it for the ticket', async () => {
    await withApp({ hash: '#/inbox', prepare: (backend) => {
      withHelpdeskRight(backend);
      withCustomer(backend);
      backend.state.groups.push({ id: '00000000-0000-4000-8000-00000000aaaa', name: 'Prüfen', members: [backend.anna.id],
        actions: [{ action: 'helpdesk.review', projectId: backend.project.id }], fields: [] });
      backend.addMail({ fromAddress: 'max@nordwerk.example', fromName: 'Max Muster' });
    } }, async (page, backend) => {
      const mail = backend.state.helpdesk.messages[0];
      const item = page.locator(`li[data-message="${mail.id}"]`);
      await item.getByRole('button', { name: 'Drucker defekt' }).click();
      await item.getByText('Absender max@nordwerk.example als Kontakt anlegen').click();
      const form = item.getByTestId('new-contact');
      assert.equal(await form.getByLabel(/^Name/).inputValue(), 'Max Muster');
      await form.getByRole('button', { name: 'Kontakt anlegen' }).click();
      await form.getByText('Kontakt Max Muster wurde angelegt und ist ausgewählt.').waitFor();
      const created = backend.state.helpdesk.contacts.find((contact) => contact.name === 'Max Muster');
      assert.deepEqual(created.addresses, ['max@nordwerk.example']);
      // The candidates are read again after the notice; the new contact is chosen once it is among them.
      await item.getByLabel('Kontakt des Tickets').locator(`option[value="${created.id}"]`).waitFor({ state: 'attached' });
      assert.equal(await item.getByLabel('Kontakt des Tickets').inputValue(), created.id);
      await item.getByRole('button', { name: 'Ticket anlegen' }).click();
      await page.getByText('Ticket 3 wurde angelegt.').waitFor();
      assert.equal(mail.contactId, created.id);
    });
  });

  test('without helpdesk.review the queue stays empty', async () => {
    await withApp({ as: BEN, hash: '#/inbox', prepare: (backend) => backend.addMail({ subject: 'Geheim' }) }, async (page) => {
      await page.getByText('Keine Mail wartet auf Prüfung.').waitFor();
      assert.equal(await page.getByText('Geheim').count(), 0);
    });
  });
});

describe('customer and mails of a ticket', () => {
  test('a ticket of the intake names the intake as author and follows the field rules', async () => {
    let number = 0;
    await withApp({ as: BEN, prepare: (backend) => {
      withReviewer(backend, ['customer']);
      const { customer } = withCustomer(backend);
      const ticket = backend.addTicket('Aus der Mail', 'Text', { id: MAIL_INTAKE });
      number = ticket.number;
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: customer.id, contactId: null });
      backend.state.history.push({ ticket: ticket.id, version: 1, occurredAt: '2026-10-28T09:00:00Z', actor: MAIL_INTAKE,
        changes: [{ field: 'customer', from: null, to: JSON.stringify({ id: customer.id, name: 'Nordwerk' }) }] });
    }, hash: '#/start' }, async (page, backend) => {
      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/${number}`);
      const section = page.getByTestId('ticket-helpdesk');
      await section.getByText('Nordwerk').waitFor();
      assert.equal(await page.getByTestId('ticket-contact').count(), 0, 'no contact without its read rule');
      assert.equal(await page.getByTestId('ticket-mails').count(), 0, 'no mails without their read rule');
      assert.equal(await section.getByRole('button', { name: 'Kunde ändern' }).count(), 0, 'no change without both write rules');
      // The model answers the choice as the backend does: customers with the rules on customer, contacts only with those on contact.
      const options = await page.evaluate((path) => fetch(path).then((answer) => answer.json()),
        `/api/v1/projects/${backend.project.id}/tickets/${number}/helpdesk/options`);
      assert.deepEqual(options.map((option) => [option.name, option.contacts.length]), [['Nordwerk', 0]], 'no contacts without the rules on contact');
      await page.getByTestId('history').getByText('von E-Mail Eingang', { exact: false }).first().waitFor();
      await page.getByTestId('history').getByText('Kunde: Leer wird Nordwerk').waitFor();
    });
  });

  test('an editor changes the customer and clears the contact', async () => {
    let number = 0;
    await withApp({ as: BEN, hash: '#/start', prepare: (backend) => {
      withReviewer(backend);
      const { customer, contact } = withCustomer(backend);
      backend.state.helpdesk.customers.push({ ...customer, id: '00000000-0000-4000-8000-00000000c003', name: 'Südtechnik' });
      const ticket = backend.addTicket('Mit Kunde', 'Text', backend.anna);
      number = ticket.number;
      backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: customer.id, contactId: contact.id });
    } }, async (page, backend) => {
      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/${number}`);
      const section = page.getByTestId('ticket-helpdesk');
      await section.getByRole('button', { name: 'Kunde ändern' }).click();
      await section.getByLabel('Kunde', { exact: true }).selectOption({ label: 'Südtechnik' });
      assert.equal(await section.getByLabel('Kontakt', { exact: true }).inputValue(), '');
      await section.getByRole('button', { name: 'Übernehmen' }).click();
      await section.getByText('Kunde und Kontakt wurden gespeichert.').waitFor();
      await page.getByTestId('ticket-customer').getByText('Südtechnik').waitFor();
      await page.getByTestId('ticket-contact').getByText('Kein Kontakt').waitFor();
      await page.getByTestId('history').getByText('Kunde: Nordwerk wird Südtechnik').waitFor();
      await page.getByTestId('history').getByText('Kontakt: Lea Lang wird Leer').waitFor();
    });
  });

  test('the pages speak English', async () => {
    await withApp({ locale: 'en-US', as: BEN, hash: '#/inbox', prepare: (backend) => {
      withReviewer(backend);
      backend.addMail({ reason: 'foreign_customer' });
    } }, async (page) => {
      await page.getByRole('heading', { name: 'Review queue', level: 1 }).waitFor();
      await page.getByText('Reason: Contact of another customer among the recipients').waitFor();
    });
  });
});

// TASK-058: replies to the contact of a ticket. Ben may reply in the project; the mailbox sends over SMTP.
function withReplier(backend) {
  withReviewer(backend);
  backend.state.groups.push({ id: '00000000-0000-4000-8000-00000000cccc', name: 'Antworten', members: [backend.ben.id],
    actions: [{ action: 'helpdesk.reply', projectId: backend.project.id }], fields: [] });
  backend.state.helpdesk.mailboxes.push({ id: '00000000-0000-4000-8000-00000000b002', name: 'Support', enabled: true, projectId: backend.project.id, ticketType: 'task',
    imapHost: 'imap.firma.example', imapPort: 993, imapSecurity: 'tls', username: 'support', password: SECRET, folder: 'INBOX', smtpHost: 'smtp.firma.example', smtpPort: 465,
    smtpSecurity: 'tls', address: 'support@firma.example', authservId: null, maxBytes: 25000000, version: 1, updatedAt: '2026-10-28T08:00:00Z', updatedBy: backend.anna.id,
    status: { polledAt: null, succeededAt: null, errorCode: null, errorAt: null, notice: null, noticeAt: null } });
  const { customer, contact } = withCustomer(backend);
  const ticket = backend.addTicket('Drucker defekt', 'Text', { id: MAIL_INTAKE });
  backend.state.helpdesk.ticketCustomers.set(ticket.id, { customerId: customer.id, contactId: contact.id });
  backend.addMail({ state: 'ticket', reason: null, ticketId: ticket.id, fromAddress: 'lea@nordwerk.example', fromName: 'Lea Lang', mailboxId: '00000000-0000-4000-8000-00000000b002' });
  return ticket;
}

describe('replies to customers', () => {
  test('a reply is written, checked with recipient and sender in view and only then confirmed', async () => {
    let ticket;
    await withApp({ as: BEN, hash: '#/start', prepare: (backend) => { ticket = withReplier(backend); } }, async (page, backend) => {
      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/${ticket.number}`);
      const section = page.getByTestId('ticket-helpdesk');
      await section.getByRole('button', { name: 'Dem Kunden antworten' }).click();
      const form = section.getByTestId('reply-form');
      assert.equal(await form.getByLabel('Betreff').inputValue(), 'Re: Drucker defekt');
      await form.getByLabel('Text').fill('Hallo Frau Lang,\nwir tauschen den Drucker morgen.');
      await form.getByRole('button', { name: 'Antwort prüfen' }).click();
      const check = form.getByTestId('reply-check');
      await check.getByText('Diese Antwort geht an Lea Lang <lea@nordwerk.example>, gesendet von Support <support@firma.example>.').waitFor();
      assert.equal(backend.state.helpdesk.replies.length, 0, 'nothing is stored before the confirmation');
      await check.getByRole('button', { name: 'Jetzt senden' }).click();
      await section.getByText('Die Antwort ist bestätigt und wird gesendet.').waitFor();
      const [reply] = backend.state.helpdesk.replies;
      assert.equal(reply.toAddress, 'lea@nordwerk.example');
      assert.equal(reply.mailboxId, '00000000-0000-4000-8000-00000000b002');
      assert.equal(reply.body, 'Hallo Frau Lang,\nwir tauschen den Drucker morgen.');
      await section.locator(`li[data-reply="${reply.id}"]`).getByTestId('reply-state').getByText('wird gesendet').waitFor();
      await page.getByTestId('history').getByText('Antwort an lea@nordwerk.example zum Versand bestätigt: Re: Drucker defekt').waitFor();
    });
  });

  test('an uncertain reply is sent again only with the accepted duplicate, or recorded as arrived', async () => {
    let ticket;
    await withApp({ as: BEN, hash: '#/start', prepare: (backend) => {
      ticket = withReplier(backend);
      backend.addReply(ticket, { subject: 'Re: Erste', state: 'uncertain', errorCode: 'helpdesk.send_uncertain', attemptedAt: '2026-10-29T08:00:00Z' });
      backend.addReply(ticket, { subject: 'Re: Zweite', state: 'uncertain', errorCode: 'helpdesk.send_uncertain', attemptedAt: '2026-10-29T08:00:00Z' });
    } }, async (page, backend) => {
      const [first, second] = backend.state.helpdesk.replies;
      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/${ticket.number}`);
      const one = page.locator(`li[data-reply="${first.id}"]`);
      await one.getByTestId('reply-state').getByText('Versand unklar').waitFor();
      const again = one.getByRole('button', { name: 'Erneut senden' });
      assert.equal(await again.isDisabled(), true, 'no second send without accepting a duplicate');
      await one.getByLabel('Mir ist bewusst, dass der Kunde die Antwort doppelt erhalten kann.').check();
      await again.click();
      await one.getByTestId('reply-state').getByText('wird gesendet').waitFor();
      assert.equal(first.sends, 2);
      const send = backend.writes().find((write) => write.path.endsWith(`/replies/${first.id}/send`));
      assert.equal(JSON.parse(send.body.toString('utf8')).duplicateAccepted, true);
      assert.equal(send.ifMatch, '"1"');

      const two = page.locator(`li[data-reply="${second.id}"]`);
      await two.getByRole('button', { name: 'Als angekommen festhalten' }).click();
      await two.getByTestId('reply-state').getByText('vom Mailserver angenommen').waitFor();
      assert.equal(second.state, 'sent');
      assert.equal(second.sends, 1);
    });
  });

  test('without helpdesk.reply there is no reply, and the server refuses it', async () => {
    let ticket;
    await withApp({ as: BEN, hash: '#/start', prepare: (backend) => {
      ticket = withReplier(backend);
      backend.state.groups = backend.state.groups.filter((group) => group.name !== 'Antworten');
    } }, async (page, backend) => {
      await page.goto(`${base()}#/projects/${backend.project.id}/tickets/${ticket.number}`);
      await page.getByTestId('ticket-mails').waitFor();
      assert.equal(await page.getByRole('button', { name: 'Dem Kunden antworten' }).count(), 0);
      const status = await page.evaluate((path) => fetch(path).then((answer) => answer.status),
        `/api/v1/projects/${backend.project.id}/tickets/${ticket.number}/helpdesk/reply`);
      assert.equal(status, 403);
    });
  });

  test('the answer of another customer waits in review and is taken to its ticket; open replies are listed', async () => {
    let ticket;
    await withApp({ as: BEN, hash: '#/inbox', prepare: (backend) => {
      ticket = withReplier(backend);
      backend.addReply(ticket, { subject: 'Re: Drucker defekt', state: 'failed', errorCode: 'helpdesk.recipient_refused' });
      backend.addMail({ subject: 'Re: Drucker defekt', fromAddress: 'max@anders.example', reason: 'reply_customer_mismatch', relatedTicketId: ticket.id });
    } }, async (page, backend) => {
      const open = page.getByTestId('open-replies');
      await open.getByText('Re: Drucker defekt an lea@nordwerk.example, Ticket 3').waitFor();
      await open.getByText('Der Mailserver hat den Empfänger abgelehnt').waitFor();
      const mail = backend.state.helpdesk.messages.at(-1);
      const item = page.locator(`li[data-message="${mail.id}"]`);
      await item.getByRole('button', { name: 'Re: Drucker defekt' }).click();
      await item.getByRole('link', { name: 'Antwortet auf Ticket 3' }).waitFor();
      await item.getByRole('button', { name: 'Zu Ticket 3 nehmen' }).click();
      await page.getByText('Die Mail steht jetzt bei Ticket 3.').waitFor();
      assert.equal(mail.state, 'ticket');
      assert.equal(mail.ticketId, ticket.id);
    });
  });
});
