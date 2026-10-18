import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatDateTime, formatNumber, parseDecimal } from '../src/i18n/format.ts';

test('the same instant has different presentations but one exchange value', () => {
  const instant = '2026-10-18T14:30:00Z';
  assert.equal(formatDateTime(instant, 'de', 'Europe/Berlin'), '18.10.2026, 16:30');
  assert.match(formatDateTime(instant, 'en', 'Europe/Berlin'), /Oct 18, 2026.*4:30/);
  assert.equal(new Date(instant).toISOString(), '2026-10-18T14:30:00.000Z');
  assert.equal(formatDateTime(instant, 'de', 'UTC'), '18.10.2026, 14:30');
});

test('numbers follow the notation of the language', () => {
  assert.equal(formatNumber(1234.5, 'de'), '1.234,5');
  assert.equal(formatNumber(1234.5, 'en'), '1,234.5');
});

test('decimal input is read in the notation of the language', () => {
  assert.equal(parseDecimal('1,5', 'de'), 1.5);
  assert.equal(parseDecimal('1.234,5', 'de'), 1234.5);
  assert.equal(parseDecimal(' 8 ', 'de'), 8);
  assert.equal(parseDecimal('1.5', 'en'), 1.5);
  assert.equal(parseDecimal('1,234.5', 'en'), 1234.5);
});

test('ambiguous or malformed input is rejected instead of guessed', () => {
  assert.equal(parseDecimal('1.5', 'de'), null);
  assert.equal(parseDecimal('1,5', 'en'), null);
  assert.equal(parseDecimal('1,23,4', 'en'), null);
  assert.equal(parseDecimal('abc', 'de'), null);
  assert.equal(parseDecimal('1e3', 'en'), null);
  assert.equal(parseDecimal('', 'en'), null);
  assert.equal(parseDecimal('12,', 'de'), null);
});

test('spaces never join digits that are not a complete group', () => {
  for (const language of ['de', 'en', 'fr']) {
    assert.equal(parseDecimal('1 5', language), null, `${language} space`);
    assert.equal(parseDecimal('1\u00a05', language), null, `${language} no-break space`);
    assert.equal(parseDecimal('1\u202f5', language), null, `${language} narrow no-break space`);
    assert.equal(parseDecimal('1 23', language), null, `${language} incomplete group`);
  }
  assert.equal(parseDecimal('1 234,5', 'de'), null);
  assert.equal(parseDecimal('1 234.5', 'en'), null);
  assert.equal(parseDecimal('1.2.3', 'de'), null);
  assert.equal(parseDecimal('12.34', 'de'), null);
});

test('languages that group with spaces accept complete groups only', () => {
  assert.equal(parseDecimal('1\u202f234,5', 'fr'), 1234.5);
  assert.equal(parseDecimal('1\u00a0234,5', 'fr'), 1234.5);
  assert.equal(parseDecimal('1 234,5', 'fr'), 1234.5);
  assert.equal(parseDecimal('1 234 567', 'fr'), 1234567);
  assert.equal(parseDecimal('1.5', 'fr'), null);
});

test('only outer whitespace is ignored', () => {
  assert.equal(parseDecimal(' \t1,5\n', 'de'), 1.5);
  assert.equal(parseDecimal('- 1,5', 'de'), null);
  assert.equal(parseDecimal('-1,5', 'de'), -1.5);
});
