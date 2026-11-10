import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mergeCriteria, type Draft } from '../src/pages/requirementCriteria.ts';

// "Meine Eingaben behalten" for criteria (F1 of the TASK-096 review): own changes per fixed number, the rest from the newer state.
const counter = () => {
  let value = 100;
  return () => ++value;
};
const drafts = (...items: [number | null, string][]): Draft[] => items.map(([id, text], index) => ({ key: index + 1, id, text }));
const plain = (list: readonly Draft[]) => list.map((item) => [item.id, item.text]);
const base = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 alt' }];

test('an own change of K1 keeps the changed K2 and the added K3 of someone else', () => {
  const theirs = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([1, 'K1 MEINE EINGABE'], [2, 'K2 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[1, 'K1 MEINE EINGABE'], [2, 'K2 VON BEN'], [3, 'K3 VON BEN']]);
});

test('an own text wins over the other text of the same criterion', () => {
  const theirs = [{ id: 1, text: 'K1 VON BEN' }, { id: 2, text: 'K2 alt' }];
  assert.deepEqual(plain(mergeCriteria(base, drafts([1, 'K1 MEINE'], [2, 'K2 alt']), theirs, counter())), [[1, 'K1 MEINE'], [2, 'K2 alt']]);
});

test('an own removal and own new criteria stay, the other changes come along', () => {
  const theirs = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([2, 'K2 alt'], [null, 'NEU']), theirs, counter());
  assert.deepEqual(plain(merged), [[2, 'K2 VON BEN'], [null, 'NEU'], [3, 'K3 VON BEN']]);
});

// R1 of the follow up review of TASK-096: a new criterion keeps the place the person gave it.
test('a new criterion between K1 and K2 stays there, the additions of the other person follow', () => {
  const theirs = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([1, 'K1 alt'], [null, 'EIGEN NEU'], [2, 'K2 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[1, 'K1 alt'], [null, 'EIGEN NEU'], [2, 'K2 VON BEN'], [3, 'K3 VON BEN']]);
});

test('a new criterion in front stays in front', () => {
  const theirs = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 VON BEN' }, { id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([null, 'EIGEN NEU'], [1, 'K1 alt'], [2, 'K2 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[null, 'EIGEN NEU'], [1, 'K1 alt'], [2, 'K2 VON BEN'], [3, 'K3 VON BEN']]);
});

test('a new criterion behind one the other person removed follows the nearest remaining one', () => {
  const three = [{ id: 1, text: 'K1 alt' }, { id: 2, text: 'K2 alt' }, { id: 3, text: 'K3 alt' }];
  const theirs = [{ id: 1, text: 'K1 alt' }, { id: 3, text: 'K3 alt' }];
  const merged = mergeCriteria(three, drafts([1, 'K1 alt'], [2, 'K2 alt'], [null, 'A'], [null, 'B'], [3, 'K3 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[1, 'K1 alt'], [null, 'A'], [null, 'B'], [3, 'K3 alt']]);
});

test('an own order wins and the criteria of the other person follow it', () => {
  const theirs = [{ id: 1, text: 'K1 VON BEN' }, { id: 2, text: 'K2 alt' }, { id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([2, 'K2 alt'], [null, 'NEU'], [1, 'K1 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[2, 'K2 alt'], [null, 'NEU'], [1, 'K1 VON BEN'], [3, 'K3 VON BEN']]);
});

test('a criterion changed here but removed meanwhile stays as a new one; an untouched removed one goes', () => {
  const theirs = [{ id: 3, text: 'K3 VON BEN' }];
  const merged = mergeCriteria(base, drafts([1, 'K1 MEINE'], [2, 'K2 alt']), theirs, counter());
  assert.deepEqual(plain(merged), [[null, 'K1 MEINE'], [3, 'K3 VON BEN']]);
});

test('without own changes the newer state is taken as it is', () => {
  const theirs = [{ id: 2, text: 'K2 VON BEN' }, { id: 1, text: 'K1 alt' }];
  assert.deepEqual(plain(mergeCriteria(base, drafts([1, 'K1 alt'], [2, 'K2 alt']), theirs, counter())), [[2, 'K2 VON BEN'], [1, 'K1 alt']]);
});
