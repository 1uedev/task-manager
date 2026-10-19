import createClient from 'openapi-fetch';
import { acceptableLogo, isAccentColor } from '../brand/brand.ts';
import { readCsrfToken } from './csrf.ts';
import type { components, paths } from './generated/api';
import type { paths as AppearancePaths } from './generated/appearance';
import type { paths as IdentityPaths } from './generated/identity';
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

const INVALID_RESPONSE: ProblemInfo = { kind: 'unknown', status: null, code: null, correlationId: null };

async function guarded<T>(call: () => Promise<Outcome<T>>): Promise<Outcome<T>> {
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

export function createServerApi(fetcher: typeof fetch = (...args) => globalThis.fetch(...args), origin?: string) {
  const baseUrl = `${origin ?? globalThis.location?.origin ?? 'http://localhost'}/api/v1`;
  return {
    identity: createClient<IdentityPaths>({ baseUrl, fetch: fetcher }),
    appearance: createClient<AppearancePaths>({ baseUrl, fetch: fetcher }),
  };
}

export interface SessionState {
  readonly authenticated: boolean;
  // Only a complete login counts; a login that still has to change its one time password does not.
  readonly full: boolean;
  // Provisional technical marker of the identity module, not a permission model. The server decides.
  readonly administrator: boolean;
}

export function parseSession(value: unknown): SessionState | null {
  if (typeof value !== 'object' || value === null) return null;
  const item = value as Record<string, unknown>;
  if (typeof item.authenticated !== 'boolean' || typeof item.administrator !== 'boolean') return null;
  return { authenticated: item.authenticated, full: item.authenticated && item.level === 'full', administrator: item.administrator };
}

export interface AppearanceLogo {
  readonly contentType: string;
  readonly size: number;
  readonly url: string;
}

export interface Appearance {
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
    accentColor: item.accentColor.toLowerCase(),
    defaultAccentColor: item.defaultAccentColor.toLowerCase(),
    logo,
    version: item.version,
  };
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
  return guarded(async () => {
    const { data, error, response } = await api.appearance.PUT('/appearance', {
      params: { header: { 'If-Match': etag } },
      headers: csrfHeader(),
      body: { accentColor },
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
