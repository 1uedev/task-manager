import { de } from '../i18n/de.ts';
import type { MessageKey } from '../i18n/index.ts';

// Error handling is driven by HTTP status and the stable `code`, never by server texts
// (server texts are not translated and may change). Unknown input degrades to a generic kind.

export type ProblemKind =
  | 'forbidden'
  | 'sessionExpired'
  | 'notFound'
  | 'conflict'
  | 'preconditionRequired'
  | 'validation'
  | 'unavailable'
  | 'server'
  | 'network'
  | 'unknown';

export interface ProblemInfo {
  readonly kind: ProblemKind;
  readonly status: number | null;
  readonly code: string | null;
  readonly correlationId: string | null;
  // The stable reasons of a refused input, such as `custom.cause.required_for_transition`; present only when the server named some.
  readonly violations?: readonly string[];
}

export const networkProblem: ProblemInfo = { kind: 'network', status: null, code: null, correlationId: null };

export function classifyStatus(status: number): ProblemKind {
  switch (status) {
    case 400:
    case 422:
      return 'validation';
    case 401:
      return 'sessionExpired';
    case 403:
      return 'forbidden';
    case 404:
      return 'notFound';
    case 409:
    case 412:
      return 'conflict';
    case 428:
      return 'preconditionRequired';
    case 503:
      return 'unavailable';
    default:
      if (status >= 500) return 'server';
      return 'unknown';
  }
}

const CODE = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/;
const CORRELATION = /^[A-Za-z0-9-]{8,64}$/;

function text(value: unknown, pattern: RegExp): string | null {
  return typeof value === 'string' && value.length <= 100 && pattern.test(value) ? value : null;
}

// A reason is a code, optionally followed by the key of what it names (`status.in_use:review`).
const VIOLATION = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*(:[a-z][a-z0-9_]*)?$/;
const MAX_VIOLATIONS = 50;

export function readProblem(status: number, body: unknown, headerCorrelationId?: string | null): ProblemInfo {
  const item = (typeof body === 'object' && body !== null ? body : {}) as Record<string, unknown>;
  const violations = Array.isArray(item.violations)
    ? item.violations.slice(0, MAX_VIOLATIONS).map((value) => text(value, VIOLATION)).filter((value): value is string => value !== null)
    : [];
  return {
    kind: classifyStatus(status),
    status,
    code: text(item.code, CODE),
    correlationId: text(item.correlationId, CORRELATION) ?? text(headerCorrelationId, CORRELATION),
    ...(violations.length > 0 ? { violations } : {}),
  };
}

// Status specific kinds explain what the person can do next and win over the generic code text.
const SPECIFIC: ReadonlySet<ProblemKind> = new Set([
  'forbidden',
  'sessionExpired',
  'notFound',
  'conflict',
  'preconditionRequired',
  'unavailable',
  'network',
]);

function isMessageKey(key: string): key is MessageKey {
  return Object.hasOwn(de, key);
}

export function problemMessageKey(problem: ProblemInfo): MessageKey {
  if (!SPECIFIC.has(problem.kind) && problem.code !== null) {
    const byCode = `problem.code.${problem.code}`;
    if (isMessageKey(byCode)) return byCode;
  }
  return `problem.kind.${problem.kind}` as MessageKey;
}
