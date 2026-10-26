import { useEffect, useState } from 'react';

// Pages of the navigation. Detail pages (a project, a ticket, a group) hang below one of them.
export const routes = ['start', 'projects', 'search', 'board', 'accounts', 'groups', 'appearance'] as const;
export type RouteName = (typeof routes)[number];
export const projectTabs = ['tickets', 'members', 'tags', 'settings', 'rights'] as const;
export type ProjectTab = (typeof projectTabs)[number];

export type Route =
  | { readonly name: RouteName }
  | { readonly name: 'project'; readonly id: string; readonly tab: ProjectTab }
  | { readonly name: 'ticket'; readonly projectId: string; readonly number: number }
  | { readonly name: 'group'; readonly id: string };

const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// Only identifiers of the expected form leave the address bar; anything else shows the start page.
export function routeFromHash(hash: string): Route {
  const parts = hash.replace(/^#\/?/, '').split('/');
  const [first, id, third, fourth] = parts;
  if (first === 'projects' && id !== undefined && ID.test(id)) {
    if (third === 'tickets' && fourth !== undefined && /^[1-9][0-9]{0,8}$/.test(fourth) && parts.length === 4) {
      return { name: 'ticket', projectId: id, number: Number(fourth) };
    }
    const tab = projectTabs.find((candidate) => candidate === (third ?? 'tickets'));
    if (tab !== undefined && parts.length <= 3) return { name: 'project', id, tab };
  }
  if (first === 'groups' && id !== undefined && ID.test(id) && parts.length === 2) return { name: 'group', id };
  if (parts.length === 1) {
    const name = routes.find((route) => route === first);
    if (name !== undefined) return { name };
  }
  return { name: 'start' };
}

export function hrefFor(route: RouteName | Route): string {
  const target = typeof route === 'string' ? { name: route } as Route : route;
  switch (target.name) {
    case 'project':
      return target.tab === 'tickets' ? `#/projects/${target.id}` : `#/projects/${target.id}/${target.tab}`;
    case 'ticket':
      return `#/projects/${target.projectId}/tickets/${target.number}`;
    case 'group':
      return `#/groups/${target.id}`;
    default:
      return `#/${target.name}`;
  }
}

// The navigation entry a page belongs to, for marking the current entry.
export function section(route: Route): RouteName {
  if (route.name === 'project' || route.name === 'ticket') return 'projects';
  if (route.name === 'group') return 'groups';
  return route.name;
}

// A page with unsaved input registers a guard. When it refuses, the page stays and the previous address is put back.
let guard: (() => boolean) | null = null;
// A navigation the application starts itself (after a save, for example) is never held back by the guard.
let bypass = false;
export function setNavigationGuard(next: (() => boolean) | null): void {
  guard = next;
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => routeFromHash(globalThis.location?.hash ?? ''));
  useEffect(() => {
    let restoring: string | null = null;
    const onChange = (event: HashChangeEvent) => {
      const previous = new URL(event.oldURL).hash;
      if (restoring !== null && globalThis.location.hash === restoring) {
        restoring = null;
        return;
      }
      const forced = bypass;
      bypass = false;
      if (!forced && guard !== null && !guard()) {
        restoring = previous;
        globalThis.location.hash = previous;
        return;
      }
      setRoute(routeFromHash(globalThis.location.hash));
    };
    globalThis.addEventListener('hashchange', onChange);
    return () => globalThis.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

export function navigate(route: Route): void {
  const target = hrefFor(route);
  if (globalThis.location.hash === target) return;
  bypass = true;
  globalThis.location.hash = target;
}
