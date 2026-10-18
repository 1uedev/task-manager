import assert from 'node:assert/strict';
import { test } from 'node:test';
import { de } from '../src/i18n/de.ts';
import { classifyStatus, problemMessageKey, readProblem } from '../src/api/problem.ts';

const UUID = '0b0e7c8a-6f3a-4d51-9a2e-5c1d7f3b9e10';

test('status classes map to kinds', () => {
  const expected: Record<number, string> = {
    400: 'validation', 401: 'sessionExpired', 403: 'forbidden', 404: 'notFound', 409: 'conflict', 412: 'conflict',
    428: 'preconditionRequired', 500: 'server', 502: 'server', 503: 'unavailable', 418: 'unknown',
  };
  for (const [status, kind] of Object.entries(expected)) assert.equal(classifyStatus(Number(status)), kind, status);
});

test('the problem shape of the backend foundation is understood', () => {
  const body = {
    type: 'about:blank', title: 'Forbidden', status: 403, instance: `urn:uuid:${UUID}`,
    code: 'request.rejected', correlationId: UUID,
  };
  assert.deepEqual(readProblem(403, body), { kind: 'forbidden', status: 403, code: 'request.rejected', correlationId: UUID });
});

test('server texts are never used and malformed values are dropped', () => {
  const info = readProblem(500, { title: '<script>x</script>', code: 'Not A Code!', correlationId: 'x' });
  assert.deepEqual(info, { kind: 'server', status: 500, code: null, correlationId: null });
  assert.deepEqual(readProblem(500, 'plain text'), { kind: 'server', status: 500, code: null, correlationId: null });
  assert.deepEqual(readProblem(500, null), { kind: 'server', status: 500, code: null, correlationId: null });
});

test('the correlation header is used when the body has none', () => {
  assert.equal(readProblem(500, {}, UUID).correlationId, UUID);
  assert.equal(readProblem(500, { correlationId: UUID }, 'other-header-value').correlationId, UUID);
});

test('status specific kinds win, generic ones use a known code, unknown codes fall back to the kind', () => {
  assert.equal(problemMessageKey(readProblem(403, { code: 'request.rejected' })), 'problem.kind.forbidden');
  assert.equal(problemMessageKey(readProblem(400, { code: 'request.invalid' })), 'problem.code.request.invalid');
  assert.equal(problemMessageKey(readProblem(500, { code: 'server.internal_error' })), 'problem.code.server.internal_error');
  assert.equal(problemMessageKey(readProblem(500, { code: 'something.new' })), 'problem.kind.server');
  assert.equal(problemMessageKey(readProblem(412, { code: 'precondition.failed' })), 'problem.kind.conflict');
});

test('every backend error code has a translation key', () => {
  const codes = [
    'request.invalid', 'resource.not_found', 'request.method_not_allowed', 'request.not_acceptable', 'request.too_large',
    'request.unsupported_media_type', 'request.rejected', 'server.unavailable', 'server.internal_error',
  ];
  for (const code of codes) assert.ok(Object.hasOwn(de, `problem.code.${code}`), code);
});

test('every kind has a translation key', () => {
  for (const kind of ['forbidden', 'sessionExpired', 'notFound', 'conflict', 'preconditionRequired', 'validation', 'unavailable', 'server', 'network', 'unknown']) {
    assert.ok(Object.hasOwn(de, `problem.kind.${kind}`), kind);
  }
});
