import { useEffect, useState } from 'react';

// Pages of the navigation. Detail pages (a project, a ticket, a group) hang below one of them.
export const routes = ['start', 'projects', 'search', 'inbox', 'board', 'escalations', 'time', 'wiki', 'requirements', 'accounts', 'groups', 'helpdesk', 'sla', 'jobs', 'tags', 'appearance', 'git'] as const;
export type RouteName = (typeof routes)[number];
export const projectTabs = ['tickets', 'members', 'tags', 'git', 'settings', 'rights'] as const;
export type ProjectTab = (typeof projectTabs)[number];
// The areas of the search (TASK-104): the overview "Alle" and one tab per area.
export const searchAreas = ['all', 'tickets', 'projects', 'wiki', 'requirements'] as const;
export type SearchArea = (typeof searchAreas)[number];

export type Route =
  | { readonly name: RouteName }
  | { readonly name: 'project'; readonly id: string; readonly tab: ProjectTab }
  | { readonly name: 'ticket'; readonly projectId: string; readonly number: number }
  | { readonly name: 'group'; readonly id: string }
  // The wiki of a project, with one of its pages when pageId is set (TASK-093). A hit of the search (TASK-102) also names the
  // language version found and, with published, that the published revision was found, not the draft.
  | { readonly name: 'wikiPage'; readonly projectId: string; readonly pageId: string | null; readonly language?: 'de' | 'en'; readonly published?: boolean }
  // The requirements of a project, with one of them when requirementId is set (TASK-096).
  | { readonly name: 'requirementPage'; readonly projectId: string; readonly requirementId: string | null }
  // One area of the search with the text to search for (TASK-104), for the quick search and "Alle anzeigen".
  | { readonly name: 'searchArea'; readonly area: SearchArea; readonly q: string };

const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// Only identifiers of the expected form leave the address bar; anything else shows the start page.
export function routeFromHash(hash: string): Route {
  const raw = hash.replace(/^#\/?/, '');
  const mark = raw.indexOf('?');
  const parts = (mark < 0 ? raw : raw.slice(0, mark)).split('/');
  const query = new URLSearchParams(mark < 0 ? '' : raw.slice(mark + 1));
  if (parts[0] === 'search' && (parts.length === 2 || (parts.length === 1 && mark >= 0))) {
    const area = searchAreas.find((candidate) => candidate === (parts[1] ?? 'all'));
    if (area !== undefined) return { name: 'searchArea', area, q: (query.get('q') ?? '').slice(0, 200) };
  }
  const [first, id, third, fourth, fifth] = parts;
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
    if ((parts.length === 4 || (parts.length === 5 && fifth === 'published')) && third !== undefined && ID.test(third)
        && (fourth === 'de' || fourth === 'en')) {
      return { name: 'wikiPage', projectId: id, pageId: third, language: fourth, published: parts.length === 5 };
    }
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
      if (target.pageId === null) return `#/wiki/${target.projectId}`;
      return `#/wiki/${target.projectId}/${target.pageId}${target.language === undefined ? '' : `/${target.language}${target.published === true ? '/published' : ''}`}`;
    case 'requirementPage':
      return target.requirementId === null ? `#/requirements/${target.projectId}` : `#/requirements/${target.projectId}/${target.requirementId}`;
    case 'searchArea':
      return `#/search/${target.area}${target.q === '' ? '' : `?q=${encodeURIComponent(target.q)}`}`;
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
  if (route.name === 'searchArea') return 'search';
  return route.name;
}

// A page with unsaved input registers a guard. When it refuses, the page stays and the previous address is put back.
let guard: (() => boolean) | null = null;
// Set by navigate and navigateAsked, so the change of the address they cause is not asked about (again).
let bypass = false;
export function setNavigationGuard(next: (() => boolean) | null): void {
  guard = next;
}

// Set while the application is shown: opens the current address once more as a new visit (TASK-110, F4).
let revisit: (() => void) | null = null;

// The current route and the number of its visit. The visit counts every change of the address and every repeated opening of
// the same address by navigateAsked, so a page keyed by it starts again.
export function useRoute(): readonly [Route, number] {
  const [state, setState] = useState(() => ({ route: routeFromHash(globalThis.location?.hash ?? ''), visit: 0 }));
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
      setState((current) => ({ route: routeFromHash(globalThis.location.hash), visit: current.visit + 1 }));
    };
    globalThis.addEventListener('hashchange', onChange);
    revisit = () => setState((current) => ({ route: current.route, visit: current.visit + 1 }));
    return () => {
      globalThis.removeEventListener('hashchange', onChange);
      revisit = null;
    };
  }, []);
  return [state.route, state.visit] as const;
}

// A navigation the application starts itself (after a save, for example) is never held back by the guard.
export function navigate(route: Route): void {
  const target = hrefFor(route);
  if (globalThis.location.hash === target) return;
  bypass = true;
  globalThis.location.hash = target;
}

// A navigation a person starts outside the page, such as the quick search (TASK-110, F1 and F4): a page with unsaved input
// asks first, like any other change of the page. A search area the person is on already is opened again with the new text;
// any other address the person is on already stays as it is, without asking. Returns false when the person stays.
export function navigateAsked(route: Route): boolean {
  const target = hrefFor(route);
  const same = globalThis.location.hash === target;
  if (same && route.name !== 'searchArea') return true;
  if (guard !== null && !guard()) return false;
  if (same) {
    revisit?.();
    return true;
  }
  bypass = true;
  globalThis.location.hash = target;
  return true;
}
