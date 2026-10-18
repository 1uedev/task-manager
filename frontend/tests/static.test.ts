import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../src/', import.meta.url));

function sources(directory: string): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) return name === 'generated' ? [] : sources(path);
    return /\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

const files = sources(root);

test('source files were found', () => assert.ok(files.length > 10));

test('browser storage is used for the language choice only', () => {
  const users = files.filter((file) => /localStorage|sessionStorage|indexedDB|document\.cookie/.test(readFileSync(file, 'utf8')));
  assert.deepEqual(users.map((file) => relative(root, file).split('\\').join('/')), ['i18n/storage.ts']);
  assert.match(readFileSync(join(root, 'i18n/storage.ts'), 'utf8'), /'taskmanager\.language'/);
});

test('no way to run or inject markup', () => {
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    assert.ok(!/dangerouslySetInnerHTML|innerHTML|\beval\(|new Function\(/.test(text), relative(root, file));
  }
});

test('no hard wired user facing text in components', () => {
  for (const file of files.filter((name) => name.endsWith('.tsx'))) {
    const text = readFileSync(file, 'utf8');
    // Text between tags must be an expression or a symbol, never words. Words come from the catalogs.
    const literals = [...text.matchAll(/>([^<>{}\n]*[A-Za-zÄÖÜäöüß]{2,}[^<>{}\n]*)</g)].map((match) => match[1]);
    assert.deepEqual(literals, [], relative(root, file));
    const attributes = [...text.matchAll(/\b(?:alt|aria-label|title|placeholder)="([^"{]*[A-Za-zÄÖÜäöüß]{2,}[^"]*)"/g)].map((match) => match[1]);
    assert.deepEqual(attributes, [], relative(root, file));
  }
});

test('the only network access goes through the generated client', () => {
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    const name = relative(root, file).split('\\').join('/');
    if (name === 'api/client.ts') continue;
    assert.ok(!/\bfetch\(|XMLHttpRequest|axios/.test(text) || name === 'api/synthetic.ts', name);
  }
});
