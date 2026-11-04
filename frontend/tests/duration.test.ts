import assert from 'node:assert/strict';
import { test } from 'node:test';
import { durationInput, formatHours, formatMinutes, parseDuration } from '../src/app/duration.ts';

test('the four agreed notations give whole minutes', () => {
  assert.equal(parseDuration('1:15', 'de'), 75);
  assert.equal(parseDuration('75m', 'de'), 75);
  assert.equal(parseDuration('75 Min.', 'de'), 75);
  assert.equal(parseDuration('1,25 h', 'de'), 75);
  assert.equal(parseDuration('1,25 Std.', 'de'), 75);
  assert.equal(parseDuration('1.25 h', 'en'), 75);
  assert.equal(parseDuration('1h 15m', 'de'), 75);
  assert.equal(parseDuration('1 h 15 min', 'en'), 75);
  assert.equal(parseDuration('24:00', 'de'), 1440);
  assert.equal(parseDuration('0,1 h', 'de'), 6);
});

test('ambiguous or out of range input is refused instead of guessed', () => {
  assert.equal(parseDuration('75', 'de'), null, 'a bare number could be hours or minutes');
  assert.equal(parseDuration('', 'de'), null);
  assert.equal(parseDuration('0m', 'de'), null);
  assert.equal(parseDuration('24:01', 'de'), null);
  assert.equal(parseDuration('25 h', 'de'), null);
  assert.equal(parseDuration('1.25 h', 'de'), null, 'German uses the decimal comma');
  assert.equal(parseDuration('1h 2h', 'de'), null);
  assert.equal(parseDuration('1:75', 'de'), null);
  assert.equal(parseDuration('eine Stunde', 'de'), null);
});

test('a duration reads the same in every language and as decimal hours', () => {
  assert.equal(formatMinutes(75, 'de'), '1 Std. 15 Min.');
  assert.equal(formatMinutes(75, 'en'), '1 h 15 min');
  assert.equal(formatMinutes(45, 'de'), '45 Min.');
  assert.equal(formatMinutes(120, 'de'), '2 Std.');
  assert.equal(formatHours(75, 'de'), '1,25');
  assert.equal(formatHours(20, 'de'), '0,33');
  assert.equal(formatHours(95, 'en'), '1.58');
  assert.equal(durationInput(75), '1:15');
});
