import assert from 'node:assert/strict';
import { test } from 'node:test';
import { blocksOf, diffBlocks, differs, type DocNode } from '../src/pages/wikiDiff.ts';

const p = (text: string): DocNode => ({ type: 'paragraph', content: [{ type: 'text', text }] });
const doc = (...content: DocNode[]): DocNode => ({ type: 'doc', content });

test('blocks carry kind and plain text, lists and tables included', () => {
  const blocks = blocksOf(doc(
    { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Titel' }] },
    { type: 'bulletList', content: [{ type: 'listItem', content: [p('eins')] }, { type: 'listItem', content: [p('zwei')] }] },
    { type: 'table', content: [{ type: 'tableRow', content: [{ type: 'tableHeader', content: [p('A')] }, { type: 'tableCell', content: [p('B')] }] }] },
    { type: 'image', attrs: { src: '/x', alt: 'Bild' } },
  ));
  assert.deepEqual(blocks.map(({ kind, text, refs }) => ({ kind, text, refs })), [
    { kind: 'heading2', text: 'Titel', refs: [] },
    { kind: 'bulletList', text: 'eins\nzwei', refs: [] },
    { kind: 'table', text: 'A | B', refs: [] },
    { kind: 'image', text: '[Bild]', refs: ['/x'] },
  ]);
});

test('the comparison names added, removed and changed blocks and keeps the order', () => {
  const before = blocksOf(doc(p('a'), p('b'), p('c'), p('d')));
  const after = blocksOf(doc(p('a'), p('B'), p('c'), p('e'), p('f')));
  assert.deepEqual(diffBlocks(before, after).map((entry) => entry.op), ['same', 'changed', 'same', 'changed', 'added']);
  assert.deepEqual(diffBlocks(blocksOf(doc(p('a'))), blocksOf(doc())).map((entry) => entry.op), ['removed']);
  assert.deepEqual(diffBlocks([], blocksOf(doc(p('x')))).map((entry) => entry.op), ['added']);
});

test('equal documents do not differ, a changed heading level does', () => {
  assert.equal(differs(doc(p('a')), doc(p('a'))), false);
  const h = (level: number): DocNode => ({ type: 'heading', attrs: { level }, content: [{ type: 'text', text: 'x' }] });
  assert.equal(differs(doc(h(1)), doc(h(2))), true);
  assert.equal(differs(null, doc()), false);
});

const linked = (href: string): DocNode => ({ type: 'paragraph', content: [{ type: 'text', text: 'Anleitung', marks: [{ type: 'link', attrs: { href } }] }] });
const image = (src: string): DocNode => ({ type: 'image', attrs: { src, alt: 'Plan' } });

test('a changed link target, an exchanged image and a pure format change count as changes', () => {
  assert.equal(differs(doc(linked('https://one.example')), doc(linked('https://two.example'))), true);
  assert.equal(differs(doc(image('/api/v1/wiki/images/a')), doc(image('/api/v1/wiki/images/b'))), true);
  const bold: DocNode = { type: 'paragraph', content: [{ type: 'text', text: 'Anleitung', marks: [{ type: 'bold' }] }] };
  assert.equal(differs(doc(p('Anleitung')), doc(bold)), true);
  const start = (value: number): DocNode => ({ type: 'orderedList', attrs: { start: value }, content: [{ type: 'listItem', content: [p('x')] }] });
  assert.equal(differs(doc(start(1)), doc(start(10000))), true);
  const [entry] = diffBlocks(blocksOf(doc(linked('https://one.example'))), blocksOf(doc(linked('https://two.example'))));
  assert.equal(entry?.op, 'changed');
  assert.ok(entry?.op === 'changed' && entry.before.refs[0] === 'https://one.example' && entry.after.refs[0] === 'https://two.example');
});

test('what the server drops or splits differently is no change', () => {
  // The editor carries further link and image attributes and may split a text run; the server keeps neither.
  const editor: DocNode = { type: 'paragraph', content: [
    { type: 'text', text: 'Anlei', marks: [{ type: 'link', attrs: { href: 'https://one.example', target: null, rel: 'noopener', class: null } }] },
    { type: 'text', text: 'tung', marks: [{ type: 'link', attrs: { href: 'https://one.example' } }] },
  ] };
  assert.equal(differs(doc(editor), doc(linked('https://one.example'))), false);
  assert.equal(differs(doc({ type: 'image', attrs: { src: '/i', alt: 'Plan', title: null, width: null } }), doc(image('/i'))), false);
  assert.equal(differs(doc({ type: 'orderedList', attrs: { start: 1, type: null }, content: [] }), doc({ type: 'orderedList', content: [] })), false);
});
