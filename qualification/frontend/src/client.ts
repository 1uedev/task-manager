import createClient from 'openapi-fetch';
import type { paths, components } from './generated/api';

export type Probe = components['schemas']['Probe'];
export function probeClient(fetcher: typeof fetch) {
  return createClient<paths>({ baseUrl: 'https://qualification.invalid', fetch: fetcher });
}

// Generated types provide compile-time checks, not runtime validation.
// This deliberately small guard qualifies the distinction; it is not a general product validator.
export function checkedProbe(value: unknown): Probe {
  if (!value || typeof value !== 'object') throw new Error('Invalid probe response');
  const item = value as Record<string, unknown>;
  if (typeof item.id !== 'string' || typeof item.label !== 'string' || typeof item.version !== 'string'
      || (item.note !== undefined && item.note !== null && typeof item.note !== 'string')) {
    throw new Error('Invalid probe response');
  }
  return value as Probe;
}
