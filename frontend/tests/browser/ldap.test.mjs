import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { ANNA, createBackend } from './backend.mjs';
import { launch, open, startServer } from './support.mjs';

// TASK-046: the connection to the LDAP directory in the account administration, directory accounts in the list and the
// message of a sign in while the directory does not answer, against the in-memory backend that follows the contract.

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

async function withApp(prepare, body, { as = ANNA, locale = 'de-DE' } = {}) {
  const backend = createBackend();
  prepare?.(backend);
  const session = await open(browser, `${base()}#/accounts`, { locale, setup: (context, page) => backend.install(context, page, { as }) });
  try {
    if (as !== null) {
      await session.page.waitForSelector('.shell main h1');
      await session.page.getByTestId('account-table').waitFor();
    } else {
      await session.page.waitForSelector('main h1');
    }
    await body(session.page, backend);
    assert.deepEqual(session.problems.filter((text) => !/Failed to load resource/.test(text)), [], 'no console warnings, errors or page errors');
  } finally {
    await session.context.close();
  }
}

async function fill(page) {
  await page.getByTestId('ldap-panel').locator('summary').click();
  await page.getByLabel('Adresse des Verzeichnisses').fill('ldaps://verzeichnis.example:636');
  await page.getByLabel('Verschlüsselung').selectOption('ldaps');
  await page.getByLabel('Suchkonto (Distinguished Name)').fill('cn=search,dc=example,dc=test');
  await page.getByLabel('Suchbasis').fill('ou=people,dc=example,dc=test');
}

describe('LDAP connection', () => {
  test('the connection is stored, never shows a secret and shows in the log', async () => {
    await withApp(null, async (page, backend) => {
      await fill(page);
      assert.match(await page.getByTestId('ldap-bind-password').innerText(), /Passwort des Suchkontos stellt der Betrieb bereit/);
      await page.getByLabel('Anmeldung über das Verzeichnis einschalten').check();
      await page.getByRole('button', { name: 'Anbindung speichern' }).click();
      await page.getByText('Die Anbindung ist gespeichert.').waitFor();
      assert.deepEqual(backend.state.ldap, {
        enabled: true, url: 'ldaps://verzeichnis.example:636', security: 'ldaps', bindDn: 'cn=search,dc=example,dc=test',
        searchBase: 'ou=people,dc=example,dc=test', userFilter: '(uid={0})', idAttribute: 'entryUUID', nameAttribute: 'cn',
      });
      await page.getByTestId('accounts-audit').getByText('Anbindung des Verzeichnisses geändert').waitFor();
      assert.equal(await page.locator('input[type="password"]').count(), 0, 'the page has no field for the password of the search account');
    });
  });

  test('a stored connection is shown again and an unsaved change asks before the page is left', async () => {
    await withApp((backend) => {
      backend.state.ldap = { enabled: true, url: 'ldap://verzeichnis.example', security: 'starttls', bindDn: null, searchBase: 'dc=example,dc=test',
        userFilter: '(mail={0})', idAttribute: 'entryUUID', nameAttribute: null };
    }, async (page) => {
      await page.getByTestId('ldap-panel').locator('summary').click();
      assert.equal(await page.getByLabel('Suchfilter').inputValue(), '(mail={0})');
      assert.equal(await page.getByLabel('Anmeldung über das Verzeichnis einschalten').isChecked(), true);
      await page.getByLabel('Suchfilter').fill('(uid={0})');
      let asked = '';
      page.once('dialog', (dialog) => {
        asked = dialog.message();
        void dialog.dismiss();
      });
      await page.getByRole('link', { name: 'Suche' }).click();
      await page.waitForTimeout(300);
      assert.equal(asked, 'Es gibt ungespeicherte Eingaben. Seite trotzdem verlassen?');
    });
  });

  test('invalid input is refused with a hint and stores nothing', async () => {
    await withApp(null, async (page, backend) => {
      await page.getByTestId('ldap-panel').locator('summary').click();
      await page.getByLabel('Adresse des Verzeichnisses').fill('http://falsch');
      await page.getByRole('button', { name: 'Anbindung speichern' }).click();
      await page.getByText(/Die Angaben sind unvollständig oder nicht gültig/).waitFor();
      assert.equal(backend.state.ldap, null);
    });
  });

  test('the check names what failed and what works', async () => {
    await withApp(null, async (page) => {
      await fill(page);
      await page.getByRole('button', { name: 'Verbindung prüfen' }).click();
      await page.getByText('Die Verbindung funktioniert.').waitFor();
      await page.getByLabel('Name zum Ausprobieren (optional)').fill('ghost');
      await page.getByRole('button', { name: 'Verbindung prüfen' }).click();
      await page.getByTestId('ldap-test-failed').getByText('Zu diesem Anmeldenamen wurde kein Eintrag gefunden.').waitFor();
      await page.getByLabel('Adresse des Verzeichnisses').fill('ldaps://down.example:636');
      await page.getByRole('button', { name: 'Verbindung prüfen' }).click();
      await page.getByTestId('ldap-test-failed').getByText('Das Verzeichnis ist nicht erreichbar.').waitFor();
    });
  });

  test('the secret of the search account is missing: the page says so', async () => {
    await withApp((backend) => { backend.state.ldapBindPasswordSet = false; }, async (page) => {
      await page.getByTestId('ldap-panel').locator('summary').click();
      assert.match(await page.getByTestId('ldap-bind-password').innerText(), /TASK_MANAGER_LDAP_BIND_PASSWORD/);
    });
  });
});

