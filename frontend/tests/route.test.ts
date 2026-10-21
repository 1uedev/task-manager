import assert from 'node:assert/strict';
import { test } from 'node:test';
import { hrefFor, routeFromHash, routes, section } from '../src/app/route.ts';

const ID = '0f8b7c1e-1111-4222-8333-444455556666';

test('known hashes select their page, anything else falls back to the start page', () => {
  for (const route of routes) assert.deepEqual(routeFromHash(hrefFor(route)), { name: route });
  assert.deepEqual(routeFromHash(''), { name: 'start' });
  assert.deepEqual(routeFromHash('#/unknown'), { name: 'start' });
  assert.deepEqual(routeFromHash('#/__proto__'), { name: 'start' });
});

test('detail pages carry checked identifiers only', () => {
  assert.deepEqual(routeFromHash(`#/projects/${ID}`), { name: 'project', id: ID, tab: 'tickets' });
  assert.deepEqual(routeFromHash(`#/projects/${ID}/members`), { name: 'project', id: ID, tab: 'members' });
  assert.deepEqual(routeFromHash(`#/projects/${ID}/tickets/12`), { name: 'ticket', projectId: ID, number: 12 });
  assert.deepEqual(routeFromHash(`#/groups/${ID}`), { name: 'group', id: ID });
  for (const bad of ['#/projects/x', `#/projects/${ID}/tickets/0`, `#/projects/${ID}/tickets/1e3`, `#/projects/${ID}/other`,
    `#/groups/${ID}/x`, `#/projects/${ID.toUpperCase()}`]) {
    assert.deepEqual(routeFromHash(bad), { name: 'start' }, bad);
  }
  for (const route of [{ name: 'project', id: ID, tab: 'tags' }, { name: 'ticket', projectId: ID, number: 3 }, { name: 'group', id: ID }] as const) {
    assert.deepEqual(routeFromHash(hrefFor(route)), route);
  }
  assert.equal(section({ name: 'ticket', projectId: ID, number: 3 }), 'projects');
  assert.equal(section({ name: 'group', id: ID }), 'groups');
});
