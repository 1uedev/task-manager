import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-111: the own connections with GitHub and GitLab, the provider administration for git.manage and the repositories
// released per project, against the in-memory backend that follows the contract.

let server;
let browser;
before(async () => {
  server = await startServer();
  browser = await launch();
});
after(async () => {
  await browser?.close();
  await server?.close();
});

const base = () => server.url.replace('127.0.0.1', 'localhost');
const GITHUB = '00000000-0000-4000-8000-0000000091a1';
const GITLAB = '00000000-0000-4000-8000-0000000091a2';

function withProviders(backend) {
  const at = '2026-11-13T08:00:00Z';
  backend.state.git.providers.push(
    { id: GITHUB, kind: 'github', name: 'GitHub', baseUrl: 'https://github.com', clientId: 'Iv1.app', secretSet: true, caCertificate: null, version: 1,
      updatedAt: at, updatedBy: backend.anna.id, removed: false },
    { id: GITLAB, kind: 'gitlab', name: 'Werk GitLab', baseUrl: 'https://git.example.com', clientId: 'gl-app', secretSet: true, caCertificate: null, version: 1,
      updatedAt: at, updatedBy: backend.anna.id, removed: false, failWith: 'git.version_unsupported' });
  backend.state.git.repositories.set(GITHUB, [
    { externalId: '101', name: 'alpha', path: 'acme/alpha', url: 'https://github.com/acme/alpha' },
    { externalId: '102', name: 'beta', path: 'acme/beta', url: 'https://github.com/acme/beta', hiddenFrom: ['ben'] },
  ]);
}

const withGitManage = (backend) => {
  withProviders(backend);
  backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000091f1', name: 'Git', members: [backend.anna.id], actions: [{ action: 'git.manage', projectId: null }], fields: [] });
};

