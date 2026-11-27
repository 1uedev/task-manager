import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-127: the AI review of a requirement and the AI revision of a wiki draft against the in-memory backend that follows the
// contract. A finding or a revised text goes only into the editor and is saved by the normal save; the AI never approves,
// changes a status or publishes, and every text of the model stays text.

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
const OLLAMA = '00000000-0000-4000-8000-0000000127a1';
const LLAMA = '00000000-0000-4000-8000-0000000127b1';
const ALL = ['criteria', 'description', 'owner', 'priority', 'title'];

function withModel(backend) {
  backend.state.ai.providers.push({ id: OLLAMA, kind: 'ollama', name: 'Ollama im Haus', baseUrl: 'http://ollama:11434', apiKey: null, caCertificate: null,
    dataPath: 'local', enabled: true, version: 1, updatedAt: '2026-11-27T08:00:00Z', updatedBy: backend.anna.id, removed: false });
  backend.state.ai.models.push({ id: LLAMA, providerId: OLLAMA, name: 'Llama', modelKey: 'llama3.2', contextTokens: 32000, outputTokens: 2000, enabled: true,
    version: 1, updatedAt: '2026-11-27T08:00:00Z', removed: false });
  for (const useCase of ['requirement_review', 'wiki_revision']) {
    backend.state.ai.assignments.set(useCase, { useCase, modelId: LLAMA, enabled: true, version: 1, updatedAt: '2026-11-27T08:00:00Z' });
  }
}

function grant(backend, accounts, actions, { read = ALL, write = ALL } = {}) {
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000001270${String(backend.state.groups.length).padStart(2, '0')}`, name: `KI ${actions.join(' ')}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })),
    fields: [...read.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'read', effect: 'allow' })),
      ...write.map((field) => ({ resourceType: 'requirement', field, projectId: null, operation: 'write', effect: 'allow' }))] });
}

