import assert from 'node:assert/strict';
import { test } from 'node:test';
import { LABEL_MAX, validateSample } from '../src/pages/validation.ts';

test('valid input in the notation of the language', () => {
  assert.deepEqual(validateSample({ label: ' Bericht ', hours: '1,5' }, 'de'), { ok: true, label: 'Bericht', hours: 1.5 });
  assert.deepEqual(validateSample({ label: 'Report', hours: '1.5' }, 'en'), { ok: true, label: 'Report', hours: 1.5 });
});

test('the same business value results from both notations', () => {
  const german = validateSample({ label: 'a', hours: '2,25' }, 'de');
  const english = validateSample({ label: 'a', hours: '2.25' }, 'en');
  assert.deepEqual(german, english);
});

test('missing and oversized input is reported per field', () => {
  const result = validateSample({ label: ' ', hours: '' }, 'de');
  assert.equal(result.ok, false);
  if (result.ok) return;
  assert.equal(result.errors.label?.key, 'form.error.required');
  assert.equal(result.errors.hours?.key, 'form.error.required');
  const long = validateSample({ label: 'x'.repeat(LABEL_MAX + 1), hours: '1' }, 'de');
  assert.equal(!long.ok && long.errors.label?.key, 'form.error.tooLong');
  assert.equal(validateSample({ label: 'x'.repeat(LABEL_MAX), hours: '1' }, 'de').ok, true);
});

test('numbers must be numbers in range', () => {
  const bad = validateSample({ label: 'a', hours: '1.5' }, 'de');
  assert.equal(!bad.ok && bad.errors.hours?.key, 'form.error.notANumber');
  const low = validateSample({ label: 'a', hours: '0' }, 'en');
  assert.equal(!low.ok && low.errors.hours?.key, 'form.error.outOfRange');
  const high = validateSample({ label: 'a', hours: '24.5' }, 'en');
  assert.equal(!high.ok && high.errors.hours?.key, 'form.error.outOfRange');
});