async function withApp({ prepare = withProviders, as = ANNA, hash = '#/git' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { setup: (context, page) => backend.install(context, page, { as }) });
  session.page.on('dialog', (dialog) => void dialog.accept());
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('Git link', () => {
  test('every person connects their own account and comes back with the outcome', async () => {
    await withApp({ as: BEN }, async (page, backend) => {
      await page.getByRole('navigation').getByRole('link', { name: 'Git' }).waitFor();
      assert.equal(await page.getByTestId('git-admin').count(), 0, 'no administration without git.manage');
      const github = page.locator(`[data-provider="${GITHUB}"]`);
      await github.getByText('Nicht verbunden').waitFor();
      await github.getByRole('button', { name: 'Mit GitHub verbinden' }).click();
      await page.getByText('Verbunden mit GitHub.').waitFor();
      await github.getByText(/Verbunden seit/).waitFor();
      assert.equal(new URL(page.url()).hash, '#/git', 'the outcome leaves the address');

      await github.getByRole('button', { name: 'Verbindung mit GitHub trennen' }).click();
      await page.getByText('Verbindung mit GitHub getrennt.').waitFor();
      assert.equal(backend.state.git.connections.size, 0);

      await page.locator(`[data-provider="${GITLAB}"]`).getByRole('button', { name: 'Mit Werk GitLab verbinden' }).click();
      await page.getByText('Verbinden fehlgeschlagen: Dieses GitLab ist älter als Version 16.0.').waitFor();
    });
  });

  test('git.manage registers a provider, its secret stays write only', async () => {
    await withApp({ prepare: withGitManage }, async (page, backend) => {
      const admin = page.getByTestId('git-admin');
      await admin.getByText('https://tm.example/api/v1/git/oauth/callback').waitFor();
      await admin.getByRole('button', { name: 'Anbieter hinzufügen' }).click();
      const form = page.getByTestId('git-provider-form');
      await form.getByLabel('Art').selectOption('gitlab');
      await form.getByLabel(/^Name/).fill('Labor GitLab');
      await form.getByLabel(/^Adresse/).fill('ftp://git.labor.example');
      await form.getByLabel(/^Kennung der Anwendung/).fill('labor-app');
      await form.getByLabel('Geheimnis der Anwendung').fill('ganz-geheim');
      assert.equal(await form.getByLabel('Geheimnis der Anwendung').getAttribute('type'), 'password');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await form.getByText('Bitte eine https Adresse ohne Anfrageteil angeben.').waitFor();
      await form.getByLabel(/^Adresse/).fill('https://git.labor.example/');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Labor GitLab angelegt.').waitFor();
      const created = backend.state.git.providers.find((provider) => provider.name === 'Labor GitLab');
      assert.equal(created.baseUrl, 'https://git.labor.example');
      assert.equal(created.secretSet, true);
      assert.equal(await page.getByText('ganz-geheim').count(), 0);
      const post = backend.state.requests.findLast((request) => request.method === 'POST' && request.path === '/api/v1/git/admin/providers');
      assert.equal(JSON.parse(post.body.toString('utf8')).clientSecret, 'ganz-geheim');

      await admin.getByRole('button', { name: 'Labor GitLab bearbeiten' }).click();
      await form.getByLabel(/^Name/).fill('Labor GitLab Neu');
      await form.getByRole('button', { name: 'Speichern' }).click();
      await page.getByText('Labor GitLab Neu gespeichert.').waitFor();
      const put = backend.state.requests.findLast((request) => request.method === 'PUT');
      assert.equal(put.ifMatch, '"1"');
      assert.equal('clientSecret' in JSON.parse(put.body.toString('utf8')), false, 'an empty secret field keeps the stored one');

      await admin.getByRole('button', { name: 'Labor GitLab Neu prüfen' }).click();
      await page.getByText('Labor GitLab Neu ist erreichbar, Version 16.4.1.').waitFor();
      await admin.getByRole('button', { name: 'Labor GitLab Neu entfernen' }).click();
      await page.getByText('Labor GitLab Neu entfernt.').waitFor();
      assert.equal(created.removed, true);
    });
  });

  test('a project releases a repository the own connection reads, others see it only with their own', async () => {
    await withApp({ hash: '#/start' }, async (page, backend) => {
      backend.state.git.connections.set(`${GITHUB}:${backend.anna.id}`, { state: 'active', connectedAt: '2026-11-13T08:00:00Z' });
      await page.goto(`${base()}#/projects/${backend.project.id}/git`);
      await page.getByRole('heading', { name: 'Repositories' }).waitFor();
      await page.getByText('Für dieses Projekt ist noch kein Repository freigegeben.').waitFor();
      const release = page.getByTestId('git-release');
      await release.getByLabel(/^Suche/).fill('be');
      await release.getByRole('button', { name: 'Suchen' }).click();
      await release.getByRole('button', { name: 'acme/beta freigeben' }).click();
      await page.getByText('acme/beta freigegeben.').waitFor();
      const link = page.getByTestId('git-repositories').getByRole('link', { name: 'acme/beta' });
      assert.equal(await link.getAttribute('href'), 'https://github.com/acme/beta');
      assert.equal(await link.getAttribute('rel'), 'noopener noreferrer');
      assert.equal(backend.state.git.releases.length, 1);
    });

    await withApp({ as: BEN, hash: '#/start' }, async (page, backend) => {
      backend.state.git.releases.push({ id: '00000000-0000-4000-8000-0000000091c1', projectId: backend.project.id, providerId: GITHUB, externalId: '102',
        releasedAt: '2026-11-13T08:00:00Z' });
      await page.goto(`${base()}#/projects/${backend.project.id}/git`);
      const list = page.getByTestId('git-repositories');
      await list.getByText('Nicht verfügbar', { exact: true }).waitFor();
      await list.getByText(/Du bist mit diesem Anbieter nicht verbunden/).waitFor();
      assert.equal(await list.getByText('acme/beta').count(), 0);
      assert.equal(await page.getByTestId('git-release').count(), 0, 'reading the project does not release');

      backend.state.git.connections.set(`${GITHUB}:${backend.ben.id}`, { state: 'active', connectedAt: '2026-11-13T08:00:00Z' });
      await page.reload();
      await list.getByText(/Nicht verfügbar\./).waitFor();
      assert.equal(await list.getByText('acme/beta').count(), 0, 'hidden from Ben at the provider, so hidden here');
    });
  });
});
