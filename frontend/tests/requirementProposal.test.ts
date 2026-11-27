import assert from 'node:assert/strict';
import { test } from 'node:test';
import { findingsOf, proposalFor, type Finding, type ReviewedContent } from '../src/pages/requirementProposal.ts';

// TASK-127, rule 1: a proposal of the review goes into the editor only where it belongs without doubt.
const all = () => true;
const finding = (kind: string, location: string, suggestion: string | null): Finding => ({ kind, location, reason: 'Grund', suggestion, sources: ['S1'] });
const paragraph = (text: string) => ({ type: 'paragraph', content: [{ type: 'text', text }] });
const content: ReviewedContent = {
  title: 'Kunden erhalten zeitnah eine Antwort',
  criteria: [{ id: 1, text: 'Die erste Antwort kommt bald.' }, { id: 2, text: 'Der Status ist sichtbar.' }],
  description: { type: 'doc', content: [paragraph('Der Support antwortet freundlich.'), paragraph('Am Wochenende gilt nichts.')] },
};

test('a missing criterion becomes a new criterion', () => {
  assert.deepEqual(proposalFor(finding('missing_criterion', 'Abnahmekriterien', 'Die Antwort nennt eine Ticketnummer.'), content, all),
    { kind: 'newCriterion', text: 'Die Antwort nennt eine Ticketnummer.' });
});

test('a passage found exactly once is replaced in its field', () => {
  assert.deepEqual(proposalFor(finding('unclear', 'zeitnah', 'innerhalb von 5 Minuten'), content, all),
    { kind: 'title', text: 'Kunden erhalten innerhalb von 5 Minuten eine Antwort' });
  assert.deepEqual(proposalFor(finding('not_measurable', 'bald', 'innerhalb von 4 Stunden'), content, all),
    { kind: 'criterion', id: 1, text: 'Die erste Antwort kommt innerhalb von 4 Stunden.' });
  const description = proposalFor(finding('unclear', 'freundlich', 'sachlich und freundlich'), content, all);
  assert.equal(description?.kind, 'description');
  assert.match(JSON.stringify(description), /sachlich und freundlich/);
  assert.match(JSON.stringify(content.description), /antwortet freundlich\./, 'the shown content is not changed');
});

test('an unclear place, a read only field or no proposal leaves only copying', () => {
  assert.equal(proposalFor(finding('unclear', 'Der', 'Ein'), content, all), null, 'the passage appears more than once');
  assert.equal(proposalFor(finding('unclear', 'gibt es nicht', 'x'), content, all), null);
  assert.equal(proposalFor(finding('unclear', 'zeitnah', null), content, all), null);
  assert.equal(proposalFor(finding('unclear', 'zeitnah', 'x'), content, (field) => field !== 'title'), null);
  assert.equal(proposalFor(finding('missing_criterion', '', 'neu'), content, (field) => field !== 'criteria'), null);
  assert.equal(proposalFor(finding('unclear', 'freundlich', 'zwei\nZeilen'), content, all), null);
  assert.equal(proposalFor(finding('unclear', 'zeitnah', 'x'.repeat(200)), content, all), null, 'the title would be too long');
});

test('a replacement with dollar signs stays as written', () => {
  assert.deepEqual(proposalFor(finding('unclear', 'zeitnah', 'für $& Kunden'), content, all),
    { kind: 'title', text: 'Kunden erhalten für $& Kunden eine Antwort' });
});

test('only findings of the known form are shown', () => {
  const list = findingsOf({ findings: [finding('unclear', 'a', null), { kind: 'approve', location: 'a', reason: 'b', suggestion: null, sources: [] },
    { kind: 'other', location: 1, reason: 'b', suggestion: null, sources: [] }] });
  assert.equal(list.length, 1);
  assert.deepEqual(findingsOf(null), []);
});
