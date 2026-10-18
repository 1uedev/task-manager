import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { launch, open, startServer } from './support.mjs';

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

async function withPage(options, body) {
  const session = await open(browser, server.url, options);
  try {
    await body(session.page);
    assert.deepEqual(session.problems, [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const activeId = (page) => page.evaluate(() => document.activeElement?.id ?? document.activeElement?.tagName);
const focusedText = (page) => page.evaluate(() => document.activeElement?.textContent?.trim() ?? '');

describe('language', () => {
  test('German is chosen for a German browser, English for an English browser', async () => {
    await withPage({ locale: 'de-DE' }, async (page) => {
      assert.equal(await page.locator('html').getAttribute('lang'), 'de');
      assert.equal(await page.locator('h1').innerText(), 'Willkommen');
      assert.equal(await page.title(), 'Willkommen · Task Manager');
    });
    await withPage({ locale: 'en-US' }, async (page) => {
      assert.equal(await page.locator('html').getAttribute('lang'), 'en');
      assert.equal(await page.locator('h1').innerText(), 'Welcome');
    });
    await withPage({ locale: 'fr-FR' }, async (page) => {
      assert.equal(await page.locator('html').getAttribute('lang'), 'de', 'unsupported browser language falls back to German');
    });
  });

  test('switching with the keyboard changes text, formats and document language, and is remembered', async () => {
    await withPage({ locale: 'de-DE' }, async (page) => {
      assert.equal(await page.getByTestId('formatted-number').innerText(), '1.234,5');
      assert.equal(await page.getByTestId('formatted-instant').innerText(), '18.10.2026, 16:30');
      await page.locator('#language').focus();
      await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator('#language').inputValue(), 'en');
      assert.equal(await page.locator('html').getAttribute('lang'), 'en');
      assert.equal(await page.locator('h1').innerText(), 'Welcome');
      assert.equal(await page.getByRole('navigation').getAttribute('aria-label'), 'Main navigation');
      assert.equal(await page.getByTestId('formatted-number').innerText(), '1,234.5');
      assert.match(await page.getByTestId('formatted-instant').innerText(), /Oct 18, 2026.*4:30/);
      assert.equal(await page.locator('time').getAttribute('datetime'), '2026-10-18T14:30:00Z', 'exchange value is unchanged');
      assert.equal(await page.title(), 'Welcome · Task Manager');
      await page.reload();
      await page.waitForSelector('main h1');
      assert.equal(await page.locator('h1').innerText(), 'Welcome', 'choice survives a reload');
      const stored = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage }, cookie: document.cookie }));
      assert.deepEqual(stored, { local: { 'taskmanager.language': 'en' }, session: {}, cookie: '' });
    });
  });
});

describe('keyboard navigation', () => {
  test('skip link is the first stop and moves focus to the content', async () => {
    await withPage({}, async (page) => {
      await page.keyboard.press('Tab');
      assert.equal(await focusedText(page), 'Zum Inhalt springen');
      assert.ok(await page.evaluate(() => document.activeElement.getBoundingClientRect().top >= 0), 'visible when focused');
      await page.keyboard.press('Enter');
      assert.equal(await activeId(page), 'main');
    });
  });

  test('navigation links work with Enter, mark the current page and move focus to the new heading', async () => {
    await withPage({}, async (page) => {
      await page.keyboard.press('Tab'); // skip link
      await page.keyboard.press('Tab'); // Start
      assert.equal(await focusedText(page), 'Start');
      await page.keyboard.press('Tab');
      assert.equal(await focusedText(page), 'Zustände');
      await page.keyboard.press('Enter');
      await page.waitForSelector('h1:has-text("Zustände und Fehler")');
      assert.equal(await page.evaluate(() => document.activeElement?.tagName), 'H1');
      assert.equal(await page.getByRole('link', { name: 'Zustände' }).getAttribute('aria-current'), 'page');
      assert.equal(await page.getByRole('link', { name: 'Start' }).getAttribute('aria-current'), null);
      assert.equal(await page.title(), 'Zustände und Fehler · Task Manager');
      await page.goBack();
      await page.waitForSelector('h1:has-text("Willkommen")');
    });
  });

  test('landmarks are present once', async () => {
    await withPage({}, async (page) => {
      for (const role of ['banner', 'navigation', 'main', 'contentinfo']) assert.equal(await page.getByRole(role).count(), 1, role);
    });
  });

  test('focus is clearly visible', async () => {
    await withPage({}, async (page) => {
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      const outline = await page.evaluate(() => {
        const style = getComputedStyle(document.activeElement);
        return { width: parseFloat(style.outlineWidth), style: style.outlineStyle };
      });
      assert.ok(outline.width >= 2 && outline.style !== 'none', JSON.stringify(outline));
    });
  });
});

