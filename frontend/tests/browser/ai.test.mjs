import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-125 and TASK-129: the page "KI" for ai.manage with accesses, models, assignment, skills with trial, limits and the record, against
// the in-memory backend that follows the contract.

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
const OLLAMA = '00000000-0000-4000-8000-0000000092a1';
const LLAMA = '00000000-0000-4000-8000-0000000092b1';

const withAiManage = (backend) => {
  backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000092f1', name: 'KI', members: [backend.anna.id], actions: [{ action: 'ai.manage', projectId: null }], fields: [] });
};
const withOllama = (backend) => {
  withAiManage(backend);
  backend.state.ai.providers.push({ id: OLLAMA, kind: 'ollama', name: 'Ollama im Haus', baseUrl: 'http://ollama:11434', apiKey: null, caCertificate: null,
    dataPath: 'local', enabled: true, version: 1, updatedAt: '2026-11-17T08:00:00Z', updatedBy: backend.anna.id, removed: false });
  backend.state.ai.models.push({ id: LLAMA, providerId: OLLAMA, name: 'Llama', modelKey: 'llama3.2', contextTokens: 32000, outputTokens: 2000, enabled: true,
    version: 1, updatedAt: '2026-11-17T08:00:00Z', removed: false });
  backend.state.ai.assignments.set('ticket_summary', { useCase: 'ticket_summary', modelId: LLAMA, enabled: true, version: 1, updatedAt: '2026-11-17T08:00:00Z' });
};

