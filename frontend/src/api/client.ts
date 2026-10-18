import createClient from 'openapi-fetch';
import type { components, paths } from './generated/api';
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
