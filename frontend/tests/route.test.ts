import assert from 'node:assert/strict';
import { test } from 'node:test';
import { hrefFor, routeFromHash, routes } from '../src/app/route.ts';

test('known hashes select their page, anything else falls back to the start page', () => {
  for (const route of routes) assert.equal(routeFromHash(hrefFor(route)), route);
  assert.equal(routeFromHash(''), 'start');
  assert.equal(routeFromHash('#/unknown'), 'start');
  assert.equal(routeFromHash('#/__proto__'), 'start');
});
