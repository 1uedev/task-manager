import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createApi, parseProbe, readProbe, updateProbe } from '../src/api/client.ts';
import { SYNTHETIC_ID, scenarioIds, syntheticFetch, type ScenarioId } from '../src/api/synthetic.ts';

const BASE = 'http://localhost';

function api(scenario: ScenarioId) {
  return createApi(syntheticFetch(scenario), BASE);
}

test('success returns the probe and the representation version separately', async () => {
  const outcome = await readProbe(api('success'), SYNTHETIC_ID);
  assert.equal(outcome.kind, 'ok');
  if (outcome.kind !== 'ok') return;
  assert.equal(outcome.data.probe.version, 'v7');
  assert.equal(outcome.data.etag, '"synthetic-1"');
  assert.notEqual(outcome.data.etag, outcome.data.probe.version);
});

const expectations: readonly [ScenarioId, string, number | null, string | null][] = [
  ['forbidden', 'forbidden', 403, 'request.rejected'],
  ['notFound', 'notFound', 404, 'resource.not_found'],
  ['serverError', 'server', 500, 'server.internal_error'],
  ['unavailable', 'unavailable', 503, 'server.unavailable'],
];

for (const [scenario, kind, status, code] of expectations) {
  test(`read problem ${scenario} is classified`, async () => {
    const outcome = await readProbe(api(scenario), SYNTHETIC_ID);
    assert.equal(outcome.kind, 'problem');
    if (outcome.kind !== 'problem') return;
    assert.deepEqual(
      { kind: outcome.problem.kind, status: outcome.problem.status, code: outcome.problem.code },
      { kind, status, code },
    );
    assert.match(outcome.problem.correlationId ?? '', /^[0-9a-f-]{36}$/);
  });
}

test('write conflicts and missing preconditions are told apart', async () => {
  const conflict = await updateProbe(api('conflict'), SYNTHETIC_ID, 'x', '"v"');
  const required = await updateProbe(api('preconditionRequired'), SYNTHETIC_ID, 'x', '"v"');
  assert.equal(conflict.kind === 'problem' && conflict.problem.kind, 'conflict');
  assert.equal(required.kind === 'problem' && required.problem.kind, 'preconditionRequired');
});

test('the If-Match header carries the representation version', async () => {
  let seen: string | null = null;
  const fetcher: typeof fetch = async (input) => {
    seen = (input as Request).headers.get('If-Match');
    return new Response(JSON.stringify({ id: SYNTHETIC_ID, label: 'x', version: 'v8' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  await updateProbe(createApi(fetcher, BASE), SYNTHETIC_ID, 'x', '"abc"');
  assert.equal(seen, '"abc"');
});

test('a network failure becomes a network problem', async () => {
  const outcome = await readProbe(api('network'), SYNTHETIC_ID);
  assert.deepEqual(outcome, { kind: 'problem', problem: { kind: 'network', status: null, code: null, correlationId: null } });
});

test('a response that violates the contract is not trusted', async () => {
  const fetcher: typeof fetch = async () =>
    new Response(JSON.stringify({ id: 1, label: 'x' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  const outcome = await readProbe(createApi(fetcher, BASE), SYNTHETIC_ID);
  assert.equal(outcome.kind === 'problem' && outcome.problem.kind, 'unknown');
});

test('an error page that is not JSON still produces a usable problem', async () => {
  const fetcher: typeof fetch = async () => new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } });
  const outcome = await readProbe(createApi(fetcher, BASE), SYNTHETIC_ID);
  assert.equal(outcome.kind === 'problem' && outcome.problem.kind, 'server');
});

test('cancelling a slow request raises an abort instead of a problem', async () => {
  const controller = new AbortController();
  const pending = readProbe(api('slow'), SYNTHETIC_ID, controller.signal);
  controller.abort();
  await assert.rejects(pending, { name: 'AbortError' });
});

test('runtime guard accepts the contract shape only', () => {
  assert.ok(parseProbe({ id: 'a', label: 'b', version: 'c', note: null }));
  assert.ok(parseProbe({ id: 'a', label: 'b', version: 'c' }));
  assert.equal(parseProbe({ id: 'a', label: 'b', version: 3 }), null);
  assert.equal(parseProbe({ id: 'a', label: 'b', version: 'c', note: 5 }), null);
  assert.equal(parseProbe('x'), null);
  assert.equal(parseProbe(null), null);
});

test('all scenarios are covered by an explicit expectation', () => {
  const handled = new Set<ScenarioId>(['success', 'empty', 'slow', 'network', 'conflict', 'preconditionRequired', ...expectations.map(([id]) => id)]);
  assert.deepEqual([...handled].sort(), [...scenarioIds].sort());
});
