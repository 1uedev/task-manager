import assert from 'node:assert/strict';
import { test } from 'node:test';
import { changeAccentColor, createServerApi, parseAppearance, parseSession, readAppearance, readSession, removeLogo, replaceLogo } from '../src/api/client.ts';
import { readCsrfToken } from '../src/api/csrf.ts';
import { restoreOrder } from '../src/app/preferences.ts';
import { move } from '../src/app/useReorder.ts';
import { COLUMNS, moveCard, type Board } from '../src/pages/board.ts';

const TOKEN = 'abcdefghijklmnopqrstuvwxyz012345';
const APPEARANCE = { accentColor: '#F97316', defaultAccentColor: '#f97316', logo: null, version: 3, updatedAt: '2026-10-19T06:00:00Z' };

test('a stored order keeps known entries only, once each, and adds new ones at the end', () => {
  const known = ['start', 'board', 'states', 'form'] as const;
  assert.deepEqual(restoreOrder('["form","start"]', known), ['form', 'start', 'board', 'states']);
  assert.deepEqual(restoreOrder('["x","form","form","__proto__"]', known), ['form', 'start', 'board', 'states']);
  for (const broken of [null, '', '{', '"form"', '{"0":"form"}', '[1,2]']) assert.deepEqual(restoreOrder(broken, known), [...known]);
});

test('moving within a list keeps every entry', () => {
  assert.deepEqual(move(['a', 'b', 'c'], 0, 2), ['b', 'c', 'a']);
  assert.deepEqual(move(['a', 'b', 'c'], 2, 0), ['c', 'a', 'b']);
  assert.deepEqual(move(['a', 'b', 'c'], 1, 9), ['a', 'c', 'b']);
  assert.deepEqual(move(['a', 'b', 'c'], 5, 0), ['a', 'b', 'c']);
});

test('the sample board moves a card between columns without losing or duplicating cards', () => {
  const card = (id: string) => ({ id, title: 'board.sample.1' as const, tag: 'ui' as const, key: id, initials: 'AK' });
  const board: Board = { todo: [card('a'), card('b')], doing: [card('c')], review: [], done: [] };
  const moved = moveCard(board, 'a', 'review', 0);
  assert.deepEqual(COLUMNS.map((column) => moved[column].map((item) => item.id)), [['b'], ['c'], ['a'], []]);
  assert.deepEqual(moveCard(board, 'b', 'todo', 0).todo.map((item) => item.id), ['b', 'a']);
  assert.equal(moveCard(board, 'unknown', 'done', 0), board);
});

test('only a well formed CSRF cookie is read', () => {
  assert.equal(readCsrfToken(`other=1; __Host-tm-csrf=${TOKEN}; x=y`), TOKEN);
  assert.equal(readCsrfToken('__Host-tm-csrf=short'), null);
  assert.equal(readCsrfToken('__Host-tm-csrf=a b<script>aaaaaaaaaaaaaa'), null);
  assert.equal(readCsrfToken('x__Host-tm-csrf=' + TOKEN), null);
  assert.equal(readCsrfToken(''), null);
});

