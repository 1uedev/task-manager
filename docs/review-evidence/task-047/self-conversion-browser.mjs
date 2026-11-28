import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { createBackend, ANNA } from '../../../frontend/tests/browser/backend.mjs';
import { launch, open, startServer } from '../../../frontend/tests/browser/support.mjs';

// API fixture reproduces the 401 after self conversion that the real backend probe separately verifies.
const server = await startServer();
const browser = await launch();
const rows = [];
try {
  for (const locale of ['de-DE', 'en-US']) {
    const backend = createBackend();
    backend.state.accounts.find((a) => a.username === ANNA.username).authKind = 'ldap';
    let converted = false;
    const session = await open(browser, `${server.url.replace('127.0.0.1','localhost')}#/accounts`, { locale, setup: async (context, page) => {
      await backend.install(context, page, { as: ANNA });
      await page.route('**/api/v1/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() === 'POST' && path.endsWith('/convert-to-local')) {
          converted = true;
          return route.fallback();
        }
        if (converted) {
          if (path === '/api/v1/session') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ authenticated: false, csrfToken: 'synthetic-anonymous-token' }) });
          return route.fulfill({ status: 401, contentType: 'application/problem+json', body: JSON.stringify({ status: 401, code: 'auth.session_expired', correlationId: 'synthetic-review' }) });
        }
        return route.fallback();
      });
    } });
    try {
      await session.page.getByTestId('account-table').waitFor();
      const first = locale === 'de-DE' ? 'anna auf lokales Konto umstellen' : 'Convert anna to a local account';
      const confirm = locale === 'de-DE' ? 'Ja, anna umstellen' : 'Yes, convert anna';
      await session.page.getByRole('button', { name: first, exact: true }).click();
      const response = session.page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/convert-to-local'));
      await session.page.getByRole('button', { name: confirm, exact: true }).click();
      const reply = await response;
      const body = await reply.json();
      await session.page.getByTestId('account-table').waitFor({ state: 'hidden' });
      const loginVisible = await session.page.locator('input[autocomplete="username"]').isVisible();
      const secretPanel = await session.page.getByTestId('one-time').count();
      rows.push({ locale, conversionStatus: reply.status(), otpInResponse: typeof body.oneTimePassword === 'string', loginVisible, secretPanelAfterRefresh: secretPanel });
      assert.equal(reply.status(), 200);
      assert.equal(loginVisible, true);
      assert.equal(secretPanel, 0);
    } finally { await session.context.close(); }
  }
} finally { await browser.close(); await server.close(); }
await writeFile(process.argv[2], JSON.stringify(rows, null, 2)+'\n');
