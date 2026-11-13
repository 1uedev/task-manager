import createClient from 'openapi-fetch';
import { acceptableLogo, isAccentColor } from '../brand/brand.ts';
import { readCsrfToken } from './csrf.ts';
import type { components, paths } from './generated/api';
import type { paths as AppearancePaths } from './generated/appearance';
import type { paths as FilesPaths } from './generated/files';
import type { paths as GitPaths } from './generated/git';
import type { paths as HelpdeskPaths } from './generated/helpdesk';
import type { paths as JobsPaths } from './generated/jobs';
import type { paths as SlaPaths } from './generated/sla';
import type { paths as TimePaths } from './generated/time';
import type { paths as WikiPaths } from './generated/wiki';
import type { paths as RequirementsPaths } from './generated/requirements';
import type { paths as PortalPaths } from './generated/portal';
import type { paths as IdentityPaths } from './generated/identity';
import type { paths as PermissionsPaths } from './generated/permissions';
import type { paths as ProjectsPaths } from './generated/projects';
import type { paths as SearchPaths } from './generated/search';
import type { paths as TagsPaths } from './generated/tags';
import type { paths as TicketsPaths } from './generated/tickets';
import { networkProblem, readProblem, type ProblemInfo } from './problem.ts';

export type Probe = components['schemas']['Probe'];
export type Api = ReturnType<typeof createApi>;

export function createApi(fetcher: typeof fetch = (...args) => globalThis.fetch(...args), baseUrl?: string) {
  return createClient<paths>({ baseUrl: baseUrl ?? globalThis.location?.origin ?? 'http://localhost', fetch: fetcher });
}

export type Outcome<T> =
  | { readonly kind: 'ok'; readonly data: T }
  | { readonly kind: 'problem'; readonly problem: ProblemInfo };

// Generated types are compile time checks only. Responses are checked again at runtime.
export function parseProbe(value: unknown): Probe | null {
  if (typeof value !== 'object' || value === null) return null;
  const item = value as Record<string, unknown>;
  if (typeof item.id !== 'string' || typeof item.label !== 'string' || typeof item.version !== 'string') return null;
  if (item.note !== undefined && item.note !== null && typeof item.note !== 'string') return null;
  return item as unknown as Probe;
}

export const INVALID_RESPONSE: ProblemInfo = { kind: 'unknown', status: null, code: null, correlationId: null };

export async function guarded<T>(call: () => Promise<Outcome<T>>): Promise<Outcome<T>> {
  try {
    return await call();
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    return { kind: 'problem', problem: networkProblem };
  }
}

export interface LoadedProbe {
  readonly probe: Probe;
  readonly etag: string | null;
}

export function readProbe(api: Api, id: string, signal?: AbortSignal): Promise<Outcome<LoadedProbe>> {
  return guarded(async () => {
    const { data, error, response } = await api.GET('/_qualification/probes/{probeId}', {
      params: { path: { probeId: id } },
      ...(signal ? { signal } : {}),
    });
    if (!response.ok) {
      return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
    }
    const probe = parseProbe(data);
    if (!probe) return { kind: 'problem', problem: INVALID_RESPONSE };
    return { kind: 'ok', data: { probe, etag: response.headers.get('ETag') } };
  });
}

// `etag` is the representation version from the last read; it is sent as If-Match and is not the business version.
export function updateProbe(api: Api, id: string, label: string, etag: string, signal?: AbortSignal): Promise<Outcome<LoadedProbe>> {
  return guarded(async () => {
    const { data, error, response } = await api.PATCH('/_qualification/probes/{probeId}', {
      params: { path: { probeId: id }, header: { 'If-Match': etag } },
      body: { label },
      ...(signal ? { signal } : {}),
    });
    if (!response.ok) {
      return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
    }
    const probe = parseProbe(data);
    if (!probe) return { kind: 'problem', problem: INVALID_RESPONSE };
    return { kind: 'ok', data: { probe, etag: response.headers.get('ETag') } };
  });
}

// ---------------------------------------------------------------- session and company appearance (TASK-036)

export type ServerApi = ReturnType<typeof createServerApi>;

