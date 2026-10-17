import type { components } from './generated/api';
import { probeClient } from './client';
type Probe = components['schemas']['Probe'];
const valid: Probe = { id: 'synthetic-id', label: 'Probe', version: 'opaque-v1' };
const nullable: Probe = { ...valid, note: null };
// @ts-expect-error A numeric version must not silently become a JavaScript counter.
const wrongVersion: Probe = { ...valid, version: 123 };
// @ts-expect-error Required label must remain required after generation.
const missingLabel: Probe = { id: 'synthetic-id', version: 'opaque-v1' };
void [nullable, wrongVersion, missingLabel];
// Compile-only function: it is not invoked and must never make a real network call.
function checkRequestHeaders() {
  const client = probeClient(fetch);
  // @ts-expect-error If-Match is required by the PATCH transport contract.
  void client.PATCH('/_qualification/probes/{probeId}', { params: { path: { probeId: 'synthetic-id' } }, body: { label: 'New' } });
}
void checkRequestHeaders;
