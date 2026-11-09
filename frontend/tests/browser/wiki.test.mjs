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

async function withApp({ prepare, as = ANNA, hash }, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${typeof hash === 'function' ? hash(backend) : hash ?? '#/wiki'}`,
    { setup: (context, page) => backend.install(context, page, { as }) });
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
});