// Listeners told when the server says the session is gone (401 on anything but the session state itself), so the
// interface can return to the login and keep what the person typed in the page that is still open.
const sessionLostListeners = new Set<() => void>();

export function onSessionLost(listener: () => void): () => void {
  sessionLostListeners.add(listener);
  return () => sessionLostListeners.delete(listener);
}

// Listeners told when the server refuses a request with 403: the rights of the session may have changed since they were
// read, so whatever the interface derives from them (navigation, offered actions) has to be read again (finding F6 of the
// review of TASK-030).
const forbiddenListeners = new Set<() => void>();

export function onForbidden(listener: () => void): () => void {
  forbiddenListeners.add(listener);
  return () => forbiddenListeners.delete(listener);
}

const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);

// Every changing request repeats the CSRF token the server set; a 401 outside the session endpoint ends the login, a 403
// there reads the rights of the session again.
export function withSession(fetcher: typeof fetch): typeof fetch {
  return async (input, init) => {
    const request = input instanceof Request ? input : new Request(input, init);
    if (!SAFE.has(request.method) && !request.headers.has('X-CSRF-Token')) {
      const token = readCsrfToken();
      if (token !== null) request.headers.set('X-CSRF-Token', token);
    }
    const response = await fetcher(request);
    const sessionState = new URL(request.url).pathname.endsWith('/api/v1/session');
    if (response.status === 401 && !sessionState) {
      for (const listener of sessionLostListeners) listener();
    }
    if (response.status === 403 && !sessionState) {
      for (const listener of forbiddenListeners) listener();
    }
    return response;
  };
}

export function createServerApi(fetcher: typeof fetch = (...args) => globalThis.fetch(...args), origin?: string) {
  const baseUrl = `${origin ?? globalThis.location?.origin ?? 'http://localhost'}/api/v1`;
  const fetch = withSession(fetcher);
  return {
    identity: createClient<IdentityPaths>({ baseUrl, fetch }),
    appearance: createClient<AppearancePaths>({ baseUrl, fetch }),
    projects: createClient<ProjectsPaths>({ baseUrl, fetch }),
    tickets: createClient<TicketsPaths>({ baseUrl, fetch }),
    tags: createClient<TagsPaths>({ baseUrl, fetch }),
    search: createClient<SearchPaths>({ baseUrl, fetch }),
    permissions: createClient<PermissionsPaths>({ baseUrl, fetch }),
    files: createClient<FilesPaths>({ baseUrl, fetch }),
    jobs: createClient<JobsPaths>({ baseUrl, fetch }),
    helpdesk: createClient<HelpdeskPaths>({ baseUrl, fetch }),
    sla: createClient<SlaPaths>({ baseUrl, fetch }),
    time: createClient<TimePaths>({ baseUrl, fetch }),
    wiki: createClient<WikiPaths>({ baseUrl, fetch }),
    requirements: createClient<RequirementsPaths>({ baseUrl, fetch }),
    portal: createClient<PortalPaths>({ baseUrl, fetch }),
    git: createClient<GitPaths>({ baseUrl, fetch }),
    // A bare HEAD below the API with the session rules above, for a question the contracts answer without a body (review F4 of TASK-051).
    head: (path: string) => fetch(`${baseUrl}${path}`, { method: 'HEAD', credentials: 'same-origin', cache: 'no-store' }),
  };
}

export interface SessionState {
  readonly authenticated: boolean;
  readonly username: string | null;
  readonly displayName: string | null;
  // End of the restricted session that may only change the one time password.
  readonly passwordChangeBy: string | null;
  // Only a complete login counts; a login that still has to change its one time password does not.
  readonly full: boolean;
  // Keys of the administrative rights the session holds, as the server lists them. Only a hint for what to show: the server
  // checks every change again and answers 403 without the right.
  readonly rights: readonly string[];
  // A customer account of the portal (TASK-075): it sees the portal pages instead of the internal interface.
  readonly portal: boolean;
}

export const APPEARANCE_RIGHT = 'appearance.manage';

