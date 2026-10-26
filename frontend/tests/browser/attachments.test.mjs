import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-051: the attachments of a ticket in the interface, against the in-memory backend that follows the contract: upload by
// file choice and by drop, the refusals with their reasons, download as a file, removal after a confirmation, the entries in
// the history and the rights of the field `attachments`.

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

async function withTicket(as, body, { prepare, locale = 'de-DE' } = {}) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}#/projects/${backend.project.id}/tickets/1`,
    { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await session.page.getByTestId('history').waitFor();
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const file = (name, text) => ({ name, mimeType: 'application/octet-stream', buffer: Buffer.from(text) });

describe('attachments of a ticket', () => {
  test('a file is attached by choosing it, listed, downloaded as a file and recorded in the history', async () => {
    await withTicket(ANNA, async (page, backend) => {
      const section = page.getByRole('region', { name: 'Anhänge' });
      await section.getByText('Dieses Ticket hat keine Anhänge.').waitFor();
      await section.getByTestId('attachment-file').setInputFiles(file('Bericht Q3.txt', 'Inhalt des Berichts'));
      await section.getByText('Bericht Q3.txt angehängt.').waitFor();
      const item = section.getByTestId('ticket-attachments').getByRole('listitem');
      await item.getByRole('link', { name: 'Anhang Bericht Q3.txt herunterladen' }).waitFor();
      assert.match(await item.textContent(), /19 B/);
      assert.match(await item.textContent(), /Anna Admin/);

      const upload = backend.writes().find((request) => request.method === 'POST' && request.path.endsWith('/attachments'));
      assert.equal(upload.csrf, 'csrf-token-for-browser-tests-0001', 'the change carries the CSRF token');
      assert.equal(upload.type, 'application/octet-stream');
      assert.equal(upload.body.toString('utf8'), 'Inhalt des Berichts', 'the file is the raw body of the request');

      const [download] = await Promise.all([
        page.waitForEvent('download'),
        item.getByRole('link', { name: 'Anhang Bericht Q3.txt herunterladen' }).click(),
      ]);
      assert.equal(download.suggestedFilename(), 'Bericht Q3.txt');

      await page.getByTestId('history').getByText('Anhang Bericht Q3.txt hinzugefügt (19 B)').waitFor();
    });
  });

  test('a dropped file is uploaded and the drop zone is keyboard reachable through its button', async () => {
    await withTicket(ANNA, async (page, backend) => {
      const section = page.getByRole('region', { name: 'Anhänge' });
      await section.getByRole('button', { name: 'Datei auswählen' }).waitFor();
      await section.getByRole('button', { name: 'Datei auswählen' }).focus();
      assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Datei auswählen');
      const data = await page.evaluateHandle(() => {
        const transfer = new DataTransfer();
        transfer.items.add(new File(['abc'], 'gezogen.txt', { type: 'text/plain' }));
        return transfer;
      });
      await section.getByTestId('attachment-dropzone').dispatchEvent('drop', { dataTransfer: data });
      await section.getByText('gezogen.txt angehängt.').waitFor();
      assert.equal(backend.state.attachments.length, 1);
    });
  });

  test('refused files show their reason and leave nothing behind', async () => {
    await withTicket(ANNA, async (page, backend) => {
      const section = page.getByRole('region', { name: 'Anhänge' });
      const input = section.getByTestId('attachment-file');
      await input.setInputFiles(file('zu gross.bin', 'x'.repeat(1001)));
      await section.getByText('zu gross.bin: Die Datei ist zu groß.').waitFor();
      await input.setInputFiles(file('programm.exe', 'abc'));
      await section.getByText(/programm\.exe: Dieser Dateityp ist nicht erlaubt/).waitFor();
      await input.setInputFiles(file('leer.txt', ''));
      await section.getByText('leer.txt: Die Datei ist leer.').waitFor();
      assert.equal(backend.state.attachments.length, 0);
      await section.getByText('Dieses Ticket hat keine Anhänge.').waitFor();
    });
  });

  test('removal asks first, hides the attachment and is recorded in the history', async () => {
    await withTicket(ANNA, async (page, backend) => {
      const section = page.getByRole('region', { name: 'Anhänge' });
      await section.getByTestId('attachment-file').setInputFiles(file('alt.txt', 'alt'));
      await section.getByRole('button', { name: 'Anhang alt.txt entfernen' }).click();
      await section.getByRole('group', { name: 'Anhang alt.txt entfernen?' }).getByRole('button', { name: 'Abbrechen' }).click();
      assert.equal(backend.state.attachments[0].removed, false, 'cancelling removes nothing');
      await section.getByRole('button', { name: 'Anhang alt.txt entfernen' }).click();
      await section.getByRole('group', { name: 'Anhang alt.txt entfernen?' }).getByRole('button', { name: 'Entfernen' }).click();
      await section.getByText('alt.txt entfernt.').waitFor();
      await section.getByText('Dieses Ticket hat keine Anhänge.').waitFor();
      assert.equal(backend.state.attachments[0].removed, true);
      await page.getByTestId('history').getByText('Anhang alt.txt entfernt').waitFor();
    });
  });

  test('a reader sees and downloads attachments but cannot change them', async () => {
    await withTicket(BEN, async (page) => {
      const section = page.getByRole('region', { name: 'Anhänge' });
      await section.getByRole('link', { name: 'Anhang Protokoll.txt herunterladen' }).waitFor();
      assert.equal(await section.getByTestId('attachment-dropzone').count(), 0, 'no way to add');
      assert.equal(await section.getByRole('button', { name: /entfernen/ }).count(), 0, 'no way to remove');
    }, {
      prepare: (backend) => backend.state.attachments.push({ id: '10000000-0000-4000-8000-000000000001', ticket: backend.state.tickets[0].id, name: 'Protokoll.txt', size: 10,
        sha256: 'a'.repeat(64), uploadedAt: '2026-10-21T08:00:00Z', uploadedBy: backend.anna.id, content: Buffer.from('0123456789'), removed: false }),
    });
  });

  test('without the read rule for attachments the section and its history entries are not shown', async () => {
    await withTicket(BEN, async (page, backend) => {
      assert.equal(await page.getByRole('region', { name: 'Anhänge' }).count(), 0);
      assert.equal(await page.getByTestId('history').getByText(/Anhang/).count(), 0);
      assert.ok(backend.state.requests.some((request) => request.path.endsWith('/attachments') && request.method === 'GET'), 'the interface asked and was refused');
    }, {
      prepare: (backend) => {
        backend.group('Lesen').fields = backend.group('Lesen').fields.filter((rule) => rule.field !== 'attachments');
      },
    });
  });

  test('English texts', async () => {
    await withTicket(ANNA, async (page) => {
      const section = page.getByRole('region', { name: 'Attachments' });
      await section.getByText('This ticket has no attachments.').waitFor();
      await section.getByTestId('attachment-file').setInputFiles(file('run.exe', 'abc'));
      await section.getByText(/run\.exe: This file type is not allowed/).waitFor();
    }, { locale: 'en-GB' });
  });
});
