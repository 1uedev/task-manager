// Synthetic responses for the state examples. This is not a backend and not a test double of a
// product endpoint: the paths belong to the synthetic qualification contract.

export type ScenarioId =
  | 'success'
  | 'empty'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'preconditionRequired'
  | 'serverError'
  | 'unavailable'
  | 'network'
  | 'slow';

export const scenarioIds: readonly ScenarioId[] = [
  'success',
  'empty',
  'forbidden',
  'notFound',
  'conflict',
  'preconditionRequired',
  'serverError',
  'unavailable',
  'network',
  'slow',
];

export const SYNTHETIC_ID = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';
export const SLOW_DELAY_MS = 1200;

const CORRELATION = 'b7d4f0a2-6c1e-4a58-9f3d-2e8c5b1a7d90';

function problem(status: number, code: string): Response {
  return new Response(
    JSON.stringify({ type: 'https://qualification.invalid/problems/synthetic', title: 'Synthetic', status, code, correlationId: CORRELATION }),
    { status, headers: { 'Content-Type': 'application/problem+json', 'X-Correlation-Id': CORRELATION } },
  );
}

function probeResponse(label: string): Response {
  return new Response(JSON.stringify({ id: SYNTHETIC_ID, label, version: 'v7', note: null }), {
    status: 200,
    headers: { 'Content-Type': 'application/json', ETag: '"synthetic-1"' },
  });
}

function wait(milliseconds: number, signal: AbortSignal | undefined): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException('Aborted', 'AbortError'));
    const timer = setTimeout(resolve, milliseconds);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    });
  });
}

export function syntheticFetch(scenario: ScenarioId): typeof fetch {
  return async (input, init) => {
    const request = input instanceof Request ? input : new Request(input, init);
    switch (scenario) {
      case 'forbidden':
        return problem(403, 'request.rejected');
      case 'notFound':
        return problem(404, 'resource.not_found');
      case 'conflict':
        return problem(412, 'precondition.failed');
      case 'preconditionRequired':
        return problem(428, 'precondition.required');
      case 'serverError':
        return problem(500, 'server.internal_error');
      case 'unavailable':
        return problem(503, 'server.unavailable');
      case 'network':
        throw new TypeError('Failed to fetch');
      case 'slow':
        await wait(SLOW_DELAY_MS, request.signal);
        return probeResponse('Beispiel');
      default:
        return probeResponse('Beispiel');
    }
  };
}
