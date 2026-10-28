// A small in-memory stand in for the backend, answered through page.route. It follows the published contracts closely
// enough for the interface: sessions, accounts, projects, tickets with versions and field rights, tags, search, groups
// and the appearance. All data is synthetic. Several browser contexts can share one backend, each with its own session.

export const TOKEN = 'csrf-token-for-browser-tests-0001';
const PNG = Buffer.from('89504e470d0a1a0a0000000d4948445200000001000000010806000000' + '1f15c4890000000d49444154789c6360f8cfc0f01f0005000201' + 'e2d1b0a50000000049454e44ae426082', 'hex');
const FIELDS = ['title', 'description', 'type', 'status', 'assignee'];
const TAG_FIELD = 'tags';
const ATTACHMENT_FIELD = 'attachments';
const COMPANY_ACTIONS = ['accounts.manage', 'appearance.manage', 'jobs.manage', 'permissions.audit.read', 'permissions.manage', 'projects.manage', 'session_duration.manage'];
const PROJECT_ACTIONS = ['project.manage', 'project.read', 'tag.create', 'tags.manage', 'ticket.create', 'ticket.edit', 'ticket.read'];
const ADMIN_RIGHTS = ['permissions.manage', 'accounts.manage', 'session_duration.manage', 'appearance.manage', 'projects.manage'];
const CONFIGURATION = {
  ticketTypes: [{ key: 'task', name: 'Aufgabe' }, { key: 'bug', name: 'Fehler' }],
  statuses: [{ key: 'new', name: 'Neu', category: 'open' }, { key: 'in_progress', name: 'In Arbeit', category: 'in_progress' }, { key: 'done', name: 'Erledigt', category: 'done' }],
  initialStatus: 'new',
  transitions: [{ from: 'new', to: 'in_progress' }, { from: 'in_progress', to: 'done' }, { from: 'in_progress', to: 'new' }, { from: 'done', to: 'in_progress' }],
  fields: [{ key: 'title', required: true }, { key: 'description', required: false }, { key: 'type', required: true }, { key: 'status', required: true }, { key: 'assignee', required: false }],
};

let counter = 0;
const uuid = () => `00000000-0000-4000-8000-${String(++counter).padStart(12, '0')}`;
const now = () => new Date(Date.UTC(2026, 9, 21, 8, 0, counter)).toISOString();

export const ANNA = { username: 'anna', password: 'Anna Passwort 2026', displayName: 'Anna Admin' };
export const BEN = { username: 'ben', password: 'Ben Passwort 2026', displayName: 'Ben Leser' };
export const NORA = { username: 'nora', password: 'EinmalNora0001', displayName: 'Nora Neu' };

