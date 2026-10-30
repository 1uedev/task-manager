import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, BEN, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-054: the background jobs for the operation, against the in-memory backend that follows the contract. Only a holder of
// jobs.manage sees the entry and the page; a failed job shows its code and log reference and can be queued again.

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

// Anna administers the company; the right to see the jobs comes from its own group, as an administration would grant it.
function withJobsRight(backend) {
  backend.state.groups.push({ id: '00000000-0000-4000-8000-0000000fffff', name: 'Betrieb', members: [backend.anna.id], actions: [{ action: 'jobs.manage', projectId: null }], fields: [] });
}

async function withApp({ prepare = withJobsRight, as = ANNA, locale = 'de-DE', hash = '#/jobs' } = {}, body) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}${hash}`, { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    await session.page.waitForSelector('.shell main h1');
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

describe('background jobs', () => {
  test('without jobs.manage there is no entry and the page refuses', async () => {
    await withApp({ prepare: null }, async (page, backend) => {
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'Hintergrundaufträge' }).count(), 0);
      await page.getByText('Sie haben für diese Aktion keine Berechtigung.').first().waitFor();
      assert.equal(backend.state.requests.filter((request) => request.path.startsWith('/api/v1/jobs')).length, 0, 'the page asks nothing it may not');
    });
    await withApp({ prepare: null, as: BEN, hash: '#/start' }, async (page) => {
      assert.equal(await page.getByRole('navigation').getByRole('link', { name: 'Hintergrundaufträge' }).count(), 0);
    });
  });

  test('the summary shows the counts and whether the queue is worked off', async () => {
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      backend.addJob('recurring.files.cleanup', 'succeeded');
      backend.addJob('mail.send', 'failed', { attempts: 5, errorCode: 'job.timeout', errorReference: '11111111-2222-4333-8444-555555555555' });
      backend.addJob('mail.send', 'queued');
    } }, async (page) => {
      await page.getByRole('navigation').getByRole('link', { name: 'Hintergrundaufträge' }).waitFor();
      const summary = page.getByTestId('jobs-summary');
      await summary.getByText('Die Warteschlange wird abgearbeitet.').waitFor();
      assert.match(await summary.locator('li[data-state="failed"]').innerText(), /Gescheitert\s*1/);
      assert.match(await summary.locator('li[data-state="succeeded"]').innerText(), /Erledigt\s*1/);
      assert.match(await summary.innerText(), /Aktive Ausführer: 1/);
      const row = page.locator('tr', { hasText: 'Zeitlimit überschritten' });
      assert.match(await row.innerText(), /5 von 5/);
      assert.match(await row.innerText(), /Protokollkennung 11111111-2222-4333-8444-555555555555/);
      assert.match(await page.locator('tr', { hasText: 'Aufräumen der Dateiablage' }).innerText(), /Aufgabe der Installation/);
    });
  });

  test('a backlog is named when nobody works the queue off', async () => {
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      backend.state.activeWorkers = 0;
      backend.addJob('mail.send', 'queued', { runAt: '2026-10-27T09:30:00Z' });
    } }, async (page) => {
      await page.getByTestId('jobs-health').getByText('Rückstau', { exact: false }).waitFor();
      assert.match(await page.getByTestId('jobs-summary').innerText(), /seit 30 Minuten/);
    });
  });

  test('a failed job is queued again and a waiting one is cancelled only after the confirmation', async () => {
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      backend.addJob('mail.send', 'failed', { attempts: 5, errorCode: 'job.failed', errorReference: '11111111-2222-4333-8444-555555555555' });
      backend.addJob('sla.check', 'queued');
    } }, async (page, backend) => {
      const failed = backend.state.jobs[0];
      const waiting = backend.state.jobs[1];
      await page.locator(`tr[data-job="${failed.id}"]`).getByRole('button', { name: 'Erneut einstellen' }).click();
      await page.getByText('Der Auftrag wartet wieder und läuft mit allen Versuchen erneut.').waitFor();
      assert.equal(failed.state, 'queued');
      await page.locator(`tr[data-job="${failed.id}"]`).getByText('Wartet').waitFor();

      await page.locator(`tr[data-job="${waiting.id}"]`).getByRole('button', { name: 'Abbrechen' }).click();
      assert.equal(waiting.state, 'queued', 'nothing happens before the confirmation');
      await page.getByRole('group', { name: 'Diesen wartenden Auftrag abbrechen?' }).getByRole('button', { name: 'Nicht abbrechen' }).click();
      assert.equal(waiting.state, 'queued');
      await page.locator(`tr[data-job="${waiting.id}"]`).getByRole('button', { name: 'Abbrechen' }).click();
      await page.getByRole('button', { name: 'Ja, abbrechen' }).click();
      await page.getByText('Der Auftrag wurde abgebrochen.').waitFor();
      assert.equal(waiting.state, 'cancelled');
      const writes = backend.writes().filter((request) => request.path.startsWith('/api/v1/jobs'));
      assert.deepEqual(writes.map((request) => request.path), [`/api/v1/jobs/${failed.id}/retry`, `/api/v1/jobs/${waiting.id}/cancel`]);
      assert.ok(writes.every((request) => request.csrf !== null), 'every change carries the CSRF token');
    });
  });

  test('a change by somebody else is explained and the list reloads', async () => {
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      backend.addJob('mail.send', 'failed', { errorCode: 'job.failed' });
      backend.state.jobConflict = true;
    } }, async (page, backend) => {
      const job = backend.state.jobs[0];
      await page.locator(`tr[data-job="${job.id}"]`).getByRole('button', { name: 'Erneut einstellen' }).click();
      await page.getByText('Der Auftrag hat inzwischen einen anderen Zustand. Die Liste wurde neu geladen.').waitFor();
      await page.locator(`tr[data-job="${job.id}"]`).getByText('Läuft').waitFor();
      assert.equal(await page.locator(`tr[data-job="${job.id}"]`).getByRole('button', { name: 'Erneut einstellen' }).count(), 0);
    });
  });

  test('the list filters by state and loads older jobs page by page', async () => {
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      for (let i = 0; i < 55; i++) backend.addJob('recurring.files.cleanup', 'succeeded');
      backend.addJob('mail.send', 'failed', { errorCode: 'mail.rejected' });
    } }, async (page, backend) => {
      const table = page.getByTestId('job-table');
      await table.waitFor();
      assert.equal(await table.locator('tbody tr').count(), 50);
      await page.getByRole('button', { name: 'Ältere Aufträge laden' }).click();
      await page.getByText('Keine älteren Aufträge.').waitFor();
      assert.equal(await table.locator('tbody tr').count(), 56);

      await page.getByLabel('Zustand', { exact: true }).selectOption('failed');
      await page.locator('tbody tr', { hasText: 'mail.rejected' }).waitFor();
      assert.equal(await table.locator('tbody tr').count(), 1);
      assert.ok(backend.state.requests.some((request) => request.method === 'GET' && request.path === '/api/v1/jobs'), 'the list was read');
      await page.getByLabel('Zustand', { exact: true }).selectOption('cancelled');
      await page.getByText('Keine Aufträge in diesem Zustand.').waitFor();
    });
  });

  test('a late answer of an earlier filter does not replace the list of the chosen one', async () => {
    // TASK-065, F2: the answer for "failed" arrives only after the list of "cancelled" is shown.
    await withApp({ prepare: (backend) => {
      withJobsRight(backend);
      backend.addJob('mail.send', 'failed', { errorCode: 'mail.rejected' });
      backend.addJob('sla.check', 'cancelled');
    } }, async (page) => {
      const table = page.getByTestId('job-table');
      await table.waitFor();
      let releaseFailed;
      const failedHeld = new Promise((resolve) => { releaseFailed = resolve; });
      let failedAsked;
      const failedArrived = new Promise((resolve) => { failedAsked = resolve; });
      await page.route((url) => url.pathname === '/api/v1/jobs' && url.searchParams.get('state') === 'failed', async (route) => {
        failedAsked();
        await failedHeld;
        await route.fallback();
      });

      await page.getByLabel('Zustand', { exact: true }).selectOption('failed');
      await failedArrived;
      await page.getByLabel('Zustand', { exact: true }).selectOption('cancelled');
      await page.waitForFunction(() => document.querySelectorAll('[data-testid="job-table"] tbody tr').length === 1);
      await table.locator('tbody tr', { hasText: 'Abgebrochen' }).waitFor();
      const failedFinished = page.waitForEvent('requestfinished', (request) => new URL(request.url()).searchParams.get('state') === 'failed');
      releaseFailed();
      await failedFinished;
      // Gives the page the time to apply the late answer, as it did before the correction.
      await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 300)));

      assert.equal(await page.getByLabel('Zustand', { exact: true }).inputValue(), 'cancelled');
      assert.equal(await table.locator('tbody tr', { hasText: 'mail.rejected' }).count(), 0, 'the late list of failed jobs is dropped');
      assert.equal(await table.locator('tbody tr').count(), 1);
      assert.equal(await table.locator('tbody tr', { hasText: 'Abgebrochen' }).count(), 1);
    });
  });

  test('the page speaks English', async () => {
    await withApp({ locale: 'en-US', prepare: (backend) => {
      withJobsRight(backend);
      backend.addJob('recurring.files.cleanup', 'failed', { errorCode: 'job.lease_expired' });
    } }, async (page) => {
      await page.getByRole('heading', { name: 'Background jobs', level: 1 }).waitFor();
      await page.getByText('The executor ended without reporting').waitFor();
      await page.getByText('Clean up of the file store', { exact: true }).waitFor();
      await page.getByRole('button', { name: 'Queue again' }).waitFor();
    });
  });
});
