import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-096: requirements of a project with list, filters, editing with criteria, review, approval of exactly the shown
// revision, a new revision after an approval, return with reason, discard, restore, history with comparison and the field
// rules of the resource type requirement, against the in-memory backend that follows the contract.

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

/** A group with project actions for everyone in the project and the given read and write rules of requirements. */
function grant(backend, accounts, actions, { read = ALL, write = ALL } = {}) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000000096${String(backend.state.groups.length).padStart(2, '0')}`, name: `Anforderungen ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })),
    fields: [...read.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'read', effect: 'allow' })),
      ...write.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'write', effect: 'allow' }))] });
}

const EVERYTHING = ['requirement.read', 'requirement.edit', 'requirement.approve'];

async function withApp({ prepare, as = ANNA, hash, locale = 'de-DE' }, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${typeof hash === 'function' ? hash(backend) : hash ?? '#/requirements'}`,
    { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const listHash = (backend) => `#/requirements/${backend.project.id}`;
const detailHash = (backend, item) => `#/requirements/${backend.project.id}/${item.id}`;
const lastWrite = (backend, pattern) => backend.state.requests.findLast((item) => item.method !== 'GET' && pattern.test(item.path));
const bodyOf = (request) => JSON.parse(request.body.toString('utf8'));

describe('requirements', () => {
  test('a requirement goes from creation over criteria and review to the approval of exactly the shown revision', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.anna, backend.ben], EVERYTHING) }, async (page, backend) => {
      await page.getByTestId('requirement-project').getByRole('link').click();
      await page.getByTestId('requirement-list').or(page.getByText('Keine Anforderungen gefunden.')).waitFor();
      const create = page.getByTestId('requirement-create');
      await create.getByLabel('Titel').fill('Anmeldung mit Firmenkonto');
      await create.getByLabel('Priorität').selectOption('high');
      await create.getByRole('button', { name: 'Anlegen' }).click();

      const requirement = page.getByTestId('requirement');
      await page.getByTestId('requirement-title').getByText('ANF-1').waitFor();
      assert.equal(await requirement.getAttribute('data-status'), 'draft');
      assert.deepEqual(bodyOf(lastWrite(backend, /\/requirements$/)), { title: 'Anmeldung mit Firmenkonto', priority: 'high' });

      // A review needs a criterion: the server says so and the page shows it.
      await page.getByTestId('requirement-submit').click();
      await page.getByText('Zur Prüfung braucht es mindestens ein Abnahmekriterium.').waitFor();

      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByTestId('requirement-criterion-add').click();
      await edit.getByLabel('Text von Neu').fill('Anmeldung gelingt mit gültigem Konto');
      await edit.getByTestId('requirement-criterion-add').click();
      await edit.getByLabel('Text von Neu').nth(1).fill('Gesperrtes Konto wird abgewiesen');
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      const save = lastWrite(backend, /\/requirements\/[^/]+$/);
      assert.equal(save.method, 'PATCH');
      assert.equal(save.ifMatch, '"1"', 'the refused submit changed nothing');
      assert.deepEqual(bodyOf(save).criteria, [{ id: null, text: 'Anmeldung gelingt mit gültigem Konto' }, { id: null, text: 'Gesperrtes Konto wird abgewiesen' }]);
      await edit.locator('[data-criterion="1"]').waitFor();
      await edit.locator('[data-criterion="2"]').waitFor();
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();

      await page.getByTestId('requirement-submit').click();
      await page.getByText('Revision 1 ist zur Prüfung gegeben.').waitFor();
      assert.equal(await requirement.getAttribute('data-status'), 'review');

      await page.getByTestId('requirement-approve').click();
      const dialog = page.getByTestId('requirement-approve-dialog');
      assert.equal(await dialog.getByTestId('requirement-approve-revision').textContent(), 'Revision 1');
      assert.equal(await dialog.getByTestId('requirement-approve-editors').textContent(), 'Anna Admin');
      await dialog.getByTestId('requirement-approve-content-criteria-list').getByText('Gesperrtes Konto wird abgewiesen').waitFor();
      assert.equal(await page.evaluate(() => document.activeElement?.dataset.testid), 'requirement-approve-confirm', 'the confirmation has the focus');
      await dialog.getByTestId('requirement-approve-confirm').click();
      await page.getByText('Revision 1 ist freigegeben.').waitFor();
      const approve = lastWrite(backend, /\/approve$/);
      assert.deepEqual(bodyOf(approve), { revision: 1 });
      assert.equal(approve.ifMatch, '"3"');
      await page.getByTestId('requirement-approval').getByText('Revision 1 freigegeben von Anna Admin', { exact: false }).waitFor();
      assert.equal(await requirement.getAttribute('data-status'), 'approved');
    });
  });

  test('a content change after the approval opens the next revision, the approved one stays valid and the history compares both', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Export als CSV', { criteria: ['Spalten wie in der Liste', 'Kodierung UTF-8'], status: 'approved' });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByTestId('requirement-new-revision-hint').getByText('eröffnet Revision 2', { exact: false }).waitFor();
      // The owner is organisational: changing it alone opens no revision.
      await edit.getByLabel('Verantwortlich').selectOption({ label: 'Ben Leser' });
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      assert.equal(item.revision, 1);
      assert.equal(item.status, 'approved');

      await edit.getByLabel('Titel').fill('Export als CSV und Excel');
      await edit.getByLabel('Text von K2').fill('Kodierung UTF-8 mit BOM');
      await edit.getByText('Beim Speichern entsteht eine neue Revision im Entwurf.').waitFor();
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Revision 2 ist eröffnet', { exact: false }).waitFor();
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();
      const requirement = page.getByTestId('requirement');
      assert.equal(await requirement.getAttribute('data-status'), 'draft');
      await page.getByTestId('requirement-newer-revision').getByText('Revision 2 ist in Bearbeitung, gültig bleibt Revision 1').waitFor();
      await page.getByTestId('requirement-approval').getByText('Revision 1 freigegeben', { exact: false }).waitFor();
      assert.equal(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id && entry.revision === 1).title, 'Export als CSV',
        'the approved revision is never changed');

      await page.getByTestId('requirement-history-open').click();
      const history = page.getByTestId('requirement-history');
      await history.locator('[data-testid="requirement-revision"][data-revision="1"][data-approved="true"]').waitFor();
      await history.locator('[data-testid="requirement-revision"][data-revision="2"][data-approved="false"]').waitFor();
      const compare = history.getByTestId('requirement-compare');
      await compare.getByLabel('Von').selectOption('1');
      await compare.getByLabel('Bis').selectOption('2');
      await compare.getByRole('button', { name: 'Vergleichen' }).click();
      const title = compare.getByTestId('requirement-compare-title');
      assert.equal(await title.locator('del').textContent(), 'Export als CSV');
      assert.equal(await title.locator('ins').textContent(), 'Export als CSV und Excel');
      const changed = compare.getByTestId('requirement-compare-criteria').locator('[data-op="changed"]');
      assert.equal(await changed.locator('ins').textContent(), 'Kodierung UTF-8 mit BOM');
      const events = history.getByTestId('requirement-events');
      await events.getByText('Revision 2 eröffnet nach Freigabe von Revision 1').waitFor();
      await events.getByText('Verantwortlich geändert von „leer“ zu „Ben Leser“').waitFor();
      await events.getByText('K2 geändert von „Kodierung UTF-8“ zu „Kodierung UTF-8 mit BOM“').waitFor();
    });
  });

  test('criteria keep their numbers when moved by keyboard or removed, and the history names the new order', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Suche über Projekte', { criteria: ['Treffer nach Relevanz', 'Höchstens eine Sekunde', 'Nur lesbare Felder'] });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByLabel('Text von K1').focus();
      await page.keyboard.press('Alt+ArrowDown');
      await edit.getByText('K1 steht jetzt an Stelle 2 von 3.').waitFor({ state: 'attached' });
      await edit.getByRole('button', { name: 'K3 nach oben' }).click();
      // The arrow buttons announce the new position like the keyboard (F5 of the TASK-096 review).
      await edit.getByText('K3 steht jetzt an Stelle 2 von 3.').waitFor({ state: 'attached' });
      await edit.getByRole('button', { name: 'K2 entfernen' }).click();
      assert.deepEqual(await edit.getByTestId('requirement-criterion').evaluateAll((rows) => rows.map((row) => row.dataset.criterion)), ['3', '1']);
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      assert.deepEqual(bodyOf(lastWrite(backend, /\/requirements\/[^/]+$/)).criteria.map((criterion) => criterion.id), [3, 1]);
      assert.deepEqual(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).criteria.map((criterion) => criterion.id), [3, 1]);

      // A new criterion gets the next number, never a number used before.
      await edit.getByTestId('requirement-criterion-add').click();
      await edit.getByLabel('Text von Neu').fill('Groß und Kleinschreibung egal');
      await edit.getByTestId('requirement-save').click();
      await edit.locator('[data-criterion="4"]').waitFor();
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();
      await page.getByTestId('requirement-history-open').click();
      const events = page.getByTestId('requirement-events');
      await events.getByText('K2 entfernt: Höchstens eine Sekunde').waitFor();
      await events.getByText('Reihenfolge der Kriterien geändert von K1, K3 zu K3, K1').waitFor();
      await events.getByText('K4 hinzugefügt: Groß und Kleinschreibung egal').waitFor();
    });
  });

  test('a save after someone else changed the requirement keeps the own input until a choice', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna, backend.ben], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Rechnungsadresse', { criteria: ['Adresse ist Pflicht'] });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByLabel('Titel').fill('Rechnungsadresse und Lieferadresse');
      // Ben changes the criterion meanwhile.
      const answer = backend.requirementCall(backend.ben, 'PATCH', `/requirements/${item.id}`, { criteria: [{ id: 1, text: 'Adresse ist Pflicht, Land auch' }] },
        { headers: { 'if-match': `"${item.version}"` } });
      assert.equal(answer.status, 200);
      await edit.getByTestId('requirement-save').click();
      const conflict = page.getByTestId('requirement-conflict');
      await conflict.getByText('Ben Leser hat die Anforderung', { exact: false }).waitFor();
      // The differences are marked: struck out the own input, inserted the newer version.
      const changed = conflict.getByTestId('requirement-compare-criteria').locator('[data-op="changed"]');
      assert.equal(await changed.locator('del').textContent(), 'Adresse ist Pflicht');
      assert.equal(await changed.locator('ins').textContent(), 'Adresse ist Pflicht, Land auch');
      assert.equal(await conflict.getByTestId('requirement-compare-title').locator('del').textContent(), 'Rechnungsadresse und Lieferadresse');
      assert.equal(await edit.getByLabel('Titel').inputValue(), 'Rechnungsadresse und Lieferadresse', 'the own input stays');
      assert.equal(await edit.getByTestId('requirement-save').isDisabled(), true, 'no save before a choice');
      await conflict.getByTestId('requirement-conflict-keep').click();
      // The untouched criterion takes the newer text, the own title stays.
      assert.equal(await edit.getByLabel('Text von K1').inputValue(), 'Adresse ist Pflicht, Land auch');
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      const save = lastWrite(backend, /\/requirements\/[^/]+$/);
      assert.equal(save.ifMatch, '"2"', 'the save builds on the version of Ben');
      // Only the own change goes out: the criterion of Ben stays.
      assert.deepEqual(bodyOf(save), { title: 'Rechnungsadresse und Lieferadresse' });
      assert.equal(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).criteria[0].text, 'Adresse ist Pflicht, Land auch');
    });
  });

  test('an approval of a revision that changed meanwhile is refused, a return needs a reason, discard and restore work', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna, backend.ben], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Passwortregeln', { criteria: ['Mindestens zwölf Zeichen'], status: 'review' });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-approve').click();
      const dialog = page.getByTestId('requirement-approve-dialog');
      await dialog.getByTestId('requirement-approve-revision').getByText('Revision 1').waitFor();
      // Every content field is in the dialog, title and priority included (F2 of the TASK-096 review).
      assert.equal(await dialog.getByTestId('requirement-approve-title-value').textContent(), 'Passwortregeln');
      assert.equal(await dialog.getByTestId('requirement-approve-priority').textContent(), 'Normal');
      backend.requirementCall(backend.ben, 'PATCH', `/requirements/${item.id}`, { title: 'Passwortregeln für alle', priority: 'critical', criteria: [{ id: 1, text: 'Mindestens 16 Zeichen' }] },
        { headers: { 'if-match': `"${item.version}"` } });
      await dialog.getByTestId('requirement-approve-confirm').click();
      await dialog.getByText('Die Anforderung wurde inzwischen geändert.', { exact: false }).waitFor();
      assert.equal(item.approvedRevision, null, 'nothing approved');
      // The dialog shows the newer content at once; a second confirmation would approve exactly that.
      await dialog.getByTestId('requirement-approve-content-criteria-list').getByText('Mindestens 16 Zeichen').waitFor();
      assert.equal(await dialog.getByTestId('requirement-approve-title-value').textContent(), 'Passwortregeln für alle');
      assert.equal(await dialog.getByTestId('requirement-approve-priority').textContent(), 'Kritisch');
      await dialog.getByRole('button', { name: 'Abbrechen' }).click();
      await page.getByTestId('requirement-content-criteria-list').getByText('Mindestens 16 Zeichen').waitFor();
      await page.getByTestId('requirement-return').click();
      const back = page.getByTestId('requirement-return-dialog');
      await back.getByTestId('requirement-return-confirm').click();
      await back.getByText('Bitte eine Begründung angeben.').waitFor();
      await back.getByLabel('Begründung').fill('Bitte mit der Sicherheitsrichtlinie abstimmen');
      await back.getByTestId('requirement-return-confirm').click();
      await page.getByText('Die Anforderung ist zurück im Entwurf.').waitFor();
      assert.deepEqual(bodyOf(lastWrite(backend, /\/return$/)), { reason: 'Bitte mit der Sicherheitsrichtlinie abstimmen' });

      await page.getByTestId('requirement-discard').click();
      await page.getByTestId('requirement-discard-confirm').click();
      await page.getByText('Die Anforderung ist verworfen.').waitFor();
      assert.equal(await page.getByTestId('requirement-edit-open').count(), 0, 'a discarded requirement is not edited');
      page.once('dialog', (confirm) => void confirm.accept());
      await page.getByTestId('requirement-restore').click();
      await page.getByText('Wiederhergestellt, Status: Entwurf.').waitFor();
      await page.getByTestId('requirement-history-open').click();
      const events = page.getByTestId('requirement-events');
      await events.getByText('Zurück in den Entwurf: Bitte mit der Sicherheitsrichtlinie abstimmen').waitFor();
      await events.getByText('Wiederhergestellt als Entwurf').waitFor();
    });
  });

  test('keeping the own input after a conflict keeps criteria someone else changed or added (F1 of the TASK-096 review)', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna, backend.ben], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Lieferschein', { criteria: ['K1 alt', 'K2 alt'] });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByLabel('Text von K1').fill('K1 MEINE EINGABE');
      const answer = backend.requirementCall(backend.ben, 'PATCH', `/requirements/${item.id}`,
        { criteria: [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: null, text: 'K3 VON BEN' }] }, { headers: { 'if-match': `"${item.version}"` } });
      assert.equal(answer.status, 200);
      await edit.getByTestId('requirement-save').click();
      await page.getByTestId('requirement-conflict').getByTestId('requirement-conflict-keep').click();
      assert.deepEqual(await edit.getByTestId('requirement-criterion').evaluateAll((rows) => rows.map((row) => [row.dataset.criterion, row.querySelector('textarea').value])),
        [['1', 'K1 MEINE EINGABE'], ['2', 'K2 VON BEN'], ['3', 'K3 VON BEN']]);
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      assert.deepEqual(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).criteria.map((criterion) => criterion.text),
        ['K1 MEINE EINGABE', 'K2 VON BEN', 'K3 VON BEN']);
    });
  });

  test('a new criterion keeps the place it was given when the own input is kept (R1 of the follow up review of TASK-096)', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna, backend.ben], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Lieferadresse', { criteria: ['K1 alt', 'K2 alt'] });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByTestId('requirement-criterion-add').click();
      await edit.getByLabel('Text von Neu').fill('EIGEN NEU');
      await edit.getByRole('button', { name: 'Neu nach oben' }).click();
      assert.deepEqual(await edit.getByTestId('requirement-criterion').evaluateAll((rows) => rows.map((row) => row.dataset.criterion)), ['1', 'new', '2']);
      const answer = backend.requirementCall(backend.ben, 'PATCH', `/requirements/${item.id}`,
        { criteria: [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: null, text: 'K3 VON BEN' }] }, { headers: { 'if-match': `"${item.version}"` } });
      assert.equal(answer.status, 200);
      await edit.getByTestId('requirement-save').click();
      await page.getByTestId('requirement-conflict').getByTestId('requirement-conflict-keep').click();
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      assert.deepEqual(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).criteria.map((criterion) => [criterion.id, criterion.text]),
        [[1, 'K1 alt'], [4, 'EIGEN NEU'], [2, 'K2 VON BEN'], [3, 'K3 VON BEN']]);
    });
  });

  test('the stand in checks descriptions by their structure and names editors like the server (N1, R2 of the follow up review)', async () => {
    const backend = createBackend();
    grant(backend, [backend.anna], EVERYTHING);
    const create = (description) => backend.requirementCall(backend.anna, 'POST', `/requirements/projects/${backend.project.id}/requirements`, { title: 'Probe', description });
    const paragraph = (text, marks) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text, ...(marks ? { marks } : {}) }] }] });
    assert.equal(create(paragraph('image')).status, 201, 'the word image is plain text');
    assert.equal(create(paragraph('wiki:ordinary text')).status, 201, 'text that starts with wiki: is no link');
    const nul = create(paragraph('Ziel', [{ type: 'link', attrs: { href: 'https://example.test/a\u0000b' } }]));
    assert.equal(nul.status, 400);
    assert.deepEqual(nul.body.violations, ['description.link_invalid']);
    const page = create(paragraph('Seite', [{ type: 'link', attrs: { href: 'wiki:00000000-0000-4000-8000-000000000001' } }]));
    assert.deepEqual(page.body.violations, ['description.page_link_not_allowed']);
    // M1 of the review of TASK-099: an image of the wiki is refused as image, any other source already as external.
    const image = create({ type: 'doc', content: [{ type: 'image', attrs: { src: '/api/v1/wiki/images/00000000-0000-4000-8000-000000000002', alt: 'Bild' } }] });
    assert.deepEqual(image.body.violations, ['description.image_not_allowed']);
    const external = create({ type: 'doc', content: [{ type: 'image', attrs: { src: '/x', alt: 'Bild' } }] });
    assert.deepEqual(external.body.violations, ['description.image_external']);
    const upper = create(paragraph('Seite', [{ type: 'link', attrs: { href: 'wiki:ABCDEFAB-ABCD-4000-8000-ABCDEFABCDEF' } }]));
    assert.deepEqual(upper.body.violations, ['description.link_invalid'], 'the server takes the identifier of a page in lower case only');

    // R2: without a readable content field even the creation names nobody, also without any field rule.
    const made = create(paragraph('Inhalt'));
    grant(backend, [backend.ben], ['requirement.read'], { read: [], write: [] });
    const editors = (account) => backend.requirementCall(account, 'GET', `/requirements/${made.body.id}`).body.editors;
    assert.deepEqual(editors(backend.ben), [], 'no field rule');
    grant(backend, [backend.ben], ['requirement.read'], { read: ['owner'], write: [] });
    assert.deepEqual(editors(backend.anna), [backend.anna.id]);
    assert.deepEqual(editors(backend.ben), [], 'only the owner is readable');
    // A second person who changes only the description is named for full readers only.
    grant(backend, [backend.nora], EVERYTHING);
    const item = backend.state.requirements.items.find((entry) => entry.id === made.body.id);
    const changed = backend.requirementCall(backend.nora, 'PATCH', `/requirements/${made.body.id}`, { description: paragraph('Neuer Inhalt') },
      { headers: { 'if-match': `"${item.version}"` } });
    assert.equal(changed.status, 200);
    assert.deepEqual([...editors(backend.anna)].sort(), [backend.anna.id, backend.nora.id].sort());
    assert.deepEqual(editors(backend.ben), []);
  });

  test('the stand in takes link targets with the space rules of the server (S1 of the review of TASK-099)', () => {
    const backend = createBackend();
    grant(backend, [backend.anna], EVERYTHING);
    const create = (href) => backend.requirementCall(backend.anna, 'POST', `/requirements/projects/${backend.project.id}/requirements`,
      { title: 'Probe', description: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Link', marks: [{ type: 'link', attrs: { href } }] }] }] } });
    // Java strip() keeps the no break spaces and the byte order mark, \s of the server pattern knows only ASCII spaces.
    for (const href of [' https://example.test', '﻿https://example.test']) {
      const answer = create(href);
      assert.equal(answer.status, 400, JSON.stringify(href));
      assert.deepEqual(answer.body.violations, ['description.link_invalid']);
    }
    for (const href of ['https://example.test/a b', 'https://example.test/a b', ' https://example.test ']) {
      assert.equal(create(href).status, 201, JSON.stringify(href));
    }
    const stored = backend.state.requirements.revisions.at(-1).description;
    assert.equal(stored.content[0].content[0].marks[0].attrs.href, 'https://example.test', 'stored stripped like the server');
  });

  test('the stand in normalizes the document before it refuses images and page links (S2 of the review of TASK-099)', () => {
    const backend = createBackend();
    grant(backend, [backend.anna], EVERYTHING);
    const create = (description) => backend.requirementCall(backend.anna, 'POST', `/requirements/projects/${backend.project.id}/requirements`, { title: 'Probe', description });
    const doc = (...content) => ({ type: 'doc', content });
    const link = (href) => ({ type: 'link', attrs: { href } });
    const text = (value, marks) => ({ type: 'text', text: value, ...(marks !== undefined ? { marks } : {}) });
    const accepted = [
      ['a second link mark is dropped', doc({ type: 'paragraph', content: [text('Link', [link('https://example.test'), link('javascript:alert(1)')])] })],
      ['a second page link is dropped', doc({ type: 'paragraph', content: [text('Link', [link('https://example.test'), link('wiki:00000000-0000-4000-8000-000000000001')])] })],
      ['marks of a line break are dropped', doc({ type: 'paragraph', content: [text('a'), { type: 'hardBreak', marks: [link('javascript:alert(1)')] }] })],
      ['marks of a paragraph are dropped', doc({ type: 'paragraph', marks: [link('javascript:alert(1)')], content: [text('a')] })],
      ['content of a rule is dropped', doc({ type: 'horizontalRule', content: [{ type: 'image', attrs: { src: '/api/v1/wiki/images/00000000-0000-4000-8000-000000000002' } }] })],
    ];
    for (const [name, description] of accepted) assert.equal(create(description).status, 201, name);
    const first = backend.state.requirements.revisions.find((entry) => entry.description?.content?.[0]?.content?.[0]?.text === 'Link').description;
    assert.deepEqual(first.content[0].content[0].marks, [link('https://example.test')], 'only the first link mark is stored');
    const refused = [
      ['an unknown block', doc({ type: 'iframe' }), 'description.unsupported'],
      ['a list instead of a document', [], 'description.invalid'],
      ['marks that are no list', doc({ type: 'paragraph', content: [text('a', 'invalid')] }), 'description.invalid'],
      ['a link in a code block', doc({ type: 'codeBlock', content: [text('a', [link('https://example.test')])] }), 'description.unsupported'],
      ['more than 1 MiB', doc({ type: 'paragraph', content: [text('x'.repeat(1_048_576))] }), 'description.too_large'],
    ];
    for (const [name, description, code] of refused) {
      const answer = create(description);
      assert.equal(answer.status, 400, name);
      assert.deepEqual(answer.body.violations, [code], name);
    }
  });

  test('a changed link target is named in the comparison of the history (F3 of the TASK-096 review)', async () => {
    const linked = (href) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Anleitung', marks: [{ type: 'link', attrs: { href } }] }] }] });
    let item;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna, backend.ben], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Verweis', { criteria: ['Ein Kriterium'], description: linked('https://old.example/'), status: 'approved', approvedBy: backend.ben });
        const answer = backend.requirementCall(backend.anna, 'PATCH', `/requirements/${item.id}`, { description: linked('https://new.example/') },
          { headers: { 'if-match': `"${item.version}"` } });
        assert.equal(answer.status, 200);
        assert.equal(item.revision, 2);
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page) => {
      await page.getByTestId('requirement-history-open').click();
      const form = page.getByTestId('requirement-compare');
      await form.getByLabel('Von').selectOption('1');
      await form.getByLabel('Bis').selectOption('2');
      await form.getByRole('button', { name: 'Vergleichen' }).click();
      const result = page.getByTestId('requirement-compare-description');
      await result.getByText('Verweise und Bilder: https://old.example/').waitFor();
      await result.getByText('Verweise und Bilder: https://new.example/').waitFor();
    });
  });

  test('typed input of a requirement not created yet asks before leaving (F6 of the TASK-096 review)', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.anna], EVERYTHING), hash: listHash }, async (page) => {
      const create = page.getByTestId('requirement-create');
      await create.getByLabel('Titel').fill('Noch nicht angelegt');
      let asked = 0;
      page.once('dialog', (confirm) => {
        asked++;
        void confirm.dismiss();
      });
      await page.getByRole('link', { name: 'Start' }).click();
      await page.waitForFunction(() => location.hash.startsWith('#/requirements/'));
      assert.equal(asked, 1);
      assert.equal(await create.getByLabel('Titel').inputValue(), 'Noch nicht angelegt', 'the input stays');
      await create.getByRole('button', { name: 'Anlegen' }).click();
      await page.waitForFunction(() => location.hash.split('/').length === 4);
      assert.equal(asked, 1, 'creating leaves without a question');
    });
  });

  test('a person with read rules for some fields sees only those, with no actions and no filters over hidden fields', async () => {
    let item;
    await withApp({
      as: BEN,
      prepare: (backend) => {
        grant(backend, [backend.anna], EVERYTHING);
        grant(backend, [backend.ben], ['requirement.read'], { read: ['title', 'criteria'], write: [] });
        item = backend.addRequirement(backend.anna, 'Datenschutzhinweis', { criteria: ['Text vor dem Absenden sichtbar'], priority: 'critical',
          description: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Vertraulich' }] }] }, ownerId: backend.anna.id, status: 'review' });
      },
      hash: listHash,
    }, async (page, backend) => {
      const list = page.getByTestId('requirement-list');
      await list.getByText('Datenschutzhinweis').waitFor();
      assert.equal(await list.getByRole('columnheader', { name: 'Priorität' }).count(), 0);
      assert.equal(await list.getByRole('columnheader', { name: 'Verantwortlich' }).count(), 0);
      const filters = page.getByTestId('requirement-filters');
      assert.equal(await filters.getByLabel('Priorität').count(), 0);
      assert.equal(await filters.getByLabel('Verantwortlich').count(), 0);
      assert.equal(await page.getByTestId('requirement-create').count(), 0);

      await list.getByRole('link', { name: 'ANF-1' }).click();
      await page.getByTestId('requirement-content-criteria-list').getByText('Text vor dem Absenden sichtbar').waitFor();
      assert.equal(await page.getByTestId('requirement-priority').textContent(), 'Nicht sichtbar');
      assert.equal(await page.getByTestId('requirement-owner').textContent(), 'Nicht sichtbar');
      assert.equal(await page.getByText('Vertraulich').count(), 0);
      for (const action of ['requirement-edit-open', 'requirement-submit', 'requirement-approve', 'requirement-return', 'requirement-discard']) {
        assert.equal(await page.getByTestId(action).count(), 0, action);
      }
      assert.equal(backend.writes().length, 0);
      assert.ok(item);
    });
  });

  test('the list filters on the server, loads further pages and drops a late answer of an earlier filter', async () => {
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], EVERYTHING);
        for (let index = 1; index <= 55; index += 1) {
          backend.addRequirement(backend.anna, `Anforderung ${index}`, { priority: index % 5 === 0 ? 'high' : 'normal', criteria: ['K'], status: index === 3 ? 'discarded' : 'draft' });
        }
        // Discarded by hand, as the stand in creates drafts only.
        backend.state.requirements.items[2].status = 'discarded';
      },
      hash: listHash,
    }, async (page, backend) => {
      const rows = page.getByTestId('requirement-row');
      await page.getByTestId('requirement-more').waitFor();
      assert.equal(await rows.count(), 50);
      assert.equal(await page.getByTestId('requirement-list').getByText('Anforderung 3', { exact: true }).count(), 0, 'discarded ones are left out');
      await page.getByTestId('requirement-more').click();
      await page.getByTestId('requirement-list').getByText('Anforderung 55', { exact: true }).waitFor();
      assert.equal(await rows.count(), 54);
      assert.equal(await page.getByTestId('requirement-more').count(), 0);

      // The first filter answers late; the second one is shown and stays.
      let release;
      const held = new Promise((resolve) => { release = resolve; });
      backend.state.requirements.delayList = (answer, query) => (query.get('priority') === 'high' ? held.then(() => answer) : answer);
      const filters = page.getByTestId('requirement-filters');
      await filters.getByLabel('Priorität').selectOption('high');
      await filters.getByRole('button', { name: 'Filtern' }).click();
      await page.getByTestId('requirement-list-loading').waitFor();
      await filters.getByLabel('Priorität').selectOption('');
      await filters.getByLabel('Status').selectOption('discarded');
      await filters.getByRole('button', { name: 'Filtern' }).click();
      await page.getByTestId('requirement-list').getByText('Anforderung 3', { exact: true }).waitFor();
      release();
      await page.waitForTimeout(300);
      assert.equal(await rows.count(), 1, 'the late answer of the earlier filter is not taken');
      const query = backend.state.requests.findLast((item) => item.method === 'GET' && item.path.endsWith('/requirements')).query;
      assert.match(query, /status=discarded/);
    });
  });

  test('the groups page sets field rules of requirements with their own resource type', async () => {
    await withApp({ hash: '#/groups' }, async (page, backend) => {
      await page.getByRole('link', { name: 'Lesen' }).click();
      const table = page.getByTestId('group-fields-requirement');
      await table.getByRole('rowheader', { name: 'Abnahmekriterien' }).waitFor();
      await table.getByLabel('Lesen von Abnahmekriterien bei Anforderungen').selectOption('allow');
      await page.getByText('Feldregel gespeichert.').waitFor();
      assert.deepEqual(bodyOf(lastWrite(backend, /\/fields$/)), { resourceType: 'requirement', field: 'criteria', projectId: null, operation: 'read', effect: 'allow' });
      assert.equal(await page.getByTestId('group-fields').getByRole('rowheader', { name: 'Abnahmekriterien' }).count(), 0, 'tickets have no criteria');
    });
  });

  // TASK-098: custom fields of requirements from the project configuration, with their own field rules.
  const OWN_FIELDS = [
    { key: 'area', name: 'Bereich', type: 'select', required: true, active: true,
      options: [{ key: 'portal', name: 'Portal', active: true }, { key: 'intern', name: 'Intern', active: true }] },
    { key: 'effort', name: 'Aufwand', type: 'integer', required: false, active: true, options: [] },
    { key: 'note', name: 'Vertraulich', type: 'text', required: false, active: true, options: [] }];
  const OWN = ['custom.area', 'custom.effort', 'custom.note'];
  const withOwnFields = (backend) => {
    backend.state.configurations.get(backend.project.id).configuration.requirementFields = structuredClone(OWN_FIELDS);
  };

  test('custom fields are required for the review, count as content in approval, revision, comparison and history (TASK-098)', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        withOwnFields(backend);
        grant(backend, [backend.anna], EVERYTHING, { read: [...ALL, ...OWN], write: [...ALL, ...OWN] });
        item = backend.addRequirement(backend.anna, 'Kunden sehen den Stand ihrer Anfragen', { criteria: ['Die Portalliste zeigt den Status.'] });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      // An empty required field blocks the review with 400 and its name.
      await page.getByTestId('requirement-submit').click();
      await page.getByText('Bereich: wird für „Zur Prüfung“ benötigt.').waitFor();
      assert.equal(item.status, 'draft');

      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      const own = edit.getByTestId('requirement-custom-editor');
      await own.getByLabel('Bereich').selectOption({ label: 'Portal' });
      await own.getByLabel('Aufwand').fill('5');
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Gespeichert in Revision 1.').waitFor();
      assert.deepEqual(bodyOf(lastWrite(backend, /\/requirements\/[^/]+$/)).customFields, { area: 'portal', effort: 5 }, 'the values go with their kind');
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();
      const shown = page.getByTestId('requirement-content-custom');
      assert.equal(await shown.locator('[data-field="area"]').textContent(), 'Portal');
      assert.equal(await shown.locator('[data-field="effort"]').textContent(), '5');
      assert.equal(await shown.locator('[data-field="note"]').textContent(), 'Leer');

      await page.getByTestId('requirement-submit').click();
      await page.getByText('Revision 1 ist zur Prüfung gegeben.').waitFor();
      await page.getByTestId('requirement-approve').click();
      const dialog = page.getByTestId('requirement-approve-dialog');
      assert.equal(await dialog.getByTestId('requirement-approve-content-custom').locator('[data-field="area"]').textContent(), 'Portal',
        'the approval dialog shows the custom fields of the revision');
      await dialog.getByTestId('requirement-approve-confirm').click();
      await page.getByText('Revision 1 ist freigegeben.').waitFor();

      // A change of a custom field after the approval opens the next revision.
      await page.getByTestId('requirement-edit-open').click();
      await edit.getByTestId('requirement-custom-editor').getByLabel('Aufwand').fill('8');
      await edit.getByText('Beim Speichern entsteht eine neue Revision im Entwurf.').waitFor();
      await edit.getByTestId('requirement-save').click();
      await page.getByText('Revision 2 ist eröffnet', { exact: false }).waitFor();
      assert.equal(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id && entry.revision === 1).custom.effort, 5,
        'the approved revision keeps its value');
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();

      await page.getByTestId('requirement-history-open').click();
      const history = page.getByTestId('requirement-history');
      const compare = history.getByTestId('requirement-compare');
      await compare.getByLabel('Von').selectOption('1');
      await compare.getByLabel('Bis').selectOption('2');
      await compare.getByRole('button', { name: 'Vergleichen' }).click();
      const effort = compare.locator('[data-testid="requirement-compare-custom"][data-field="effort"]');
      assert.equal(await effort.locator('del').textContent(), '5');
      assert.equal(await effort.locator('ins').textContent(), '8');
      const events = history.getByTestId('requirement-events');
      await events.getByText('Bereich geändert von „leer“ zu „Portal“').waitFor();
      await events.getByText('Aufwand geändert von „5“ zu „8“').waitFor();
    });
  });

  test('a custom field without a read rule is missing everywhere, and a hidden required field stops the review with a conflict (TASK-098)', async () => {
    let item;
    await withApp({
      prepare: (backend) => {
        withOwnFields(backend);
        grant(backend, [backend.anna], EVERYTHING, { read: [...ALL, 'custom.area'], write: ALL });
        item = backend.addRequirement(backend.anna, 'Kunden sehen den Stand', { criteria: ['Die Portalliste zeigt den Status.'],
          custom: { area: 'intern', effort: 3, note: 'nur intern' } });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      const shown = page.getByTestId('requirement-content-custom');
      await shown.locator('[data-field="area"]').getByText('Intern').waitFor();
      assert.equal(await shown.locator('dd').count(), 1, 'only the readable field with its definition');
      assert.equal(await page.getByText('nur intern').count(), 0);
      await page.getByTestId('requirement-edit-open').click();
      assert.equal(await page.getByTestId('requirement-custom-editor').count(), 0, 'no control without a write rule');
      await page.getByRole('button', { name: 'Bearbeitung schließen' }).click();
      await page.getByTestId('requirement-submit').click();
      await page.getByText('Ein Pflichtfeld für „Zur Prüfung“ darfst du nicht lesen oder ändern.', { exact: false }).waitFor();
      assert.equal(item.status, 'draft');
    });
  });

  test('a writable custom person field offers the members without the owner, and a failed load is shown with a retry (TASK-100)', async () => {
    let item;
    let failures = 1;
    await withApp({
      prepare: (backend) => {
        backend.state.configurations.get(backend.project.id).configuration.requirementFields = [
          { key: 'reviewer', name: 'Prüfer', type: 'user', required: false, active: true, options: [] }];
        grant(backend, [backend.anna], EVERYTHING, { read: [...ALL, 'custom.reviewer'], write: ['title', 'custom.reviewer'] });
        item = backend.addRequirement(backend.anna, 'Mit Prüfer', { criteria: ['Jemand prüft.'] });
        backend.state.requirements.failOwners = () => failures-- > 0;
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page, backend) => {
      await page.getByTestId('requirement-edit-open').click();
      const edit = page.getByTestId('requirement-edit');
      const error = edit.getByTestId('requirement-people-error');
      await error.getByText('Die Personenauswahl konnte nicht geladen werden.', { exact: false }).waitFor();
      await error.getByRole('button', { name: 'Erneut versuchen' }).click();
      await error.waitFor({ state: 'detached' });
      assert.equal(await edit.getByLabel('Verantwortlich').count(), 0, 'no owner control without its write rule');
      const reviewer = edit.getByLabel('Prüfer');
      await reviewer.locator('option', { hasText: BEN.displayName }).waitFor({ state: 'attached' });
      await reviewer.selectOption({ label: BEN.displayName });
      await edit.getByRole('button', { name: 'Speichern' }).click();
      await edit.getByText('Stand 2', { exact: false }).waitFor();
      await edit.getByRole('button', { name: 'Bearbeitung schließen' }).click();
      await page.getByTestId('requirement-content-custom').locator('[data-field="reviewer"]').getByText(BEN.displayName).waitFor();
      assert.equal(backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).custom.reviewer, backend.ben.id);
      assert.equal(item.ownerId ?? null, null);
    });
  });

  test('the pages work in English as well', async () => {
    let item;
    await withApp({
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.anna], EVERYTHING);
        item = backend.addRequirement(backend.anna, 'Single sign on', { criteria: ['Works with the company account'], status: 'approved' });
      },
      hash: (backend) => detailHash(backend, item),
    }, async (page) => {
      await page.getByTestId('requirement-status').getByText('Approved').waitFor();
      await page.getByTestId('requirement-approval').getByText('Revision 1 approved by Anna Admin', { exact: false }).waitFor();
      await page.getByRole('button', { name: 'History' }).waitFor();
    });
  });
});

