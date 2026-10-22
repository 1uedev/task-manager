// Independent regression replays against the unmodified production build and real backend.
// Usage: node replay.mjs PRIVATE_BOOTSTRAP_LOG [BACKEND_PORT=61546] [FRONTEND_PORT=61547]
// Only a disposable synthetic database may be used. Credentials/cookies stay in memory.
import assert from 'node:assert/strict';
import http from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve, dirname, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn, spawnSync } from 'node:child_process';
import { chromium } from '../../../frontend/node_modules/playwright-core/index.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../../..');
const dist = resolve(root, 'frontend/dist');
const backendPort = Number(process.argv[3] ?? 61546);
const frontendPort = Number(process.argv[4] ?? 61547);
const base = `http://localhost:${frontendPort}`;
const password = 'Synthetic-Review-Password-2026!';
const results = [];
const apiTraffic = [];
const runSuffix = Date.now().toString(36).slice(-6);
const delay = ms => new Promise(r => setTimeout(r, ms));
const gate = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

class Client {
  cookies = new Map();
  async call(method, path, body, etag) {
    const raw = body === undefined ? null : Buffer.from(JSON.stringify(body));
    const headers = { Cookie: [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; '), Accept: 'application/json' };
    if (this.cookies.has('__Host-tm-csrf')) headers['X-CSRF-Token'] = this.cookies.get('__Host-tm-csrf');
    if (raw) Object.assign(headers, { 'Content-Type': 'application/json', 'Content-Length': raw.length });
    if (etag) headers['If-Match'] = etag;
    return await new Promise((resolve, reject) => {
      const req = http.request({ hostname: '127.0.0.1', port: backendPort, path: '/api/v1' + path, method, headers }, res => {
        const chunks = [];
        for (const cookie of res.headers['set-cookie'] ?? []) {
          const [pair] = cookie.split(';'); const split = pair.indexOf('=');
          this.cookies.set(pair.slice(0, split), pair.slice(split + 1));
        }
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => { const text = Buffer.concat(chunks).toString(); resolve({ status: res.statusCode, body: text ? JSON.parse(text) : null, headers: res.headers }); });
      });
      req.on('error', reject); req.setTimeout(30000, () => req.destroy(new Error('request timeout'))); req.end(raw);
    });
  }
  async ok(method, path, body, etag, status = 200) {
    const r = await this.call(method, path, body, etag);
    assert.equal(r.status, status, `${method} ${path}: ${r.status} ${r.body?.code ?? ''}`); return r;
  }
  async login(name, pass = password) { await this.ok('GET', '/session'); await this.ok('POST', '/session', { username: name, password: pass }); }
}

const admin = new Client(), peer = new Client();
const otp = (await readFile(process.argv[2], 'utf8')).match(/One time password \(shown only now\): (.+)/)?.[1]?.trim();
assert.ok(otp, 'private bootstrap output required');
if (process.env.REVIEW_RESUME === '1') await admin.login('reviewadmin');
else { await admin.login('reviewadmin', otp); await admin.ok('POST', '/session/password', { currentPassword: otp, newPassword: password }); }
const adminGroup = (await admin.ok('GET', '/permissions/groups')).body.find(g => g.name === 'Unternehmensverwaltung').id;
const adminId = (await admin.ok('GET', '/directory/accounts')).body.find(a => a.username === 'reviewadmin').id;
const existingPeer = (await admin.ok('GET', '/directory/accounts')).body.find(a => a.username === 'reviewpeer');
if (existingPeer) await peer.login('reviewpeer');
else {
  const issued = (await admin.ok('POST', '/accounts', { username: 'reviewpeer', displayName: 'Review Peer' }, null, 201)).body;
  await admin.ok('PUT', `/permissions/groups/${adminGroup}/members/${issued.account.id}`);
  await peer.login('reviewpeer', issued.oneTimePassword);
  await peer.ok('POST', '/session/password', { currentPassword: issued.oneTimePassword, newPassword: password });
}
const existingGroup = (await admin.ok('GET', '/permissions/groups')).body.find(g => g.name === 'Review editor 141');
const group = existingGroup?.id ?? (await admin.ok('POST', '/permissions/groups', { name: 'Review editor 141' }, null, 201)).body.id;
for (const action of ['project.read','project.manage','ticket.read','ticket.create','ticket.edit','tag.create'])
  await admin.ok('PUT', `/permissions/groups/${group}/actions`, { action, projectId: null, enabled: true });
async function field(name, operation, effect) {
  await peer.ok('PUT', `/permissions/groups/${group}/fields`, { resourceType: 'ticket', field: name, operation, effect, projectId: null });
}
for (const name of ['title','description','type','status','assignee','tags']) for (const op of ['read','write']) await field(name, op, 'allow');
await admin.ok('PUT', `/permissions/groups/${group}/members/${adminId}`);
const existingProject = (await admin.ok('GET', '/projects')).body.find(p => p.key === 'REV');
const project = existingProject ?? (await admin.ok('POST', '/projects', { key: 'REV', name: 'Review Projekt 141' }, null, 201)).body;
const projectPath = `/projects/${project.id}`;
let p = await admin.ok('GET', projectPath);
await admin.ok('PUT', `${projectPath}/members/${adminId}`, undefined, p.headers.etag);
const existingTicket = (await admin.ok('GET', `${projectPath}/tickets`)).body[0];
const ticket = existingTicket ?? (await admin.ok('POST', `${projectPath}/tickets`, { title: 'Review141-Titel', description: 'Review141-GeheimAlt', type: 'task' }, null, 201)).body;
const ticketPath = `${projectPath}/tickets/${ticket.number}`;
const hash = `#${ticketPath}`;
let t = await admin.ok('GET', ticketPath);
await admin.ok('PATCH', ticketPath, { description: 'Review141-GeheimNeu' }, t.headers.etag);

const server = http.createServer(async (req, res) => {
  if (req.url.startsWith('/api/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: backendPort, path: req.url, method: req.method, headers: req.headers }, response => {
      apiTraffic.push({ method: req.method, path: req.url, status: response.statusCode });
      res.writeHead(response.statusCode, response.headers); response.pipe(res);
    });
    upstream.on('error', () => { res.writeHead(502); res.end(); }); req.pipe(upstream); return;
  }
  const pathname = new URL(req.url, base).pathname;
  const target = resolve(dist, pathname === '/' ? 'index.html' : pathname.slice(1));
  if (!target.startsWith(dist + sep)) { res.writeHead(404).end(); return; }
  try {
    const data = await readFile(target);
    res.writeHead(200, { 'Content-Type': ({ '.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.svg':'image/svg+xml' })[extname(target)] ?? 'application/octet-stream' }); res.end(data);
  } catch { res.writeHead(404).end(); }
});
await new Promise(r => server.listen(frontendPort, '127.0.0.1', r));
const browser = await chromium.launch(process.env.TASKMANAGER_BROWSER ? { executablePath: process.env.TASKMANAGER_BROWSER } : { channel: 'chrome' });
async function pageAs(locale = 'de-DE', name = 'reviewadmin', pass = password) {
  const context = await browser.newContext({ locale, viewport: { width: 1100, height: 850 } });
  const page = await context.newPage(); page.setDefaultTimeout(12000);
  page.on('pageerror', error => results.push({ unexpectedPageError: error.message }));
  await page.goto(base);
  await page.getByLabel(locale === 'de-DE' ? /^Anmeldename/ : /^User name/).fill(name);
  await page.getByLabel(locale === 'de-DE' ? /^Passwort/ : /^Password/).fill(pass);
  await page.getByRole('button', { name: locale === 'de-DE' ? 'Anmelden' : 'Sign in', exact: true }).click();
  await page.waitForSelector(name.startsWith('reviewpending') ? '.login-card h1' : '.shell main h1');
  return { context, page };
}
async function shot(page, name) { await page.screenshot({ path: resolve(here, name + '.jpg'), type: 'jpeg', quality: 65, fullPage: true }); }
async function check(id, run) {
  try { const detail = await run(); results.push({ id, result: 'PASS', ...detail }); }
  catch (error) { results.push({ id, result: 'FAIL', message: error.message }); }
  console.log(JSON.stringify(results.at(-1)));
}
const save = p => p.getByRole('button', { name: 'Änderungen speichern', exact: true });