describe('directory accounts', () => {
  const directoryAccount = (backend) => {
    backend.state.accounts.push({ id: '00000000-0000-4000-8000-0000000000aa', username: 'dora', displayName: 'Dora Verzeichnis', password: 'unbekannt', active: true,
      passwordChangeRequired: false, createdAt: '2026-10-22T08:00:00Z', authKind: 'ldap' });
  };

  test('a directory account has no password reset and no name to change, but can be deactivated', async () => {
    await withApp(directoryAccount, async (page, backend) => {
      const row = page.locator('tr', { hasText: 'dora' });
      assert.match(await row.innerText(), /Konto aus dem Verzeichnis/);
      assert.equal(await page.getByRole('button', { name: 'Neues Einmalpasswort für dora' }).count(), 0);
      assert.equal(await page.getByRole('button', { name: 'Anzeigename von dora ändern' }).count(), 0);
      assert.equal(await page.getByRole('button', { name: 'Neues Einmalpasswort für ben' }).count(), 1, 'local accounts keep both');
      await page.getByRole('button', { name: 'dora deaktivieren' }).click();
      await page.getByText('dora ist deaktiviert; alle Sitzungen sind beendet.').waitFor();
      assert.equal(backend.state.accounts.find((item) => item.username === 'dora').active, false);
    });
  });

  test('a sign in while the directory does not answer says so and keeps local accounts apart', async () => {
    await withApp((backend) => {
      backend.state.ldap = { enabled: true, url: 'ldap://verzeichnis.example', security: 'starttls', bindDn: null, searchBase: 'dc=example,dc=test',
        userFilter: '(uid={0})', idAttribute: 'entryUUID', nameAttribute: null };
      backend.state.directoryDown = true;
    }, async (page) => {
      await page.getByLabel(/^Anmeldename/).fill('dora');
      await page.getByLabel(/^Passwort/).fill('irgendwas');
      await page.keyboard.press('Enter');
      assert.match(await page.getByTestId('login-error').innerText(), /Das Verzeichnis antwortet gerade nicht/);
      await page.getByLabel(/^Anmeldename/).fill(ANNA.username);
      await page.getByLabel(/^Passwort/).fill(ANNA.password);
      await page.keyboard.press('Enter');
      await page.waitForSelector('.shell');
    }, { as: null });
  });
});

describe('converting a directory account', () => {
  const directoryAccount = (backend) => {
    backend.state.accounts.push({ id: '00000000-0000-4000-8000-0000000000bb', username: 'emil', displayName: 'Emil Verzeichnis', password: 'unbekannt', active: true,
      passwordChangeRequired: false, createdAt: '2026-10-22T08:00:00Z', authKind: 'ldap' });
  };

  test('the conversion asks first, then shows the one time password once and the account becomes local', async () => {
    await withApp(directoryAccount, async (page, backend) => {
      await page.getByRole('button', { name: 'emil auf lokales Konto umstellen' }).click();
      assert.match(await page.getByRole('group', { name: 'Umstellung von emil bestätigen' }).innerText(), /meldet sich danach nur noch mit einem neuen Einmalpasswort an/);
      await page.getByRole('button', { name: 'Abbrechen' }).click();
      assert.equal(backend.state.accounts.find((item) => item.username === 'emil').authKind, 'ldap', 'cancelling changed nothing');

      await page.getByRole('button', { name: 'emil auf lokales Konto umstellen' }).click();
      await page.getByRole('button', { name: 'Ja, emil umstellen' }).click();
      const shown = page.getByTestId('one-time');
      await shown.waitFor();
      assert.match(await shown.innerText(), /Einmalpasswort für emil/);
      assert.equal(backend.state.accounts.find((item) => item.username === 'emil').authKind, 'local');
      const row = page.locator('tr', { hasText: 'emil' });
      assert.doesNotMatch(await row.innerText(), /Konto aus dem Verzeichnis/);
      assert.equal(await page.getByRole('button', { name: 'emil auf lokales Konto umstellen' }).count(), 0);
      assert.equal(await page.getByRole('button', { name: 'Neues Einmalpasswort für emil' }).count(), 1, 'a local account has the reset again');
      await page.getByTestId('accounts-audit').getByText('Konto emil auf lokal umgestellt, neues Einmalpasswort ausgegeben').waitFor();
    });
  });

  test('a local account has no conversion', async () => {
    await withApp(null, async (page) => {
      assert.equal(await page.getByRole('button', { name: /auf lokales Konto umstellen/ }).count(), 0);
    });
  });
});

