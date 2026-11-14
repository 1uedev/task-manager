import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-112: Git objects linked to tickets and requirements, read live with the own connection, shown only by their kind when the
// person cannot read them, refreshed on request and named by kind only in both histories, against the in-memory
// backend that follows the contract.

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
const GITHUB = '00000000-0000-4000-8000-0000000112a1';
const GITLAB = '00000000-0000-4000-8000-0000000112a2';
const SHA = 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678';

function withGit(backend, { connect = [backend.anna] } = {}) {
  const at = '2026-11-13T08:00:00Z';
  backend.state.git.providers.push(
    { id: GITHUB, kind: 'github', name: 'GitHub', baseUrl: 'https://github.com', clientId: 'Iv1.app', secretSet: true, version: 1, updatedAt: at,
      updatedBy: backend.anna.id, removed: false },
    { id: GITLAB, kind: 'gitlab_com', name: 'GitLab', baseUrl: 'https://gitlab.com', clientId: 'gl-app', secretSet: true, version: 1, updatedAt: at,
      updatedBy: backend.anna.id, removed: false });
  backend.state.git.repositories.set(GITHUB, [{ externalId: '101', name: 'alpha', path: 'acme/alpha', url: 'https://github.com/acme/alpha',
    commits: [{ sha: SHA, message: 'Anmeldung repariert', author: 'Octo Cat', at: '2026-11-12T08:30:00Z' }],
    changes: [{ number: 7, title: 'Anmeldung absichern', state: 'open', author: 'octo' }] }]);
  backend.state.git.repositories.set(GITLAB, [{ externalId: '201', name: 'gamma', path: 'grp/gamma', url: 'https://gitlab.com/grp/gamma',
    commits: [], changes: [{ number: 3, title: 'Merge Request Anmeldung', state: 'open', author: 'gl-user' }] }]);
  backend.state.git.releases.push(
    { id: '00000000-0000-4000-8000-0000000112c1', projectId: backend.project.id, providerId: GITHUB, externalId: '101', releasedAt: at },
    { id: '00000000-0000-4000-8000-0000000112c2', projectId: backend.project.id, providerId: GITLAB, externalId: '201', releasedAt: at });
  for (const account of connect) {
    for (const provider of [GITHUB, GITLAB]) backend.state.git.connections.set(`${provider}:${account.id}`, { state: 'active', connectedAt: at });
  }
}

/** The field git of tickets and requirements for the accounts, read or read and write. */
function gitRules(backend, accounts, { write = true, actions = [] } = {}) {
  const operations = write ? ['read', 'write'] : ['read'];
  backend.state.groups.push({ id: `00000000-0000-4000-8000-0000000112${String(backend.state.groups.length).padStart(2, '0')}`, name: `Git Felder ${backend.state.groups.length}`,
    members: accounts.map((account) => account.id), actions: actions.map((action) => ({ action, projectId: null })),
    fields: ['ticket', 'requirement'].flatMap((resourceType) => operations.map((operation) => ({ resourceType, field: 'git', projectId: null, operation, effect: 'allow' }))) });
}

async function withApp({ prepare, as = ANNA, hash }, body) {
  const backend = createBackend();
  const prepared = prepare?.(backend);
  const session = await open(browser, `${base()}${hash(backend, prepared)}`, { setup: (context, page) => backend.install(context, page, { as }) });
  session.page.on('dialog', (dialog) => void dialog.accept());
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend, prepared);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

const ticketHash = (backend) => `#/projects/${backend.project.id}/tickets/1`;

