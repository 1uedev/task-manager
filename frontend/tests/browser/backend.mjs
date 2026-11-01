// A small in-memory stand in for the backend, answered through page.route. It follows the published contracts closely
// enough for the interface: sessions, accounts, projects, tickets with versions and field rights, tags, search, groups
// and the appearance. All data is synthetic. Several browser contexts can share one backend, each with its own session.

export const TOKEN = 'csrf-token-for-browser-tests-0001';
const PNG = Buffer.from('89504e470d0a1a0a0000000d4948445200000001000000010806000000' + '1f15c4890000000d49444154789c6360f8cfc0f01f0005000201' + 'e2d1b0a50000000049454e44ae426082', 'hex');
const FIELDS = ['title', 'description', 'type', 'status', 'assignee'];
const TAG_FIELD = 'tags';
const ATTACHMENT_FIELD = 'attachments';
const HELPDESK_FIELDS = ['customer', 'contact', 'mail'];
const PRIORITY_FIELD = 'priority';
const PRIORITIES = ['low', 'normal', 'high', 'critical'];
// The fixed actor of tickets the mail intake opens (TASK-057).
export const MAIL_INTAKE = '00000000-0000-0000-0000-00004d41494c';
const COMPANY_ACTIONS = ['accounts.manage', 'appearance.manage', 'helpdesk.manage', 'jobs.manage', 'permissions.audit.read', 'permissions.manage', 'projects.manage', 'session_duration.manage', 'sla.manage'];
const PROJECT_ACTIONS = ['helpdesk.reply', 'helpdesk.review', 'project.manage', 'project.read', 'tag.create', 'tags.manage', 'ticket.create', 'ticket.edit', 'ticket.read'];
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
// A tag as stored (TASK-028, TASK-072); `mergedInto` names the identifier of the tag that took its assignments.
export const newTag = (name, projectId, by, extra = {}) => ({ id: uuid(), name, catalog: projectId === null ? 'company' : 'project', projectId, description: null,
  color: null, archived: false, mergedInto: null, version: 1, createdAt: now(), createdBy: by.id, updatedAt: now(), updatedBy: by.id, ...extra });
