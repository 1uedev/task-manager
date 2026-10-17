import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { probeClient, checkedProbe } from '../src/client.ts';

const fixture = name => readFileSync(new URL(`../../backend/target/contract-fixtures/${name}.json`, import.meta.url), 'utf8');
const id = '00000000-0000-4000-8000-000000000021';
test('GET consumes Java JSON, Unicode, nullable field and ETag', async () => {
  const client = probeClient(async request => {
    assert.equal(request.method, 'GET');
    assert.equal(new URL(request.url).pathname, `/_qualification/probes/${id}`);
    return new Response(fixture('success'), {status: 200, headers: {'Content-Type': 'application/json', ETag: '"representation-v1"'}});
  });
  const { data, error, response } = await client.GET('/_qualification/probes/{probeId}', {params: {path: {probeId: id}}});
  assert.equal(error, undefined);
  const probe = checkedProbe(data);
  assert.equal(probe.label, 'Prüfdatensatz');
  assert.equal(probe.version, 'opaque-v1');
  assert.equal(probe.note, null);
  assert.equal(response.headers.get('ETag'), '"representation-v1"');
});
test('POST carries the repetition key and handles 201 with Location', async () => {
  const client = probeClient(async request => {
    assert.equal(request.headers.get('Idempotency-Key'), 'synthetic-key');
    assert.equal((await request.json()).label, 'Probe');
    return new Response(fixture('success'), {status: 201, headers: {'Content-Type': 'application/json', Location: `/_qualification/probes/${id}`}});
  });
  const {data, response} = await client.POST('/_qualification/probes', {params: {header: {'Idempotency-Key': 'synthetic-key'}}, body: {label: 'Probe'}});
  assert.equal(checkedProbe(data).id, id);
  assert.equal(response.status, 201);
  assert.equal(response.headers.get('Location'), `/_qualification/probes/${id}`);
});
test('PATCH preserves If-Match and returns Java problem+json as an error', async () => {
  const client = probeClient(async request => {
    assert.equal(request.method, 'PATCH');
    assert.equal(request.headers.get('If-Match'), '"stale"');
    return new Response(fixture('conflict'), {status: 412, headers: {'Content-Type': 'application/problem+json'}});
  });
  const {data, error, response} = await client.PATCH('/_qualification/probes/{probeId}', {params: {path: {probeId: id}, header: {'If-Match': '"stale"'}}, body: {label: 'New'}});
  assert.equal(data, undefined);
  assert.equal(response.status, 412);
  assert.equal(error.status, 412);
  assert.equal(error.code, 'VERSION_CONFLICT');
});
test('403 remains an error without successful application data', async () => {
  const client = probeClient(async () => new Response(fixture('forbidden'), {status: 403, headers: {'Content-Type': 'application/problem+json'}}));
  const {data, error} = await client.GET('/_qualification/probes/{probeId}', {params: {path: {probeId: id}}});
  assert.equal(data, undefined);
  assert.equal(error.code, 'FORBIDDEN');
});
test('network failure is not converted into an empty success', async () => {
  const client = probeClient(async () => {throw new Error('synthetic transport failure');});
  await assert.rejects(client.GET('/_qualification/probes/{probeId}', {params: {path: {probeId: id}}}), /synthetic transport failure/);
});
test('runtime malformed shape is rejected despite generated static types', () => {
  assert.throws(() => checkedProbe({id, label: 'Probe', version: 21}), /Invalid probe/);
  assert.throws(() => checkedProbe(null), /Invalid probe/);
});