describe('states', () => {
  const expectations = {
    de: [
      ['success', 'Beispieleintrag geladen'],
      ['empty', 'Noch keine Einträge'],
      ['forbidden', 'Sie haben für diese Aktion keine Berechtigung.'],
      ['notFound', 'Der Eintrag wurde nicht gefunden oder ist für Sie nicht sichtbar.'],
      ['conflict', 'Der Eintrag wurde zwischenzeitlich geändert.'],
      ['preconditionRequired', 'Die Änderung benötigt den aktuellen Stand'],
      ['serverError', 'Beim Verarbeiten ist ein interner Fehler aufgetreten.'],
      ['unavailable', 'Der Dienst ist vorübergehend nicht erreichbar. Bitte versuchen'],
      ['network', 'Keine Verbindung zum Server.'],
    ],
    en: [
      ['success', 'Example entry loaded'],
      ['empty', 'No entries yet'],
      ['forbidden', 'You do not have permission for this action.'],
      ['notFound', 'The entry was not found or is not visible to you.'],
      ['conflict', 'The entry was changed in the meantime.'],
      ['preconditionRequired', 'The change needs the current version'],
      ['serverError', 'An internal error occurred while processing.'],
      ['unavailable', 'The service is temporarily unavailable. Please try'],
      ['network', 'No connection to the server.'],
    ],
  };

  for (const [language, locale] of [['de', 'de-DE'], ['en', 'en-US']]) {
    test(`every state is reachable by keyboard and shown in ${language}`, async () => {
      await withPage({ locale }, async (page) => {
        await page.goto(`${server.url}#/states`);
        await page.waitForSelector('select#' + (await page.locator('main select').getAttribute('id')));
        const select = page.locator('main select');
        for (const [scenario, text] of expectations[language]) {
          await select.focus();
          await select.selectOption(scenario);
          await page.keyboard.press('Tab');
          await page.keyboard.press('Enter');
          await page.getByTestId('result').getByText(text, { exact: false }).first().waitFor();
          const result = page.getByTestId('result');
          const isProblem = !['success', 'empty'].includes(scenario);
          assert.equal(await result.getByRole('alert').count(), isProblem ? 1 : 0, scenario);
          if (isProblem && scenario !== 'network') {
            assert.match(await result.innerText(), /b7d4f0a2-6c1e-4a58-9f3d-2e8c5b1a7d90/, 'support reference is shown');
          }
          assert.ok(!(await result.innerText()).includes('Synthetic'), 'server titles are never displayed');
        }
      });
    });
  }

  test('the loading state is shown while a slow answer is pending', async () => {
    await withPage({}, async (page) => {
      await page.goto(`${server.url}#/states`);
      await page.locator('main select').selectOption('slow');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByRole('status').filter({ hasText: 'Wird geladen' }).waitFor();
      await page.getByText('Beispieleintrag geladen').waitFor();
    });
  });

  test('an older answer never overwrites a newer choice', async () => {
    await withPage({}, async (page) => {
      await page.goto(`${server.url}#/states`);
      const select = page.locator('main select');
      await select.selectOption('slow');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await select.selectOption('forbidden');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByText('Sie haben für diese Aktion keine Berechtigung.').waitFor();
      await page.waitForTimeout(1600);
      assert.equal(await page.getByText('Beispieleintrag geladen').count(), 0);
      assert.equal(await page.getByRole('alert').count(), 1);
    });
  });

  test('recovery actions: conflict offers the current version, server errors offer a retry', async () => {
    await withPage({}, async (page) => {
      await page.goto(`${server.url}#/states`);
      const select = page.locator('main select');
      await select.selectOption('conflict');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByRole('button', { name: 'Aktuellen Stand laden' }).click();
      await page.getByText('Beispieleintrag geladen').waitFor();
      await select.selectOption('serverError');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByRole('button', { name: 'Erneut versuchen' }).waitFor();
      await select.selectOption('forbidden');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByText('Sie haben für diese Aktion keine Berechtigung.').waitFor();
      assert.equal(await page.getByRole('button', { name: 'Erneut versuchen' }).count(), 0, 'no pointless retry for missing rights');
    });
  });

  test('a shown error changes language with the interface', async () => {
    await withPage({}, async (page) => {
      await page.goto(`${server.url}#/states`);
      await page.locator('main select').selectOption('notFound');
      await page.getByRole('button', { name: 'Beispiel laden' }).click();
      await page.getByText('Der Eintrag wurde nicht gefunden').waitFor();
      await page.locator('#language').selectOption('en');
      await page.getByText('The entry was not found').waitFor();
    });
  });
});