const TAG_LIMIT = 30;

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
    projectTags: [],
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
    // The helpdesk (TASK-057): customers, contacts, mailboxes with their status, mails and the customer of each ticket.
    // Service deadlines (TASK-068): calendars, rules and the priority per ticket; `cycles` holds prepared cycles per ticket.
    sla: { calendars: [], rules: [], priorities: new Map(), cycles: new Map() },
    helpdesk: { secretKey: true, customers: [], contacts: [], mailboxes: [], messages: [], replies: [], ticketCustomers: new Map() },
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
  state.tags.push(newTag('Sicherheit', project.id, anna));

  // ------------------------------------------------------------------ rights
  const groupsOf = (account) => state.groups.filter((group) => group.members.includes(account.id));
  // tags.manage is a company right for the company catalog and, with the membership, a project action for its catalog.
  const companyRights = (account) => [...new Set(groupsOf(account).flatMap((group) => group.actions.filter((rule) => rule.projectId === null
    && (COMPANY_ACTIONS.includes(rule.action) || rule.action === 'tags.manage')).map((rule) => rule.action)))];
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

  // ------------------------------------------------------------------ tags (TASK-072)
  const manageTags = (account, projectId) => (projectId === null ? companyRights(account).includes('tags.manage') : may(account, 'tags.manage', projectId));
  const createTags = (account, projectId) => manageTags(account, projectId)
    || (projectId === null ? groupsOf(account).some((group) => group.actions.some((rule) => rule.action === 'tag.create' && rule.projectId === null)) : may(account, 'tag.create', projectId));
  const catalogVisible = (account, projectId) => ['project.read', 'project.manage', 'ticket.read', 'tag.create', 'tags.manage'].some((action) => may(account, action, projectId));
  // As the search (TASK-073): the given tags filter after tagMatch; without the read rule for tags no tag filter matches.
  const tagFilterMatches = (account, ticket, query) => {
    const match = query.get('tagMatch') ?? 'all';
    const wanted = query.getAll('tag');
    if (match === 'all' && wanted.length === 0) return true;
    if (!field(account, ticket.projectId, TAG_FIELD, 'read')) return false;
    const held = new Set(state.ticketTags.filter((entry) => entry.ticket === ticket.id).map((entry) => entry.tag));
    if (match === 'untagged') return held.size === 0;
    if (match === 'any') return wanted.some((id) => held.has(id));
    if (match === 'none') return !wanted.some((id) => held.has(id));
    return wanted.every((id) => held.has(id));
  };
  // As POST /tags/bulk (TASK-073): tags checked first, then every ticket for itself; keys only for readable tickets.
  const bulkTags = (account, body) => {
    const add = body?.action === 'add';
    if (!add && body?.action !== 'remove') return problem(400, 'request.invalid', { violations: ['action.invalid'] });
    const chosen = [];
    for (const id of new Set(body.tags ?? [])) {
      const tag = state.tags.find((candidate) => candidate.id === id);
      if (!tag || (tag.projectId !== null && !catalogVisible(account, tag.projectId))) return problem(404, 'tag.not_found');
      if (tag.mergedInto) return problem(409, 'tag.merged');
      if (add && tag.archived) return problem(409, 'tag.archived');
      chosen.push(tag);
    }
    const result = { changed: 0, unchanged: 0, notMatching: 0, refused: 0, items: [] };
    for (const id of new Set(body.tickets ?? [])) {
      const ticket = state.tickets.find((candidate) => candidate.id === id);
      const readable = ticket !== undefined && may(account, 'ticket.read', ticket.projectId);
      let outcome;
      let code = null;
      if (!ticket || !may(account, 'ticket.edit', ticket.projectId) || !field(account, ticket.projectId, TAG_FIELD, 'write')) {
        outcome = 'refused';
        code = ticket && readable ? 'auth.forbidden' : null;
      } else {
        const fitting = chosen.filter((tag) => tag.projectId === null || tag.projectId === ticket.projectId);
        const held = state.ticketTags.filter((entry) => entry.ticket === ticket.id);
        const missing = fitting.filter((tag) => !held.some((entry) => entry.tag === tag.id));
        const present = fitting.filter((tag) => held.some((entry) => entry.tag === tag.id));
        if (fitting.length === 0) outcome = 'not_matching';
        else if (add && missing.length > 0 && held.length + missing.length > TAG_LIMIT) {
          outcome = 'refused';
          code = 'tag.limit_reached';
        } else if (add ? missing.length === 0 : present.length === 0) outcome = 'unchanged';
        else {
          outcome = 'changed';
          for (const tag of add ? missing : present) {
            if (add) state.ticketTags.push({ ticket: ticket.id, tag: tag.id });
            else state.ticketTags.splice(state.ticketTags.findIndex((entry) => entry.ticket === ticket.id && entry.tag === tag.id), 1);
            state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: TAG_FIELD, from: add ? null : tag.id, to: add ? tag.id : null }] });
          }
        }
      }
      result[{ changed: 'changed', unchanged: 'unchanged', not_matching: 'notMatching', refused: 'refused' }[outcome]] += 1;
      result.items.push({ ticketId: id, key: readable ? ticket.key : null, outcome, code: readable ? code : null });
    }
    return ok(result);
  };
  const tagKey = (name) => name.trim().replace(/\s+/g, ' ').toUpperCase().toLowerCase();
  const tagView = (account, tag, usage = false) => {
    const target = tag.mergedInto === null ? null : state.tags.find((entry) => entry.id === tag.mergedInto);
    const ticketCount = state.ticketTags.filter((entry) => entry.tag === tag.id).filter((entry) => {
      const ticket = state.tickets.find((item) => item.id === entry.ticket);
      return ticket !== undefined && may(account, 'ticket.read', ticket.projectId) && field(account, ticket.projectId, TAG_FIELD, 'read');
    }).length;
    const projectCount = state.projectTags.filter((entry) => entry.tag === tag.id)
      .filter((entry) => projectVisible(account, state.projects.find((item) => item.id === entry.project))).length;
    return { ...tag, mergedInto: target ? { id: target.id, name: target.name } : null, manageable: manageTags(account, tag.projectId),
      usage: usage ? { tickets: ticketCount, projects: projectCount } : null };
  };
  const catalogList = (account, projectId, query) => {
    const q = tagKey(query.get('q') ?? '');
    const after = query.get('after');
    const limit = Number(query.get('limit') ?? 100);
    return state.tags.filter((tag) => tag.projectId === projectId && tag.mergedInto === null && (query.get('archived') === 'true' || !tag.archived)
      && (q === '' || tagKey(tag.name).includes(q)) && (after === null || tagKey(tag.name) > tagKey(after)))
      .sort((a, b) => tagKey(a.name).localeCompare(tagKey(b.name))).slice(0, limit).map((tag) => tagView(account, tag, query.get('usage') === 'true'));
  };
  const suggestions = (account, projectId, query) => {
    const q = tagKey(query.get('q') ?? '');
    const rank = (tag) => (q === '' || tagKey(tag.name).startsWith(q) ? 0 : tagKey(tag.name).includes(` ${q}`) ? 1 : 2);
    const items = state.tags.filter((tag) => (tag.projectId === null || tag.projectId === projectId) && tag.mergedInto === null && !tag.archived
      && (q === '' || tagKey(tag.name).includes(q)))
      .sort((a, b) => rank(a) - rank(b) || tagKey(a.name).localeCompare(tagKey(b.name))).slice(0, 20).map((tag) => tagView(account, tag));
    return { items, createCompany: createTags(account, null), createProject: projectId !== null && createTags(account, projectId) };
  };
  // Changes and merges of one catalog, as contracts/tags.openapi.yaml describes them.
  const catalogTagRoute = (account, projectId, method, rest, query, headers, body) => {
    const [, id, action] = rest.split('/');
    const tag = state.tags.find((entry) => entry.id === id && entry.projectId === projectId);
    if (!tag) return problem(404, 'tag.not_found');
    if (method === 'GET' && action === undefined) return ok(tagView(account, tag), `"${tag.version}"`);
    if (!manageTags(account, projectId)) return problem(403, 'auth.forbidden');
    if (action === 'merge') {
      const into = method === 'GET' ? query.get('into') : body?.into;
      const target = state.tags.find((entry) => entry.id === into && entry.projectId === projectId);
      if (into === id) return problem(400, 'request.invalid', { violations: ['into.invalid'] });
      if (!target) return problem(404, 'tag.not_found');
      if (tag.mergedInto !== null || target.mergedInto !== null) return problem(409, 'tag.merged');
      if (target.archived) return problem(409, 'tag.archived');
      if (method === 'GET') return ok({ source: tagView(account, tag, true), target: tagView(account, target, true), usage: tagView(account, tag, true).usage });
      if (!headers['if-match']) return problem(428, 'tag.precondition_required');
      if (headers['if-match'] !== `"${tag.version}"`) return problem(412, 'tag.version_conflict');
      for (const list of [state.ticketTags, state.projectTags]) {
        const owner = list === state.ticketTags ? 'ticket' : 'project';
        for (const entry of list.filter((item) => item.tag === tag.id)) {
          if (list.some((item) => item[owner] === entry[owner] && item.tag === target.id)) list.splice(list.indexOf(entry), 1);
          else entry.tag = target.id;
        }
      }
      Object.assign(tag, { mergedInto: target.id, version: tag.version + 1 });
      for (const other of state.tags.filter((entry) => entry.mergedInto === tag.id)) other.mergedInto = target.id;
      return ok(tagView(account, target), `"${target.version}"`);
    }
    if (method !== 'PATCH') return problem(405, 'request.method');
    if (!headers['if-match']) return problem(428, 'tag.precondition_required');
    if (headers['if-match'] !== `"${tag.version}"`) return problem(412, 'tag.version_conflict');
    if (tag.mergedInto !== null) return problem(409, 'tag.merged');
    if (body.color !== undefined && body.color !== null && !['red', 'orange', 'amber', 'yellow', 'lime', 'green', 'teal', 'cyan', 'blue', 'indigo', 'violet', 'pink'].includes(body.color)) {
      return problem(400, 'request.invalid', { violations: ['color.invalid'] });
    }
    if (typeof body.description === 'string' && body.description.trim().length > 500) return problem(400, 'request.invalid', { violations: ['description.invalid'] });
    if (body.name !== undefined && state.tags.some((entry) => entry !== tag && entry.projectId === projectId && entry.mergedInto === null && tagKey(entry.name) === tagKey(body.name))) {
      return problem(409, 'tag.name_taken');
    }
    const next = { ...tag, ...(body.name !== undefined ? { name: body.name.trim() } : {}), ...(body.archived !== undefined ? { archived: body.archived } : {}),
      ...(body.description !== undefined ? { description: body.description === null || body.description.trim() === '' ? null : body.description.trim() } : {}),
      ...(body.color !== undefined ? { color: body.color } : {}) };
    const changed = ['name', 'archived', 'description', 'color'].some((key) => next[key] !== tag[key]);
    if (changed) Object.assign(tag, next, { version: tag.version + 1, updatedAt: now(), updatedBy: account.id });
    return ok(tagView(account, tag), `"${tag.version}"`);
  };
  // Assigning a tag by identifier or by name, creating a missing name when allowed; at most 30 tags per object.
  const resolveTag = (account, projectId, body, companyOnly) => {
    if (body.tagId !== undefined) {
      const tag = state.tags.find((entry) => entry.id === body.tagId && (entry.projectId === null || (!companyOnly && entry.projectId === projectId)));
      if (!tag) return { problem: problem(404, 'tag.not_found') };
      if (tag.mergedInto !== null) return { problem: problem(409, 'tag.merged') };
      if (tag.archived) return { problem: problem(409, 'tag.archived') };
      return { tag };
    }
    const catalog = companyOnly || body.catalog === 'company' ? null : projectId;
    const existing = state.tags.find((entry) => entry.projectId === catalog && entry.mergedInto === null && tagKey(entry.name) === tagKey(body.name));
    if (existing) return existing.archived ? { problem: problem(409, 'tag.archived') } : { tag: existing };
    if (!createTags(account, catalog)) return { problem: problem(403, 'auth.forbidden') };
    return { tag: newTag(body.name.trim(), catalog, account), created: true };
  };

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
    if (p === '/tags/bulk' && method === 'POST') return bulkTags(account, body);
    if (p === '/tags' && method === 'GET') return ok(catalogList(account, null, query));
    if (p === '/tags' && method === 'POST') {
      if (!createTags(account, null)) return problem(403, 'auth.forbidden');
      if (state.tags.some((tag) => tag.projectId === null && tag.mergedInto === null && tagKey(tag.name) === tagKey(body.name))) return problem(409, 'tag.name_taken');
      const tag = newTag(body.name.trim(), null, account);
      state.tags.push(tag);
      return ok(tagView(account, tag), '"1"');
    }
    if (p === '/tags/suggestions' && method === 'GET') return ok(suggestions(account, null, query));
    if (p.startsWith('/tags/')) return catalogTagRoute(account, null, method, p.slice('/tags'.length), query, headers, body);
    if (p === '/search/tickets') {
      const words = (query.get('q') ?? '').toLowerCase().split(/\s+/).filter(Boolean);
      const items = state.tickets.filter((ticket) => may(account, 'ticket.read', ticket.projectId))
        .filter((ticket) => words.every((word) => readable(account, ticket.projectId).some((name) => String(ticket.values[name] ?? '').toLowerCase().split(/\W+/).some((part) => part.startsWith(word))) || ticket.key.toLowerCase().startsWith(word)))
        .filter((ticket) => tagFilterMatches(account, ticket, query))
        .map((ticket) => ({ id: ticket.id, projectId: ticket.projectId, key: ticket.key, number: ticket.number, fields: ticketView(account, ticket).fields, customFields: ticketView(account, ticket).customFields,
          tags: field(account, ticket.projectId, TAG_FIELD, 'read') ? state.ticketTags.filter((entry) => entry.ticket === ticket.id).map((entry) => {
            const tag = state.tags.find((candidate) => candidate.id === entry.tag);
            return { id: tag.id, name: tag.name };
          }) : null }));
      const tagMatch = query.get('tagMatch') ?? 'all';
      const tags = query.getAll('tag');
      if (!['all', 'any', 'none', 'untagged'].includes(tagMatch) || (tagMatch === 'untagged') !== (tags.length === 0) && query.has('tagMatch')) {
        return problem(400, 'request.invalid', { violations: ['tagMatch.invalid'] });
      }
      if (tags.length > 10) return problem(400, 'request.invalid', { violations: ['tag.invalid'] });
      const offset = Number(query.get('offset') ?? 0);
      const limit = Number(query.get('limit') ?? 20);
      return ok({ total: items.length, items: items.slice(offset, offset + limit) });
    }
    if (p === '/search/projects') {
      const q = (query.get('q') ?? '').toLowerCase();
      const items = state.projects.filter((item) => projectVisible(account, item) && (item.name.toLowerCase().includes(q) || item.key.toLowerCase().startsWith(q)))
        .map((item) => ({ id: item.id, key: item.key, name: item.name, description: item.description, tags: [] }));
      return ok({ total: items.length, items });
    }
    if (p.startsWith('/permissions')) return permissionsRoute(account, session, method, p, body, query);
    if (p.startsWith('/jobs')) return jobsRoute(account, method, p, query);
    if (p.startsWith('/helpdesk/inbox')) return inboxRoute(account, method, p, query, headers, body);
    if (p === '/helpdesk/replies' && method === 'GET') {
      // As the backend (TASK-058): replies not yet accepted, of the projects in which the person may reply.
      return ok(hd.replies.filter((reply) => reply.state !== 'sent' && mayReply(account, reply.projectId))
        .map((reply) => ({ id: reply.id, projectId: reply.projectId, number: state.tickets.find((entry) => entry.id === reply.ticketId).number, toAddress: reply.toAddress,
          subject: reply.subject, state: reply.state, errorCode: reply.errorCode, createdAt: reply.createdAt, attemptedAt: reply.attemptedAt })));
    }
    if (p.startsWith('/helpdesk')) return helpdeskRoute(account, method, p, headers, body);
    if (p === '/sla/escalations' && method === 'GET') {
      // As the backend (TASK-070): every full login; stages of the groups of the person on tickets they may read with the
      // read rules of customer and priority, newest first, checked when reading.
      const mine = new Set(groupsOf(account).map((group) => group.id));
      const items = [];
      for (const ticket of state.tickets) {
        if (!may(account, 'ticket.read', ticket.projectId) || !field(account, ticket.projectId, 'customer', 'read') || !field(account, ticket.projectId, PRIORITY_FIELD, 'read')) continue;
        const key = state.projects.find((entry) => entry.id === ticket.projectId)?.key ?? '';
        for (const cycle of sla.cycles.get(ticket.id) ?? []) {
          for (const stage of cycle.escalations ?? []) {
            if (!mine.has(stage.groupId)) continue;
            items.push({ projectId: ticket.projectId, projectKey: key, number: ticket.number, cycleNumber: cycle.number, cycleOutcome: cycle.outcome ?? null, ...stage });
          }
        }
      }
      // At most 200 visible entries, counted after the rights as on the server (TASK-071, F2).
      return ok(items.sort((a, b) => b.reachedAt.localeCompare(a.reachedAt)).slice(0, 200));
    }
    if (p.startsWith('/sla')) return slaRoute(account, method, p, headers, body);
    return problem(404, 'resource.not_found');
  }

  function projectRoute(account, item, method, rest, query, headers, body) {
    if (rest === '' && method === 'GET') return ok(projectView(account, item), `"${item.version}"`);
    if (rest === '/members' && method === 'GET') return ok(state.members.get(item.id), `"${item.version}"`);
    if (rest.startsWith('/configuration/')) return configurationRoute(account, item, method, rest, headers, body);
    if (rest.startsWith('/catalog/') && !catalogVisible(account, item.id)) return problem(404, 'project.not_found');
    if (rest === '/catalog/tags' && method === 'GET') return ok(catalogList(account, item.id, query));
    if (rest === '/catalog/tags' && method === 'POST') {
      if (!createTags(account, item.id)) return problem(403, 'auth.forbidden');
      if (state.tags.some((tag) => tag.projectId === item.id && tag.mergedInto === null && tagKey(tag.name) === tagKey(body.name))) return problem(409, 'tag.name_taken');
      const tag = newTag(body.name.trim(), item.id, account);
      state.tags.push(tag);
      return ok(tagView(account, tag), '"1"');
    }
    if (rest === '/catalog/suggestions' && method === 'GET') return ok(suggestions(account, item.id, query));
    if (rest === '/catalog/lookup' && method === 'GET') {
      const ids = query.getAll('id');
      if (ids.length === 0 || ids.length > 100) return problem(400, 'request.invalid', { violations: ['ids.invalid'] });
      return ok(state.tags.filter((tag) => ids.includes(tag.id) && (tag.projectId === null || tag.projectId === item.id)).map((tag) => tagView(account, tag)));
    }
    if (rest.startsWith('/catalog/tags/')) return catalogTagRoute(account, item.id, method, rest.slice('/catalog/tags'.length), query, headers, body);
    if (rest.startsWith('/tags')) {
      const view = projectView(account, item);
      if (method === 'GET' && rest === '/tags') {
        return ok(state.projectTags.filter((entry) => entry.project === item.id).map((entry) => tagView(account, state.tags.find((tag) => tag.id === entry.tag))));
      }
      if (!view.manageable) return problem(403, 'auth.forbidden');
      if (method === 'POST' && rest === '/tags') {
        const resolved = resolveTag(account, item.id, body, true);
        if (resolved.problem) return resolved.problem;
        const present = state.projectTags.some((entry) => entry.project === item.id && entry.tag === resolved.tag.id);
        if (!present && state.projectTags.filter((entry) => entry.project === item.id).length >= TAG_LIMIT) return problem(409, 'tag.limit_reached');
        if (resolved.created) state.tags.push(resolved.tag);
        if (!present) state.projectTags.push({ project: item.id, tag: resolved.tag.id });
        return empty();
      }
      if (method === 'DELETE') {
        const tagId = rest.split('/')[2];
        const index = state.projectTags.findIndex((entry) => entry.project === item.id && entry.tag === tagId);
        if (index >= 0) state.projectTags.splice(index, 1);
        return empty();
      }
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
    if (tail.startsWith('/helpdesk')) return ticketHelpdeskRoute(account, item, ticket, method, tail, headers, body);
    if (tail.startsWith('/sla')) return ticketSlaRoute(account, item, ticket, method, tail, body);
    if (tail === '/history') {
      const shown = [...readable(account, item.id), ...[...HELPDESK_FIELDS, PRIORITY_FIELD].filter((name) => field(account, item.id, name, 'read')), ...(customReadable(account, item.id) ? customFieldsOf(item.id).map((entry) => `custom.${entry.key}`) : []), ...(field(account, item.id, TAG_FIELD, 'read') ? [TAG_FIELD] : []), ...(field(account, item.id, ATTACHMENT_FIELD, 'read') ? [ATTACHMENT_FIELD] : [])];
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
      if (method === 'GET') return ok(state.ticketTags.filter((entry) => entry.ticket === ticket.id).map((entry) => tagView(account, state.tags.find((tag) => tag.id === entry.tag))));
      if (!may(account, 'ticket.edit', item.id) || !field(account, item.id, TAG_FIELD, 'write')) return problem(403, 'auth.forbidden');
      let tagId = tail.split('/')[2];
      if (method === 'POST') {
        const resolved = resolveTag(account, item.id, body, false);
        if (resolved.problem) return resolved.problem;
        const present = state.ticketTags.some((entry) => entry.ticket === ticket.id && entry.tag === resolved.tag.id);
        if (!present && state.ticketTags.filter((entry) => entry.ticket === ticket.id).length >= TAG_LIMIT) return problem(409, 'tag.limit_reached');
        if (resolved.created) state.tags.push(resolved.tag);
        tagId = resolved.tag.id;
      }
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


  // ------------------------------------------------------------------ helpdesk (TASK-057)
  const hd = state.helpdesk;
  const versioned = (headers, entry, prefix) => {
    if (!headers['if-match']) return problem(428, 'helpdesk.precondition_required');
    if (headers['if-match'] !== `"${entry.version}"`) return problem(412, `${prefix}.version_conflict`);
    return null;
  };
  const mailboxView = ({ password: _password, ...mailbox }) => ({ ...mailbox, passwordSet: Boolean(_password) });

  function helpdeskRoute(account, method, p, headers, body) {
    if (!may(account, 'helpdesk.manage')) return problem(403, 'auth.forbidden');
    if (p === '/helpdesk/status') return ok({ secretKey: hd.secretKey, pollIntervalSeconds: 60, maxBytes: 25000000 });
    if (p === '/helpdesk/customers' && method === 'GET') return ok([...hd.customers].sort((a, b) => a.name.localeCompare(b.name)));
    if (p === '/helpdesk/customers' && method === 'POST') {
      if (!body.name?.trim()) return problem(400, 'request.invalid', { violations: ['name.invalid'] });
      if (hd.customers.some((entry) => entry.name.toLowerCase() === body.name.trim().toLowerCase())) return problem(409, 'customer.name_taken');
      const customer = { id: uuid(), name: body.name.trim(), projects: body.projects ?? [], version: 1, createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      hd.customers.push(customer);
      return { status: 201, body: customer, etag: '"1"' };
    }
    let match = p.match(/^\/helpdesk\/customers\/([^/]+)$/);
    if (match && method === 'PUT') {
      const customer = hd.customers.find((entry) => entry.id === match[1]);
      if (!customer) return problem(404, 'customer.not_found');
      const refused = versioned(headers, customer, 'customer');
      if (refused) return refused;
      Object.assign(customer, { name: body.name.trim(), projects: body.projects ?? [], version: customer.version + 1, updatedAt: now(), updatedBy: account.id });
      return ok(customer, `"${customer.version}"`);
    }
    if (p === '/helpdesk/contacts' && method === 'GET') return ok([...hd.contacts].sort((a, b) => a.name.localeCompare(b.name)));
    if (p === '/helpdesk/contacts' && method === 'POST') {
      const addresses = (body.addresses ?? []).map((value) => value.trim().toLowerCase());
      if (addresses.length === 0 || addresses.some((value) => !/^[^@\s]+@[^@\s]+$/.test(value))) return problem(400, 'request.invalid', { violations: ['addresses.invalid'] });
      if (hd.contacts.some((entry) => entry.addresses.some((value) => addresses.includes(value)))) return problem(409, 'contact.address_taken');
      const contact = { id: uuid(), customerId: body.customerId, name: body.name.trim(), addresses, version: 1, createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      hd.contacts.push(contact);
      return { status: 201, body: contact, etag: '"1"' };
    }
    match = p.match(/^\/helpdesk\/contacts\/([^/]+)$/);
    if (match && method === 'PUT') {
      const contact = hd.contacts.find((entry) => entry.id === match[1]);
      if (!contact) return problem(404, 'contact.not_found');
      const refused = versioned(headers, contact, 'contact');
      if (refused) return refused;
      Object.assign(contact, { customerId: body.customerId, name: body.name.trim(), addresses: body.addresses.map((value) => value.trim().toLowerCase()), version: contact.version + 1 });
      return ok(contact, `"${contact.version}"`);
    }
    if (p === '/helpdesk/mailboxes' && method === 'GET') return ok(hd.mailboxes.map(mailboxView));
    const settings = (mailbox) => {
      const violations = [];
      if (!body.name?.trim()) violations.push('name.invalid');
      if (!body.imapHost?.trim()) violations.push('imapHost.invalid');
      if (!(body.imapPort >= 1 && body.imapPort <= 65535)) violations.push('imapPort.invalid');
      if (!body.username?.trim()) violations.push('username.invalid');
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      const password = body.password === undefined || body.password === null ? mailbox?.password ?? null : body.password === '' ? null : body.password;
      return { name: body.name.trim(), enabled: body.enabled ?? true, projectId: body.projectId, ticketType: body.ticketType, imapHost: body.imapHost.trim(), imapPort: body.imapPort,
        imapSecurity: body.imapSecurity, username: body.username.trim(), password, folder: body.folder ?? 'INBOX', smtpHost: body.smtpHost ?? null, smtpPort: body.smtpPort ?? null,
        smtpSecurity: body.smtpSecurity ?? null, address: body.address ?? null, authservId: body.authservId ?? null, maxBytes: body.maxBytes ?? 25000000 };
    };
    if (p === '/helpdesk/mailboxes' && method === 'POST') {
      if (body.password && !hd.secretKey) return problem(409, 'helpdesk.secret_key_missing');
      const values = settings(null);
      if (values.status) return values;
      const mailbox = { id: uuid(), ...values, status: { polledAt: null, succeededAt: null, errorCode: null, errorAt: null, notice: null, noticeAt: null }, version: 1, updatedAt: now(), updatedBy: account.id };
      hd.mailboxes.push(mailbox);
      return { status: 201, body: mailboxView(mailbox), etag: '"1"' };
    }
    match = p.match(/^\/helpdesk\/mailboxes\/([^/]+)(\/test)?$/);
    const mailbox = hd.mailboxes.find((entry) => entry.id === match?.[1]);
    if (match && !mailbox) return problem(404, 'mailbox.not_found');
    if (match && match[2] && method === 'POST') {
      return ok(mailbox.password === 'falsch' ? { ok: false, code: 'helpdesk.authentication_failed' } : { ok: true, code: null });
    }
    if (match && method === 'PUT') {
      const refused = versioned(headers, mailbox, 'mailbox');
      if (refused) return refused;
      const values = settings(mailbox);
      if (values.status) return values;
      Object.assign(mailbox, values, { version: mailbox.version + 1, updatedAt: now(), updatedBy: account.id });
      return ok(mailboxView(mailbox), `"${mailbox.version}"`);
    }
    return problem(404, 'resource.not_found');
  }

  const messageView = (message, withBody) => {
    const { raw: _raw, companyId: _company, ...rest } = message;
    const ticket = message.ticketId ? state.tickets.find((entry) => entry.id === message.ticketId) : null;
    const related = message.relatedTicketId ? state.tickets.find((entry) => entry.id === message.relatedTicketId) : null;
    const { relatedTicketId: _related, ...shown } = rest;
    return { ...shown, body: withBody ? message.body : null, ticket: ticket ? { projectId: ticket.projectId, number: ticket.number } : null,
      relatedTicket: related ? { projectId: related.projectId, number: related.number } : null };
  };

  function inboxRoute(account, method, p, query, headers, body) {
    const mine = hd.messages.filter((message) => may(account, 'helpdesk.review', message.projectId));
    if (p === '/helpdesk/inbox' && method === 'GET') {
      const wanted = query.get('state') ?? 'review';
      return ok(mine.filter((message) => message.state === wanted).sort((a, b) => b.receivedAt.localeCompare(a.receivedAt)).map((message) => messageView(message, false)));
    }
    if (p === '/helpdesk/inbox/summary') {
      return ok({ review: mine.filter((message) => message.state === 'review').length, automatic: mine.filter((message) => message.state === 'automatic').length });
    }
    const match = p.match(/^\/helpdesk\/inbox\/([^/]+)(\/[a-z]+)?$/);
    const message = mine.find((entry) => entry.id === match?.[1]);
    if (!message) return problem(404, 'helpdesk.message_not_found');
    const tail = match[2] ?? '';
    if (tail === '' && method === 'GET') return ok(messageView(message, true), `"${message.version}"`);
    if (tail === '/candidates') {
      const domain = message.fromAddress?.split('@')[1];
      return ok(hd.contacts.filter((contact) => contact.addresses.some((value) => value === message.fromAddress || value.endsWith(`@${domain}`)))
        .map((contact) => {
          const customer = hd.customers.find((entry) => entry.id === contact.customerId);
          return { contactId: contact.id, contactName: contact.name, addresses: contact.addresses, customerId: customer.id, customerName: customer.name,
            sender: contact.addresses.includes(message.fromAddress) };
        }));
    }
    if (method !== 'POST') return problem(404, 'resource.not_found');
    if (!headers['if-match']) return problem(428, 'helpdesk.precondition_required');
    if (!['review', 'automatic'].includes(message.state)) return problem(409, 'helpdesk.message_processed');
    if (headers['if-match'] !== `"${message.version}"`) return problem(412, 'helpdesk.version_conflict');
    if (tail === '/discard') {
      if (!body.reason?.trim()) return problem(400, 'request.invalid', { violations: ['reason.invalid'] });
      Object.assign(message, { state: 'discarded', discardReason: body.reason.trim(), processedAt: now(), processedBy: account.id, version: message.version + 1 });
      return ok(messageView(message, true), `"${message.version}"`);
    }
    if (tail === '/attach') {
      const ticket = message.relatedTicketId ? state.tickets.find((entry) => entry.id === message.relatedTicketId) : null;
      if (!ticket || message.state !== 'review') return problem(409, 'helpdesk.no_related_ticket');
      state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id,
        changes: [{ field: 'mail', from: null, to: JSON.stringify({ id: message.id, from: message.fromAddress, subject: message.subject }) }] });
      Object.assign(message, { state: 'ticket', ticketId: ticket.id, processedAt: now(), processedBy: account.id, version: message.version + 1 });
      return ok({ ticketId: ticket.id, projectId: ticket.projectId, number: ticket.number, attachments: message.attachments });
    }
    if (tail === '/ticket') {
      const contact = body.contactId ? hd.contacts.find((entry) => entry.id === body.contactId) : null;
      const ticket = addTicket(message.subject ?? '(kein Betreff)', message.body, account, message.projectId);
      if (contact) hd.ticketCustomers.set(ticket.id, { customerId: contact.customerId, contactId: contact.id });
      state.history.push({ ticket: ticket.id, version: 1, occurredAt: now(), actor: account.id,
        changes: [{ field: 'mail', from: null, to: JSON.stringify({ id: message.id, from: message.fromAddress, subject: message.subject }) }] });
      Object.assign(message, { state: 'ticket', ticketId: ticket.id, contactId: contact?.id ?? null, customerId: contact?.customerId ?? null, processedAt: now(), processedBy: account.id, version: message.version + 1 });
      return ok({ ticketId: ticket.id, projectId: ticket.projectId, number: ticket.number, attachments: message.attachments });
    }
    return problem(404, 'resource.not_found');
  }

  // Calendars and SLA rules as the backend (TASK-068): the company right sla.manage, versions in If-Match, the most specific
  // rule unique per project, customer and type.
  const sla = state.sla;
  function slaRoute(account, method, p, headers, body) {
    if (!may(account, 'sla.manage')) return problem(403, 'auth.forbidden');
    const version = () => Number((headers['if-match'] ?? '').replaceAll('"', ''));
    const invalidCalendar = (input) => {
      const violations = [];
      if (!input.name?.trim()) violations.push('name.invalid');
      if (!/^[A-Za-z]+\/[A-Za-z_]+$/.test(input.timeZone ?? '') && input.timeZone !== 'UTC') violations.push('timeZone.invalid');
      const spans = (hours) => hours.every((span) => /^\d\d:\d\d$/.test(span.from) && /^(\d\d:\d\d)$/.test(span.to) && span.from < span.to);
      if (!(input.week ?? []).every((day) => spans(day.hours))) violations.push('week.hours_invalid');
      if (!(input.week ?? []).some((day) => day.hours.length > 0)) violations.push('week.closed');
      if (!(input.exceptions ?? []).every((item) => /^\d{4}-\d\d-\d\d$/.test(item.date))) violations.push('exceptions.date_invalid');
      if (!(input.exceptions ?? []).every((item) => spans(item.hours))) violations.push('exceptions.hours_invalid');
      return violations;
    };
    if (p === '/sla/calendars' && method === 'GET') return ok([...sla.calendars].sort((a, b) => a.name.localeCompare(b.name)));
    if (p === '/sla/calendars' && method === 'POST') {
      const violations = invalidCalendar(body);
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      if (sla.calendars.some((item) => item.name.toLowerCase() === body.name.trim().toLowerCase())) return problem(409, 'sla.calendar_name_taken');
      const calendar = { id: uuid(), name: body.name.trim(), timeZone: body.timeZone, week: body.week, exceptions: body.exceptions ?? [], version: 1,
        createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      sla.calendars.push(calendar);
      return { status: 201, body: calendar, etag: '"1"' };
    }
    let match = p.match(/^\/sla\/calendars\/([^/]+)$/);
    if (match) {
      const calendar = sla.calendars.find((item) => item.id === match[1]);
      if (!calendar) return problem(404, 'sla.calendar_not_found');
      if (method === 'GET') return ok(calendar, `"${calendar.version}"`);
      if (version() !== calendar.version) return problem(412, 'sla.version_conflict');
      const violations = invalidCalendar(body);
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      Object.assign(calendar, { name: body.name.trim(), timeZone: body.timeZone, week: body.week, exceptions: body.exceptions ?? [], version: calendar.version + 1, updatedAt: now() });
      return ok(calendar, `"${calendar.version}"`);
    }
    if (p === '/sla/options' && method === 'GET') {
      return ok({ projects: state.projects.map((item) => ({ id: item.id, key: item.key, name: item.name, ticketTypes: state.configurations.get(item.id).configuration.ticketTypes })),
        customers: hd.customers.map((item) => ({ id: item.id, name: item.name, projects: item.projects })),
        groups: state.groups.map(({ id, name }) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name)) });
    }
    const checkedRule = (input, projectId, id) => {
      const violations = [];
      if (!input.name?.trim()) violations.push('name.invalid');
      if (!state.projects.some((item) => item.id === projectId)) violations.push('projectId.unknown');
      if (input.customerId && !hd.customers.find((item) => item.id === input.customerId)?.projects.includes(projectId)) violations.push('customerId.not_released');
      if (input.ticketType && !input.customerId) violations.push('ticketType.requires_customer');
      if (!sla.calendars.some((item) => item.id === input.calendarId)) violations.push('calendarId.unknown');
      const budgets = input.budgets ?? {};
      if (!PRIORITIES.every((priority) => budgets[priority])) violations.push('budgets.required');
      else if (!PRIORITIES.every((priority) => [budgets[priority].firstResponse, budgets[priority].resolution].every((value) => Number.isInteger(value) && value >= 1 && value <= 525600))) {
        violations.push('budgets.invalid');
      }
      // The stages as Rule.Stage.violations: at most 10 per clock, then per stage the first reason, each code once.
      const stages = input.escalations ?? [];
      if (['first_response', 'resolution'].some((clock) => stages.filter((stage) => stage?.clock === clock).length > 10)) violations.push('escalations.too_many');
      const seen = new Set();
      for (const stage of stages) {
        const code = !['first_response', 'resolution'].includes(stage?.clock) || !Number.isInteger(stage.percent) || stage.percent < 1 || stage.percent > 1000 ? 'escalations.invalid'
          : !state.groups.some((group) => group.id === stage.groupId) ? 'escalations.group_unknown'
          : seen.has(`${stage.clock}/${stage.percent}/${stage.groupId}`) ? 'escalations.duplicate' : null;
        if (code === null) seen.add(`${stage.clock}/${stage.percent}/${stage.groupId}`);
        else if (!violations.includes(code)) violations.push(code);
      }
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      if (sla.rules.some((rule) => rule.id !== id && rule.projectId === projectId && (rule.customerId ?? null) === (input.customerId ?? null)
        && (rule.ticketType ?? null) === (input.ticketType ?? null))) return problem(409, 'sla.rule_conflict');
      return null;
    };
    if (p === '/sla/rules' && method === 'GET') return ok([...sla.rules].sort((a, b) => a.name.localeCompare(b.name)));
    if (p === '/sla/rules' && method === 'POST') {
      const refused = checkedRule(body, body.projectId, null);
      if (refused) return refused;
      const rule = { id: uuid(), name: body.name.trim(), projectId: body.projectId, customerId: body.customerId ?? null, ticketType: body.ticketType ?? null,
        calendarId: body.calendarId, budgets: body.budgets, escalations: body.escalations ?? [], version: 1, createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      sla.rules.push(rule);
      return { status: 201, body: rule, etag: '"1"' };
    }
    match = p.match(/^\/sla\/rules\/([^/]+)$/);
    const rule = sla.rules.find((item) => item.id === match?.[1]);
    if (!match) return problem(404, 'resource.not_found');
    if (!rule) return problem(404, 'sla.rule_not_found');
    if (version() !== rule.version) return problem(412, 'sla.version_conflict');
    if (method === 'DELETE') {
      sla.rules.splice(sla.rules.indexOf(rule), 1);
      return { status: 204, body: null };
    }
    const refused = checkedRule(body, rule.projectId, rule.id);
    if (refused) return refused;
    Object.assign(rule, { name: body.name.trim(), customerId: body.customerId ?? null, ticketType: body.ticketType ?? null, calendarId: body.calendarId,
      budgets: body.budgets, escalations: body.escalations ?? [], version: rule.version + 1, updatedAt: now() });
    return ok(rule, `"${rule.version}"`);
  }

  // The priority and the deadlines of a ticket: the priority with its read rule, the cycles also with the read rule of customer.
  function ticketSlaRoute(account, item, ticket, method, tail, body) {
    const readableHere = [PRIORITY_FIELD, 'customer'].filter((name) => field(account, item.id, name, 'read'));
    const writableHere = may(account, 'ticket.edit', item.id) && field(account, item.id, PRIORITY_FIELD, 'read') && field(account, item.id, PRIORITY_FIELD, 'write') ? [PRIORITY_FIELD] : [];
    const view = () => {
      const priority = sla.priorities.get(ticket.id) ?? 'normal';
      const both = readableHere.length === 2;
      // Reached stages only of the groups of the person (TASK-070).
      const mine = new Set(groupsOf(account).map((group) => group.id));
      const cycles = (sla.cycles.get(ticket.id) ?? []).map((cycle) => ({ ...cycle, escalations: (cycle.escalations ?? []).filter((stage) => mine.has(stage.groupId)) }));
      const stateOf = !hd.ticketCustomers.get(ticket.id) ? 'no_customer' : cycles.length === 0 ? 'no_rule' : 'active';
      return { readable: readableHere, writable: writableHere, priority: readableHere.includes(PRIORITY_FIELD) ? priority : null,
        state: both ? stateOf : null, cycles: both ? cycles : null, now: '2026-10-30T10:00:00Z' };
    };
    if (tail === '/sla' && method === 'GET') return ok(view());
    if (tail === '/sla/priority' && method === 'PUT') {
      if (!writableHere.includes(PRIORITY_FIELD)) return problem(403, 'auth.forbidden');
      if (!PRIORITIES.includes(body?.priority)) return problem(400, 'request.invalid', { violations: ['priority.invalid'] });
      const before = sla.priorities.get(ticket.id) ?? 'normal';
      if (before !== body.priority) {
        sla.priorities.set(ticket.id, body.priority);
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: PRIORITY_FIELD, from: before, to: body.priority }] });
      }
      return ok(view());
    }
    return problem(404, 'resource.not_found');
  }

  // helpdesk.reply with ticket.read and the read rules on mail and contact, as ReplyService.mayReply.
  const mayReply = (account, projectId) => may(account, 'helpdesk.reply', projectId) && may(account, 'ticket.read', projectId)
    && field(account, projectId, 'mail', 'read') && field(account, projectId, 'contact', 'read');
  const replyView = ({ ticketId: _ticket, projectId: _project, contactId: _contact, ...reply }) => reply;

  function ticketHelpdeskRoute(account, item, ticket, method, tail, headers, body) {
    const readableHere = HELPDESK_FIELDS.filter((name) => field(account, item.id, name, 'read'));
    const writableHere = may(account, 'ticket.edit', item.id) ? ['customer', 'contact'].filter((name) => field(account, item.id, name, 'read') && field(account, item.id, name, 'write')) : [];
    const view = () => {
      const link = hd.ticketCustomers.get(ticket.id);
      const customer = link ? hd.customers.find((entry) => entry.id === link.customerId) : null;
      const contact = link?.contactId ? hd.contacts.find((entry) => entry.id === link.contactId) : null;
      return { readable: readableHere, writable: writableHere,
        customer: readableHere.includes('customer') && customer ? { id: customer.id, name: customer.name } : null,
        contact: readableHere.includes('contact') && contact ? { id: contact.id, name: contact.name, addresses: contact.addresses } : null,
        mails: readableHere.includes('mail') ? hd.messages.filter((message) => message.ticketId === ticket.id).map((message) => ({ id: message.id, receivedAt: message.receivedAt,
          sentAt: message.sentAt, fromAddress: message.fromAddress, fromName: message.fromName, to: message.to, cc: message.cc, subject: message.subject, body: message.body, attachments: message.attachments })) : null,
        replies: readableHere.includes('mail') ? hd.replies.filter((reply) => reply.ticketId === ticket.id).map(replyView) : null,
        replyAllowed: Boolean(contact) && mayReply(account, item.id) };
    };
    const senders = () => hd.mailboxes.filter((mailbox) => mailbox.enabled && mailbox.projectId === item.id && mailbox.smtpHost && mailbox.address);
    const contactOfTicket = () => {
      const link = hd.ticketCustomers.get(ticket.id);
      return link?.contactId ? hd.contacts.find((entry) => entry.id === link.contactId) : null;
    };
    if (tail.startsWith('/helpdesk/repl')) {
      if (!mayReply(account, item.id)) return problem(403, 'auth.forbidden');
      const contact = contactOfTicket();
      if (!contact) return problem(409, 'helpdesk.reply_no_contact');
      if (tail === '/helpdesk/reply' && method === 'GET') {
        const last = hd.messages.filter((message) => message.ticketId === ticket.id).at(-1);
        const list = senders();
        return ok({ mailboxes: list.map((mailbox) => ({ id: mailbox.id, name: mailbox.name, address: mailbox.address })), contactId: contact.id, contactName: contact.name,
          recipients: contact.addresses, subject: last?.subject ? `Re: ${last.subject}` : null,
          suggestedMailbox: list.find((mailbox) => mailbox.id === last?.mailboxId)?.id ?? list[0]?.id ?? null });
      }
      if (tail === '/helpdesk/replies' && method === 'POST') {
        const mailbox = senders().find((entry) => entry.id === body.mailboxId);
        const violations = [];
        if (!mailbox) violations.push('mailboxId.unavailable');
        if (!contact.addresses.includes(body.to)) violations.push('to.not_of_contact');
        if (!body.subject?.trim() || /[\r\n]/.test(body.subject)) violations.push('subject.invalid');
        if (!body.body?.trim()) violations.push('body.invalid');
        if (violations.length > 0) return problem(400, 'request.invalid', { violations });
        const reply = { id: uuid(), ticketId: ticket.id, projectId: item.id, contactId: contact.id, mailboxId: mailbox.id, fromAddress: mailbox.address, toAddress: body.to,
          subject: body.subject.trim(), body: body.body, state: 'pending', errorCode: null, sends: 1, createdAt: now(), createdBy: account.id, attemptedAt: null, sentAt: null, version: 1 };
        hd.replies.push(reply);
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id,
          changes: [{ field: 'mail', from: null, to: JSON.stringify({ reply: reply.id, to: reply.toAddress, subject: reply.subject }) }] });
        return { status: 201, body: replyView(reply), etag: `"${reply.version}"` };
      }
      const match = tail.match(/^\/helpdesk\/replies\/([^/]+)\/(send|settle)$/);
      const reply = hd.replies.find((entry) => entry.ticketId === ticket.id && entry.id === match?.[1]);
      if (!match || method !== 'POST') return problem(404, 'resource.not_found');
      if (!reply) return problem(404, 'helpdesk.reply_not_found');
      if (!headers['if-match']) return problem(428, 'helpdesk.precondition_required');
      if (headers['if-match'] !== `"${reply.version}"`) return problem(412, 'helpdesk.version_conflict');
      if (match[2] === 'settle') {
        if (reply.state !== 'uncertain') return problem(409, 'helpdesk.reply_not_uncertain');
        Object.assign(reply, { state: 'sent', sentAt: now(), version: reply.version + 1 });
        return ok(replyView(reply), `"${reply.version}"`);
      }
      if (!['failed', 'uncertain'].includes(reply.state)) return problem(409, 'helpdesk.reply_not_failed');
      if (reply.state === 'uncertain' && body.duplicateAccepted !== true) return problem(409, 'helpdesk.reply_uncertain');
      Object.assign(reply, { state: 'pending', sends: reply.sends + 1, version: reply.version + 1 });
      return ok(replyView(reply), `"${reply.version}"`);
    }
    if (tail === '/helpdesk' && method === 'GET') return ok(view());
    if (tail === '/helpdesk/options' && method === 'GET') {
      if (!writableHere.includes('customer')) return problem(403, 'auth.forbidden');
      // As the backend: contacts only with read and write on contact (TASK-059, F4; TASK-060).
      const contacts = writableHere.includes('contact');
      return ok(hd.customers.filter((customer) => customer.projects.includes(item.id)).map((customer) => ({ id: customer.id, name: customer.name,
        contacts: hd.contacts.filter((contact) => contacts && contact.customerId === customer.id).map((contact) => ({ id: contact.id, name: contact.name, addresses: contact.addresses })) })));
    }
    if (tail === '/helpdesk/customer' && method === 'PUT') {
      if (writableHere.length < 2) return problem(403, 'auth.forbidden');
      const before = hd.ticketCustomers.get(ticket.id) ?? null;
      const name = (list, id) => { const found = list.find((entry) => entry.id === id); return found ? JSON.stringify({ id: found.id, name: found.name }) : null; };
      if (body.customerId) hd.ticketCustomers.set(ticket.id, { customerId: body.customerId, contactId: body.contactId ?? null });
      else hd.ticketCustomers.delete(ticket.id);
      const after = hd.ticketCustomers.get(ticket.id) ?? null;
      const changes = [];
      if ((before?.customerId ?? null) !== (after?.customerId ?? null)) changes.push({ field: 'customer', from: name(hd.customers, before?.customerId), to: name(hd.customers, after?.customerId) });
      if ((before?.contactId ?? null) !== (after?.contactId ?? null)) changes.push({ field: 'contact', from: name(hd.contacts, before?.contactId), to: name(hd.contacts, after?.contactId) });
      if (changes.length > 0) state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes });
      return ok(view());
    }
    return problem(404, 'resource.not_found');
  }

  const addMail = (extra = {}) => {
    const message = { id: uuid(), mailboxId: hd.mailboxes[0]?.id ?? uuid(), projectId: project.id, receivedAt: now(), sentAt: null, fromAddress: 'max@unbekannt.example',
      fromName: 'Max Muster', to: 'support@firma.example', cc: null, subject: 'Drucker defekt', body: 'Hallo,\nder Drucker im zweiten Stock druckt nicht.', sizeBytes: 2048,
      attachments: [], state: 'review', reason: 'sender_unknown', ticketId: null, contactId: null, customerId: null, processedAt: null, processedBy: null, discardReason: null,
      version: 1, ...extra };
    hd.messages.push(message);
    return message;
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
      state.requests.push({ method: request.method(), path: url.pathname, query: url.search, ifMatch: headers['if-match'] ?? null, csrf: headers['x-csrf-token'] ?? null, type: headers['content-type'] ?? null, body: raw, user: session.account?.username ?? null });
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
    addMail,
    addTicket,
    // A prepared cycle of a ticket, as the server would report it (TASK-068).
    addCycle: (ticket, cycle) => sla.cycles.set(ticket.id, [...(sla.cycles.get(ticket.id) ?? []), cycle]),
    addReply: (ticket, extra = {}) => {
      const link = hd.ticketCustomers.get(ticket.id);
      const contact = hd.contacts.find((entry) => entry.id === link?.contactId);
      const reply = { id: uuid(), ticketId: ticket.id, projectId: ticket.projectId, contactId: contact?.id ?? null, mailboxId: hd.mailboxes[0]?.id ?? uuid(),
        fromAddress: hd.mailboxes[0]?.address ?? 'support@firma.example', toAddress: contact?.addresses[0] ?? 'lea@nordwerk.example', subject: 'Re: Drucker defekt',
        body: 'Wir kümmern uns.', state: 'pending', errorCode: null, sends: 1, createdAt: now(), createdBy: anna.id, attemptedAt: null, sentAt: null, version: 1, ...extra };
      hd.replies.push(reply);
      return reply;
    },
    deactivate: (account) => { account.active = false; },
  };
}
