import { useEffect, useState } from 'react';

export const routes = ['start', 'board', 'states', 'form', 'appearance'] as const;
export type Route = (typeof routes)[number];

export function routeFromHash(hash: string): Route {
  const name = hash.replace(/^#\/?/, '');
  return routes.find((route) => route === name) ?? 'start';
}

export function hrefFor(route: Route): string {
  return `#/${route}`;
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => routeFromHash(globalThis.location?.hash ?? ''));
  useEffect(() => {
    const onChange = () => setRoute(routeFromHash(globalThis.location.hash));
    globalThis.addEventListener('hashchange', onChange);
    return () => globalThis.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
