import assert from 'node:assert/strict';
import { test } from 'node:test';
import { FALLBACK_LANGUAGE, placeholders, resolveLanguage, translate, type Registry } from '../src/i18n/core.ts';
import { de } from '../src/i18n/de.ts';
import { en } from '../src/i18n/en.ts';
import { registry, supportedLanguages } from '../src/i18n/index.ts';

test('German and English provide exactly the same keys with the same placeholders', () => {
  assert.deepEqual(Object.keys(en).sort(), Object.keys(de).sort());
  for (const key of Object.keys(de) as (keyof typeof de)[]) {
    assert.deepEqual(placeholders(en[key]), placeholders(de[key]), `placeholders of ${key}`);
  }
});

test('every supported language has a catalog and a display name in every catalog', () => {
  for (const language of supportedLanguages) {
    assert.ok(registry[language], language);
    for (const catalog of Object.values(registry)) assert.ok(Object.hasOwn(catalog, `language.${language}`), language);
  }
});

test('texts contain no dash characters used as punctuation and no empty values', () => {
  for (const [language, catalog] of Object.entries(registry)) {
    for (const [key, message] of Object.entries(catalog)) {
      const texts = typeof message === 'string' ? [message] : Object.values(message);
      for (const text of texts) {
        assert.notEqual(text.trim(), '', `${language}:${key} is empty`);
        assert.ok(!/[–—]| - /.test(text), `${language}:${key} contains a dash`);
      }
    }
  }
});

test('selected language is used', () => {
  assert.equal(translate(registry, 'de', 'nav.start'), 'Start');
  assert.equal(translate(registry, 'en', 'nav.start'), 'Home');
});

test('plural forms follow the selected language', () => {
  assert.equal(translate(registry, 'de', 'start.format.items', { count: 1 }), '1 Eintrag');
  assert.equal(translate(registry, 'de', 'start.format.items', { count: 3 }), '3 Einträge');
  assert.equal(translate(registry, 'en', 'start.format.items', { count: 1 }), '1 item');
  assert.equal(translate(registry, 'en', 'start.format.items', { count: 0 }), '0 items');
});

test('numbers in texts are presented in the notation of the language', () => {
  assert.equal(translate(registry, 'de', 'start.format.items', { count: 1234 }), '1.234 Einträge');
  assert.equal(translate(registry, 'en', 'start.format.items', { count: 1234 }), '1,234 items');
});

// REQ-I18N-002: an additional language is added through the catalog only, no change in components or logic.
test('an additional test language works through the registry and falls back to English for gaps', () => {
  const test: Registry = {
    ...registry,
    pl: {
      'nav.start': 'Start',
      'start.format.items': { one: '{count} pozycja', few: '{count} pozycje', many: '{count} pozycji', other: '{count} pozycji' },
    },
  };
  assert.equal(translate(test, 'pl', 'start.format.items', { count: 1 }), '1 pozycja');
  assert.equal(translate(test, 'pl', 'start.format.items', { count: 3 }), '3 pozycje');
  assert.equal(translate(test, 'pl', 'start.format.items', { count: 5 }), '5 pozycji');
  const missing: string[] = [];
  assert.equal(translate(test, 'pl', 'nav.label', {}, (language, key) => missing.push(`${language}:${key}`)), 'Main navigation');
  assert.deepEqual(missing, ['pl:nav.label']);
});

test('a key missing everywhere is shown as the key and reported', () => {
  const missing: string[] = [];
  assert.equal(translate(registry, 'en', 'does.not.exist', {}, (language, key) => missing.push(`${language}:${key}`)), 'does.not.exist');
  assert.deepEqual(missing, ['en:does.not.exist']);
  assert.equal(FALLBACK_LANGUAGE, 'en');
});

test('prototype names are not mistaken for keys', () => {
  assert.equal(translate(registry, 'en', 'constructor'), 'constructor');
  assert.equal(translate(registry, 'en', '__proto__'), '__proto__');
});

test('placeholders without a value stay visible instead of failing', () => {
  assert.equal(translate(registry, 'en', 'problem.reference'), 'Reference for support: {id}');
  assert.equal(translate(registry, 'en', 'problem.reference', { id: 'abc' }), 'Reference for support: abc');
});

test('language resolution prefers the stored choice, then the browser, then German', () => {
  const supported = ['de', 'en'];
  assert.equal(resolveLanguage('en', ['de-DE'], supported), 'en');
  assert.equal(resolveLanguage(null, ['fr-FR', 'en-GB', 'de'], supported), 'en');
  assert.equal(resolveLanguage(null, ['fr-FR'], supported), 'de');
  assert.equal(resolveLanguage('xx', [], supported), 'de');
  assert.equal(resolveLanguage(null, ['DE-at'], supported), 'de');
});
