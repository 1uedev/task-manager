import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-093: the wiki of a project with tree, shared draft, explicit publication, history, conflict, archive, images and
// the paste filter, after the actions wiki.read, wiki.edit and wiki.publish, against the in-memory backend that follows
// the contract.

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

function grant(backend, accounts, actions) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000000093${String(backend.state.groups.length).padStart(2, '0')}`, name: `Wiki ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })), fields: [] });
}

const doc = (...texts) => ({ type: 'doc', content: texts.map((text) => (text === '' ? { type: 'paragraph' } : { type: 'paragraph', content: [{ type: 'text', text }] })) });

async function withApp({ prepare, as = ANNA, hash, locale = 'de-DE' }, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${typeof hash === 'function' ? hash(backend) : hash ?? '#/wiki'}`,
    { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const wikiHash = (backend, page = null) => `#/wiki/${backend.project.id}${page === null ? '' : `/${page.id}`}`;

describe('wiki', () => {
  test('readers see published pages only, and a link to a page they may not read shows no title', async () => {
    let hidden;
    let shown;
    await withApp({
      as: BEN,
      prepare: (backend) => {
        grant(backend, [backend.ben], ['wiki.read']);
        hidden = backend.addWikiPage(backend.anna, 'Geheimer Entwurf', doc('Noch nicht fertig'));
        shown = backend.addWikiPage(backend.anna, 'Handbuch', {
          type: 'doc', content: [
            { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Einstieg' }] },
            { type: 'paragraph', content: [{ type: 'text', text: 'Siehe ' }, { type: 'text', text: 'Geheimer Entwurf', marks: [{ type: 'link', attrs: { href: `wiki:${hidden.id}` } }] }] },
          ],
        }, { publish: true });
      },
      hash: '#/wiki',
    }, async (page, backend) => {
      await page.getByTestId('wiki-project').getByRole('link').click();
      const tree = page.getByTestId('wiki-tree');
      await tree.getByRole('link', { name: 'Handbuch' }).click();
      const content = page.getByTestId('wiki-content');
      await content.getByRole('heading', { name: 'Einstieg' }).waitFor();
      assert.equal(await content.locator('[data-wiki-unavailable]').textContent(), 'Seite nicht verfügbar');
      assert.equal(await content.getByText('Geheimer Entwurf').count(), 0, 'the title of an unreadable page stays hidden');
      assert.equal(await tree.getByText('Geheimer Entwurf').count(), 0, 'drafts are not in the tree of readers');
      assert.equal(await page.getByTestId('wiki-edit-open').count(), 0);
      assert.equal(await page.getByTestId('wiki-history-open').count(), 0);
      await page.goto(`${base()}${wikiHash(backend, hidden)}`);
      await page.getByText('Der Eintrag wurde nicht gefunden oder ist für Sie nicht sichtbar.').waitFor();
      assert.equal(await page.getByTestId('wiki-page').count(), 0);
      assert.ok(shown);
    });
  });

  test('a page is created, saved as draft and published with exactly the revision shown', async () => {
    await withApp({ prepare: (backend) => grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']), hash: wikiHash }, async (page, backend) => {
      const create = page.getByTestId('wiki-create');
      await create.getByRole('button', { name: 'Seite anlegen' }).click();
      await create.getByText('Bitte einen Titel angeben.').waitFor();
      await create.getByLabel('Titel').fill('Betriebshandbuch');
      await create.getByRole('button', { name: 'Seite anlegen' }).click();
      await page.getByTestId('wiki-title').getByText('Betriebshandbuch').waitFor();
      await page.getByTestId('wiki-tree').getByTestId('wiki-tree-draft').waitFor();

      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      await editor.click();
      await page.keyboard.type('Neustart des Dienstes');
      await page.getByTestId('wiki-save-state').getByText('Ungespeicherte Änderungen').waitFor();
      await page.getByRole('button', { name: 'Überschrift 2' }).click();
      assert.equal(await editor.locator('h2').textContent(), 'Neustart des Dienstes');
      await page.keyboard.press('End');
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Fett' }).click();
      await page.keyboard.type('Wichtig');
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 2 gespeichert.').waitFor();
      const save = backend.state.requests.findLast((item) => item.method === 'PUT' && item.path.endsWith('/draft'));
      assert.equal(save.ifMatch, '"1"');
      const sent = JSON.parse(save.body.toString('utf8'));
      assert.deepEqual(sent.content.content.map((block) => block.type), ['heading', 'paragraph']);
      assert.deepEqual(sent.content.content[1].content[0].marks, [{ type: 'bold' }]);

      await page.getByRole('button', { name: 'Bearbeiten beenden' }).click();
      await page.getByTestId('wiki-publish').click();
      const dialog = page.getByTestId('wiki-publish-dialog');
      await dialog.getByText('Revision 2, gespeichert von Anna Admin', { exact: false }).waitFor();
      await dialog.getByText('Alle mit dem Recht „Wiki lesen“ im Projekt PLAT · Plattform').waitFor();
      await dialog.getByRole('button', { name: 'Revision 2 veröffentlichen' }).click();
      await page.getByText('Revision 2 ist veröffentlicht.').waitFor();
      const publish = backend.state.requests.findLast((item) => item.path.endsWith('/publish'));
      assert.equal(publish.ifMatch, '"2"');
      assert.deepEqual(JSON.parse(publish.body.toString('utf8')), { revision: 2 });
      await page.getByTestId('wiki-status').getByText('Veröffentlicht: Revision 2', { exact: false }).waitFor();
      assert.equal(await page.getByTestId('wiki-content').locator('h2').textContent(), 'Neustart des Dienstes');
    });
  });

  test('a newer draft of someone else keeps the own input and shows the differences', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit']);
        wikiPage = backend.addWikiPage(backend.anna, 'Rufbereitschaft', doc('Montag Anna', 'Dienstag Ben'));
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      await editor.getByText('Dienstag Ben').click();
      await page.keyboard.press('End');
      await page.keyboard.type(' und Nora');
      backend.state.wikiConflict = { title: 'Rufbereitschaft', content: doc('Montag Anna', 'Mittwoch Lea'), by: backend.ben.id };
      await page.getByTestId('wiki-save').click();
      const conflict = page.getByTestId('wiki-conflict');
      await conflict.getByText('Revision 2 von Ben Leser', { exact: false }).waitFor();
      const changed = conflict.getByTestId('wiki-conflict-diff').locator('[data-op="changed"]');
      assert.equal(await changed.locator('del').textContent(), 'Dienstag Ben und Nora');
      assert.equal(await changed.locator('ins').textContent(), 'Mittwoch Lea');
      await editor.getByText('Dienstag Ben und Nora').waitFor();
      assert.equal(await page.getByTestId('wiki-save').isDisabled(), true, 'no save before a choice');

      await conflict.getByRole('button', { name: 'Meine Eingabe behalten und auf neuer Grundlage speichern' }).click();
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 3 gespeichert.').waitFor();
      const save = backend.state.requests.findLast((item) => item.method === 'PUT' && item.path.endsWith('/draft'));
      assert.equal(save.ifMatch, '"2"');
      assert.equal(backend.state.wiki.revisions.find((item) => item.pageId === wikiPage.id && item.revision === 3).content.content[1].content[0].text,
        'Dienstag Ben und Nora');

      // Unsaved input asks before leaving.
      await editor.getByText('Dienstag Ben und Nora').click();
      await page.keyboard.type('!');
      await page.getByTestId('wiki-save-state').getByText('Ungespeicherte Änderungen').waitFor();
      let asked = null;
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?');
      await editor.waitFor();
    });
  });

  test('pasting keeps allowed elements, drops foreign images and embedded content and says so', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit']);
        wikiPage = backend.addWikiPage(backend.anna, 'Einfügen', doc(''));
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page) => {
      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      await editor.click();
      await page.evaluate(() => {
        const data = new DataTransfer();
        data.setData('text/html', '<h2>Übernommen</h2><p><s>durch</s> <u>unter</u> <strong>fett</strong></p><img src="https://fremd.example/bild.png" alt="x">'
          + '<iframe src="https://fremd.example/"></iframe><table><tr><th>Kopf</th></tr><tr><td>Zelle</td></tr></table>');
        document.querySelector('[data-testid="wiki-editor"]').dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
      });
      await page.getByTestId('wiki-paste-notice').getByText('(Anzahl: 2)', { exact: false }).waitFor();
      await editor.locator('h2', { hasText: 'Übernommen' }).waitFor();
      assert.equal(await editor.locator('img').count(), 0);
      assert.equal(await editor.locator('s, u, iframe').count(), 0, 'unknown formatting becomes text');
      assert.equal(await editor.locator('strong').textContent(), 'fett');
      assert.ok((await editor.textContent()).includes('durch unter'));
      await editor.locator('th', { hasText: 'Kopf' }).waitFor();
    });
  });

  test('history shows revisions, compares them by blocks and takes an old one over as new draft', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Abläufe', doc('Schritt eins', 'Schritt zwei'), { publish: true });
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, revision: 2, title: 'Abläufe', content: doc('Schritt eins', 'Schritt drei'), savedAt: '2026-10-21T09:00:00Z',
          savedBy: backend.ben.id, basedOn: 1, restoredFrom: null });
        wikiPage.draftRevision = 2;
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-status').getByText('Der Entwurf (Revision 2) ist neuer.', { exact: false }).waitFor();
      await page.getByTestId('wiki-history-open').click();
      const history = page.getByTestId('wiki-history');
      await history.locator('[data-revision="1"][data-published="true"]').waitFor();
      const compare = history.getByTestId('wiki-compare');
      await compare.getByLabel('Von').selectOption('1');
      await compare.getByLabel('Bis').selectOption('2');
      await compare.getByRole('button', { name: 'Vergleichen' }).click();
      const changed = compare.getByTestId('wiki-compare-diff').locator('[data-op="changed"]');
      assert.equal(await changed.locator('del').textContent(), 'Schritt zwei');
      assert.equal(await changed.locator('ins').textContent(), 'Schritt drei');

      page.once('dialog', (dialog) => void dialog.accept());
      await history.locator('[data-revision="1"]').getByTestId('wiki-restore').click();
      await page.getByText('Revision 1 ist als Revision 3 der neue Entwurf.').waitFor();
      const restore = backend.state.requests.findLast((item) => item.path.endsWith('/restore'));
      assert.equal(restore.ifMatch, '"2"');
      await history.locator('[data-revision="3"]').getByText('(aus Revision 1 übernommen)').waitFor();
      assert.equal(wikiPage.publishedRevision, 1, 'taking over does not publish');
    });
  });

  test('archiving hides a page and moves its subpages up; restoring brings it back', async () => {
    let parent;
    let child;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        parent = backend.addWikiPage(backend.anna, 'Server', doc('Übersicht'), { publish: true });
        child = backend.addWikiPage(backend.anna, 'Datenbank', doc('Sicherung'), { publish: true, parentId: parent.id });
      },
      hash: (backend) => wikiHash(backend, parent),
    }, async (page) => {
      const tree = page.getByTestId('wiki-tree');
      await tree.locator(`[data-page="${parent.id}"] [data-page="${child.id}"]`).waitFor();
      page.once('dialog', (dialog) => void dialog.accept());
      await page.getByTestId('wiki-archive').click();
      await page.getByText('Die Seite ist archiviert.').waitFor();
      await page.getByTestId('wiki-archived').waitFor();
      await tree.locator(`.wiki-tree-level > [data-page="${child.id}"]`).waitFor();
      assert.equal(await tree.locator(`[data-testid="wiki-tree-item"][data-page="${parent.id}"]`).count(), 0);
      await page.getByTestId('wiki-show-archived').check();
      await page.getByTestId('wiki-archived-item').getByRole('link', { name: 'Server' }).waitFor();
      await page.getByTestId('wiki-unarchive').click();
      await page.getByText('Die Seite ist wiederhergestellt.').waitFor();
      await tree.locator(`[data-testid="wiki-tree-item"][data-page="${parent.id}"]`).waitFor();
    });
  });

  test('an uploaded image needs an alternative text and lands in the draft', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit']);
        wikiPage = backend.addWikiPage(backend.anna, 'Bilder', doc('Vorher'));
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-edit-open').click();
      await page.getByTestId('wiki-editor').getByText('Vorher').click();
      await page.getByRole('button', { name: 'Bild', exact: true }).click();
      const form = page.getByTestId('wiki-image-form');
      const png = Buffer.from('89504e470d0a1a0a0000000d4948445200000001000000010806000000' + '1f15c4890000000d49444154789c6360f8cfc0f01f0005000201'
        + 'e2d1b0a50000000049454e44ae426082', 'hex');
      await form.getByLabel('Bilddatei').setInputFiles({ name: 'plan.png', mimeType: 'image/png', buffer: png });
      await form.getByRole('button', { name: 'Bild einfügen' }).click();
      await form.getByText('Bitte einen Alternativtext angeben.').waitFor();
      await form.getByLabel('Alternativtext').fill('Netzplan');
      await form.getByRole('button', { name: 'Bild einfügen' }).click();
      await page.getByTestId('wiki-editor').locator('img[alt="Netzplan"]').waitFor();
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 2 gespeichert.').waitFor();
      const image = backend.state.wiki.images[0];
      const saved = backend.state.wiki.revisions.find((item) => item.pageId === wikiPage.id && item.revision === 2).content;
      assert.ok(JSON.stringify(saved).includes(`/api/v1/wiki/images/${image.id}`));
    });
  });
  test('an unchanged draft is saved; input typed while a save runs stays and remains unsaved (F1, F3)', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit']);
        wikiPage = backend.addWikiPage(backend.anna, 'Speichern', doc('Start'));
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-edit-open').click();
      const state = page.getByTestId('wiki-save-state');
      await state.getByText('Revision 1', { exact: false }).waitFor();
      assert.equal(await state.getAttribute('data-dirty'), 'false', 'opening without input is clean');
      assert.equal(await page.getByTestId('wiki-save').isDisabled(), true);

      const editor = page.getByTestId('wiki-editor');
      await editor.getByText('Start').click();
      await page.keyboard.press('End');
      await page.keyboard.type(' gesendet');
      let release;
      backend.state.hold = { [`/api/v1/wiki/pages/${wikiPage.id}/draft`]: new Promise((resolve) => { release = resolve; }) };
      await page.getByTestId('wiki-save').click();
      await page.waitForFunction(() => document.querySelector('[data-testid="wiki-save"]')?.disabled === true);
      await editor.getByText('Start gesendet').click();
      await page.keyboard.press('End');
      await page.keyboard.type(' SPAETER');
      await editor.getByText('Start gesendet SPAETER').waitFor();
      release();
      await page.getByText('Entwurf als Revision 2 gespeichert.').waitFor();
      backend.state.hold = null;
      await editor.getByText('Start gesendet SPAETER').waitFor();
      assert.equal(await state.getAttribute('data-dirty'), 'true', 'the later input is still unsaved');
      assert.equal(backend.state.wiki.revisions.find((item) => item.pageId === wikiPage.id && item.revision === 2).content.content[0].content[0].text, 'Start gesendet');
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 3 gespeichert.').waitFor();
      assert.equal(backend.state.wiki.revisions.find((item) => item.pageId === wikiPage.id && item.revision === 3).content.content[0].content[0].text, 'Start gesendet SPAETER');
      assert.equal(await state.getAttribute('data-dirty'), 'false');
    });
  });

  test('the comparison names a changed link target; lists keep a large start (F2, F4)', async () => {
    let wikiPage;
    const linked = (href) => ({ type: 'doc', content: [
      { type: 'paragraph', content: [{ type: 'text', text: 'Anleitung', marks: [{ type: 'link', attrs: { href } }] }] },
      { type: 'orderedList', attrs: { start: 10000 }, content: [{ type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Fortsetzung' }] }] }] },
    ] });
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Verweise', linked('https://one.example'), { publish: true });
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, revision: 2, title: 'Verweise', content: linked('https://two.example'), savedAt: '2026-10-21T09:00:00Z',
          savedBy: backend.ben.id, basedOn: 1, restoredFrom: null });
        wikiPage.draftRevision = 2;
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page) => {
      const list = page.getByTestId('wiki-content').locator('ol');
      await list.waitFor();
      assert.equal(await list.evaluate((element) => element.start), 10000);
      await page.getByTestId('wiki-history-open').click();
      const compare = page.getByTestId('wiki-history').getByTestId('wiki-compare');
      await compare.getByLabel('Von').selectOption('1');
      await compare.getByLabel('Bis').selectOption('2');
      await compare.getByRole('button', { name: 'Vergleichen' }).click();
      const changed = compare.getByTestId('wiki-compare-diff').locator('[data-op="changed"]');
      await changed.first().waitFor();
      assert.equal(await changed.count(), 1);
      assert.equal(await changed.locator('del').textContent(), 'AnleitungVerweise und Bilder: https://one.example');
      assert.equal(await changed.locator('ins').textContent(), 'AnleitungVerweise und Bilder: https://two.example');
    });
  });

  test('like the server: readers get no draft ETag and no image that only the draft shows', async () => {
    let wikiPage;
    let draftImage;
    await withApp({
      as: BEN,
      prepare: (backend) => {
        grant(backend, [backend.ben], ['wiki.read']);
        wikiPage = backend.addWikiPage(backend.anna, 'Bildseite', doc('Text'), { publish: true });
        draftImage = { id: '00000000-0000-4000-8000-000000009301', pageId: wikiPage.id, size: 3, content: Buffer.from([0xff, 0xd8, 0xff]), mediaType: 'image/jpeg' };
        backend.state.wiki.images.push(draftImage);
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page) => {
      await page.getByTestId('wiki-content').getByText('Text').waitFor();
      const answers = await page.evaluate(async ({ pageId, imageId }) => {
        const read = await fetch(`/api/v1/wiki/pages/${pageId}`);
        const image = await fetch(`/api/v1/wiki/images/${imageId}`);
        return { etag: read.headers.get('etag'), image: image.status };
      }, { pageId: wikiPage.id, imageId: draftImage.id });
      assert.deepEqual(answers, { etag: null, image: 404 });
    });
  });

  // TASK-094: language versions German and English.
  test('a reader in English gets the published German version with a notice, never an English draft', async () => {
    let wikiPage;
    await withApp({
      as: BEN,
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.ben], ['wiki.read']);
        wikiPage = backend.addWikiPage(backend.anna, 'Serviceanfrage ergänzen', doc('Kunde und Kontakt prüfen'), { publish: true });
        backend.addWikiLanguage(wikiPage, 'en', backend.anna, { title: 'Complete a service request', content: doc('Not finished yet') });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-content').getByText('Kunde und Kontakt prüfen').waitFor();
      assert.equal(await page.getByTestId('wiki-fallback').textContent(), 'You asked for English. Showing the German version.');
      assert.equal(await page.getByTestId('wiki-title').getAttribute('lang'), 'de');
      assert.equal(await page.getByText('Not finished yet').count(), 0, 'the English draft stays hidden');
      assert.equal(await page.getByTestId('wiki-languages').count(), 0, 'one readable version needs no choice');
      assert.equal(await page.getByTestId('wiki-tree').getByRole('link', { name: 'Serviceanfrage ergänzen' }).count(), 1);
      const read = backend.state.requests.findLast((item) => item.method === 'GET' && item.path.endsWith(`/wiki/pages/${wikiPage.id}`));
      assert.equal(read.query, '?language=en', 'the page is asked for in the language of the interface');
    });
  });

  test('a reader sees the published English version and learns that the German source changed since', async () => {
    let wikiPage;
    await withApp({
      as: BEN,
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.ben], ['wiki.read']);
        wikiPage = backend.addWikiPage(backend.anna, 'Rufbereitschaft', doc('Montag Anna'), { publish: true });
        backend.addWikiLanguage(wikiPage, 'en', backend.anna, { title: 'On call', content: doc('Monday Anna'), publish: true });
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, language: 'de', revision: 2, title: 'Rufbereitschaft', content: doc('Montag Ben'),
          savedAt: '2026-11-09T09:00:00Z', savedBy: backend.anna.id, basedOn: 1, restoredFrom: null, sourceRevision: null });
        Object.assign(wikiPage, { draftRevision: 2, publishedRevision: 2 });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page) => {
      await page.getByTestId('wiki-content').getByText('Monday Anna').waitFor();
      assert.equal(await page.getByTestId('wiki-fallback').count(), 0);
      assert.equal(await page.getByTestId('wiki-outdated').textContent(), 'The source version (German) has changed since this translation.');
      assert.equal(await page.getByTestId('wiki-tree').getByRole('link', { name: 'On call' }).count(), 1, 'the tree shows the English title');
      const languages = page.getByTestId('wiki-languages');
      await languages.locator('[data-language="de"]').click();
      await page.getByTestId('wiki-content').getByText('Montag Ben').waitFor();
      assert.equal(await languages.locator('[data-language="de"]').getAttribute('aria-pressed'), 'true');
      assert.equal(await page.getByTestId('wiki-outdated').count(), 0);
      assert.equal(await page.getByTestId('wiki-fallback').count(), 0, 'a chosen version is no fallback');
    });
  });

  test('an editor adds the English version as a draft that starts with the published German content', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Serviceanfrage ergänzen', doc('Kunde und Kontakt prüfen'), { publish: true });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      const languages = page.getByTestId('wiki-languages');
      await languages.getByRole('button', { name: 'Fassung Englisch hinzufügen' }).click();
      await page.getByText('Die Fassung Englisch ist als Entwurf angelegt. Sie beginnt mit dem Inhalt der Ausgangsfassung.').waitFor();
      const added = backend.state.requests.findLast((item) => item.method === 'POST' && item.path.endsWith('/languages'));
      assert.deepEqual(JSON.parse(added.body.toString('utf8')), { language: 'en' });
      assert.equal(await languages.locator('[data-language="en"]').getAttribute('aria-pressed'), 'true');
      assert.equal((await languages.locator('[data-language="en"]').textContent()).trim(), 'Englisch · Entwurf');
      assert.equal((await languages.locator('[data-language="de"]').textContent()).trim(), 'Deutsch · Ausgangsfassung');
      assert.equal(await page.getByTestId('wiki-languages').getByTestId('wiki-language-add').count(), 0);

      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      assert.equal(await editor.textContent(), 'Kunde und Kontakt prüfen');
      await page.getByLabel('Titel').fill('Complete a service request');
      await editor.click();
      await page.keyboard.press('Control+A');
      await page.keyboard.type('Check customer and contact');
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 2 gespeichert.').waitFor();
      const save = backend.state.requests.findLast((item) => item.method === 'PUT' && item.path.endsWith('/draft'));
      assert.equal(save.query, '?language=en');
      assert.equal(save.ifMatch, '"1"', 'the English draft has its own revisions');
      assert.equal(backend.state.wiki.revisions.find((item) => item.pageId === wikiPage.id && item.language === 'en' && item.revision === 2).sourceRevision, 1,
        'the translated source revision stays');
      assert.equal(wikiPage.draftRevision, 1, 'the German draft is untouched');

      await page.getByRole('button', { name: 'Bearbeiten beenden' }).click();
      await page.getByTestId('wiki-publish').click();
      const dialog = page.getByTestId('wiki-publish-dialog');
      await dialog.getByTestId('wiki-publish-language').getByText('Englisch').waitFor();
      await dialog.getByRole('button', { name: 'Revision 2 veröffentlichen' }).click();
      await page.getByText('Revision 2 ist veröffentlicht.').waitFor();
      assert.equal(wikiPage.variants.en.publishedRevision, 2);
      assert.equal(wikiPage.publishedRevision, 1);
    });
  });

  test('a changed source shows its changes to editors, and the aligned translation names the new source revision', async () => {
    let wikiPage;
    await withApp({
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Rufbereitschaft', doc('Montag Anna', 'Dienstag Ben'), { publish: true });
        backend.addWikiLanguage(wikiPage, 'en', backend.anna, { title: 'On call', content: doc('Monday Anna', 'Tuesday Ben'), publish: true });
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, language: 'de', revision: 2, title: 'Rufbereitschaft', content: doc('Montag Anna', 'Dienstag Lea'),
          savedAt: '2026-11-09T09:00:00Z', savedBy: backend.anna.id, basedOn: 1, restoredFrom: null, sourceRevision: null });
        Object.assign(wikiPage, { draftRevision: 2, publishedRevision: 2 });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      const changed = page.getByTestId('wiki-source-changed');
      await changed.getByText('This translation is based on revision 1 of the German version; revision 2 is published now.').waitFor();
      await changed.getByRole('button', { name: 'Show changes of the source version' }).click();
      const diff = page.getByTestId('wiki-source-diff');
      await diff.getByText('Dienstag Lea').waitFor();
      await diff.getByText('Dienstag Ben').waitFor();

      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      await editor.getByText('Tuesday Ben').click();
      await page.keyboard.press('End');
      for (let index = 0; index < 3; index++) await page.keyboard.press('Backspace');
      await page.keyboard.type('Lea');
      await page.getByTestId('wiki-align').getByText('Translation matches revision 2 of the source version').click();
      await page.getByTestId('wiki-save').click();
      await page.getByText('Draft saved as revision 2.').waitFor();
      const save = backend.state.requests.findLast((item) => item.method === 'PUT' && item.path.endsWith('/draft'));
      assert.equal(JSON.parse(save.body.toString('utf8')).sourceRevision, 2);
      assert.equal(await page.getByTestId('wiki-align').count(), 0, 'the draft no longer lags behind the source');
    });
  });

  // Fixes of the Codex review of TASK-094.
  test('F1: an English source that was never published is not named to readers of the German version', async () => {
    let wikiPage;
    await withApp({
      as: BEN,
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.ben], ['wiki.read']);
        wikiPage = backend.addWikiPage(backend.anna, 'Guide', doc('English draft'), { language: 'en' });
        backend.addWikiLanguage(wikiPage, 'de', backend.anna, { title: 'Anleitung', content: doc('Deutsch veröffentlicht'), publish: true });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      await page.getByTestId('wiki-content').getByText('Deutsch veröffentlicht').waitFor();
      assert.equal(await page.getByTestId('wiki-fallback').textContent(), 'You asked for English. Showing the German version.');
      assert.equal(await page.getByTestId('wiki-outdated').count(), 0);
      assert.equal(await page.getByTestId('wiki-languages').count(), 0);
      const answer = backend.wikiCall(backend.ben, 'GET', `/wiki/pages/${wikiPage.id}`, {}, { query: 'language=de' }).body;
      assert.equal(answer.sourceLanguage, null);
      assert.equal(answer.translation, null);
      assert.equal(answer.fallback, false);
    });
  });

  test('F2: the open page follows a change of the interface language, an open editor keeps its version and input', async () => {
    let wikiPage;
    await withApp({
      locale: 'en-US',
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Rufbereitschaft', doc('Montag Anna'), { publish: true });
        backend.addWikiLanguage(wikiPage, 'en', backend.anna, { title: 'On call', content: doc('Monday Anna'), publish: true });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page, backend) => {
      const content = page.getByTestId('wiki-content');
      await content.getByText('Monday Anna').waitFor();
      await page.locator('#language').selectOption('de');
      await content.getByText('Montag Anna').waitFor();
      assert.equal(await page.getByTestId('wiki-title').getAttribute('lang'), 'de');
      assert.equal(await page.getByTestId('wiki-fallback').count(), 0);
      await page.locator('#language').selectOption('en');
      await content.getByText('Monday Anna').waitFor();

      await page.getByTestId('wiki-edit-open').click();
      const editor = page.getByTestId('wiki-editor');
      await editor.getByText('Monday Anna').click();
      await page.keyboard.press('End');
      await page.keyboard.type(' and Ben');
      await page.locator('#language').selectOption('de');
      await page.getByTestId('wiki-save-state').getByText('Ungespeicherte Änderungen').waitFor();
      assert.equal(await editor.textContent(), 'Monday Anna and Ben', 'the English input stays in the editor');
      await page.getByTestId('wiki-save').click();
      await page.getByText('Entwurf als Revision 2 gespeichert.').waitFor();
      const save = backend.state.requests.findLast((item) => item.method === 'PUT' && item.path.endsWith('/draft'));
      assert.equal(save.query, '?language=en', 'saved into the version that was open');
      await page.getByRole('button', { name: 'Bearbeiten beenden' }).click();
      await content.getByText('Montag Anna').waitFor();
    });
  });

  test('F3, F4: the source comparison names a changed title and is dropped when the source is published again', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
        wikiPage = backend.addWikiPage(backend.anna, 'Alter Titel', doc('Gleicher Inhalt'), { publish: true });
        backend.addWikiLanguage(wikiPage, 'en', backend.anna, { title: 'Old title', content: doc('Same content'), publish: true });
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, language: 'en', revision: 2, title: 'Old title', content: doc('Same content again'),
          savedAt: '2026-11-09T09:00:00Z', savedBy: backend.anna.id, basedOn: 1, restoredFrom: null, sourceRevision: 1 });
        wikiPage.variants.en.draftRevision = 2;
        backend.state.wiki.revisions.push({ pageId: wikiPage.id, language: 'de', revision: 2, title: 'Neuer Titel', content: doc('Gleicher Inhalt'),
          savedAt: '2026-11-09T09:00:00Z', savedBy: backend.anna.id, basedOn: 1, restoredFrom: null, sourceRevision: null });
        Object.assign(wikiPage, { draftRevision: 2, publishedRevision: 2 });
      },
      hash: (backend) => `${wikiHash(backend, wikiPage)}`,
    }, async (page, backend) => {
      await page.getByTestId('wiki-languages').locator('[data-language="en"]').click();
      const changed = page.getByTestId('wiki-source-changed');
      await changed.getByText('Diese Übersetzung beruht auf Revision 1 der Fassung Deutsch; veröffentlicht ist inzwischen Revision 2.').waitFor();
      await changed.getByRole('button', { name: 'Änderungen der Ausgangsfassung zeigen' }).click();
      const title = page.getByTestId('wiki-source-title');
      await title.getByText('Alter Titel').waitFor();
      await title.getByText('Neuer Titel').waitFor();
      assert.equal(await title.locator('dd').first().getAttribute('lang'), 'de');
      assert.equal(await page.getByTestId('wiki-source-diff-none').textContent(), 'Der Inhalt ist unverändert, nur der Titel wurde geändert.');
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'wiki-source-result', 'H2: the focus moves to the result');

      // Meanwhile the source is published a third time; publishing the English draft brings the new pair.
      backend.state.wiki.revisions.push({ pageId: wikiPage.id, language: 'de', revision: 3, title: 'Neuer Titel', content: doc('Gleicher Inhalt', 'Neuer Absatz'),
        savedAt: '2026-11-09T10:00:00Z', savedBy: backend.ben.id, basedOn: 2, restoredFrom: null, sourceRevision: null });
      Object.assign(wikiPage, { draftRevision: 3, publishedRevision: 3 });
      await page.getByTestId('wiki-publish').click();
      await page.getByTestId('wiki-publish-dialog').getByRole('button', { name: 'Revision 2 veröffentlichen' }).click();
      await changed.getByText('Diese Übersetzung beruht auf Revision 1 der Fassung Deutsch; veröffentlicht ist inzwischen Revision 3.').waitFor();
      assert.equal(await page.getByTestId('wiki-source-result').count(), 0, 'the comparison of revisions 1 and 2 is dropped');
      await changed.getByRole('button', { name: 'Änderungen der Ausgangsfassung zeigen' }).click();
      await page.getByTestId('wiki-source-diff').getByText('Neuer Absatz').waitFor();
      assert.equal(await page.getByTestId('wiki-source-diff').locator('ins').first().getAttribute('lang'), 'de');
    });
  });

  test('H2: after adding a version the focus is on its button', async () => {
    let wikiPage;
    await withApp({
      prepare: (backend) => {
        grant(backend, [backend.anna], ['wiki.edit']);
        wikiPage = backend.addWikiPage(backend.anna, 'Fokus', doc('Text'), { publish: true });
      },
      hash: (backend) => wikiHash(backend, wikiPage),
    }, async (page) => {
      await page.getByTestId('wiki-language-add').click();
      await page.getByText('Die Fassung Englisch ist als Entwurf angelegt.', { exact: false }).waitFor();
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-language') === 'en');
    });
  });

  test('H1: the stand in answers like the server for a missing language and a withdrawal of an archived page', () => {
    const backend = createBackend();
    grant(backend, [backend.ben], ['wiki.read']);
    grant(backend, [backend.anna], ['wiki.edit', 'wiki.publish']);
    const wikiPage = backend.addWikiPage(backend.anna, 'Archiv', doc('Text'), { publish: true });
    const path = `/wiki/pages/${wikiPage.id}`;
    const missing = backend.wikiCall(backend.ben, 'POST', `${path}/languages`, {});
    assert.equal(missing.status, 400);
    assert.deepEqual(missing.body.violations, ['language.required']);
    assert.equal(backend.wikiCall(backend.ben, 'POST', `${path}/languages`, { language: 'en' }).status, 403);
    backend.addWikiLanguage(wikiPage, 'en', backend.anna, { publish: true });
    wikiPage.archived = true;
    for (const query of ['language=de', 'language=en']) {
      const answer = backend.wikiCall(backend.anna, 'POST', `${path}/withdraw`, {}, { query });
      assert.equal(answer.status, 409, query);
      assert.equal(answer.body.code, 'wiki.page_archived', query);
    }
  });
});
