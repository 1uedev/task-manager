import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Configuration } from '../src/api/product.ts';
import { keyFromName, nextStatuses, parseViolation, requiredFor, sendValue, statusKeysOf, workflowOf } from '../src/app/configuration.ts';
import { formatDate } from '../src/i18n/format.ts';
import { readProblem } from '../src/api/problem.ts';

const CONFIG: Configuration = {
  ticketTypes: [{ key: 'bug', name: 'Fehler' }, { key: 'question', name: 'Frage' }],
  statuses: [{ key: 'new', name: 'Neu', category: 'open' }, { key: 'doing', name: 'In Arbeit', category: 'in_progress' }, { key: 'done', name: 'Erledigt', category: 'done' }],
  initialStatus: 'new',
  transitions: [{ from: 'new', to: 'doing' }, { from: 'doing', to: 'done', requiredFields: ['custom.cause'] }],
  workflows: [{ type: 'question', initialStatus: 'new', transitions: [{ from: 'new', to: 'done' }] }],
  fields: [{ key: 'title', required: true }, { key: 'description', required: false }, { key: 'type', required: true }, { key: 'status', required: true }, { key: 'assignee', required: false }],
};

test('a type without a workflow of its own follows the default one', () => {
  assert.equal(workflowOf(CONFIG, 'bug').own, false);
  assert.deepEqual(statusKeysOf(CONFIG, 'bug'), ['new', 'doing', 'done']);
  assert.deepEqual(nextStatuses(CONFIG, 'bug', 'doing'), ['doing', 'done']);
  assert.deepEqual(requiredFor(CONFIG, 'bug', 'doing', 'done'), ['custom.cause']);
  assert.deepEqual(requiredFor(CONFIG, 'bug', 'new', 'doing'), []);
});

test('a type with its own workflow knows only the statuses it names', () => {
  assert.equal(workflowOf(CONFIG, 'question').own, true);
  assert.deepEqual(statusKeysOf(CONFIG, 'question'), ['new', 'done']);
  assert.deepEqual(nextStatuses(CONFIG, 'question', 'new'), ['new', 'done']);
  assert.deepEqual(requiredFor(CONFIG, 'question', 'doing', 'done'), []);
});

test('keys are made from names, stay valid and unique', () => {
  assert.equal(keyFromName('Interne Ursache', []), 'interne_ursache');
  assert.equal(keyFromName('Größe der Änderung', []), 'grosse_der_anderung');
  assert.equal(keyFromName('Interne Ursache', ['interne_ursache']), 'interne_ursache_2');
  assert.equal(keyFromName('Interne Ursache', ['interne_ursache', 'interne_ursache_2']), 'interne_ursache_3');
  assert.equal(keyFromName('1. Wahl', []), 'f_1_wahl');
  assert.equal(keyFromName('???', []), 'f');
  assert.match(keyFromName('x'.repeat(100), []), /^[a-z][a-z0-9_]{0,39}$/);
});

test('form text turns into the value of the kind, empty clears, nonsense is refused', () => {
  assert.equal(sendValue('text', '  Hallo '), 'Hallo');
  assert.equal(sendValue('text', '   '), null);
  assert.equal(sendValue('longtext', 'a\nb'), 'a\nb');
  assert.equal(sendValue('integer', '12'), 12);
  assert.equal(sendValue('integer', '-3'), -3);
  assert.equal(sendValue('integer', '1.5'), undefined);
  assert.equal(sendValue('integer', '007'), undefined);
  assert.equal(sendValue('integer', '9007199254740993'), undefined);
  assert.equal(sendValue('boolean', 'true'), true);
  assert.equal(sendValue('boolean', 'false'), false);
  assert.equal(sendValue('boolean', 'ja'), undefined);
  assert.equal(sendValue('date', '2026-11-05'), '2026-11-05');
  assert.equal(sendValue('date', '2026-02-30'), undefined);
  assert.equal(sendValue('date', '5.11.2026'), undefined);
  assert.equal(sendValue('select', 'hoch'), 'hoch');
  assert.equal(sendValue('user', ''), null);
});

test('violations of the server are read into the field and the reason', () => {
  assert.deepEqual(parseViolation('custom.interne_ursache.required_for_transition'), { field: 'custom.interne_ursache', reason: 'required_for_transition', subject: null });
  assert.deepEqual(parseViolation('description.required_for_transition'), { field: 'description', reason: 'required_for_transition', subject: null });
  assert.deepEqual(parseViolation('status.in_use:review'), { field: 'status', reason: 'in_use', subject: 'review' });
  assert.equal(parseViolation('???'), null);
});

test('a problem carries the reasons the server named and drops anything of another shape', () => {
  const info = readProblem(400, { code: 'request.invalid', violations: ['custom.cause.required', 'status.in_use:review', '<script>', 5, 'x'.repeat(200)] });
  assert.deepEqual(info.violations, ['custom.cause.required', 'status.in_use:review']);
  assert.equal(readProblem(400, { code: 'request.invalid' }).violations, undefined);
});

test('a calendar date has no time zone', () => {
  assert.equal(formatDate('2026-11-05', 'de'), '05.11.2026');
  assert.equal(formatDate('2026-11-05', 'en').includes('Nov'), true);
  assert.equal(formatDate('kaputt', 'de'), 'kaputt');
});