describe('Git links', () => {
  test('a ticket links a commit by address and a pull request by choice, refreshes its state and names both in the history', async () => {
    await withApp({
      prepare: (backend) => {
        withGit(backend);
        gitRules(backend, [backend.anna]);
      },
      hash: ticketHash,
    }, async (page, backend) => {
      const panel = page.getByTestId('git-links');
      await panel.getByText('Noch nichts aus Git verknüpft.').waitFor();
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Bitte eine Adresse angeben.').waitFor();
      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/alpha/commit/${SHA.slice(0, 7)}?diff=split`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Commit verknüpft.').waitFor();
      const commit = panel.getByRole('link', { name: 'acme/alpha Commit a1b2c3d' });
      assert.equal(await commit.getAttribute('href'), `https://github.com/acme/alpha/commit/${SHA}`);
      assert.equal(await commit.getAttribute('rel'), 'noopener noreferrer');
      await panel.getByText(/Anmeldung repariert · von Octo Cat/).waitFor();
      assert.equal(await panel.getByLabel(/^Adresse aus Git/).inputValue(), '', 'the address is cleared after linking');

      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/alpha/commit/${SHA}`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Diese Verknüpfung besteht bereits.').waitFor();
      await panel.getByLabel(/^Adresse aus Git/).fill('https://github.com/acme/alpha/issues/3');
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText(/Diese Adresse gehört zu keinem eingerichteten Anbieter/).waitFor();

      await panel.getByRole('button', { name: 'Aus freigegebenem Repository wählen' }).click();
      await panel.getByLabel('Repository', { exact: true }).selectOption({ label: 'acme/alpha (GitHub)' });
      await panel.getByLabel('Art').selectOption('pull_request');
      await panel.getByLabel('Nummer').fill('7');
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Pull Request verknüpft.').waitFor();
      const state = panel.locator('[data-kind="pull_request"]').getByTestId('git-link-state');
      assert.equal((await state.textContent()).trim(), 'offen');
      const chosen = backend.state.requests.findLast((request) => request.method === 'POST' && request.path.endsWith('/git'));
      assert.deepEqual(JSON.parse(chosen.body.toString('utf8')), { releaseId: '00000000-0000-4000-8000-0000000112c1', kind: 'pull_request', key: '7' });

      backend.state.git.repositories.get(GITHUB)[0].changes[0].state = 'merged';
      await panel.getByRole('button', { name: 'Aktualisieren' }).click();
      await panel.getByText('zusammengeführt').waitFor();
      const refreshed = backend.state.requests.findLast((request) => request.method === 'GET' && request.path.endsWith('/tickets/1/git'));
      assert.match(refreshed.query ?? '', /refresh=true/);
      assert.equal(backend.state.tickets[0].version, 1, 'links leave the version of the ticket');

      const history = page.getByTestId('history');
      await history.getByText(/^Git Commit verknüpft$/).waitFor();
      await history.getByText(/^Git Pull Request verknüpft/).waitFor();
      await panel.getByRole('button', { name: 'acme/alpha Commit a1b2c3d lösen' }).click();
      await panel.getByText('Verknüpfung mit acme/alpha Commit a1b2c3d gelöst.').waitFor();
      await history.getByText(/^Git Commit gelöst/).waitFor();
      assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Git', 'the focus returns to the panel');
      assert.equal(backend.state.git.links.length, 1);
    });
  });

  test('without a connection or access only the kind is shown, and a rate limit keeps the page usable', async () => {
    const prepare = (backend) => {
      withGit(backend, { connect: [] });
      gitRules(backend, [backend.ben], { write: false });
      backend.state.git.links.push({ id: '00000000-0000-4000-8000-0000000112d1', subjectKind: 'ticket', subjectId: backend.state.tickets[0].id,
        projectId: backend.project.id, providerId: GITHUB, repositoryId: '101', kind: 'commit', key: SHA, linkedAt: '2026-11-13T09:00:00Z', linkedBy: backend.anna.id });
    };
    await withApp({ prepare, as: BEN, hash: ticketHash }, async (page, backend) => {
      const panel = page.getByTestId('git-links');
      await panel.getByText('Git Objekt nicht verfügbar').waitFor();
      await panel.getByText('(Commit)').waitFor();
      await panel.getByText(/Du bist mit diesem Anbieter nicht verbunden/).waitFor();
      assert.equal(await panel.getByRole('link', { name: 'Zu meinen Verbindungen' }).getAttribute('href'), '#/git');
      const text = await panel.textContent();
      assert.doesNotMatch(text, /alpha|a1b2c3d|Anmeldung/, 'neither path, SHA nor message');
      assert.equal(await panel.getByTestId('git-link-form').count(), 0, 'reading does not link');
      assert.equal(await panel.getByRole('button', { name: /lösen$/ }).count(), 0);

      backend.state.git.connections.set(`${GITHUB}:${backend.ben.id}`, { state: 'active', connectedAt: '2026-11-13T08:00:00Z' });
      backend.state.git.repositories.get(GITHUB)[0].hiddenFrom = ['ben'];
      await panel.getByRole('button', { name: 'Aktualisieren' }).click();
      await panel.getByTestId('git-link-reason').getByText('Nicht verfügbar.').waitFor();
      assert.doesNotMatch(await panel.textContent(), /alpha|a1b2c3d/);

      backend.state.git.repositories.get(GITHUB)[0].hiddenFrom = [];
      backend.state.git.limited = true;
      await panel.getByRole('button', { name: 'Aktualisieren' }).click();
      await panel.getByText('Vorübergehend nicht verfügbar.').waitFor();
      await page.getByRole('heading', { name: 'Anmeldung prüfen' }).waitFor();
    });
  });

  test('the address, the choice and the removal key follow the server (TASK-114)', async () => {
    await withApp({
      prepare: (backend) => {
        withGit(backend);
        gitRules(backend, [backend.anna, backend.ben]);
        // A second repository on GitHub that Anna can read but that is not released, and a released one hidden from her.
        backend.state.git.repositories.get(GITHUB).push({ externalId: '102', name: 'beta', path: 'acme/beta', url: 'https://github.com/acme/beta',
          commits: [{ sha: 'b'.repeat(40), message: 'Geheim', author: 'Octo', at: '2026-11-12T08:30:00Z' }], changes: [] });
        backend.state.git.releases.splice(1, 1);
      },
      hash: ticketHash,
    }, async (page, backend) => {
      const panel = page.getByTestId('git-links');
      await panel.getByText('Noch nichts aus Git verknüpft.').waitFor();
      // GitLab has no release for the project: refused at once, before anything is read at the provider.
      await panel.getByLabel(/^Adresse aus Git/).fill('https://gitlab.com/grp/gamma/-/merge_requests/3');
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Dieses Repository ist für das Projekt nicht freigegeben.').waitFor();
      // A readable repository that is not released tells nothing of its commit.
      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/beta/commit/${'b'.repeat(40)}`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Dieses Repository ist für das Projekt nicht freigegeben.').waitFor();
      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/beta/commit/${'c'.repeat(40)}`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Dieses Repository ist für das Projekt nicht freigegeben.').waitFor();

      // A connection to renew refuses the choice like the server, and the choice holds only readable repositories.
      backend.state.git.connections.set(`${GITHUB}:${backend.anna.id}`, { state: 'renew', connectedAt: '2026-11-13T08:00:00Z' });
      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/alpha/commit/${SHA}`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Deine Verbindung muss erneuert werden.').waitFor();
      await panel.getByRole('button', { name: 'Aus freigegebenem Repository wählen' }).click();
      await panel.getByText(/Für dieses Projekt ist kein Repository freigegeben/).waitFor();
      backend.state.git.connections.set(`${GITHUB}:${backend.anna.id}`, { state: 'active', connectedAt: '2026-11-13T08:00:00Z' });

      await panel.getByRole('button', { name: 'Adresse eingeben' }).click();
      await panel.getByLabel(/^Adresse aus Git/).fill(`https://github.com/acme/alpha/commit/${SHA}`);
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Commit verknüpft.').waitFor();
      const list = backend.state.requests.findLast((request) => request.method === 'GET' && request.path.endsWith('/tickets/1/git'));
      assert.ok(list, 'the list was read again');
      const [link] = backend.state.git.links;
      const shown = await page.evaluate(async (path) => (await (await fetch(path)).json()).links[0], `/api/v1/projects/${backend.project.id}/tickets/1/git`);
      assert.match(shown.key, /^[A-Za-z0-9_-]{24}$/);
      assert.notEqual(shown.key, link.id, 'the key is not the identifier of the link');
      assert.equal(shown.id, undefined);
      const history = page.getByTestId('history');
      await history.getByText(/^Git Commit verknüpft$/).waitFor();
      assert.equal(backend.state.history.at(-1).changes[0].to, 'commit', 'the history keeps the kind only');
    });
  });

  test('a requirement links a merge request, which changes nothing when it is merged', async () => {
    await withApp({
      prepare: (backend) => {
        withGit(backend);
        gitRules(backend, [backend.anna], { actions: ['requirement.read', 'requirement.edit'] });
        backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000112f1', name: 'Anforderungsfelder', members: [backend.anna.id], actions: [],
          fields: ['title', 'description', 'criteria', 'priority', 'owner'].flatMap((field) => ['read', 'write'].map((operation) => ({ resourceType: 'requirement', field,
            projectId: null, operation, effect: 'allow' }))) });
        return backend.addRequirement(backend.anna, 'Anmeldung sicher', { criteria: ['Sperre nach Fehlversuchen'], status: 'approved' });
      },
      hash: (backend, item) => `#/requirements/${backend.project.id}/${item.id}`,
    }, async (page, backend, item) => {
      const panel = page.getByTestId('git-links');
      await panel.getByText('Noch nichts aus Git verknüpft.').waitFor();
      await panel.getByLabel(/^Adresse aus Git/).fill('https://gitlab.com/grp/gamma/-/merge_requests/3');
      await panel.getByRole('button', { name: 'Verknüpfen' }).click();
      await panel.getByText('Merge Request verknüpft.').waitFor();
      const link = panel.getByRole('link', { name: 'grp/gamma Merge Request !3' });
      assert.equal(await link.getAttribute('href'), 'https://gitlab.com/grp/gamma/-/merge_requests/3');

      backend.state.git.repositories.get(GITLAB)[0].changes[0].state = 'merged';
      await panel.getByRole('button', { name: 'Aktualisieren' }).click();
      await panel.getByText('zusammengeführt').waitFor();
      assert.equal(item.version, 1, 'the version stays');
      assert.equal(item.status, 'approved', 'a merged merge request approves nothing and changes no status');

      await page.getByTestId('requirement-history-open').click();
      await page.getByText(/Git Merge Request verknüpft/).waitFor();
    });
  });
});
