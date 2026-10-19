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

const name = (file: string) => relative(root, file).split('\\').join('/');

test('browser storage holds personal display preferences only', () => {
  const users = files.filter((file) => /localStorage|sessionStorage|indexedDB/.test(readFileSync(file, 'utf8')));
  assert.deepEqual(users.map(name), ['app/preferences.ts']);
  const text = readFileSync(join(root, 'app/preferences.ts'), 'utf8');
  const keys = [...text.matchAll(/'(taskmanager\.[a-z.]+)'/g)].map((match) => match[1]);
  assert.deepEqual(keys, ['taskmanager.language', 'taskmanager.theme', 'taskmanager.order.navigation', 'taskmanager.order.cards']);
  assert.ok(!/sessionStorage|indexedDB/.test(text));
});

test('the only cookie read is the CSRF token, and no cookie is written', () => {
  const users = files.filter((file) => /document\??\.cookie/.test(readFileSync(file, 'utf8')));
  assert.deepEqual(users.map(name), ['api/csrf.ts']);
  const text = readFileSync(join(root, 'api/csrf.ts'), 'utf8');
  assert.match(text, /'__Host-tm-csrf'/);
  assert.ok(!/document\??\.cookie\s*=[^=]/.test(text));
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