// TASK-100 (F4 of the review of TASK-098): the stand in checks and keeps the values of custom fields like the server. The same
// case matrix runs against the server in RequirementApiTest.
test('the stand in answers the shared case matrix of custom values like the server (TASK-100)', async () => {
  const matrix = JSON.parse(await readFile(new URL('../../../contracts/cases/requirement-custom-values.json', import.meta.url), 'utf8'));
  const backend = createBackend();
  backend.state.configurations.get(backend.project.id).configuration.requirementFields = structuredClone(matrix.fields);
  const own = matrix.fields.map((field) => `custom.${field.key}`);
  grant(backend, [backend.anna], EVERYTHING, { read: [...ALL, ...own], write: [...ALL, ...own] });
  const member = backend.ben.id;
  const stranger = '00000000-0000-4000-8000-00000000f100';
  const expand = (value) => {
    if (value === '$member') return member;
    if (value === '$MEMBER') return member.toUpperCase();
    if (value === '$stranger') return stranger;
    if (value !== null && typeof value === 'object' && typeof value.$repeat === 'string') return value.$repeat.repeat(value.times);
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, expand(entry)]));
    return value;
  };
  for (const entry of matrix.cases) {
    const created = backend.requirementCall(backend.anna, 'POST', `/requirements/projects/${backend.project.id}/requirements`, { title: entry.name });
    assert.equal(created.status, 201, entry.name);
    const answer = backend.requirementCall(backend.anna, 'PATCH', `/requirements/${created.body.id}`, { customFields: expand(entry.customFields) },
      { headers: { 'if-match': created.etag } });
    assert.equal(answer.status, entry.status, `${entry.name}: ${JSON.stringify(answer.body)}`);
    if (entry.status === 400) {
      assert.ok(answer.body.violations.includes(entry.violation), `${entry.name}: ${JSON.stringify(answer.body.violations)}`);
      continue;
    }
    const read = backend.requirementCall(backend.anna, 'GET', `/requirements/${created.body.id}`);
    for (const [key, value] of Object.entries(expand(entry.stored))) {
      assert.deepEqual(read.body.content.customFields[key] ?? null, value, `${entry.name}: ${key}`);
    }
  }
});