test('session and appearance answers are checked again at runtime', () => {
  assert.deepEqual(parseSession({ authenticated: true, level: 'full', rights: ['appearance.manage', 'accounts.manage'] }), { authenticated: true, full: true, rights: ['appearance.manage', 'accounts.manage'], username: null, displayName: null, passwordChangeBy: null });
  assert.deepEqual(parseSession({ authenticated: true, level: 'password_change_required', rights: [] }), { authenticated: true, full: false, rights: [], username: null, displayName: null, passwordChangeBy: null });
  // No list, or one that is not a list of texts, means no right; a state without the flag is not a state at all.
  assert.deepEqual(parseSession({ authenticated: true, level: 'full' }), { authenticated: true, full: true, rights: [], username: null, displayName: null, passwordChangeBy: null });
  assert.deepEqual(parseSession({ authenticated: true, level: 'full', rights: ['appearance.manage', 7, null] }), { authenticated: true, full: true, rights: ['appearance.manage'], username: null, displayName: null, passwordChangeBy: null });
  assert.deepEqual(parseSession({ authenticated: true, level: 'full', rights: 'appearance.manage' }), { authenticated: true, full: true, rights: [], username: null, displayName: null, passwordChangeBy: null });
  assert.equal(parseSession({ authenticated: 'yes', rights: [] }), null);

  assert.equal(parseAppearance(APPEARANCE)?.accentColor, '#f97316');
  for (const broken of [
    { ...APPEARANCE, accentColor: 'red;}body{display:none' },
    { ...APPEARANCE, version: 0 },
    { ...APPEARANCE, logo: { contentType: 'image/svg+xml', size: 10, url: '/api/v1/appearance/logo?v=3' } },
    { ...APPEARANCE, logo: { contentType: 'image/png', size: 10, url: 'https://elsewhere.invalid/logo.png' } },
    { ...APPEARANCE, logo: { contentType: 'image/png', size: 10, url: '/\\elsewhere.invalid/logo.png' } },
    { ...APPEARANCE, logo: { contentType: 'image/png', size: 10_000_000, url: '/api/v1/appearance/logo?v=3' } },
  ]) assert.equal(parseAppearance(broken), null, JSON.stringify(broken));
  const withLogo = parseAppearance({ ...APPEARANCE, logo: { contentType: 'image/png', size: 10, url: '/api/v1/appearance/logo?v=3' } });
  assert.equal(withLogo?.logo?.url, '/api/v1/appearance/logo?v=3');
});

function recorder(status = 200, body: unknown = APPEARANCE) {
  const seen: Request[] = [];
  const fetcher: typeof fetch = async (input) => {
    seen.push(input as Request);
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json', ETag: '"4"' } });
  };
  return { api: createServerApi(fetcher, 'http://localhost'), seen };
}

test('reads go to the versioned API and return the version separately', async () => {
  const { api, seen } = recorder();
  const outcome = await readAppearance(api);
  assert.equal(outcome.kind === 'ok' && outcome.data.etag, '"4"');
  assert.equal(seen[0]?.url, 'http://localhost/api/v1/appearance');
  const session = recorder(200, { authenticated: false });
  assert.equal((await readSession(session.api)).kind, 'ok');
  assert.equal(session.seen[0]?.url, 'http://localhost/api/v1/session');
});

test('changes carry If-Match and the CSRF token, and a logo is sent as raw image bytes', async () => {
  const global = globalThis as { document?: { cookie: string } };
  global.document = { cookie: `__Host-tm-csrf=${TOKEN}` };
  try {
    const { api, seen } = recorder();
    await changeAccentColor(api, '"3"', '#1d4ed8');
    await replaceLogo(api, '"4"', new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' }), 'image/png');
    await removeLogo(api, '"5"');
    assert.deepEqual(seen.map((request) => [request.method, new URL(request.url).pathname, request.headers.get('If-Match'), request.headers.get('X-CSRF-Token')]), [
      ['PUT', '/api/v1/appearance', '"3"', TOKEN],
      ['PUT', '/api/v1/appearance/logo', '"4"', TOKEN],
      ['DELETE', '/api/v1/appearance/logo', '"5"', TOKEN],
    ]);
    assert.deepEqual(await seen[0]?.json(), { accentColor: '#1d4ed8' });
    assert.equal(seen[1]?.headers.get('Content-Type'), 'image/png');
    assert.deepEqual([...new Uint8Array(await seen[1]!.arrayBuffer())], [0x89, 0x50, 0x4e, 0x47]);
  } finally {
    delete global.document;
  }
});

test('a refused change is a classified problem, not a crash', async () => {
  const { api } = recorder(412, { code: 'appearance.version_conflict', status: 412, correlationId: '0f8b7c1e-1111-4222-8333-444455556666' });
  const outcome = await changeAccentColor(api, '"1"', '#123456');
  assert.equal(outcome.kind === 'problem' && outcome.problem.kind, 'conflict');
  assert.equal(outcome.kind === 'problem' && outcome.problem.code, 'appearance.version_conflict');
  const invalid = await changeAccentColor(recorder(200, { accentColor: 'x' }).api, '"1"', '#123456');
  assert.equal(invalid.kind, 'problem');
});