describe('form', () => {
  test('error correction with the keyboard in German', async () => {
    await withPage({ locale: 'de-DE' }, async (page) => {
      await page.goto(`${server.url}#/form`);
      await page.waitForSelector('h1:has-text("Beispielformular")');
      await page.getByLabel('Bezeichnung (Pflichtfeld)').focus();
      await page.keyboard.press('Enter'); // submits the empty form
      const summary = page.getByTestId('error-summary');
      await summary.waitFor();
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'error-summary');
      assert.match(await summary.innerText(), /2 Felder bitte korrigieren/);
      const label = page.getByLabel('Bezeichnung (Pflichtfeld)');
      assert.equal(await label.getAttribute('aria-invalid'), 'true');
      const described = await label.getAttribute('aria-describedby');
      assert.equal(described.split(' ').length, 2, 'hint and error are both announced');
      for (const id of described.split(' ')) assert.ok((await page.locator(`[id="${id}"]`).innerText()).length > 0);
      assert.match(await page.locator(`[id="${described.split(' ')[1]}"]`).innerText(), /Fehler: Bitte füllen Sie dieses Feld aus\./, 'error is not only a color');
      // Follow the summary link to the first field.
      await page.keyboard.press('Tab');
      assert.equal(await focusedText(page), 'Bezeichnung');
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('name')), 'label');
      await page.keyboard.type('Bericht');
      await page.keyboard.press('Tab');
      await page.keyboard.type('1.5'); // wrong notation for German
      await page.keyboard.press('Enter');
      assert.match(await page.getByTestId('error-summary').innerText(), /Ein Feld bitte korrigieren/);
      assert.match(await page.getByTestId('error-summary').innerText(), /Zahl in der Schreibweise dieser Sprache/);
      await page.getByLabel('Aufwand in Stunden (Pflichtfeld)').fill('1,5');
      await page.getByLabel('Aufwand in Stunden (Pflichtfeld)').press('Enter');
      const success = page.getByTestId('success');
      await success.waitFor();
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'success');
      assert.match(await success.innerText(), /Synthetisch übernommen: Bericht, 1,5 Stunden\./);
      assert.equal(await page.getByTestId('error-summary').count(), 0);
    });
  });

  test('the same input rules apply in English with English notation', async () => {
    await withPage({ locale: 'en-US' }, async (page) => {
      await page.goto(`${server.url}#/form`);
      await page.waitForSelector('h1:has-text("Example form")');
      await page.getByLabel('Label (Required)').fill('Report');
      await page.getByLabel('Effort in hours (Required)').fill('1,5');
      await page.keyboard.press('Enter');
      await page.getByTestId('error-summary').waitFor();
      assert.match(await page.getByTestId('error-summary').innerText(), /Please correct one field/);
      await page.getByLabel('Effort in hours (Required)').fill('1.5');
      await page.keyboard.press('Enter');
      assert.match(await page.getByTestId('success').innerText(), /Accepted synthetically: Report, 1\.5 hours\./);
    });
  });

  test('errors are translated when the language changes', async () => {
    await withPage({}, async (page) => {
      await page.goto(`${server.url}#/form`);
      await page.getByRole('button', { name: 'Prüfen und übernehmen' }).click();
      await page.getByText('Bitte füllen Sie dieses Feld aus.').first().waitFor();
      await page.locator('#language').selectOption('en');
      await page.getByText('Please fill in this field.').first().waitFor();
    });
  });
});

describe('branding', () => {
  test('an example branding applies to the whole interface and can be reset', async () => {
    await withPage({}, async (page) => {
      const header = page.locator('header');
      const color = () => header.evaluate((element) => getComputedStyle(element).backgroundColor);
      const before = await color();
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Task Manager');
      await page.getByRole('button', { name: 'Beispielauftritt anwenden' }).click();
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Beispiel GmbH');
      assert.notEqual(await color(), before);
      assert.equal(await color(), 'rgb(122, 46, 14)');
      assert.equal(await page.title(), 'Willkommen · Beispiel GmbH');
      assert.equal(await page.evaluate(() => getComputedStyle(document.body).backgroundColor), 'rgb(253, 248, 243)');
      await page.getByRole('button', { name: 'Auftritt zurücksetzen' }).click();
      assert.equal(await color(), before);
      assert.equal(await page.getByTestId('brand-name').innerText(), 'Task Manager');
    });
  });
});

describe('layout', () => {
  for (const route of ['start', 'states', 'form']) {
    test(`no horizontal scrolling at 320 px width on ${route}, also with longer English texts`, async () => {
      await withPage({ locale: 'en-US', viewport: { width: 320, height: 640 } }, async (page) => {
        await page.goto(`${server.url}#/${route}`);
        await page.waitForSelector('main h1');
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        assert.ok(overflow <= 0, `overflow ${overflow}px`);
      });
    });
  }
});