async function withApp({ prepare = withAiManage, as = ANNA, hash = '#/ai' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { setup: (context, page) => backend.install(context, page, { as }) });
  session.page.on('dialog', (dialog) => void dialog.accept());
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('AI administration', () => {
  test('without ai.manage there is neither entry nor page', async () => {
    await withApp({ as: BEN, prepare: null }, async (page) => {
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'KI', exact: true }).count(), 0);
      await page.getByRole('heading', { level: 1, name: 'KI' }).waitFor();
      assert.equal(await page.getByTestId('ai-accesses').count(), 0);
    });
  });

  test('an access with its key stays write only, a model is assigned and the use case becomes available', async () => {
    await withApp({}, async (page, backend) => {
      await page.getByRole('navigation').getByRole('link', { name: 'KI', exact: true }).waitFor();
      const accesses = page.getByTestId('ai-accesses');
      await accesses.getByText('Noch kein Anbieter eingetragen.').waitFor();
      await accesses.getByRole('button', { name: 'Anbieter hinzufügen' }).click();
      const form = page.getByTestId('ai-provider-form');
      await form.getByLabel('Name').fill('OpenAI Firma');
      await form.getByLabel('API Schlüssel').fill('sk-synthetic-1');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('OpenAI Firma angelegt.').waitFor();
      const row = accesses.locator('[data-provider]').filter({ hasText: 'OpenAI Firma' });
      await row.getByText(/api\.openai\.com · extern · Schlüssel hinterlegt/).waitFor();
      assert.equal(await page.getByText('sk-synthetic-1').count(), 0, 'the key never comes back');

      await row.getByRole('button', { name: 'OpenAI Firma prüfen' }).click();
      await page.getByText('OpenAI Firma ist erreichbar und meldet 3 Modelle.').waitFor();

      await row.getByRole('button', { name: 'Modell für OpenAI Firma hinzufügen' }).click();
      const modelForm = page.getByTestId('ai-model-form');
      await modelForm.getByLabel('Name').fill('GPT Test');
      await modelForm.getByLabel('Höchste Ausgabe in Tokens').fill('50000');
      await modelForm.getByLabel('Modellkennung beim Anbieter').fill('gpt-test');
      await modelForm.getByLabel('Kontextfenster in Tokens').fill('32000');
      await modelForm.getByRole('button', { name: 'Speichern' }).click();
      await modelForm.getByText('Zwischen 100 und 100000 und kleiner als das Kontextfenster.').waitFor();
      await modelForm.getByLabel('Höchste Ausgabe in Tokens').fill('2000');
      await modelForm.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('GPT Test angelegt.').waitFor();

      const useCase = page.locator('[data-use-case="reply_draft"]');
      await useCase.getByText('Kein Modell zugeordnet.').waitFor();
      await useCase.getByLabel('Modell für Antwortentwurf').selectOption({ label: 'GPT Test (OpenAI Firma)' });
      await page.getByText('Zuordnung für Antwortentwurf gespeichert.').waitFor();
      await useCase.getByText('Ausgeschaltet.').waitFor();
      await useCase.getByLabel('Antwortentwurf eingeschaltet').click();
      await useCase.getByText('Bereit über OpenAI Firma. Daten gehen an einen externen Anbieter.').waitFor();
      assert.equal(backend.state.ai.assignments.get('reply_draft').enabled, true);
    });
  });

  test('a local Ollama access over http needs the local data path', async () => {
    await withApp({}, async (page) => {
      await page.getByTestId('ai-accesses').getByRole('button', { name: 'Anbieter hinzufügen' }).click();
      const form = page.getByTestId('ai-provider-form');
      await form.getByLabel('Anbieter').selectOption('ollama');
      await form.getByLabel('Name').fill('Ollama');
      await form.getByLabel('Adresse').fill('http://ollama:11434');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await form.getByText('Bitte eine Adresse mit https eingeben. http geht nur mit lokalem Datenweg und für Hosts, die der Betrieb freigegeben hat.').waitFor();
      await form.getByLabel('Datenweg').selectOption('local');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Ollama angelegt.').waitFor();
      await page.getByTestId('ai-provider-list').getByText(/http:\/\/ollama:11434 · lokal · ohne Schlüssel/).waitFor();
    });
  });

  test('a skill draft applies only after activation and the trial shows the checked suggestion as text', async () => {
    await withApp({ prepare: withOllama }, async (page, backend) => {
      const skills = page.getByTestId('ai-skills');
      await skills.getByTestId('ai-skill-applied').getByText('Es gilt der mitgelieferte Skill builtin-1.').waitFor();
      await skills.getByLabel('Anweisungen (Markdown)').fill('Fasse knapp zusammen.');
      await skills.getByLabel('Name').fill('Knapp');
      await skills.getByRole('button', { name: 'Als Entwurf speichern' }).click();
      await page.getByText('Entwurf gespeichert. Es gilt weiter builtin-1.').waitFor();

      const trial = page.getByTestId('ai-trial');
      await trial.getByText('Läuft mit dem gespeicherten Entwurf über das zugeordnete Modell.').waitFor();
      await trial.getByLabel('Beispieltext').fill('Fehler DEMO-42 <script>alert(1)</script>');
      await trial.getByRole('button', { name: 'Probelauf starten' }).click();
      const result = page.getByTestId('ai-trial-result');
      await result.getByText('Fertig mit draft-1.1 über llama3.2. Das Ergebnis ist ein Vorschlag:').waitFor();
      await result.getByText(/Bericht bricht mit DEMO-42 ab/).waitFor();
      assert.equal(await page.locator('script', { hasText: 'alert(1)' }).count(), 0);

      await skills.getByRole('button', { name: 'Entwurf aktivieren' }).click();
      await page.getByText('v1 ist jetzt aktiv.').waitFor();
      await skills.getByTestId('ai-skill-applied').getByText(/Es gilt v1/).waitFor();
      await skills.getByRole('button', { name: 'Mitgelieferten Skill verwenden' }).click();
      await page.getByText('Der mitgelieferte Skill builtin-1 gilt wieder.').waitFor();

      backend.state.ai.aiFail = 'ai.timeout';
      await trial.getByRole('button', { name: 'Probelauf starten' }).click();
      await result.getByText('Kein Ergebnis: Der Anbieter hat nicht rechtzeitig geantwortet.').waitFor();

      const records = page.getByTestId('ai-records');
      await records.getByRole('button', { name: 'Aktualisieren' }).click();
      const list = records.getByTestId('ai-run-list');
      await list.getByText('Der Anbieter hat nicht rechtzeitig geantwortet.').waitFor();
      await list.getByText('Vorschlag erstellt').waitFor();
      assert.equal(await records.getByText('DEMO-42').count(), 0, 'the record holds no content');
    });
  });

  test('a discarded draft never appears in the list of versions, in German and in English', async () => {
    for (const { locale, save, discard, draftState, discardedState, content, name } of [
      { locale: 'de-DE', save: 'Als Entwurf speichern', discard: 'Entwurf verwerfen', draftState: 'Entwurf', discardedState: 'verworfen', content: 'Anweisungen (Markdown)', name: 'Name' },
      { locale: 'en-US', save: 'Save as draft', discard: 'Discard draft', draftState: 'draft', discardedState: 'discarded', content: 'Instructions (Markdown)', name: 'Name' },
    ]) {
      const session = await open(browser, `${base()}#/ai`, { locale, setup: (context, page) => {
        const backend = createBackend();
        withOllama(backend);
        return backend.install(context, page, { as: ANNA });
      } });
      session.page.on('dialog', (dialog) => void dialog.accept());
      try {
        const page = session.page;
        await page.waitForSelector('.shell main h1');
        const skills = page.getByTestId('ai-skills');
        await skills.getByLabel(content).fill('Fasse knapp zusammen.');
        await skills.getByLabel(name).fill('Knapp');
        await skills.getByRole('button', { name: save }).click();
        const versions = skills.getByTestId('ai-skill-versions');
        await versions.waitFor({ state: 'attached' });
        assert.match(await versions.textContent(), new RegExp(draftState, 'i'), locale);
        await skills.getByRole('button', { name: discard }).click();
        await versions.waitFor({ state: 'detached' });
        assert.equal(await skills.getByText(discardedState, { exact: true }).count(), 0, `${locale}: the discarded state is not listed`);
        assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
      } finally {
        await session.context.close();
      }
    }
  });

  test('an access whose address breaks today\'s rules is named in the use case, in German and in English', async () => {
    for (const { locale, text } of [
      { locale: 'de-DE', text: 'Die Adresse des Anbieters ist nach den heutigen Regeln nicht erlaubt.' },
      { locale: 'en-US', text: 'The address of the provider is not allowed under today\'s rules.' },
    ]) {
      const session = await open(browser, `${base()}#/ai`, { locale, setup: (context, page) => {
        const backend = createBackend();
        withOllama(backend);
        backend.state.ai.providers[0].addressRefused = true;
        return backend.install(context, page, { as: ANNA });
      } });
      try {
        const page = session.page;
        await page.waitForSelector('.shell main h1');
        await page.locator('[data-use-case="ticket_summary"]').getByTestId('ai-availability').getByText(text, { exact: false }).waitFor();
      } finally {
        await session.context.close();
      }
    }
  });

  test('a stale skill draft is refused and reloaded, limits and usage are shown', async () => {
    await withApp({ prepare: withOllama }, async (page, backend) => {
      const skills = page.getByTestId('ai-skills');
      await skills.getByTestId('ai-skill-applied').waitFor();
      backend.state.ai.skills.set('ticket_summary/de', { versions: [], draft: { number: 4, round: 1, name: 'Fremd', content: 'Von jemand anderem.', updatedAt: '2026-11-17T09:00:00Z' }, number: 4 });
      await skills.getByLabel('Anweisungen (Markdown)').fill('Eigene Fassung.');
      await skills.getByRole('button', { name: 'Als Entwurf speichern' }).click();
      await page.getByText('Jemand hat den Skill inzwischen geändert. Der aktuelle Stand ist geladen.').waitFor();
      await skills.getByText('Entwurf 4 liegt vor', { exact: false }).waitFor();

      const limits = page.getByTestId('ai-limits');
      await limits.getByLabel('Ausführungen je Person und Tag').fill('0');
      await limits.getByRole('button', { name: 'Grenzen speichern' }).click();
      await page.getByText('Grenzen gespeichert.').waitFor();
      await page.getByTestId('ai-trial').getByLabel('Beispieltext').fill('Probe');
      await page.getByTestId('ai-trial').getByRole('button', { name: 'Probelauf starten' }).click();
      await page.getByText('Deine Tagesgrenze ist erreicht.').waitFor();
      await limits.getByText('Noch keine Nutzung.').waitFor();

      // TASK-129, F12: the refused start is in the record, without model and skill.
      const records = page.getByTestId('ai-records');
      await records.getByRole('button', { name: 'Aktualisieren' }).click();
      await records.getByTestId('ai-run-list').getByText('Abgelehnt: Deine Tagesgrenze ist erreicht.').waitFor();
      await records.getByTestId('ai-run-list').getByText('Kein Modell').waitFor();
    });
  });

  test('a running trial can be cancelled and a suggestion that no longer counts shows its reason', async () => {
    await withApp({ prepare: withOllama }, async (page, backend) => {
      const trial = page.getByTestId('ai-trial');
      const result = page.getByTestId('ai-trial-result');
      backend.state.ai.aiHold = true;
      await trial.getByLabel('Beispieltext').fill('Fehler DEMO-42');
      await trial.getByRole('button', { name: 'Probelauf starten' }).click();
      await result.getByText('Läuft …').waitFor();
      await trial.getByRole('button', { name: 'Abbrechen' }).click();
      await result.getByText('Kein Ergebnis: Abgebrochen.').waitFor();
      assert.equal(backend.state.ai.runs[0].state, 'cancelled');
      await trial.getByRole('button', { name: 'Abbrechen' }).waitFor({ state: 'detached' });

      backend.state.ai.aiHold = false;
      backend.state.ai.aiInvalid = 'ai.configuration_changed';
      await trial.getByRole('button', { name: 'Probelauf starten' }).click();
      await result.getByText('Der Vorschlag gilt nicht mehr: Zuordnung, Anbieter, Modell oder Skill haben sich seit dem Start geändert.').waitFor();
      assert.equal(await result.getByText(/Bericht bricht mit DEMO-42 ab/).count(), 0, 'no content of an invalid suggestion');
    });
  });

  test('a changed address drops the stored key unless a new one is entered', async () => {
    const withCompatible = (backend) => {
      withAiManage(backend);
      backend.state.ai.providers.push({ id: '00000000-0000-4000-8000-0000000092a2', kind: 'openai', name: 'Kompatibel', baseUrl: 'https://llm.example.com',
        apiKey: 'sk-synthetic-2', caCertificate: null, dataPath: 'external', enabled: true, version: 1, updatedAt: '2026-11-17T08:00:00Z',
        updatedBy: backend.anna.id, removed: false });
    };
    await withApp({ prepare: withCompatible }, async (page, backend) => {
      const row = page.getByTestId('ai-accesses').locator('[data-provider]').filter({ hasText: 'Kompatibel' });
      await row.getByText(/Schlüssel hinterlegt/).waitFor();
      await row.getByRole('button', { name: 'Kompatibel bearbeiten' }).click();
      const form = page.getByTestId('ai-provider-form');
      assert.equal(await form.getByTestId('ai-key-dropped').count(), 0);
      await form.getByLabel('Adresse').fill('https://LLM2.example.com/');
      await form.getByTestId('ai-key-dropped').getByText('Adresse oder CA ändern sich.', { exact: false }).waitFor();
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Kompatibel gespeichert.').waitFor();
      await page.getByTestId('ai-accesses').locator('[data-provider]').filter({ hasText: 'Kompatibel' })
        .getByText(/https:\/\/llm2\.example\.com · extern · Schlüssel fehlt/).waitFor();
      assert.equal(backend.state.ai.providers.find((entry) => entry.name === 'Kompatibel').apiKey, null);
    });
  });
});