export function parseSession(value: unknown): SessionState | null {
  if (typeof value !== 'object' || value === null) return null;
  const item = value as Record<string, unknown>;
  if (typeof item.authenticated !== 'boolean') return null;
  // The list is only sent for a signed in request; anything that is not a list of texts counts as no right.
  const rights = Array.isArray(item.rights) ? item.rights.filter((right): right is string => typeof right === 'string') : [];
  const text = (value: unknown) => (typeof value === 'string' ? value : null);
  return {
    authenticated: item.authenticated,
    username: text(item.username),
    displayName: text(item.displayName),
    passwordChangeBy: text(item.passwordChangeBy),
    full: item.authenticated && item.level === 'full',
    rights,
    portal: item.kind === 'portal',
  };
}

export interface AppearanceLogo {
  readonly contentType: string;
  readonly size: number;
  readonly url: string;
}

export interface Appearance {
  readonly name: string;
  readonly defaultName: string;
  readonly accentColor: string;
  readonly defaultAccentColor: string;
  readonly logo: AppearanceLogo | null;
  readonly version: number;
}

export interface LoadedAppearance {
  readonly appearance: Appearance;
  readonly etag: string;
}

export const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp'] as const;
export type LogoType = (typeof LOGO_TYPES)[number];
export const LOGO_MAX_BYTES = 512 * 1024;

export function isLogoType(value: string): value is LogoType {
  return LOGO_TYPES.some((type) => type === value);
}

// The logo address must be a path on the own server; the server only ever sends its own path.
export function parseAppearance(value: unknown): Appearance | null {
  if (typeof value !== 'object' || value === null) return null;
  const item = value as Record<string, unknown>;
  if (!isAccentColor(item.accentColor) || !isAccentColor(item.defaultAccentColor)) return null;
  if (!isBrandName(item.name) || !isBrandName(item.defaultName)) return null;
  if (typeof item.version !== 'number' || !Number.isSafeInteger(item.version) || item.version < 1) return null;
  let logo: AppearanceLogo | null = null;
  if (item.logo !== null) {
    if (typeof item.logo !== 'object' || item.logo === undefined) return null;
    const raw = item.logo as Record<string, unknown>;
    const url = typeof raw.url === 'string' && raw.url.startsWith('/') ? acceptableLogo(raw.url) : null;
    if (url === null || typeof raw.contentType !== 'string' || !isLogoType(raw.contentType)) return null;
    if (typeof raw.size !== 'number' || raw.size < 1 || raw.size > LOGO_MAX_BYTES) return null;
    logo = { contentType: raw.contentType, size: raw.size, url };
  }
  return {
    name: item.name,
    defaultName: item.defaultName,
    accentColor: item.accentColor.toLowerCase(),
    defaultAccentColor: item.defaultAccentColor.toLowerCase(),
    logo,
    version: item.version,
  };
}

export const BRAND_NAME_MAX = 80;

// The rule of the name, character by character the same as AppearanceService.EDGE and FORBIDDEN on the server and the check
// of V0054 in the database (TASK-087, finding F1 of docs/REVIEW_TASK-081_PR-243.md), so a stored name is never refused here.
// Spaces of every kind, the protected ones included, are removed at both ends; control characters, line and paragraph
// separators, format characters (Unicode 15.1, Cf) and halves of a surrogate pair are refused anywhere.
const NAME_EDGES = /^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$/gu;
const NAME_FORBIDDEN = /[\u0000-\u001f\u007f-\u009f\u00ad\u0600-\u0605\u061c\u06dd\u070f\u0890\u0891\u08e2\u180e\u200b-\u200f\u2028-\u202e\u2060-\u2064\u2066-\u206f\ufeff\ufff9-\ufffb\u{110bd}\u{110cd}\u{13430}-\u{1343f}\u{1bca0}-\u{1bca3}\u{1d173}-\u{1d17a}\u{e0001}\u{e0020}-\u{e007f}\ud800-\udfff]/u;

// The name as the server will store it: without the spaces at its edges.
export function normalizeBrandName(value: string): string {
  return value.replace(NAME_EDGES, '');
}

// A name as the server stores it: 1 to 80 characters after the edges are removed, none of the refused characters (TASK-081, TASK-087).
export function isBrandName(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  const length = [...value].length;
  return length >= 1 && length <= BRAND_NAME_MAX && value === normalizeBrandName(value) && !NAME_FORBIDDEN.test(value);
}