try {
  await check('F1', async () => {
    const { page, context } = await pageAs(); const waiting = gate(), release = gate();
    let hold = false;
    try {
      await page.goto(base + '/' + hash);
      await page.getByTestId('history').waitFor();
      assert.match(await page.getByTestId('history').innerText(), /Review141-GeheimAlt/);
      const before = (await admin.ok('GET', ticketPath)).body.version;
      await page.getByLabel(/^Beschreibung/).fill('Abgelehnter Entwurf 141');
      await field('description', 'read', 'remove');
      await page.route('**/history', async route => { const response = await route.fetch(); if (hold) { waiting.resolve(); await release.promise; } await route.fulfill({ response }); });
      hold = true; await save(page).click(); await waiting.promise;
      const whileWaiting = await page.locator('section[aria-labelledby="ticket-history"]').innerText();
      assert.ok(!whileWaiting.includes('Review141-Geheim'), 'stale secret is absent while fresh history is outstanding');
      release.resolve();
      await page.locator('[data-field="description"].hint').waitFor();
      await page.getByTestId('history').waitFor();
      assert.ok(!(await page.getByTestId('history').innerText()).includes('Review141-Geheim'));
      const fresh = await admin.ok('GET', ticketPath);
      assert.equal(fresh.body.version, before);
      const history = (await admin.ok('GET', ticketPath + '/history')).body;
      assert.equal(history.flatMap(e => e.changes).filter(c => c.field === 'description').length, 0);
      await shot(page, '01-history-after-field-withdrawal');
      return { unchangedVersion: before, descriptionChanges: 0, staleHistoryVisibleWhileLoading: false };
    } finally { release.resolve(); await field('description', 'read', 'allow'); await context.close(); }
  });

  await check('F2', async () => {
    const { page, context } = await pageAs(); const waiting = gate(), release = gate(); let held = false;
    try {
      await page.goto(base + '/' + hash); await page.getByLabel(/^Titel/).fill('Gesendeter Titel 141 ' + runSuffix);
      await page.route('**/api/v1/projects/*/tickets/*', async route => {
        if (route.request().method() !== 'PATCH' || held) return route.continue();
        held = true; const response = await route.fetch(); assert.equal(response.status(), 200); waiting.resolve(); await release.promise; await route.fulfill({ response });
      });
      await save(page).click(); await waiting.promise;
      await page.getByLabel(/^Beschreibung/).fill('Nach dem Absenden zusätzlich getippt 141'); release.resolve();
      await page.getByText('Gespeichert.', { exact: true }).waitFor();
      assert.equal(await page.getByLabel(/^Beschreibung/).inputValue(), 'Nach dem Absenden zusätzlich getippt 141');
      assert.equal(await save(page).isEnabled(), true);
      assert.equal((await admin.ok('GET', ticketPath)).body.fields.description, 'Review141-GeheimNeu');
      const dialog = page.waitForEvent('dialog'); const click = page.getByRole('link', { name: 'Suche', exact: true }).click();
      const d = await dialog; assert.equal(d.type(), 'confirm'); await d.dismiss(); await click;
      await page.waitForURL(url => url.hash === hash);
      await shot(page, '02-later-input-still-unsaved');
      const savedAgain = page.waitForResponse(r => r.request().method() === 'PATCH' && r.status() === 200);
      await save(page).click(); await savedAgain;
      await page.waitForFunction(() => document.querySelector('button[type=submit]')?.disabled === true);
      assert.equal((await admin.ok('GET', ticketPath)).body.fields.description, 'Nach dem Absenden zusätzlich getippt 141');
      return { committedBeforeDelivery: true, laterInputPreserved: true, navigationAsked: true, secondSavePersisted: true };
    } finally { release.resolve(); await context.close(); }
  });

  for (const locale of ['de-DE', 'en-US']) for (const kind of ['project','account','group']) await check(`F3-${kind}-${locale}`, async () => {
    const { page, context } = await pageAs(locale); const de = locale === 'de-DE'; const asked = [];
    try {
      await page.goto(base + '/#/' + ({ project:'projects', account:'accounts', group:'groups' })[kind]);
      if (kind !== 'group') await page.locator('summary').click();
      const fields = kind === 'project' ? [[de?'Projektschlüssel':'Project key', `D${runSuffix.toUpperCase()}${de?'D':'E'}`], ['Name', `Entwurf ${locale}`]]
        : kind === 'account' ? [[de?'Anmeldename':'User name', `draft${de?'de':'en'}${runSuffix}`], [de?'Anzeigename':'Display name', `Entwurf ${locale}`]]
        : [[de?'Neue Gruppe':'New group', `Entwurf ${locale} ${runSuffix}`]];
      for (const [label, value] of fields) await page.getByLabel(new RegExp('^' + label)).fill(value);
      const old = page.url();
      const onDialog = async d => { asked.push({ type:d.type(), message:d.message() }); await d.dismiss(); }; page.on('dialog', onDialog);
      await page.getByRole('link', { name: de?'Suche':'Search', exact:true }).click();
      await page.waitForTimeout(100); assert.equal(asked.length, 1); assert.equal(page.url(), old);
      assert.equal(await page.getByLabel(new RegExp('^' + fields[0][0])).inputValue(), fields[0][1]);
      await page.reload({ waitUntil:'domcontentloaded', timeout:1500 }).catch(() => {});
      assert.equal(asked.at(-1).type, 'beforeunload'); assert.equal(asked.length, 2);
      const submit = ({ project: de?'Projekt anlegen':'Create project', account:de?'Konto anlegen':'Create account', group:de?'Gruppe anlegen':'Create group' })[kind];
      await page.getByRole('button', { name:submit, exact:true }).click();
      if (kind === 'account') { await page.getByTestId('one-time').waitFor(); assert.equal(await page.getByLabel(new RegExp('^'+fields[0][0])).inputValue(), ''); }
      else await page.waitForFunction(old => location.href !== old, old);
      await page.getByRole('link', { name:de?'Suche':'Search', exact:true }).click();
      await page.getByRole('heading', { name:de?'Suche':'Search', exact:true }).waitFor(); assert.equal(asked.length, 2);
      page.off('dialog', onDialog);
      return { navigationAsked:true, cancelledInputRetained:true, beforeUnloadAsked:true, successfulCreationWithoutQuestion:true };
    } finally { await context.close(); }
  });

  await check('F4', async () => {
    const pendingName = 'reviewpending' + runSuffix;
    const pending = (await admin.ok('POST', '/accounts', { username:pendingName, displayName:'Review Pending' }, null, 201)).body;
    const { page, context } = await pageAs('en-US', pendingName, pending.oneTimePassword);
    try {
      await page.getByRole('heading', { name:'Choose your password', exact:true }).waitFor();
      const start = apiTraffic.length; await page.getByRole('button', { name:'Sign out', exact:true }).click();
      await page.getByRole('heading', { name:'Sign in', exact:true }).waitFor();
      await page.getByLabel(/^User name/).fill('reviewadmin'); await page.getByLabel(/^Password/).fill(password);
      await page.getByRole('button', { name:'Sign in', exact:true }).click(); await page.waitForSelector('.shell main h1');
      const sequence = apiTraffic.slice(start).filter(r => r.path === '/api/v1/session');
      assert.ok(sequence.some((r,i) => r.method === 'DELETE' && r.status === 204 && sequence.slice(i+1).some(s => s.method==='GET' && s.status===200)));
      assert.ok(sequence.some(r => r.method==='POST' && r.status===200)); assert.ok(!sequence.some(r => r.status===403));
      return { samePageWithoutReload:true, actualCookieCsrfSequence:sequence };
    } finally { await context.close(); }
  });

  await check('F6', async () => {
    const { page, context } = await pageAs();
    try {
      await page.goto(base + '/' + hash); await page.getByLabel(/^Titel/).fill('Verweigerter Titel 141');
      assert.equal(await page.locator('.sidebar').getByRole('link', { name:'Gruppen und Rechte', exact:true }).count(), 1);
      await peer.ok('PUT', `/permissions/groups/${group}/actions`, { action:'ticket.edit', enabled:false, projectId:null });
      await peer.ok('DELETE', `/permissions/groups/${adminGroup}/members/${adminId}`);
      await save(page).click(); await page.getByText(/Ticket und Rechte sind neu geladen/).waitFor();
      assert.equal(await page.locator('.sidebar').getByRole('link', { name:'Gruppen und Rechte', exact:true }).count(), 0);
      assert.equal(await page.locator('.sidebar').getByRole('link', { name:'Konten', exact:true }).count(), 0);
      assert.equal(await page.getByLabel(/^Titel/).count(), 0);
      assert.deepEqual((await admin.ok('GET', '/session')).body.rights, []);
      await shot(page, '03-navigation-after-rights-withdrawal');
      return { freshRights:[], administrationLinks:0, writableTitleInputs:0 };
    } finally {
      await peer.ok('PUT', `/permissions/groups/${adminGroup}/members/${adminId}`);
      await peer.ok('PUT', `/permissions/groups/${group}/actions`, { action:'ticket.edit', enabled:true, projectId:null });
      await context.close();
    }
  });

  await check('F5', async () => {
    assert.ok(process.env.PSQL, 'PSQL and PG* must point to the disposable review database');
    const holder = spawn(process.env.PSQL, ['-X','-At','-c', 'begin; select revision from permissions_revision for update; select pg_sleep(9); rollback;'],
      { env:{ ...process.env, PGAPPNAME:'task030-followup-lock-holder' }, stdio:'pipe' });
    const finished = new Promise((resolve, reject) => { holder.on('error', reject); holder.on('exit', code => code===0 ? resolve() : reject(new Error('lock holder exited '+code))); });
    for (let i=0; ; i++) {
      const seen = spawnSync(process.env.PSQL, ['-X','-At','-c', "select count(*) from pg_stat_activity where application_name='task030-followup-lock-holder' and wait_event='PgSleep'"], { encoding:'utf8' });
      if (seen.stdout.trim()==='1') break;
      assert.ok(i<100, 'actual holder observed before blocked request'); await delay(50);
    }
    const started = performance.now();
    const blockedName = 'Blocked independent 141 ' + runSuffix;
    const answer = await admin.call('POST','/permissions/groups', { name:blockedName });
    const seconds = (performance.now()-started)/1000;
    assert.equal(answer.status, 503); assert.equal(answer.body.code,'permissions.busy'); assert.equal(answer.headers['retry-after'],'1');
    assert.ok(answer.headers['x-correlation-id']); assert.ok(seconds>=4 && seconds<8);
    assert.ok(!JSON.stringify(answer.body).match(/SQLException|55P03|stacktrace/i));
    await finished;
    assert.equal((await admin.ok('GET','/permissions/groups')).body.filter(g=>g.name===blockedName).length,0);
    await admin.ok('POST','/permissions/groups',{ name:'Retry after holder released 141 ' + runSuffix },null,201);
    return { status:answer.status, code:answer.body.code, retryAfter:answer.headers['retry-after'], correlation:true, elapsedSeconds:Number(seconds.toFixed(2)), rolledBack:true, retryAfterRelease:201 };
  });
} finally {
  await browser.close(); server.closeAllConnections(); await new Promise(r => server.close(r));
  await writeFile(resolve(here, 'replay-results.json'), JSON.stringify(results, null, 2) + '\n');
}
if (results.some(r => r.result==='FAIL' || r.unexpectedPageError)) process.exitCode = 1;
