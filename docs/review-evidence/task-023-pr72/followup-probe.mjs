// Independent regression probes. Imports the supplied checkout; HTTP requests stay on its own loopback test server.
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [frontendPath, label = 'review'] = process.argv.slice(2);
assert.ok(frontendPath, 'Usage: node followup-probe.mjs <frontend-directory> <label>');
const moduleAt = (path) => import(pathToFileURL(resolve(frontendPath, path)).href);
const { parseDecimal } = await moduleAt('src/i18n/format.ts');
const { validateSample } = await moduleAt('src/pages/validation.ts');
const { defaultBrand, parseBrand, brandToCssVariables, contrastRatio } = await moduleAt('src/brand/brand.ts');

let rejectedNumbers = 0;
for (const language of ['de', 'en', 'fr']) {
  for (const input of ['1 5', '1\u00a05', '1\u202f5']) {
    assert.equal(parseDecimal(input, language), null, `${language}: ${JSON.stringify(input)}`);
    assert.equal(validateSample({ label: 'Synthetic review', hours: input }, language).ok, false);
    rejectedNumbers += 1;
  }
}
assert.equal(parseDecimal('1,5', 'de'), 1.5);
assert.equal(parseDecimal('1.5', 'en'), 1.5);
assert.equal(parseDecimal('1\u202f234,5', 'fr'), 1234.5);

const invalidLogos = [
  '/\\example.invalid/logo.svg', '/\t/example.invalid/logo.svg', '/\n/example.invalid/logo.svg',
  '//example.invalid/logo.svg', '/ /example.invalid/logo.svg',
  'http://example.invalid/logo.svg', 'https://user:synthetic@example.invalid/logo.svg',
];
for (const logoUrl of invalidLogos) {
  assert.equal(parseBrand({ ...defaultBrand, logoUrl }).ok, false, JSON.stringify(logoUrl));
}
const local = parseBrand({ ...defaultBrand, logoUrl: '/brand/logo.svg?v=2' });
assert.equal(local.ok && local.brand.logoUrl, '/brand/logo.svg?v=2');
const remote = parseBrand({ ...defaultBrand, logoUrl: 'https://CDN.example.invalid/logo.svg' });
assert.equal(remote.ok && remote.brand.logoUrl, 'https://cdn.example.invalid/logo.svg');

const blackAndWhite = parseBrand({ ...defaultBrand,
  colors: { primary: '#ffffff', onPrimary: '#000000', background: '#000000', text: '#ffffff' } });
assert.equal(blackAndWhite.ok, true);
const variables = brandToCssVariables(blackAndWhite.brand);
const ratios = {};
for (const [name, minimum] of [['--muted', 4.5], ['--error', 4.5], ['--link', 4.5], ['--border', 3], ['--focus', 3]]) {
  ratios[name] = contrastRatio(variables[name], '#000000');
  assert.ok(ratios[name] >= minimum, `${name}: ${ratios[name]} < ${minimum}`);
}
const { startServer } = await moduleAt('tests/browser/support.mjs');
const server = await startServer();
let serverStatuses;
try {
  const root = await fetch(server.url);
  const escape = await fetch(server.url + '%2e%2e%5cpackage.json');
  const malformed = await fetch(server.url + '%ZZ');
  serverStatuses = [root.status, escape.status, malformed.status];
  assert.deepEqual(serverStatuses, [200, 404, 400]);
  await Promise.all([root.arrayBuffer(), escape.arrayBuffer(), malformed.arrayBuffer()]);
} finally {
  await server.close();
}
console.log(JSON.stringify({ label, rejectedNumbers, rejectedLogos: invalidLogos.length,
  mutedColor: variables['--muted'], ratios, serverStatuses }, null, 2));
