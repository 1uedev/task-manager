import { useEffect, useState } from 'react';

// Pages of the navigation. Detail pages (a project, a ticket, a group) hang below one of them.
export const routes = ['start', 'projects', 'search', 'inbox', 'board', 'escalations', 'time', 'wiki', 'requirements', 'accounts', 'groups', 'helpdesk', 'sla', 'jobs', 'tags', 'appearance'] as const;
export type RouteName = (typeof routes)[number];
export const projectTabs = ['tickets', 'members', 'tags', 'settings', 'rights'] as const;
export type ProjectTab = (typeof projectTabs)[number];

export type Route =
  | { readonly name: RouteName }
  | { readonly name: 'project'; readonly id: string; readonly tab: ProjectTab }
  | { readonly name: 'ticket'; readonly projectId: string; readonly number: number }
  | { readonly name: 'group'; readonly id: string }
  // The wiki of a project, with one of its pages when pageId is set (TASK-093).
  | { readonly name: 'wikiPage'; readonly projectId: string; readonly pageId: string | null }
  // The requirements of a project, with one of them when requirementId is set (TASK-096).
  | { readonly name: 'requirementPage'; readonly projectId: string; readonly requirementId: string | null };

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
  if (first === 'wiki' && id !== undefined && ID.test(id)) {
    if (parts.length === 2) return { name: 'wikiPage', projectId: id, pageId: null };
    if (parts.length === 3 && third !== undefined && ID.test(third)) return { name: 'wikiPage', projectId: id, pageId: third };
  }
  if (first === 'requirements' && id !== undefined && ID.test(id)) {
    if (parts.length === 2) return { name: 'requirementPage', projectId: id, requirementId: null };
    if (parts.length === 3 && third !== undefined && ID.test(third)) return { name: 'requirementPage', projectId: id, requirementId: third };
  }
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
    case 'wikiPage':
      return target.pageId === null ? `#/wiki/${target.projectId}` : `#/wiki/${target.projectId}/${target.pageId}`;
    case 'requirementPage':
      return target.requirementId === null ? `#/requirements/${target.projectId}` : `#/requirements/${target.projectId}/${target.requirementId}`;
    default:
      return `#/${target.name}`;
  }
}

// The navigation entry a page belongs to, for marking the current entry.
export function section(route: Route): RouteName {
  if (route.name === 'project' || route.name === 'ticket') return 'projects';
  if (route.name === 'group') return 'groups';
  if (route.name === 'wikiPage') return 'wiki';
  if (route.name === 'requirementPage') return 'requirements';
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