async function withApp({ prepare, as = BEN, hash }, body) {
  const backend = createBackend();
  const prepared = prepare(backend);
  const session = await open(browser, `${base()}${hash(backend, prepared)}`, { setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend, prepared);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const finding = (kind, location, suggestion) => ({ kind, location, reason: 'Nicht prüfbar.', suggestion, sources: ['S1'] });
const lastWrite = (backend, pattern) => backend.state.requests.findLast((item) => item.method !== 'GET' && pattern.test(item.path));
const bodyOf = (request) => JSON.parse(request.body.toString('utf8'));
const paragraph = (...content) => ({ type: 'paragraph', content });
const text = (value, marks) => (marks ? { type: 'text', text: value, marks } : { type: 'text', text: value });
const heading = (value) => ({ type: 'heading', attrs: { level: 2 }, content: [text(value)] });

describe('AI review of a requirement', () => {
  function prepareRequirement(backend, { status = 'draft', actions = ['requirement.read', 'requirement.edit', 'ai.use'] } = {}) {
    withModel(backend);
    grant(backend, [backend.ben], actions);
    grant(backend, [backend.anna], ['requirement.read', 'requirement.approve']);
    const item = backend.addRequirement(backend.ben, 'Kunden erhalten zeitnah eine Antwort', { status, approvedBy: backend.anna,
      criteria: ['Die erste Antwort kommt bald.', 'Der Status ist sichtbar.'], description: { type: 'doc', content: [paragraph(text('Der Support antwortet.'))] } });
    backend.state.ai.reviewAnswer = { findings: [finding('unclear', 'zeitnah', 'innerhalb von 5 Minuten'),
      finding('missing_criterion', 'Abnahmekriterien', 'Die Antwort nennt die Ticketnummer.'),
      finding('other', 'Der', '<img src=x onerror="window.__xss=1">'),
      { ...finding('not_measurable', 'Kriterium K2', null), sources: ['S4'] }] };
    return { item };
  }
  const detail = (backend, { item }) => `#/requirements/${backend.project.id}/${item.id}`;

  test('rule 1: the findings of the shown revision go into the editor and are saved by the normal save only', async () => {
    await withApp({ prepare: (backend) => prepareRequirement(backend, { status: 'approved' }), hash: detail }, async (page, backend, { item }) => {
      const panel = page.getByTestId('requirement-ai');
      await panel.getByText('Prüft die gezeigte Revision 1').waitFor();
      await panel.getByTestId('requirement-ai-start').click();
      const findings = panel.getByTestId('requirement-ai-finding');
      await findings.first().waitFor();
      assert.equal(await findings.count(), 4);
      const context = backend.state.ai.contexts.at(-1);
      assert.deepEqual(context.sources.map((source) => `${source.id}:${source.part}`), ['S1:title', 'S2:description', 'S3:criterion', 'S4:criterion']);
      assert.ok(bodyOf(lastWrite(backend, /\/ai\/review$/)).revision === 1, 'exactly the shown revision');
      // A15: the proposal of the model is text, nothing of it runs.
      await findings.nth(2).getByText('Vorschlag: <img src=x onerror="window.__xss=1">').waitFor();
      assert.equal(await page.evaluate(() => window.__xss), undefined);
      assert.equal(await findings.nth(2).getByTestId('requirement-ai-take').count(), 0, 'an ambiguous passage is offered for copying only');
      await findings.nth(2).getByTestId('requirement-ai-copy-only').waitFor();
      await findings.nth(3).getByText('Quellen: S4: Kriterium K2').waitFor();
      assert.equal(item.status, 'approved', 'the review changes no status');

      await findings.first().getByTestId('requirement-ai-take').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByText('Der Vorschlag der KI steht im Editor').waitFor();
      assert.equal(await edit.getByLabel('Titel').inputValue(), 'Kunden erhalten innerhalb von 5 Minuten eine Antwort');
      await edit.getByTestId('requirement-new-revision-hint').waitFor();
      assert.equal(await edit.getByTestId('requirement-save-state').getAttribute('data-dirty'), 'true');
      assert.equal(lastWrite(backend, /\/requirements\/[^/]+$/), undefined, 'nothing is saved before the person saves');
      await edit.getByTestId('requirement-save').click();
      await edit.getByText('Revision 2').first().waitFor();
      assert.equal(item.revision, 2, 'A04: the save of an approved requirement opens the next revision');
      assert.equal(bodyOf(lastWrite(backend, /\/requirements\/[^/]+$/)).title, 'Kunden erhalten innerhalb von 5 Minuten eine Antwort');
    });
  });

  test('a missing criterion becomes a new criterion, and a changed requirement makes the review invalid before the take over', async () => {
    await withApp({ prepare: (backend) => prepareRequirement(backend), hash: detail }, async (page, backend, { item }) => {
      const panel = page.getByTestId('requirement-ai');
      await panel.getByTestId('requirement-ai-start').click();
      const findings = panel.getByTestId('requirement-ai-finding');
      await findings.first().waitFor();
      await findings.nth(1).getByTestId('requirement-ai-take').click();
      const edit = page.getByTestId('requirement-edit');
      await edit.getByText('Der Vorschlag der KI steht im Editor').waitFor();
      const criteria = edit.getByTestId('requirement-criterion');
      assert.equal(await criteria.count(), 3);
      assert.equal(await criteria.nth(2).getAttribute('data-criterion'), 'new');
      assert.equal(await criteria.nth(2).locator('textarea').inputValue(), 'Die Antwort nennt die Ticketnummer.');
      page.once('dialog', (dialog) => void dialog.accept());
      await page.getByRole('button', { name: 'Bearbeitung schließen' }).click();

      // Someone else saved meanwhile: the server no longer confirms the suggestion, nothing goes into the editor (A09).
      await panel.getByTestId('requirement-ai-start').click();
      await findings.first().waitFor();
      backend.state.requirements.revisions.find((entry) => entry.requirementId === item.id).title = 'Kunden erhalten zeitnah eine Rückmeldung';
      await findings.first().getByTestId('requirement-ai-take').click();
      await panel.getByText('Der Vorschlag gilt nicht mehr: Die Quelle hat sich geändert.').waitFor();
      assert.equal(await page.getByTestId('requirement-edit').count(), 0);
    });
  });

  test('without ai.use or the right to edit no review or no take over is offered', async () => {
    await withApp({ prepare: (backend) => prepareRequirement(backend, { actions: ['requirement.read'] }), hash: detail }, async (page) => {
      await page.getByTestId('requirement-content').waitFor();
      assert.equal(await page.getByTestId('requirement-ai').count(), 0);
    });
    await withApp({ prepare: (backend) => prepareRequirement(backend, { actions: ['requirement.read', 'ai.use'] }), hash: detail }, async (page) => {
      const panel = page.getByTestId('requirement-ai');
      await panel.getByTestId('requirement-ai-start').click();
      await panel.getByTestId('requirement-ai-finding').first().waitFor();
      assert.equal(await panel.getByTestId('requirement-ai-take').count(), 0, 'a reader may copy only');
    });
  });
});

describe('AI revision of a wiki draft', () => {
  function prepareWiki(backend, { actions = ['wiki.read', 'wiki.edit', 'ai.use'] } = {}) {
    withModel(backend);
    grant(backend, [backend.ben], actions, { read: [], write: [] });
    const target = backend.addWikiPage(backend.ben, 'Anleitung', { type: 'doc', content: [paragraph(text('Ziel'))] });
    const page = backend.addWikiPage(backend.ben, 'Ablauf', { type: 'doc', content: [heading('Einleitung'), paragraph(text('Eine lange Einleitung.')),
      heading('Ablauf'), paragraph(text('Erst öffnen, dann prüfen, siehe '), text('Anleitung', [{ type: 'link', attrs: { href: `wiki:${target.id}` } }])),
      heading('Ende'), paragraph(text('Schluss'))] });
    return { page, target };
  }
  const pageHash = (backend, { page }) => `#/wiki/${backend.project.id}/${page.id}`;

  test('rule 2: a section is revised, shown as a comparison and taken into the editor only; saving and publishing stay separate', async () => {
    await withApp({ prepare: (backend) => prepareWiki(backend), hash: pageHash }, async (page, backend, prepared) => {
      await page.getByTestId('wiki-edit-open').click();
      const panel = page.getByTestId('wiki-ai');
      await panel.getByLabel('Bereich').selectOption({ label: 'Abschnitt „Ablauf“' });
      await panel.getByLabel('Ziel', { exact: true }).selectOption({ label: 'Kürzer' });
      await panel.getByTestId('wiki-ai-start').click();
      const result = panel.getByTestId('wiki-ai-result');
      await result.waitFor();
      const start = bodyOf(lastWrite(backend, /\/ai\/revision$/));
      assert.deepEqual([start.revision, start.section, start.goal], [1, 2, 'shorter']);
      await result.getByText('Vergleich vorher und nachher: Ablauf').waitFor();
      await result.getByTestId('wiki-ai-diff').getByText('Kurz: Erst öffnen, dann prüfen, siehe Anleitung').waitFor();
      await result.getByText('Gekürzt.').waitFor();
      assert.equal(await page.getByTestId('wiki-save-state').getAttribute('data-dirty'), 'false', 'nothing changed before the take over');

      await result.getByTestId('wiki-ai-take').click();
      await panel.getByText('Die Überarbeitung steht im Editor und ist noch nicht gespeichert.').waitFor();
      await page.getByTestId('wiki-save-state').getByText('Ungespeicherte Änderungen').waitFor();
      assert.equal(lastWrite(backend, /\/draft$/), undefined, 'nothing is saved before the person saves');
      await page.getByTestId('wiki-save').click();
      await page.getByTestId('wiki-save-state').getByText('Revision 2', { exact: false }).waitFor();
      const saved = bodyOf(lastWrite(backend, /\/draft$/)).content;
      const json = JSON.stringify(saved);
      assert.ok(json.includes('Kurz: Erst öffnen') && json.includes(`wiki:${prepared.target.id}`) && json.includes('Eine lange Einleitung.'), json);
      assert.equal(prepared.page.publishedRevision, null, 'publishing stays a separate action');
    });
  });

  test('unsaved input blocks the start and the take over', async () => {
    await withApp({ prepare: (backend) => prepareWiki(backend), hash: pageHash }, async (page) => {
      await page.getByTestId('wiki-edit-open').click();
      const panel = page.getByTestId('wiki-ai');
      await panel.getByTestId('wiki-ai-start').click();
      await panel.getByTestId('wiki-ai-result').waitFor();
      await page.locator('.ProseMirror').click();
      await page.keyboard.type(' Eigene Eingabe');
      await panel.getByTestId('wiki-ai-dirty').waitFor();
      assert.equal(await panel.getByTestId('wiki-ai-take').isDisabled(), true);
      assert.equal(await panel.getByTestId('wiki-ai-start').isDisabled(), true);
      assert.ok((await page.locator('.ProseMirror').textContent()).includes('Eigene Eingabe'), 'the own input stays');
    });
  });

  test('a section someone changed meanwhile is not taken over', async () => {
    await withApp({ prepare: (backend) => prepareWiki(backend), hash: pageHash }, async (page, backend, prepared) => {
      await page.getByTestId('wiki-edit-open').click();
      const panel = page.getByTestId('wiki-ai');
      await panel.getByLabel('Bereich').selectOption({ label: 'Abschnitt „Einleitung“' });
      await panel.getByTestId('wiki-ai-start').click();
      await panel.getByTestId('wiki-ai-result').waitFor();
      // Someone else changed the section in the stored draft: the server no longer confirms the result (A09).
      const revision = backend.state.wiki.revisions.find((entry) => entry.pageId === prepared.page.id);
      revision.content = { ...revision.content, content: [heading('Einleitung'), paragraph(text('Neu geschrieben.')), ...revision.content.content.slice(2)] };
      await panel.getByTestId('wiki-ai-take').click();
      await panel.getByText('Die Quelle hat sich geändert.').waitFor();
      assert.equal(await panel.getByTestId('wiki-ai-result').count(), 0);
      assert.ok(!(await page.locator('.ProseMirror').textContent()).includes('Kurz:'), 'nothing went into the editor');
      assert.equal(await page.getByTestId('wiki-save-state').getAttribute('data-dirty'), 'false');
    });
  });

  test('a refused answer and a script in the answer stay without effect', async () => {
    await withApp({ prepare: (backend) => {
      const prepared = prepareWiki(backend);
      backend.state.ai.aiFail = 'ai.placeholders_changed';
      return prepared;
    }, hash: pageHash }, async (page, backend) => {
      await page.getByTestId('wiki-edit-open').click();
      const panel = page.getByTestId('wiki-ai');
      await panel.getByTestId('wiki-ai-start').click();
      await panel.getByText('Kein Vorschlag: Die Antwort hat Bilder, Links oder andere Blöcke nicht unverändert zurückgegeben.').waitFor();
      assert.equal(await panel.getByTestId('wiki-ai-result').count(), 0);

      backend.state.ai.aiFail = null;
      backend.state.ai.wikiRevise = (blocks) => [...blocks, paragraph(text('<img src=x onerror="window.__xss=1">'))];
      await panel.getByTestId('wiki-ai-start').click();
      await panel.getByTestId('wiki-ai-result').getByText('<img src=x onerror="window.__xss=1">').waitFor();
      await panel.getByTestId('wiki-ai-take').click();
      await page.locator('.ProseMirror').getByText('<img src=x onerror="window.__xss=1">').waitFor();
      assert.equal(await page.evaluate(() => window.__xss), undefined, 'A15: nothing of the answer runs');
    });
  });

  test('readers without wiki.edit and people without ai.use see no revision', async () => {
    await withApp({ prepare: (backend) => prepareWiki(backend, { actions: ['wiki.read', 'wiki.edit'] }), hash: pageHash }, async (page) => {
      await page.getByTestId('wiki-edit-open').click();
      await page.getByTestId('wiki-edit').waitFor();
      assert.equal(await page.getByTestId('wiki-ai').count(), 0);
    });
  });
});