export function createBackend({ accent = '#f97316', anonymousAppearance = false } = {}) {
  const accounts = [ANNA, BEN, NORA].map((item) => ({
    id: uuid(), username: item.username, displayName: item.displayName, password: item.password, active: true,
    passwordChangeRequired: item === NORA, createdAt: now(),
  }));
  const [anna, ben, nora] = accounts;
  const project = { id: uuid(), key: 'PLAT', name: 'Plattform', description: 'Synthetisches Projekt', version: 1, configurationVersion: 1, createdAt: now(), updatedAt: now() };
  const state = {
    accounts,
    projects: [project],
    // The published configuration per project, and the draft being edited (TASK-044).
    configurations: new Map([[project.id, { version: 1, configuration: structuredClone(CONFIGURATION) }]]),
    drafts: new Map(),
    members: new Map([[project.id, [anna, ben, nora].map((account) => ({ accountId: account.id, addedAt: now() }))]]),
    tickets: [],
    history: [],
    tags: [],
    ticketTags: [],
    // The attachments of tickets (TASK-051); `limit` is the largest file the stand in accepts, small so that tests can reach it.
    attachments: [],
    attachmentLimit: 1000,
    groups: [
      { id: uuid(), name: 'Unternehmensverwaltung', members: [anna.id], actions: [...ADMIN_RIGHTS, 'project.read', 'ticket.read', 'ticket.create', 'ticket.edit', 'tag.create'].map((action) => ({ action, projectId: null })),
        fields: [...FIELDS, TAG_FIELD, ATTACHMENT_FIELD].flatMap((field) => ['read', 'write'].map((operation) => ({ resourceType: 'ticket', field, projectId: null, operation, effect: 'allow' }))) },
      { id: uuid(), name: 'Lesen', members: [ben.id, nora.id], actions: ['project.read', 'ticket.read'].map((action) => ({ action, projectId: null })),
        fields: ['title', 'type', 'status', 'assignee', TAG_FIELD, ATTACHMENT_FIELD].map((field) => ({ resourceType: 'ticket', field, projectId: null, operation: 'read', effect: 'allow' })) },
    ],
    appearance: { version: 3, accent, logo: null },
    // The LDAP connection (TASK-046): null until stored; `directoryDown` makes a sign in of an unknown name fail like an outage.
    ldap: null,
    ldapBindPasswordSet: true,
    directoryDown: false,
    // The rights log, newest last; pages are cut by rightsVersion like on the server.
    audit: [],
    accountAudit: [],
    // The background jobs (TASK-054), newest last, and how many executors report; `jobConflict` makes the next action fail like a concurrent change.
    jobs: [],
    activeWorkers: 1,
    jobConflict: false,
    requests: [],
  };

  const addTicket = (title, description, by) => {
    const number = state.tickets.filter((item) => item.projectId === project.id).length + 1;
    const ticket = { id: uuid(), projectId: project.id, key: `${project.key}-${number}`, number, version: 1, configurationVersion: 1,
      values: { title, description, type: 'task', status: 'new', assignee: null }, custom: {}, createdAt: now(), createdBy: by.id, updatedAt: now(), updatedBy: by.id };
    state.tickets.push(ticket);
    state.history.push({ ticket: ticket.id, version: 1, occurredAt: ticket.createdAt, actor: by.id,
      changes: FIELDS.filter((field) => ticket.values[field] !== null).map((field) => ({ field, from: null, to: ticket.values[field] })) });
    return ticket;
  };
  addTicket('Anmeldung prüfen', 'Ablauf mit synthetischen Konten.', anna);
  addTicket('Suche nach Tags', 'Nur für Berechtigte sichtbar.', anna);
  state.tags.push({ id: uuid(), name: 'Sicherheit', catalog: 'project', projectId: project.id, archived: false, version: 1, createdAt: now(), createdBy: anna.id, updatedAt: now(), updatedBy: anna.id });

  // ------------------------------------------------------------------ rights
  const groupsOf = (account) => state.groups.filter((group) => group.members.includes(account.id));
  const companyRights = (account) => [...new Set(groupsOf(account).flatMap((group) => group.actions.filter((rule) => rule.projectId === null && COMPANY_ACTIONS.includes(rule.action)).map((rule) => rule.action)))];
  const isMember = (account, projectId) => (state.members.get(projectId) ?? []).some((member) => member.accountId === account.id);
  const may = (account, action, projectId) => {
    if (COMPANY_ACTIONS.includes(action)) return companyRights(account).includes(action);
    if (!isMember(account, projectId)) return false;
    return groupsOf(account).some((group) => group.actions.some((rule) => rule.action === action && (rule.projectId === null || rule.projectId === projectId)));
  };
  const field = (account, projectId, name, operation) => {
    const rules = groupsOf(account).flatMap((group) => group.fields).filter((rule) => rule.field === name && rule.operation === operation && (rule.projectId === null || rule.projectId === projectId));
    return rules.some((rule) => rule.effect === 'allow') && !rules.some((rule) => rule.effect === 'deny');
  };
  const readable = (account, projectId) => FIELDS.filter((name) => field(account, projectId, name, 'read'));
  const writable = (account, projectId) => may(account, 'ticket.edit', projectId) ? FIELDS.filter((name) => field(account, projectId, name, 'read') && field(account, projectId, name, 'write')) : [];
  const projectVisible = (account, item) => may(account, 'projects.manage') || (isMember(account, item.id) && PROJECT_ACTIONS.some((action) => may(account, action, item.id)));

  const configurationOf = (projectId) => state.configurations.get(projectId).configuration;
  const customFieldsOf = (projectId) => configurationOf(projectId).customFields ?? [];
  // Custom fields follow the title rule here; the real rules per field are tested against the real backend.
  const customReadable = (account, projectId) => readable(account, projectId).includes('title');
  const customWritable = (account, projectId) => writable(account, projectId).includes('title');
  const ticketView = (account, ticket) => {
    const shown = readable(account, ticket.projectId);
    const custom = customFieldsOf(ticket.projectId);
    return { id: ticket.id, projectId: ticket.projectId, key: ticket.key, number: ticket.number, version: ticket.version, configurationVersion: state.configurations.get(ticket.projectId).version,
      fields: Object.fromEntries(shown.map((name) => [name, ticket.values[name]])),
      customFields: customReadable(account, ticket.projectId) ? Object.fromEntries(custom.filter((entry) => ticket.custom[entry.key] !== undefined).map((entry) => [entry.key, ticket.custom[entry.key]])) : {},
      writableFields: [...writable(account, ticket.projectId), ...(customWritable(account, ticket.projectId) ? custom.filter((entry) => entry.active !== false).map((entry) => `custom.${entry.key}`) : []),
        // Like the server since review F1 of TASK-051: tags and attachments are named when their own write rule allows it.
        ...[TAG_FIELD, ATTACHMENT_FIELD].filter((name) => may(account, 'ticket.edit', ticket.projectId) && field(account, ticket.projectId, name, 'read') && field(account, ticket.projectId, name, 'write'))],
      createdAt: ticket.createdAt, createdBy: ticket.createdBy, updatedAt: ticket.updatedAt, updatedBy: ticket.updatedBy };
  };
  const projectView = (account, item) => ({ ...item, configurationVersion: state.configurations.get(item.id).version, configuration: configurationOf(item.id),
    manageable: may(account, 'projects.manage') || may(account, 'project.manage', item.id) });
  const summary = (account, item) => ({ id: item.id, key: item.key, name: item.name, description: item.description, version: item.version, manageable: projectView(account, item).manageable });
  // An account as the administration sees it (TASK-045): the state of the one time password and a running waiting time.
  const accountView = ({ password: _password, delayedUntil, ...rest }) => ({ authKind: 'local', ...rest,
    oneTimePasswordState: rest.passwordChangeRequired ? 'open' : 'none', oneTimePasswordExpiresAt: rest.passwordChangeRequired ? '2026-10-24T08:00:00Z' : null,
    loginDelayedUntil: delayedUntil ?? null });
  const logAccount = (actor, event, target) => state.accountAudit.push({ id: state.accountAudit.length + 1, occurredAt: now(), actor: actor.username, event, accountId: target?.id ?? null, detail: null });

  // ------------------------------------------------------------------ http
  const problem = (status, code, extra = {}) => ({ status, body: { code, status, correlationId: '0f8b7c1e-1111-4222-8333-444455556666', ...extra } });
  const ok = (body, etag) => ({ status: 200, body, etag });
  const empty = () => ({ status: 204 });

  function handle(session, method, path, query, headers, body) {
    const p = path.replace(/^\/api\/v1/, '');
    if (p === '/session' && method === 'GET') return ok(sessionBody(session));
    if (p === '/session' && method === 'POST') {
      const account = state.accounts.find((item) => item.username === body.username);
      if (!account && state.ldap?.enabled && state.directoryDown) return problem(503, 'auth.directory_unavailable');
      if (!account || !account.active || account.password !== body.password) return problem(401, 'auth.invalid_credentials');
      session.account = account;
      return ok(sessionBody(session));
    }
    if (p === '/session' && method === 'DELETE') {
      session.account = null;
      return empty();
    }
    const account = session.account;
    if (!account || !account.active) {
      session.account = null;
      return problem(401, 'auth.required');
    }
    if (p === '/session/password' && method === 'POST') {
      if (body.currentPassword !== account.password) return problem(400, 'password.current_incorrect');
      if (body.newPassword.length < 12) return problem(400, 'password.policy_violation', { violations: ['password.too_short'] });
      account.password = body.newPassword;
      account.passwordChangeRequired = false;
      return ok(sessionBody(session));
    }
    if (account.passwordChangeRequired) return problem(403, 'auth.password_change_required');

    if (p === '/appearance' && method === 'GET') return ok(appearanceBody(), `"${state.appearance.version}"`);
    if (p.startsWith('/appearance')) {
      if (p === '/appearance/logo' && method === 'GET') return { status: 200, raw: PNG, type: 'image/png' };
      if (!may(account, 'appearance.manage')) return problem(403, 'auth.forbidden');
      if (headers['if-match'] !== `"${state.appearance.version}"`) return problem(412, 'appearance.version_conflict');
      state.appearance.version += 1;
      if (p === '/appearance') state.appearance.accent = body.accentColor;
      else if (method === 'PUT') state.appearance.logo = { contentType: 'image/png', size: body.length, url: `/api/v1/appearance/logo?v=${state.appearance.version}` };
      else state.appearance.logo = null;
      return ok(appearanceBody(), `"${state.appearance.version}"`);
    }

    if (p.startsWith('/settings/ldap')) {
      if (!may(account, 'accounts.manage')) return problem(403, 'auth.forbidden');
      const view = () => (state.ldap === null
        ? { configured: false, enabled: false, url: null, security: null, bindDn: null, searchBase: null, userFilter: null, idAttribute: null, nameAttribute: null, bindPasswordSet: state.ldapBindPasswordSet, updatedAt: null }
        : { configured: true, ...state.ldap, bindPasswordSet: state.ldapBindPasswordSet, updatedAt: '2026-10-23T09:00:00Z' });
      const valid = (value) => typeof value?.url === 'string' && /^ldaps?:\/\/[^/]+$/.test(value.url) && typeof value.searchBase === 'string' && value.searchBase !== ''
        && typeof value.userFilter === 'string' && value.userFilter.includes('{0}') && typeof value.idAttribute === 'string' && value.idAttribute !== '';
      if (p === '/settings/ldap' && method === 'GET') return ok(view());
      if (p === '/settings/ldap' && method === 'PUT') {
        if (!valid(body) || (body.enabled && body.bindDn && !state.ldapBindPasswordSet)) return problem(400, 'request.invalid', { violations: ['ldap.url.invalid'] });
        state.ldap = { enabled: body.enabled === true, url: body.url, security: body.security, bindDn: body.bindDn ?? null, searchBase: body.searchBase, userFilter: body.userFilter, idAttribute: body.idAttribute, nameAttribute: body.nameAttribute ?? null };
        logAccount(account, 'ldap.configured', null);
        return ok(view());
      }
      if (p === '/settings/ldap/test' && method === 'POST') {
        const values = body.settings ?? state.ldap;
        if (values === null || values === undefined) return problem(409, 'ldap.not_configured');
        if (!valid(values)) return problem(400, 'request.invalid', { violations: ['ldap.url.invalid'] });
        if (values.url.includes('down')) return ok({ ok: false, reason: 'directory.unreachable' });
        if (body.username === 'ghost') return ok({ ok: false, reason: 'directory.user_not_found' });
        return ok({ ok: true, reason: null });
      }
    }
    if (p === '/directory/accounts') return ok(state.accounts.map(({ id, username, displayName, active }) => ({ id, username, displayName, active })).sort((a, b) => a.username.localeCompare(b.username)));
    if (p.startsWith('/accounts')) {
      if (!may(account, 'accounts.manage')) return problem(403, 'auth.forbidden');
      if (p === '/accounts' && method === 'GET') return ok(state.accounts.map(accountView));
      if (p === '/accounts/audit' && method === 'GET') {
        const before = Number(query.get('before') ?? Infinity);
        return ok([...state.accountAudit].filter((entry) => entry.id < before).sort((a, b) => b.id - a.id).slice(0, Number(query.get('limit') ?? 50)));
      }
      const single = p.match(/^\/accounts\/([^/]+)$/);
      if (single && method === 'PUT') {
        const target = state.accounts.find((item) => item.id === single[1]);
        if (!target) return problem(404, 'account.not_found');
        if (target.authKind === 'ldap') return problem(409, 'account.directory_managed');
        const name = typeof body.displayName === 'string' && body.displayName.trim() !== '' ? body.displayName.trim() : null;
        if (name !== null && name.length > 100) return problem(400, 'request.invalid', { violations: ['displayName.too_long'] });
        if (name !== target.displayName) {
          target.displayName = name;
          logAccount(account, 'account.renamed', target);
        }
        return ok(accountView(target));
      }
      if (p === '/accounts' && method === 'POST') {
        if (state.accounts.some((item) => item.username === body.username)) return problem(409, 'account.username_taken');
        const created = { id: uuid(), username: body.username, displayName: body.displayName ?? null, password: `Einmal${counter}Passwort`, active: true, passwordChangeRequired: true, createdAt: now() };
        state.accounts.push(created);
        logAccount(account, 'account.created', created);
        return ok({ account: accountView(created), oneTimePassword: created.password, oneTimePasswordExpiresAt: '2026-10-24T08:00:00Z' });
      }
      const [, id, action] = p.match(/^\/accounts\/([^/]+)\/([a-z-]+)$/) ?? [];
      const target = state.accounts.find((item) => item.id === id);
      if (!target) return problem(404, 'account.not_found');
      if (action === 'deactivate') {
        if (target.id === anna.id) return problem(409, 'account.last_administrator');
        target.active = false;
        logAccount(account, 'account.deactivated', target);
      }
      if (action === 'reactivate') {
        target.active = true;
        logAccount(account, 'account.reactivated', target);
      }
      if (action === 'clear-login-delay' && target.delayedUntil) {
        target.delayedUntil = null;
        logAccount(account, 'account.login_delay_cleared', target);
      }
      if (action === 'convert-to-local') {
        if (target.authKind !== 'ldap') return problem(409, 'account.already_local');
        target.authKind = 'local';
        target.password = `Einmal${counter}Lokal`;
        target.passwordChangeRequired = true;
        logAccount(account, 'account.converted_to_local', target);
        // Like the server: all sessions of the account end, the requester's own included.
        if (target.id === account.id) session.account = null;
        return ok({ account: accountView(target), oneTimePassword: target.password, oneTimePasswordExpiresAt: '2026-10-24T08:00:00Z' });
      }
      if (action === 'password-reset') {
        if (target.authKind === 'ldap') return problem(409, 'account.directory_managed');
        target.password = `Einmal${counter}Neu`;
        target.passwordChangeRequired = true;
        logAccount(account, 'password.reset', target);
        return ok({ account: accountView(target), oneTimePassword: target.password, oneTimePasswordExpiresAt: '2026-10-24T08:00:00Z' });
      }
      return ok(accountView(target));
    }

    if (p === '/projects' && method === 'GET') return ok(state.projects.filter((item) => projectVisible(account, item)).map((item) => summary(account, item)));
    if (p === '/projects' && method === 'POST') {
      if (!may(account, 'projects.manage')) return problem(403, 'auth.forbidden');
      if (state.projects.some((item) => item.key === body.key.toUpperCase())) return problem(409, 'project.key_taken');
      const created = { id: uuid(), key: body.key.toUpperCase(), name: body.name, description: body.description, version: 1, configurationVersion: 1, createdAt: now(), updatedAt: now() };
      state.projects.push(created);
      state.configurations.set(created.id, { version: 1, configuration: structuredClone(CONFIGURATION) });
      state.members.set(created.id, [{ accountId: account.id, addedAt: now() }]);
      return ok(projectView(account, created), '"1"');
    }
    let match = p.match(/^\/projects\/([^/]+)(\/.*)?$/);
    if (match) {
      const item = state.projects.find((entry) => entry.id === match[1]);
      if (!item || !projectVisible(account, item)) return problem(404, 'project.not_found');
      return projectRoute(account, item, method, match[2] ?? '', query, headers, body);
    }
    if (p === '/tags' && method === 'GET') return ok(state.tags.filter((tag) => tag.catalog === 'company'));
    if (p === '/search/tickets') {
      const words = (query.get('q') ?? '').toLowerCase().split(/\s+/).filter(Boolean);
      const items = state.tickets.filter((ticket) => may(account, 'ticket.read', ticket.projectId))
        .filter((ticket) => words.every((word) => readable(account, ticket.projectId).some((name) => String(ticket.values[name] ?? '').toLowerCase().split(/\W+/).some((part) => part.startsWith(word))) || ticket.key.toLowerCase().startsWith(word)))
        .map((ticket) => ({ id: ticket.id, projectId: ticket.projectId, key: ticket.key, number: ticket.number, fields: ticketView(account, ticket).fields, customFields: ticketView(account, ticket).customFields, tags: [] }));
      return ok({ total: items.length, items });
    }
    if (p === '/search/projects') {
      const q = (query.get('q') ?? '').toLowerCase();
      const items = state.projects.filter((item) => projectVisible(account, item) && (item.name.toLowerCase().includes(q) || item.key.toLowerCase().startsWith(q)))
        .map((item) => ({ id: item.id, key: item.key, name: item.name, description: item.description, tags: [] }));
      return ok({ total: items.length, items });
    }
    if (p.startsWith('/permissions')) return permissionsRoute(account, session, method, p, body, query);
    if (p.startsWith('/jobs')) return jobsRoute(account, method, p, query);
    return problem(404, 'resource.not_found');
  }

  function projectRoute(account, item, method, rest, query, headers, body) {
    if (rest === '' && method === 'GET') return ok(projectView(account, item), `"${item.version}"`);
    if (rest === '/members' && method === 'GET') return ok(state.members.get(item.id), `"${item.version}"`);
    if (rest.startsWith('/configuration/')) return configurationRoute(account, item, method, rest, headers, body);
    if (rest === '/catalog/tags' && method === 'GET') return ok(state.tags.filter((tag) => tag.projectId === item.id));
    if (rest === '/catalog/tags' && method === 'POST') {
      if (!may(account, 'tag.create', item.id) && !may(account, 'tags.manage', item.id)) return problem(403, 'auth.forbidden');
      const tag = { id: uuid(), name: body.name, catalog: 'project', projectId: item.id, archived: false, version: 1, createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      state.tags.push(tag);
      return ok(tag);
    }
    if (rest === '/tickets' && method === 'GET') {
      if (!may(account, 'ticket.read', item.id)) return problem(404, 'project.not_found');
      return ok(state.tickets.filter((ticket) => ticket.projectId === item.id).sort((a, b) => b.number - a.number).map((ticket) => ticketView(account, ticket)));
    }
    if (rest === '/tickets' && method === 'POST') {
      if (!may(account, 'ticket.create', item.id)) return problem(403, 'auth.forbidden');
      if (typeof body.title !== 'string' || body.title.trim() === '') return problem(400, 'request.invalid', { violations: ['title.invalid'] });
      const missing = customFieldsOf(item.id).filter((entry) => entry.required && entry.active !== false && body.customFields?.[entry.key] === undefined)
        .map((entry) => `custom.${entry.key}.required`);
      if (missing.length > 0) return problem(400, 'request.invalid', { violations: missing });
      const ticket = addTicket(body.title, body.description ?? null, account);
      ticket.custom = { ...(body.customFields ?? {}) };
      for (const [key, value] of Object.entries(ticket.custom)) {
        state.history.find((entry) => entry.ticket === ticket.id).changes.push({ field: `custom.${key}`, from: null, to: String(value) });
      }
      return ok(ticketView(account, ticket), '"1"');
    }
    const match = rest.match(/^\/tickets\/(\d+)(\/.*)?$/);
    if (!match) return problem(404, 'resource.not_found');
    const ticket = state.tickets.find((entry) => entry.projectId === item.id && entry.number === Number(match[1]));
    if (!ticket || !may(account, 'ticket.read', item.id)) return problem(404, 'ticket.not_found');
    const tail = match[2] ?? '';
    if (tail === '' && method === 'GET') return ok(ticketView(account, ticket), `"${ticket.version}"`);
    if (tail === '' && method === 'PATCH') {
      if (!headers['if-match']) return problem(428, 'ticket.precondition_required');
      if (headers['if-match'] !== `"${ticket.version}"`) return problem(412, 'ticket.version_conflict');
      const allowed = writable(account, item.id);
      const { customFields: customChanges = {}, ...standard } = body;
      const names = Object.keys(standard);
      if (names.some((name) => !allowed.includes(name))) return problem(403, 'auth.forbidden');
      const known = customFieldsOf(item.id);
      if (Object.keys(customChanges).some((key) => !customWritable(account, item.id) || !known.some((entry) => entry.key === key && entry.active !== false))) return problem(403, 'auth.forbidden');
      const merged = { ...ticket.custom };
      for (const [key, value] of Object.entries(customChanges)) {
        if (value === null) delete merged[key];
        else merged[key] = value;
      }
      if (standard.status !== undefined && standard.status !== ticket.values.status) {
        const configuration = configurationOf(item.id);
        const own = (configuration.workflows ?? []).find((workflow) => workflow.type === (standard.type ?? ticket.values.type));
        const step = (own?.transitions ?? configuration.transitions).find((entry) => entry.from === ticket.values.status && entry.to === standard.status);
        if (!step) return problem(409, 'ticket.transition_not_allowed');
        const after = { ...ticket.values, ...standard };
        const missing = (step.requiredFields ?? []).filter((id) => (id.startsWith('custom.') ? merged[id.slice(7)] === undefined : after[id] === null || after[id] === undefined || after[id] === ''))
          .map((id) => `${id}.required_for_transition`);
        if (missing.length > 0) return problem(400, 'request.invalid', { violations: missing });
      }
      const changes = [
        ...names.filter((name) => standard[name] !== ticket.values[name]).map((name) => ({ field: name, from: ticket.values[name], to: standard[name] })),
        ...Object.keys(customChanges).filter((key) => (merged[key] ?? null) !== (ticket.custom[key] ?? null))
          .map((key) => ({ field: `custom.${key}`, from: ticket.custom[key] === undefined ? null : String(ticket.custom[key]), to: merged[key] === undefined ? null : String(merged[key]) })),
      ];
      if (changes.length > 0) {
        for (const change of changes) {
          if (change.field.startsWith('custom.')) continue;
          ticket.values[change.field] = change.to;
        }
        ticket.custom = merged;
        ticket.version += 1;
        ticket.updatedAt = now();
        ticket.updatedBy = account.id;
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: ticket.updatedAt, actor: account.id, changes });
      }
      return ok(ticketView(account, ticket), `"${ticket.version}"`);
    }
    if (tail === '/history') {
      const shown = [...readable(account, item.id), ...(customReadable(account, item.id) ? customFieldsOf(item.id).map((entry) => `custom.${entry.key}`) : []), ...(field(account, item.id, TAG_FIELD, 'read') ? [TAG_FIELD] : []), ...(field(account, item.id, ATTACHMENT_FIELD, 'read') ? [ATTACHMENT_FIELD] : [])];
      return ok(state.history.filter((entry) => entry.ticket === ticket.id)
        .map(({ ticket: _ticket, ...entry }) => ({ ...entry, changes: entry.changes.filter((change) => shown.includes(change.field)) }))
        .filter((entry) => entry.changes.length > 0));
    }
    if (tail.startsWith('/attachments')) {
      if (!field(account, item.id, ATTACHMENT_FIELD, 'read')) return problem(403, 'auth.forbidden');
      const id = tail.split('/')[2];
      const mine = state.attachments.filter((entry) => entry.ticket === ticket.id);
      const view = ({ ticket: _ticket, content: _content, removed: _removed, ...entry }) => entry;
      const described = (entry) => JSON.stringify({ id: entry.id, name: entry.name, size: entry.size, sha256: entry.sha256 });
      if (method === 'GET' && id === undefined) return { ...ok(mine.filter((entry) => !entry.removed).map(view)), headers: { 'X-Attachment-Max-Bytes': String(state.attachmentLimit) } };
      if (method === 'GET' || method === 'HEAD') {
        // `content: null` stands for a file the store lost, for example after restoring an older backup.
        const found = mine.find((entry) => entry.id === id && !entry.removed && entry.content !== null);
        return found ? { status: 200, raw: found.content, type: 'application/octet-stream', headers: { 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(found.name)}` } } : problem(404, 'attachment.not_found');
      }
      if (!may(account, 'ticket.edit', item.id) || !field(account, item.id, ATTACHMENT_FIELD, 'write')) return problem(403, 'auth.forbidden');
      if (method === 'POST') {
        const name = query.get('name');
        const content = Buffer.isBuffer(body) ? body : Buffer.alloc(0);
        if (!name || name.replace(/[\\/]/g, '').trim() === '') return problem(400, 'request.invalid', { violations: ['name.invalid'] });
        if (content.length === 0) return problem(400, 'attachment.empty');
        if (content.length > state.attachmentLimit) return problem(413, 'attachment.too_large');
        if (/\.(exe|bat|js|sh|html)$/i.test(name)) return problem(415, 'attachment.type_blocked');
        const entry = { id: uuid(), ticket: ticket.id, name: name.split(/[\\/]/).pop(), size: content.length, sha256: 'a'.repeat(64), uploadedAt: now(), uploadedBy: account.id, content, removed: false };
        state.attachments.push(entry);
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: ATTACHMENT_FIELD, from: null, to: described(entry) }] });
        return { status: 201, body: view(entry) };
      }
      if (method === 'DELETE') {
        const found = mine.find((entry) => entry.id === id);
        if (!found) return problem(404, 'attachment.not_found');
        if (!found.removed) {
          found.removed = true;
          state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: ATTACHMENT_FIELD, from: described(found), to: null }] });
        }
        return empty();
      }
    }
    if (tail.startsWith('/tags')) {
      if (!field(account, item.id, TAG_FIELD, 'read')) return problem(403, 'auth.forbidden');
      if (method === 'GET') return ok(state.ticketTags.filter((entry) => entry.ticket === ticket.id).map((entry) => state.tags.find((tag) => tag.id === entry.tag)));
      if (!may(account, 'ticket.edit', item.id) || !field(account, item.id, TAG_FIELD, 'write')) return problem(403, 'auth.forbidden');
      const tagId = method === 'POST' ? body.tagId : tail.split('/')[2];
      const assigned = state.ticketTags.findIndex((entry) => entry.ticket === ticket.id && entry.tag === tagId);
      if (method === 'POST' && assigned < 0) {
        state.ticketTags.push({ ticket: ticket.id, tag: tagId });
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: TAG_FIELD, from: null, to: tagId }] });
      }
      if (method === 'DELETE' && assigned >= 0) {
        state.ticketTags.splice(assigned, 1);
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: TAG_FIELD, from: tagId, to: null }] });
      }
      return empty();
    }
    return problem(404, 'resource.not_found');
  }

  // The draft of a configuration and its publication (TASK-044), following contracts/projects.openapi.yaml.
  function configurationRoute(account, item, method, rest, headers, body) {
    if (!projectView(account, item).manageable) return problem(403, 'auth.forbidden');
    const published = state.configurations.get(item.id);
    const draft = state.drafts.get(item.id);
    const draftBody = () => ({ baseVersion: draft.baseVersion, revision: draft.revision, configuration: draft.configuration, updatedAt: now(), updatedBy: account.id });
    const usage = () => {
      const counts = new Map();
      for (const ticket of state.tickets.filter((entry) => entry.projectId === item.id)) {
        const key = `${ticket.values.type}\u0000${ticket.values.status}`;
        counts.set(key, (counts.get(key) ?? 0) + 1);
      }
      return [...counts].map(([key, count]) => ({ type: key.split('\u0000')[0], status: key.split('\u0000')[1], count }));
    };
    if (rest === '/configuration/usage' && method === 'GET') return ok(usage());
    if (rest === '/configuration/draft') {
      if (method === 'GET') return draft ? ok(draftBody(), `"${draft.revision}"`) : problem(404, 'project.draft_not_found');
      if (method === 'PUT') {
        const match = headers['if-match'];
        if (draft ? match !== `"${draft.revision}"` : match !== undefined) return problem(412, 'project.draft_conflict');
        if (body.baseVersion !== published.version) return problem(409, 'project.draft_stale');
        const saved = { baseVersion: body.baseVersion, revision: (draft?.revision ?? 0) + 1, configuration: body.configuration };
        state.drafts.set(item.id, saved);
        return ok({ ...saved, updatedAt: now(), updatedBy: account.id }, `"${saved.revision}"`);
      }
      if (method === 'DELETE') {
        if (!draft) return problem(404, 'project.draft_not_found');
        if (headers['if-match'] !== `"${draft.revision}"`) return problem(412, 'project.draft_conflict');
        state.drafts.delete(item.id);
        return empty();
      }
    }
    if (rest === '/configuration/publish' && method === 'POST') {
      if (headers['if-match'] !== `"${item.version}"`) return problem(412, 'project.version_conflict');
      if (!draft || draft.revision !== body.draftRevision) return problem(412, 'project.draft_conflict');
      const next = draft.configuration;
      const typeMapping = body.typeMapping ?? {};
      const statusMapping = body.statusMapping ?? {};
      const statusKeys = (type) => {
        const own = (next.workflows ?? []).find((workflow) => workflow.type === type);
        if (!own) return next.statuses.map((entry) => entry.key);
        return [...new Set([own.initialStatus, ...own.transitions.flatMap((step) => [step.from, step.to])])];
      };
      const open = [];
      const moves = [];
      for (const use of usage()) {
        let type = use.type;
        if (!next.ticketTypes.some((entry) => entry.key === type)) {
          if (!typeMapping[type]) { open.push(`type.in_use:${type}`); continue; }
          type = typeMapping[type];
        }
        let status = use.status;
        if (!statusKeys(type).includes(status)) {
          // Like the server since review F4 of TASK-044: `<type>:<status>` with the type the tickets have now wins.
          const chosen = statusMapping[`${use.type}:${status}`] ?? statusMapping[status];
          if (!chosen) { open.push(`status.in_use:${status}`); continue; }
          if (!statusKeys(type).includes(chosen)) { open.push(`status.mapping_invalid:${status}`); continue; }
          status = chosen;
        }
        if (type !== use.type || status !== use.status) moves.push({ from: use, type, status });
      }
      if (open.length > 0) return problem(409, 'project.configuration_in_use', { violations: open });
      for (const move of moves) {
        for (const ticket of state.tickets.filter((entry) => entry.projectId === item.id && entry.values.type === move.from.type && entry.values.status === move.from.status)) {
          const changes = [];
          if (ticket.values.type !== move.type) changes.push({ field: 'type', from: ticket.values.type, to: move.type });
          if (ticket.values.status !== move.status) changes.push({ field: 'status', from: ticket.values.status, to: move.status });
          ticket.values.type = move.type;
          ticket.values.status = move.status;
          ticket.version += 1;
          state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes });
        }
      }
      state.configurations.set(item.id, { version: published.version + 1, configuration: next });
      state.drafts.delete(item.id);
      item.version += 1;
      return ok(projectView(account, item), `"${item.version}"`);
    }
    return problem(404, 'resource.not_found');
  }

  // A project administration: permissions.manage for this one project, as a member of it (TASK-043).
  const managesProject = (account, projectId) => may(account, 'permissions.manage') || (isMember(account, projectId)
    && groupsOf(account).some((group) => group.actions.some((rule) => rule.action === 'permissions.manage' && rule.projectId === projectId)));

  function permissionsRoute(account, session, method, p, body, query) {
    if (p === '/permissions/audit' && method === 'GET') {
      if (!companyRights(account).includes('permissions.audit.read')) return problem(403, 'auth.forbidden');
      const before = Number(query.get('before') ?? Infinity);
      const limit = Number(query.get('limit') ?? 50);
      return ok(state.audit.filter((entry) => entry.rightsVersion < before).sort((a, b) => b.rightsVersion - a.rightsVersion).slice(0, limit));
    }
    const projectRights = p.match(/^\/permissions\/projects\/([^/]+)\/groups$/);
    if (projectRights && method === 'GET') {
      const projectId = projectRights[1];
      if (!managesProject(account, projectId)) return problem(403, 'auth.forbidden');
      const company = may(account, 'permissions.manage');
      return ok({
        actions: PROJECT_ACTIONS.filter((action) => company || may(account, action, projectId)),
        fields: [{ resourceType: 'ticket', fields: [...[...FIELDS, TAG_FIELD, ATTACHMENT_FIELD].sort(), ...customFieldsOf(projectId).map((entry) => `custom.${entry.key}`)].filter((name) => company || field(account, projectId, name, 'write')) }],
        groups: state.groups.map((group) => ({ id: group.id, name: group.name,
          actions: group.actions.filter((rule) => rule.projectId === projectId).map((rule) => ({ ...rule })),
          fields: group.fields.filter((rule) => rule.projectId === projectId).map((rule) => ({ ...rule })) })),
      });
    }
    const delegated = method === 'PUT' && /^\/permissions\/groups\/[^/]+\/(actions|fields)$/.test(p) && body?.projectId && managesProject(account, body.projectId);
    if (!may(account, 'permissions.manage') && !delegated) return problem(403, 'auth.forbidden');
    const detail = (group) => ({ id: group.id, name: group.name, members: [...group.members], actions: group.actions.map((rule) => ({ ...rule })), fields: group.fields.map((rule) => ({ ...rule })) });
    if (p === '/permissions/catalog') return ok({ companyActions: COMPANY_ACTIONS, projectActions: PROJECT_ACTIONS, fields: [{ resourceType: 'ticket', fields: [...FIELDS, TAG_FIELD, ATTACHMENT_FIELD].sort() }] });
    if (p === '/permissions/groups' && method === 'GET') return ok(state.groups.map(({ id, name }) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name)));
    if (p === '/permissions/groups' && method === 'POST') {
      if (state.groups.some((entry) => entry.name.toLowerCase() === body.name.toLowerCase())) return problem(409, 'permissions.group_name_taken');
      const group = { id: uuid(), name: body.name, members: [], actions: [], fields: [] };
      state.groups.push(group);
      return ok(detail(group));
    }
    const match = p.match(/^\/permissions\/groups\/([^/]+)(\/(members|actions|fields))?(\/([^/]+))?$/);
    const group = state.groups.find((entry) => entry.id === match?.[1]);
    if (!group) return problem(404, 'permissions.group_not_found');
    const part = match[3];
    if (!part) return ok(detail(group));
    if (part === 'members') {
      const id = match[5];
      group.members = group.members.filter((member) => member !== id);
      if (method === 'PUT') group.members.push(id);
    }
    if (part === 'actions') {
      group.actions = group.actions.filter((rule) => !(rule.action === body.action && rule.projectId === body.projectId));
      if (body.enabled) group.actions.push({ action: body.action, projectId: body.projectId });
    }
    if (part === 'fields') {
      group.fields = group.fields.filter((rule) => !(rule.field === body.field && rule.operation === body.operation && rule.projectId === body.projectId));
      if (body.effect !== 'remove') group.fields.push({ resourceType: 'ticket', field: body.field, projectId: body.projectId, operation: body.operation, effect: body.effect });
    }
    return may(account, 'permissions.manage') ? ok(detail(group)) : empty();
  }

  // The background jobs as the operation sees them (TASK-054): no payload, a failure only with code and reference.
  function jobsRoute(account, method, p, query) {
    if (!may(account, 'jobs.manage')) return problem(403, 'auth.forbidden');
    const view = ({ companyId: _company, ...job }) => job;
    if (p === '/jobs/summary' && method === 'GET') {
      const states = Object.fromEntries(['queued', 'running', 'succeeded', 'failed', 'cancelled'].map((name) => [name, state.jobs.filter((job) => job.state === name).length]));
      const due = state.jobs.filter((job) => job.state === 'queued').map((job) => job.runAt).sort()[0] ?? null;
      const seconds = due === null ? null : Math.max(0, Math.round((Date.parse('2026-10-27T10:00:00Z') - Date.parse(due)) / 1000));
      return ok({ states, oldestDueAt: due, oldestDueSeconds: seconds, activeWorkers: state.activeWorkers,
        backlog: (seconds !== null && seconds > 900) || (state.activeWorkers === 0 && states.queued > 0), checkedAt: '2026-10-27T10:00:00Z' });
    }
    if (p === '/jobs' && method === 'GET') {
      const filter = query.get('state');
      if (filter !== null && !['queued', 'running', 'succeeded', 'failed', 'cancelled'].includes(filter)) return problem(400, 'job.state_invalid');
      const before = Number(query.get('before') ?? Infinity);
      return ok(state.jobs.filter((job) => (filter === null || job.state === filter) && job.position < before)
        .sort((a, b) => b.position - a.position).slice(0, Number(query.get('limit') ?? 50)).map(view));
    }
    const match = p.match(/^\/jobs\/([^/]+)\/(retry|cancel)$/);
    const job = state.jobs.find((entry) => entry.id === match?.[1]);
    if (!match || method !== 'POST') return problem(404, 'request.not_found');
    if (!job) return problem(404, 'job.not_found');
    if (state.jobConflict) {
      state.jobConflict = false;
      job.state = 'running';
      return problem(409, 'job.state_conflict');
    }
    if (match[2] === 'retry') {
      if (!['failed', 'cancelled'].includes(job.state)) return problem(409, 'job.state_conflict');
      Object.assign(job, { state: 'queued', attempts: 0, finishedAt: null, errorCode: null, errorReference: null, runAt: '2026-10-27T10:00:00Z' });
    } else {
      if (job.state !== 'queued') return problem(409, 'job.state_conflict');
      Object.assign(job, { state: 'cancelled', finishedAt: '2026-10-27T10:00:00Z' });
    }
    return ok(view(job));
  }

  const addJob = (kind, jobState, extra = {}) => {
    const position = state.jobs.length + 1;
    const job = { id: uuid(), position, companyId: kind.startsWith('recurring.') ? null : 'company', kind, state: jobState, attempts: jobState === 'queued' ? 0 : 1,
      maxAttempts: kind.startsWith('recurring.') ? 1 : 5, runAt: '2026-10-27T09:58:00Z', createdAt: '2026-10-27T09:58:00Z', startedAt: null,
      finishedAt: ['succeeded', 'failed', 'cancelled'].includes(jobState) ? '2026-10-27T09:59:00Z' : null, errorCode: null, errorReference: null,
      installation: kind.startsWith('recurring.'), ...extra };
    state.jobs.push(job);
    return job;
  };

  function sessionBody(session) {
    const account = session.account;
    if (!account) return { authenticated: false, username: null, displayName: null, level: null, expiresAt: null, passwordChangeBy: null, csrfToken: TOKEN };
    return { authenticated: true, username: account.username, displayName: account.displayName, level: account.passwordChangeRequired ? 'password_change_required' : 'full',
      expiresAt: '2026-10-22T08:00:00Z', passwordChangeBy: account.passwordChangeRequired ? '2026-10-21T08:30:00Z' : null, csrfToken: TOKEN,
      rights: account.passwordChangeRequired ? [] : companyRights(account) };
  }
  function appearanceBody() {
    return { accentColor: state.appearance.accent, defaultAccentColor: '#f97316', logo: state.appearance.logo, version: state.appearance.version, updatedAt: '2026-10-19T06:00:00Z' };
  }

  // Installs the backend for one browser context. `as` starts the context signed in.
  async function install(context, page, { as = null } = {}) {
    const session = { account: as ? state.accounts.find((item) => item.username === as.username) : null };
    await context.addCookies([{ name: '__Host-tm-csrf', value: TOKEN, domain: 'localhost', path: '/', secure: true, sameSite: 'Lax' }]);
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const headers = request.headers();
      const raw = request.postDataBuffer();
      const json = raw && (headers['content-type'] ?? '').includes('json') ? JSON.parse(raw.toString('utf8')) : raw;
      state.requests.push({ method: request.method(), path: url.pathname, ifMatch: headers['if-match'] ?? null, csrf: headers['x-csrf-token'] ?? null, type: headers['content-type'] ?? null, body: raw, user: session.account?.username ?? null });
      const unsafe = !['GET', 'HEAD'].includes(request.method());
      const answer = unsafe && headers['x-csrf-token'] !== TOKEN ? problem(403, 'auth.csrf_invalid')
        : anonymousAppearance && url.pathname === '/api/v1/session' ? ok({ authenticated: false })
          : handle(session, request.method(), url.pathname, url.searchParams, headers, json ?? {});
      // Like the server: logging out removes the CSRF cookie, GET /session issues a new one.
      if (url.pathname === '/api/v1/session' && request.method() === 'DELETE' && answer.status === 204) {
        await context.clearCookies({ name: '__Host-tm-csrf' });
      }
      if (url.pathname === '/api/v1/session' && request.method() === 'GET') {
        const cookies = await context.cookies();
        if (!cookies.some((cookie) => cookie.name === '__Host-tm-csrf')) {
          await context.addCookies([{ name: '__Host-tm-csrf', value: TOKEN, domain: 'localhost', path: '/', secure: true, sameSite: 'Lax' }]);
        }
      }
      if (answer.raw) return route.fulfill({ status: answer.status, contentType: answer.type, headers: answer.headers ?? {}, body: answer.raw });
      return route.fulfill({
        status: answer.status,
        contentType: answer.status < 400 ? 'application/json' : 'application/problem+json',
        headers: { ...(answer.headers ?? {}), ...(answer.etag ? { ETag: answer.etag } : {}) },
        body: answer.status === 204 ? '' : JSON.stringify(answer.body),
      });
    });
    return session;
  }

  return {
    state, install, anna, ben, nora, project,
    writes: () => state.requests.filter((item) => item.method !== 'GET'),
    group: (name) => state.groups.find((group) => group.name === name),
    addJob,
    deactivate: (account) => { account.active = false; },
  };
}
