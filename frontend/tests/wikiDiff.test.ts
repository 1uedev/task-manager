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
  assert.deepEqual(blocks, [
    { kind: 'heading2', text: 'Titel' },
    { kind: 'bulletList', text: 'eins\nzwei' },
    { kind: 'table', text: 'A | B' },
    { kind: 'image', text: '[Bild]' },
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