// What the login page shows before anyone is signed in (TASK-081): name, accent color and the public logo, nothing else.
export interface PublicAppearance {
  readonly name: string;
  readonly accentColor: string;
  readonly logoUrl: string | null;
}

export function parsePublicAppearance(value: unknown): PublicAppearance | null {
  if (typeof value !== 'object' || value === null) return null;
  const item = value as Record<string, unknown>;
  if (!isBrandName(item.name) || !isAccentColor(item.accentColor)) return null;
  let logoUrl: string | null = null;
  if (item.logoUrl !== null) {
    logoUrl = typeof item.logoUrl === 'string' && item.logoUrl.startsWith('/') ? acceptableLogo(item.logoUrl) : null;
    if (logoUrl === null) return null;
  }
  return { name: item.name, accentColor: item.accentColor.toLowerCase(), logoUrl };
}

export function readPublicAppearance(api: ServerApi, signal?: AbortSignal): Promise<Outcome<PublicAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.GET('/appearance/public', { ...(signal ? { signal } : {}) });
    if (!response.ok) {
      return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
    }
    const parsed = parsePublicAppearance(data);
    return parsed ? { kind: 'ok', data: parsed } : { kind: 'problem', problem: INVALID_RESPONSE };
  });
}

function csrfHeader(): Record<string, string> {
  const token = readCsrfToken();
  return token === null ? {} : { 'X-CSRF-Token': token };
}

function loaded(response: Response, data: unknown, error: unknown): Outcome<LoadedAppearance> {
  if (!response.ok) {
    return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
  }
  const appearance = parseAppearance(data);
  const etag = response.headers.get('ETag');
  if (!appearance || etag === null) return { kind: 'problem', problem: INVALID_RESPONSE };
  return { kind: 'ok', data: { appearance, etag } };
}

export function readSession(api: ServerApi, signal?: AbortSignal): Promise<Outcome<SessionState>> {
  return guarded(async () => {
    const { data, error, response } = await api.identity.GET('/session', { ...(signal ? { signal } : {}) });
    if (!response.ok) {
      return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
    }
    const session = parseSession(data);
    return session ? { kind: 'ok', data: session } : { kind: 'problem', problem: INVALID_RESPONSE };
  });
}

export function readAppearance(api: ServerApi, signal?: AbortSignal): Promise<Outcome<LoadedAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.GET('/appearance', { ...(signal ? { signal } : {}) });
    return loaded(response, data, error);
  });
}

// `etag` is the version of the last read; the server refuses a change against an older one (412).
export function changeAccentColor(api: ServerApi, etag: string, accentColor: string): Promise<Outcome<LoadedAppearance>> {
  return changeAppearance(api, etag, { accentColor });
}

// A part that is not sent stays as it is on the server (TASK-081).
export function changeAppearance(api: ServerApi, etag: string, change: { readonly accentColor?: string; readonly name?: string }): Promise<Outcome<LoadedAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.PUT('/appearance', {
      params: { header: { 'If-Match': etag } },
      headers: csrfHeader(),
      body: change,
    });
    return loaded(response, data, error);
  });
}

// Name, accent color and logo back to the defaults in one step (TASK-081).
export function resetAppearance(api: ServerApi, etag: string): Promise<Outcome<LoadedAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.DELETE('/appearance', {
      params: { header: { 'If-Match': etag } },
      headers: csrfHeader(),
    });
    return loaded(response, data, error);
  });
}

export function replaceLogo(api: ServerApi, etag: string, file: Blob, type: LogoType): Promise<Outcome<LoadedAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.PUT('/appearance/logo', {
      params: { header: { 'If-Match': etag } },
      headers: { ...csrfHeader(), 'Content-Type': type },
      // The image is sent as it is; the server checks its first bytes against the declared type.
      body: file as unknown as string,
      bodySerializer: (body: unknown) => body as Blob,
    });
    return loaded(response, data, error);
  });
}

export function removeLogo(api: ServerApi, etag: string): Promise<Outcome<LoadedAppearance>> {
  return guarded(async () => {
    const { data, error, response } = await api.appearance.DELETE('/appearance/logo', {
      params: { header: { 'If-Match': etag } },
      headers: csrfHeader(),
    });
    return loaded(response, data, error);
  });
}