describe('converting the own account', () => {
  async function confirm(page) {
    await page.getByRole('button', { name: 'anna auf lokales Konto umstellen' }).click();
    assert.match(await page.getByRole('group', { name: 'Umstellung von anna bestätigen' }).innerText(), /Das ist Ihr eigenes Konto\. Danach endet Ihre Sitzung/);
    await page.getByRole('button', { name: 'Ja, anna umstellen' }).click();
  }

  test('a screen above all pages keeps the one time password, loads nothing and leads to the login', async () => {
    await withApp((backend) => { backend.anna.authKind = 'ldap'; }, async (page, backend) => {
      await confirm(page);
      const shown = page.getByTestId('one-time');
      await shown.waitFor();
      assert.equal(await page.locator('main h1').innerText(), 'Ihr Konto ist auf lokal umgestellt');
      const password = await shown.locator('code').innerText();
      assert.notEqual(password, '');
      await shown.getByText('Ihre Sitzung ist beendet').waitFor();
      const conversion = backend.state.requests.findIndex((item) => item.path.endsWith('/convert-to-local'));
      await page.waitForTimeout(500);
      assert.equal(backend.state.requests.length, conversion + 1, 'no request follows the conversion');
      assert.equal(await shown.locator('code').innerText(), password);
      assert.equal(await page.getByRole('button', { name: /deaktivieren|Neues Einmalpasswort|Verbindung prüfen/ }).count(), 0, 'no action that needs a session is offered');
      assert.equal(await page.getByRole('navigation').count(), 0, 'the navigation of the application is gone');
      await page.getByRole('button', { name: 'Zur Anmeldung' }).click();
      await page.getByLabel(/^Anmeldename/).waitFor();
      assert.equal(await page.getByTestId('one-time').count(), 0, 'the password is gone from the page after the login is shown');
    });
  });

  test('a late answer that ends the session does not take the password away', async () => {
    await withApp((backend) => { backend.anna.authKind = 'ldap'; }, async (page) => {
      // A request that is under way when the person confirms: the check of the connection, held back until after the conversion.
      let release;
      const held = new Promise((resolve) => { release = resolve; });
      let reached;
      const arrived = new Promise((resolve) => { reached = resolve; });
      await page.route('**/api/v1/settings/ldap/test', async (route) => {
        reached();
        await held;
        await route.fallback();
      });
      await page.getByTestId('ldap-panel').locator('summary').click();
      await page.getByLabel('Adresse des Verzeichnisses').fill('ldap://verzeichnis.example');
      await page.getByLabel('Suchbasis').fill('dc=example,dc=test');
      await page.getByRole('button', { name: 'Verbindung prüfen' }).click();
      await arrived;
      await confirm(page);
      const shown = page.getByTestId('one-time');
      await shown.waitFor();
      const password = await shown.locator('code').innerText();
      release();
      await page.waitForTimeout(800);
      assert.equal(await shown.locator('code').innerText(), password, 'the late 401 does not replace the screen');
      assert.equal(await page.getByLabel(/^Anmeldename/).count(), 0, 'the login is not shown before the person asks for it');
      await page.getByRole('button', { name: 'Zur Anmeldung' }).click();
      await page.getByLabel(/^Anmeldename/).waitFor();
    });
  });

  test('the English text says the same', async () => {
    await withApp((backend) => { backend.anna.authKind = 'ldap'; }, async (page) => {
      await page.getByRole('button', { name: 'Convert anna to a local account' }).click();
      await page.getByRole('button', { name: 'Yes, convert anna' }).click();
      await page.getByTestId('one-time').waitFor();
      assert.equal(await page.locator('main h1').innerText(), 'Your account has been converted to local');
      await page.getByRole('button', { name: 'Go to the login' }).click();
      await page.getByLabel(/^User name/).waitFor();
    }, { locale: 'en-US' });
  });
});
