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
// The rule of the name, the same as AppearanceService on the server and frontend/src/api/client.ts (TASK-087).
const NAME_EDGES = /^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$/gu;
const NAME_FORBIDDEN = /[\u0000-\u001f\u007f-\u009f\u00ad\u0600-\u0605\u061c\u06dd\u070f\u0890\u0891\u08e2\u180e\u200b-\u200f\u2028-\u202e\u2060-\u2064\u2066-\u206f\ufeff\ufff9-\ufffb\u{110bd}\u{110cd}\u{13430}-\u{1343f}\u{1bca0}-\u{1bca3}\u{1d173}-\u{1d17a}\u{e0001}\u{e0020}-\u{e007f}\ud800-\udfff]/u;
const COMPANY_ACTIONS = ['accounts.manage', 'appearance.manage', 'helpdesk.manage', 'jobs.manage', 'permissions.audit.read', 'permissions.manage', 'projects.manage', 'session_duration.manage', 'sla.manage'];
const PROJECT_ACTIONS = ['helpdesk.reply', 'helpdesk.review', 'project.manage', 'project.read', 'tag.create', 'tags.manage', 'ticket.create', 'ticket.edit', 'ticket.read', 'time.book', 'time.manage', 'time.read', 'wiki.edit', 'wiki.publish', 'wiki.read', 'requirement.approve', 'requirement.edit', 'requirement.read'];
// The fields of requirements in the rights core (TASK-096).
const REQUIREMENT_FIELDS = ['criteria', 'description', 'owner', 'priority', 'title'];
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
  color: null, archived: false, mergedInto: null, version: 1, createdAt: now(), createdBy: by.id, updatedAt: now(), updatedBy: by.id,
  visibility: 'internal', organizationId: null, ...extra });
const TAG_LIMIT = 30;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const ANNA = { username: 'anna', password: 'Anna Passwort 2026', displayName: 'Anna Admin' };
export const BEN = { username: 'ben', password: 'Ben Passwort 2026', displayName: 'Ben Leser' };
export const NORA = { username: 'nora', password: 'EinmalNora0001', displayName: 'Nora Neu' };

// The mail template "Antwort an Kunden" as the server sets it (TASK-082): plain text parts with placeholders, filled and
// escaped for the HTML. The browser tests read the parts and the plain text; the HTML follows the same rules.
const PLACEHOLDERS = ['company.name', 'contact.name', 'ticket.number', 'ticket.title', 'sender.name', 'mailbox.address', 'portal.url'];
const BUILT_IN = {
  de: { greeting: 'Guten Tag {{contact.name}},', signature: 'Viele Grüße\n{{sender.name}}\n{{company.name}}',
    footer: 'Ticket {{ticket.number}}: {{ticket.title}}\nIhre Anfragen im Kundenportal: {{portal.url}}' },
  en: { greeting: 'Hello {{contact.name}},', signature: 'Kind regards\n{{sender.name}}\n{{company.name}}',
    footer: 'Ticket {{ticket.number}}: {{ticket.title}}\nYour requests in the customer portal: {{portal.url}}' },
};
const PLACEHOLDER_TOKEN = /\{\{([^{}]{0,60})\}\}/g;

function unknownPlaceholders(text) {
  const unknown = [...text.matchAll(PLACEHOLDER_TOKEN)].map((match) => match[1]).filter((name) => !PLACEHOLDERS.includes(name)).map((name) => `{{${name}}}`);
  const rest = text.replace(PLACEHOLDER_TOKEN, ' ');
  const open = rest.indexOf('{{');
  const close = rest.indexOf('}}');
  if (open >= 0) unknown.push(rest.slice(open, open + 30).trim());
  else if (close >= 0) unknown.push(rest.slice(Math.max(0, close - 28), close + 2).trim());
  return [...new Set(unknown)];
}

const escapeHtml = (text) => text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const trimLines = (lines) => {
  let from = 0;
  let to = lines.length;
  while (from < to && lines[from].trim() === '') from += 1;
  while (to > from && lines[to - 1].trim() === '') to -= 1;
  return lines.slice(from, to).map((line) => line.trimEnd());
};

function fillTemplate(template, values, html) {
  const lines = [];
  for (const line of String(template).replace(/\r\n?/g, '\n').split('\n')) {
    if (!values['portal.url'] && line.includes('{{portal.url}}')) continue;
    let out = '';
    let last = 0;
    for (const match of line.matchAll(PLACEHOLDER_TOKEN)) {
      out += html ? escapeHtml(line.slice(last, match.index)) : line.slice(last, match.index);
      const value = String(values[match[1]] ?? '').replace(/[\u0000-\u001f\u007f\u2028\u2029]+/g, ' ').trim();
      out += html ? escapeHtml(value) : value;
      last = match.index + match[0].length;
    }
    out += html ? escapeHtml(line.slice(last)) : line.slice(last);
    lines.push(out);
  }
  return trimLines(lines).join(html ? '<br>\n' : '\n');
}

function renderMail(code, texts, address, name, color, logo, sample) {
  const values = { 'company.name': name, 'contact.name': sample.contact, 'ticket.number': sample.number, 'ticket.title': sample.title,
    'sender.name': sample.sender, 'mailbox.address': sample.mailbox, 'portal.url': address ?? '' };
  const parts = { name, accentColor: color, logo, greeting: fillTemplate(texts.greeting, values, false),
    body: trimLines(sample.body.replace(/\r\n?/g, '\n').split('\n')).join('\n'), signature: fillTemplate(texts.signature, values, false),
    footer: fillTemplate(texts.footer, values, false) };
  const sections = [parts.greeting, parts.body, parts.signature].filter((part, index) => index === 1 || part !== '');
  const text = `${sections.join('\n\n')}${parts.footer === '' ? '\n' : `\n\n-- \n${parts.footer}\n`}`;
  const html = `<!DOCTYPE html>\n<html lang="${code}"><body><p>${fillTemplate(texts.greeting, values, true)}</p><p>${escapeHtml(parts.body).replace(/\n/g, '<br>\n')}</p></body></html>`;
  return { language: code, text, html, parts };
}

export function createBackend({ accent = '#f97316', anonymousAppearance = false, brandName = 'Task Manager' } = {}) {
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
    appearance: { version: 3, accent, name: brandName, logo: null },
    // The mail template of TASK-082: null for a language means the built-in one.
    mailTemplate: { version: 1, applicationUrl: null, de: null, en: null },
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
    helpdesk: { secretKey: true, customers: [], contacts: [], mailboxes: [], messages: [], replies: [], ticketCustomers: new Map(),
      // The portal conversation (TASK-076): posts of portal accounts and of the support, and when an account last opened a request
      // with the highest position of an answer of the support it acknowledged (TASK-080); `positions` counts the answers per ticket.
      posts: [], seen: new Map(), positions: new Map() },
    requests: [],
    // Time entries (TASK-069) with their revisions; `timeConflict` makes the next change fail like a concurrent one.
    time: [],
    timeConflict: false,
    // The wiki (TASK-093): pages with their revisions, events and uploaded images; `wikiConflict` makes the next save fail
    // like a save of someone else that came first.
    wiki: { pages: [], revisions: [], events: [], images: [] },
    // The requirements (TASK-096) with their revisions and events, and the last number per project.
    requirements: { items: [], revisions: [], events: [], counters: new Map(), ticketLinks: [], pageLinks: [] },
    wikiConflict: null,
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
    const rules = groupsOf(account).flatMap((group) => group.fields).filter((rule) => (rule.resourceType ?? 'ticket') === 'ticket' && rule.field === name
      && rule.operation === operation && (rule.projectId === null || rule.projectId === projectId));
    return rules.some((rule) => rule.effect === 'allow') && !rules.some((rule) => rule.effect === 'deny');
  };
  // The field rules of requirements (TASK-096): their own resource type, the same policy.
  const requirementField = (account, projectId, name, operation) => {
    const rules = groupsOf(account).flatMap((group) => group.fields).filter((rule) => rule.resourceType === 'requirement' && rule.field === name
      && rule.operation === operation && (rule.projectId === null || rule.projectId === projectId));
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
    // The body checks of the server (TASK-086, Codex H2): known fields, an action, 1 to 10 tags and 1 to 500 tickets.
    const violations = [];
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      return problem(400, 'request.invalid', { violations: ['action.invalid', 'tags.invalid', 'tickets.invalid'] });
    }
    if (Object.keys(body).some((key) => !['action', 'tags', 'tickets'].includes(key))) violations.push('fields.unknown');
    if (!add && body.action !== 'remove') violations.push('action.invalid');
    const list = (value, max) => Array.isArray(value) && value.length > 0 && value.length <= max && value.every((item) => typeof item === 'string' && UUID.test(item));
    if (!list(body.tags, 10)) violations.push('tags.invalid');
    if (!list(body.tickets, 500)) violations.push('tickets.invalid');
    if (violations.length > 0) return problem(400, 'request.invalid', { violations });
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
    const afterId = query.get('afterId');
    const limit = Number(query.get('limit') ?? 100);
    // Ordered by name and identifier like the server; the cursor names both (TASK-079, F4).
    const order = (a, b) => (tagKey(a.name) < tagKey(b.name) ? -1 : tagKey(a.name) > tagKey(b.name) ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
    const beyond = (tag) => after === null || tagKey(tag.name) > tagKey(after) || (afterId !== null && tagKey(tag.name) === tagKey(after) && tag.id > afterId);
    return state.tags.filter((tag) => tag.projectId === projectId && tag.mergedInto === null && (query.get('archived') === 'true' || !tag.archived)
      && (q === '' || tagKey(tag.name).includes(q)) && beyond(tag))
      .sort(order).slice(0, limit).map((tag) => tagView(account, tag, query.get('usage') === 'true'));
  };
  // The limit of the suggestions as the server reads it: 1 to 20, 20 without one (TASK-079, H3).
  const suggestionLimit = (query) => Math.min(20, Math.max(1, Number(query.get('limit') ?? 20) || 20));
  const suggestions = (account, projectId, query) => {
    const q = tagKey(query.get('q') ?? '');
    const rank = (tag) => (q === '' || tagKey(tag.name).startsWith(q) ? 0 : tagKey(tag.name).includes(` ${q}`) ? 1 : 2);
    // archived=true also offers archived tags, for filters and removals (TASK-086, Codex F3).
    const archived = query.get('archived') === 'true';
    const items = state.tags.filter((tag) => (tag.projectId === null || tag.projectId === projectId) && tag.mergedInto === null && (archived || !tag.archived)
      && (q === '' || tagKey(tag.name).includes(q)))
      .sort((a, b) => rank(a) - rank(b) || tagKey(a.name).localeCompare(tagKey(b.name))).slice(0, suggestionLimit(query)).map((tag) => tagView(account, tag));
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
    if (body.visibility !== undefined && !['internal', 'customer'].includes(body.visibility)) return problem(400, 'request.invalid', { violations: ['visibility.invalid'] });
    // Tags in the portal (TASK-077): a tag of one organization becomes one for every customer when released or made internal.
    const visibility = body.visibility ?? tag.visibility;
    const organizationId = body.forAllCustomers === true || visibility === 'internal' ? null : tag.organizationId;
    const name = body.name !== undefined ? body.name : tag.name;
    if ((body.name !== undefined || organizationId !== tag.organizationId) && state.tags.some((entry) => entry !== tag && entry.projectId === projectId
      && entry.mergedInto === null && entry.organizationId === organizationId && tagKey(entry.name) === tagKey(name))) {
      return problem(409, 'tag.name_taken');
    }
    const next = { ...tag, ...(body.name !== undefined ? { name: body.name.trim() } : {}), ...(body.archived !== undefined ? { archived: body.archived } : {}),
      ...(body.description !== undefined ? { description: body.description === null || body.description.trim() === '' ? null : body.description.trim() } : {}),
      ...(body.color !== undefined ? { color: body.color } : {}), visibility, organizationId };
    const changed = ['name', 'archived', 'description', 'color', 'visibility', 'organizationId'].some((key) => next[key] !== tag[key]);
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
    const existing = state.tags.find((entry) => entry.projectId === catalog && entry.mergedInto === null && entry.organizationId === null && tagKey(entry.name) === tagKey(body.name));
    if (existing) return existing.archived ? { problem: problem(409, 'tag.archived') } : { tag: existing };
    if (!createTags(account, catalog)) return { problem: problem(403, 'auth.forbidden') };
    return { tag: newTag(body.name.trim(), catalog, account), created: true };
  };

  const configurationOf = (projectId) => state.configurations.get(projectId).configuration;
  const customFieldsOf = (projectId) => configurationOf(projectId).customFields ?? [];
  const requirementFieldsOf = (projectId) => state.configurations.get(projectId)?.configuration.requirementFields ?? [];
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
  // Like the server since TASK-100 (F1 of the review of TASK-098): the custom fields of requirements only for who manages the project.
  const projectView = (account, item) => {
    const manageable = may(account, 'projects.manage') || may(account, 'project.manage', item.id);
    const configuration = configurationOf(item.id);
    return { ...item, configurationVersion: state.configurations.get(item.id).version,
      configuration: manageable ? configuration : { ...configuration, requirementFields: [] }, manageable };
  };
  const summary = (account, item) => ({ id: item.id, key: item.key, name: item.name, description: item.description, version: item.version, manageable: projectView(account, item).manageable });
  // An account as the administration sees it (TASK-045): the state of the one time password and a running waiting time.
  const accountView = ({ password: _password, delayedUntil, ...rest }) => ({ authKind: 'local', kind: 'internal', organizationId: null, contactId: null, ...rest,
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
    // What the login page reads without a session (TASK-081).
    if (p === '/appearance/public' && method === 'GET') {
      if (state.publicAppearanceDown) return problem(503, 'server.unavailable');
      return ok({ name: state.appearance.name, accentColor: state.appearance.accent, logoUrl: state.appearance.logo ? '/api/v1/appearance/public/logo' : null });
    }
    if (p === '/appearance/public/logo' && method === 'GET') {
      return state.appearance.logo ? { status: 200, raw: PNG, type: 'image/png' } : problem(404, 'appearance.logo_not_found');
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

    if (p.startsWith('/appearance/mail-template')) {
      if (!may(account, 'appearance.manage')) return problem(403, 'auth.forbidden');
      const template = state.mailTemplate;
      if (p === '/appearance/mail-template' && method === 'GET') return ok(mailTemplateBody(), `"${template.version}"`);
      if (p === '/appearance/mail-template/preview' && method === 'POST') {
        if (body.language !== 'de' && body.language !== 'en') return problem(400, 'mail_template.languages_missing');
        const code = body.language;
        const refused = checkTexts(code, body.texts) ?? checkAddress(body.applicationUrl);
        if (refused) return refused;
        const sample = code === 'en'
          ? { contact: 'Alex Example', number: 'HELP-42', title: 'Printer in hall 2 does not start', sender: 'Sam Support', mailbox: 'support@example.com',
            body: 'Thank you for your message.\nWe have restarted the printer; it works again.\nPlease let us know if anything is still wrong.' }
          : { contact: 'Erika Beispiel', number: 'HELP-42', title: 'Drucker in Halle 2 startet nicht', sender: 'Max Support', mailbox: 'support@example.com',
            body: 'Vielen Dank für Ihre Nachricht.\nWir haben den Drucker neu gestartet, er läuft wieder.\nBitte melden Sie sich, falls noch etwas nicht stimmt.' };
        return ok(renderMail(code, body.texts, (body.applicationUrl?.trim().replace(/^https:/i, 'https:') || null), body.name ?? state.appearance.name,
          (body.accentColor ?? state.appearance.accent).toLowerCase(), state.appearance.logo !== null, sample));
      }
      if (!headers['if-match']) return problem(428, 'mail_template.precondition_required');
      if (headers['if-match'] !== `"${template.version}"`) return problem(412, 'mail_template.version_conflict');
      if (p === '/appearance/mail-template' && method === 'PUT') {
        if (!body.de || !body.en) return problem(400, 'mail_template.languages_missing');
        const refused = checkAddress(body.applicationUrl) ?? checkTexts('de', body.de) ?? checkTexts('en', body.en);
        if (refused) return refused;
        Object.assign(template, { applicationUrl: (body.applicationUrl?.trim().replace(/^https:/i, 'https:') || null), de: { ...body.de }, en: { ...body.en }, version: template.version + 1 });
        return ok(mailTemplateBody(), `"${template.version}"`);
      }
      if (p === '/appearance/mail-template' && method === 'DELETE') {
        Object.assign(template, { de: null, en: null, version: template.version + 1 });
        return ok(mailTemplateBody(), `"${template.version}"`);
      }
      return problem(404, 'resource.not_found');
    }
    if (p === '/appearance' && method === 'GET') {
      if (state.appearanceReadDown) return problem(503, 'server.unavailable');
      return ok(appearanceBody(), `"${state.appearance.version}"`);
    }
    if (p.startsWith('/appearance')) {
      if (p === '/appearance/logo' && method === 'GET') return { status: 200, raw: PNG, type: 'image/png' };
      if (!may(account, 'appearance.manage')) return problem(403, 'auth.forbidden');
      const current = headers['if-match'] === `"${state.appearance.version}"`;
      if (p === '/appearance' && method === 'PUT') {
        // The rule of the server (TASK-087): a part sent as null is not sent, a request without parts is refused, the
        // values are checked before the version, spaces of every kind leave the edges, an unchanged request is no change.
        // Like the JSON binding of the server (TASK-089, finding S2): an object or a list in a field or as the whole body is
        // 400 request.invalid, a number or a truth value is taken as its text, a body of null sends no part.
        if (Array.isArray(body) || (body !== null && typeof body !== 'object')) return problem(400, 'request.invalid');
        const asText = (value) => (value === undefined || value === null ? null
          : typeof value === 'object' ? undefined : String(value));
        const sentName = asText(body?.name);
        const sentAccent = asText(body?.accentColor);
        if (sentName === undefined || sentAccent === undefined) return problem(400, 'request.invalid');
        if (sentName === null && sentAccent === null) return problem(400, 'appearance.change_empty');
        if (sentAccent !== null && !/^#[0-9a-fA-F]{6}$/.test(sentAccent)) return problem(400, 'appearance.color_invalid');
        const name = sentName === null ? state.appearance.name : sentName.replace(NAME_EDGES, '');
        if (name === '' || [...name].length > 80 || NAME_FORBIDDEN.test(name)) return problem(400, 'appearance.name_invalid');
        if (!current) return problem(412, 'appearance.version_conflict');
        const accent = sentAccent === null ? state.appearance.accent : sentAccent.toLowerCase();
        if (name !== state.appearance.name || accent !== state.appearance.accent) {
          state.appearance.version += 1;
          Object.assign(state.appearance, { name, accent });
        }
        return ok(appearanceBody(), `"${state.appearance.version}"`);
      }
      // The checks of a logo come before the version like on the server (TASK-089, hint H2): empty, too large, a type the bytes
      // do not show, or a declared type that is not the one of the bytes.
      let logo = null;
      if (p === '/appearance/logo' && method === 'PUT') {
        const content = Buffer.isBuffer(body) ? body : Buffer.alloc(0);
        if (content.length === 0) return problem(400, 'appearance.logo_empty');
        if (content.length > 512 * 1024) return problem(413, 'request.too_large');
        const type = content.subarray(0, 8).equals(PNG.subarray(0, 8)) ? 'image/png'
          : content[0] === 0xff && content[1] === 0xd8 && content[2] === 0xff ? 'image/jpeg'
            : content.subarray(0, 4).toString('latin1') === 'RIFF' && content.subarray(8, 12).toString('latin1') === 'WEBP' ? 'image/webp' : null;
        const declared = (headers['content-type'] ?? '').toLowerCase().split(';')[0].trim();
        if (type === null || declared !== type) return problem(415, 'appearance.logo_type_invalid');
        logo = { type, content };
      }
      if (!current) return problem(412, 'appearance.version_conflict');
      // Reset, removing the logo and the same logo again change nothing when nothing changes (TASK-087, finding R2).
      const a = state.appearance;
      if (p === '/appearance' && method === 'DELETE') {
        if (a.accent === '#f97316' && a.name === 'Task Manager' && a.logo === null) return ok(appearanceBody(), `"${a.version}"`);
        a.version += 1;
        Object.assign(a, { accent: '#f97316', name: 'Task Manager', logo: null });
      } else if (logo !== null) {
        if (a.logo !== null && a.logo.contentType === logo.type && a.logo.content?.equals(logo.content)) return ok(appearanceBody(), `"${a.version}"`);
        a.version += 1;
        a.logo = { contentType: logo.type, size: logo.content.length, content: logo.content, url: `/api/v1/appearance/logo?v=${a.version}` };
      } else {
        if (a.logo === null) return ok(appearanceBody(), `"${a.version}"`);
        a.version += 1;
        a.logo = null;
      }
      return ok(appearanceBody(), `"${a.version}"`);
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
    // Portal accounts (TASK-075): one customer organization each, the group "Portal" and the projects released to the organization.
    const portalOf = (item) => item.kind === 'portal';
    if (p === '/portal/projects' && method === 'GET') {
      if (!portalOf(account)) return problem(404, 'project.not_found');
      return ok(state.projects.filter((item) => isMember(account, item.id)).map(({ id, key, name }) => ({ id, key, name,
        ticketTypes: configurationOf(id).ticketTypes.filter((type) => type.portal === true).map((type) => ({ key: type.key, name: type.name })),
        statuses: configurationOf(id).statuses.map((status) => ({ key: status.key, name: status.name, category: status.category })) })));
    }
    // The portal routes of a request (TASK-075, TASK-076); a request of another organization answers 404 like an unknown one.
    const portalHd = state.helpdesk;
    const ownTickets = (projectId) => state.tickets.filter((item) => item.projectId === projectId && portalHd.ticketCustomers.get(item.id)?.customerId === account.organizationId);
    const portalFile = ({ id, name, size, uploadedAt }) => ({ id, name, size, uploadedAt });
    // Tags in the portal (TASK-077): only tags visible to the organization; changing needs ticket.edit and the write rule for tags.
    const visibleTag = (tag) => tag.visibility === 'customer' && tag.mergedInto === null && (tag.organizationId === null || tag.organizationId === account.organizationId);
    const portalTag = ({ id, name, description, color, archived, organizationId }) => ({ id, name, description, color, archived, own: organizationId !== null });
    const portalTagMatch = p.match(/^\/portal\/projects\/([^/]+)\/(?:tags|tickets\/(\d+)\/tags(?:\/([^/]+))?)$/);
    if (portalTagMatch) {
      const [, projectId, number, tagId] = portalTagMatch;
      if (!portalOf(account) || !isMember(account, projectId) || !may(account, 'ticket.read', projectId) || !field(account, projectId, TAG_FIELD, 'read')) {
        return problem(404, number === undefined ? 'project.not_found' : 'ticket.not_found');
      }
      const inProject = (tag) => visibleTag(tag) && (tag.projectId === null || tag.projectId === projectId);
      if (number === undefined) {
        const q = tagKey(query.get('q') ?? '');
        return ok({ items: state.tags.filter((tag) => inProject(tag) && !tag.archived && (q === '' || tagKey(tag.name).includes(q)))
          .sort((a, b) => tagKey(a.name).localeCompare(tagKey(b.name))).slice(0, suggestionLimit(query)).map(portalTag), create: may(account, 'tag.create', projectId) });
      }
      const ticket = ownTickets(projectId).find((item) => item.number === Number(number));
      if (!ticket) return problem(404, 'ticket.not_found');
      const writes = may(account, 'ticket.edit', projectId) && field(account, projectId, TAG_FIELD, 'write');
      const held = () => state.ticketTags.filter((entry) => entry.ticket === ticket.id).map((entry) => state.tags.find((tag) => tag.id === entry.tag));
      if (method === 'GET') return ok({ items: held().filter(visibleTag).sort((a, b) => tagKey(a.name).localeCompare(tagKey(b.name))).map(portalTag), writable: writes });
      if (!writes) return problem(403, 'auth.forbidden');
      if (method === 'DELETE') {
        const tag = state.tags.find((entry) => entry.id === tagId && inProject(entry));
        if (!tag) return problem(404, 'tag.not_found');
        const index = state.ticketTags.findIndex((entry) => entry.ticket === ticket.id && entry.tag === tag.id);
        if (index >= 0) state.ticketTags.splice(index, 1);
        return empty();
      }
      if (method !== 'POST') return problem(405, 'request.method');
      if (Object.keys(body ?? {}).some((key) => !['tagId', 'name'].includes(key))) return problem(400, 'request.invalid', { violations: ['fields.unknown'] });
      let tag = body.tagId !== undefined ? state.tags.find((entry) => entry.id === body.tagId && inProject(entry))
        : state.tags.filter((entry) => inProject(entry) && tagKey(entry.name) === tagKey(body.name ?? ''))
          .sort((a, b) => (a.organizationId === null) - (b.organizationId === null) || (a.projectId === null) - (b.projectId === null))[0];
      if (!tag && body.tagId !== undefined) return problem(404, 'tag.not_found');
      if (!tag) {
        if (!body.name?.trim()) return problem(400, 'request.invalid', { violations: ['name.invalid'] });
        if (!may(account, 'tag.create', projectId)) return problem(403, 'auth.forbidden');
        // Like the server, a refused assignment leaves no new tag behind (TASK-079, H3): the limit is checked first.
        if (held().length >= TAG_LIMIT) return problem(409, 'tag.limit_reached');
        tag = newTag(body.name.trim(), projectId, account, { visibility: 'customer', organizationId: account.organizationId });
        state.tags.push(tag);
      }
      if (tag.archived) return problem(409, 'tag.archived');
      if (!held().some((entry) => entry.id === tag.id)) {
        if (held().length >= TAG_LIMIT) return problem(409, 'tag.limit_reached');
        state.ticketTags.push({ ticket: ticket.id, tag: tag.id });
      }
      return ok(portalTag(tag));
    }
    let portalMatch = p.match(/^\/portal\/projects\/([^/]+)\/(tickets|activity)(?:\/(\d+))?(\/[a-z]+)?(?:\/([^/]+))?$/);
    if (portalMatch) {
      const [, projectId, kind, number, tail, id] = portalMatch;
      if (!portalOf(account) || !isMember(account, projectId) || !may(account, 'ticket.read', projectId)) return problem(404, 'project.not_found');
      const own = ownTickets(projectId);
      const view = (item) => ({ ...ticketView(account, item), writableFields: [] });
      if (kind === 'activity' && method === 'GET') {
        return ok(own.map((item) => {
          const posts = portalHd.posts.filter((post) => post.ticketId === item.id && post.organizationId === account.organizationId);
          const replies = portalHd.replies.filter((reply) => reply.ticketId === item.id && reply.state === 'sent' && reply.customerId === account.organizationId);
          const answers = [...posts.filter((post) => post.authorKind === 'support').map((post) => ({ at: post.createdAt, position: post.position })),
            ...replies.map((reply) => ({ at: reply.sentAt, position: reply.portalPosition }))];
          const support = answers.map((answer) => answer.at).sort().at(-1) ?? null;
          const highest = Math.max(0, ...answers.map((answer) => answer.position ?? 0));
          const mark = portalHd.seen.get(`${account.id}:${item.id}`);
          return { number: item.number, lastActivityAt: [...posts.map((post) => post.createdAt), ...replies.map((reply) => reply.sentAt)].sort().at(-1) ?? null,
            lastSupportAt: support, seenAt: mark?.at ?? null, unread: highest > (mark?.position ?? 0) };
        }));
      }
      if (number === undefined && method === 'GET') {
        const configuration = configurationOf(projectId);
        const category = (key) => configuration.statuses.find((status) => status.key === key)?.category;
        const q = query.get('q')?.toLowerCase() ?? null;
        return ok(own.filter((item) => !query.get('status') || item.values.status === query.get('status'))
          .filter((item) => !query.get('state') || (query.get('state') === 'done') === (category(item.values.status) === 'done'))
          .filter((item) => !q || [item.values.title, item.values.description].some((text) => text?.toLowerCase().includes(q)))
          .filter((item) => !query.get('tag') || state.ticketTags.some((entry) => entry.ticket === item.id && entry.tag === query.get('tag')
            && visibleTag(state.tags.find((tag) => tag.id === entry.tag))))
          // Newest first and paged like the server: at most `limit` (default 50) below `before` (TASK-078, F6).
          .filter((item) => !query.get('before') || item.number < Number(query.get('before')))
          .sort((a, b) => b.number - a.number).slice(0, Number(query.get('limit') ?? 50)).map(view));
      }
      if (number === undefined && method === 'POST') {
        if (!may(account, 'ticket.create', projectId)) return problem(403, 'auth.forbidden');
        const configuration = configurationOf(projectId);
        if (!configuration.ticketTypes.some((type) => type.portal === true && type.key === body.type)) return problem(400, 'request.invalid', { violations: ['type.not_offered'] });
        if (!body.title?.trim()) return problem(400, 'request.invalid', { violations: ['title.invalid'] });
        const item = state.projects.find((entry) => entry.id === projectId);
        const next = state.tickets.filter((entry) => entry.projectId === projectId).length + 1;
        const ticket = { id: uuid(), projectId, key: item.key, number: next, version: 1, configurationVersion: state.configurations.get(projectId).version,
          values: { title: body.title.trim(), description: body.description ?? null, type: body.type, status: configuration.initialStatus, assignee: null }, custom: {},
          createdAt: now(), createdBy: account.id, updatedAt: now(), updatedBy: account.id };
        state.tickets.push(ticket);
        portalHd.ticketCustomers.set(ticket.id, { customerId: account.organizationId, contactId: account.contactId ?? null });
        return { status: 201, body: { id: ticket.id, number: next } };
      }
      const ticket = own.find((item) => item.number === Number(number));
      if (!ticket) return problem(404, 'ticket.not_found');
      if (tail === undefined && method === 'GET') return ok(view(ticket));
      if (tail === '/seen' && method === 'PUT') {
        // The mark names the highest position of an answer of the support shown (TASK-080, F2); an answer not shown stays new,
        // a mark beyond the answers counts as the last of them, an earlier mark changes nothing.
        if (!Number.isInteger(body?.position) || body.position < 0) return problem(400, 'request.invalid', { violations: ['position.invalid'] });
        const key = `${account.id}:${ticket.id}`;
        const position = Math.min(body.position, portalHd.positions.get(ticket.id) ?? 0);
        portalHd.seen.set(key, { position: Math.max(position, portalHd.seen.get(key)?.position ?? 0), at: now() });
        return empty();
      }
      // Entries and files keep the organization they were written for (TASK-079, F1).
      const files = state.attachments.filter((entry) => entry.ticket === ticket.id && !entry.removed && entry.visibility === 'customer'
        && entry.organizationId === account.organizationId);
      if (tail === '/conversation' && method === 'GET') {
        const entries = [
          ...portalHd.messages.filter((message) => message.ticketId === ticket.id && message.customerId === account.organizationId)
            .map((message) => ({ kind: 'mail', id: message.id, at: message.receivedAt, author: 'customer', own: false, subject: message.subject, body: message.body, attachments: [], position: null })),
          // A sent mail answer belongs to the organization its contact had when it was written (TASK-079, F1; TASK-080, H3).
          ...portalHd.replies.filter((reply) => reply.ticketId === ticket.id && reply.state === 'sent' && reply.customerId === account.organizationId)
            .map((reply) => ({ kind: 'reply', id: reply.id, at: reply.sentAt ?? reply.createdAt, author: 'support', own: false, subject: reply.subject, body: reply.body, attachments: [],
              position: reply.portalPosition ?? null })),
          ...portalHd.posts.filter((post) => post.ticketId === ticket.id && post.organizationId === account.organizationId).map((post) => ({ kind: 'post', id: post.id, at: post.createdAt, author: post.authorKind,
            own: post.authorId === account.id, subject: null, body: post.body, attachments: files.filter((file) => post.attachmentIds.includes(file.id)).map(portalFile),
            position: post.position ?? null })),
        ].sort((a, b) => a.at.localeCompare(b.at));
        return ok(entries);
      }
      if (tail === '/conversation' && method === 'POST') {
        if (!may(account, 'portal.answer', projectId)) return problem(403, 'auth.forbidden');
        if (!body.body?.trim()) return problem(400, 'request.invalid', { violations: ['body.invalid'] });
        const ids = body.attachmentIds ?? [];
        const taken = portalHd.posts.flatMap((post) => post.attachmentIds);
        if (!ids.every((fileId) => files.some((file) => file.id === fileId && file.uploadedBy === account.id) && !taken.includes(fileId))) {
          return problem(400, 'request.invalid', { violations: ['attachmentIds.invalid'] });
        }
        const post = { id: uuid(), ticketId: ticket.id, authorKind: 'customer', authorId: account.id, body: body.body.trim(), createdAt: now(), attachmentIds: ids,
          organizationId: account.organizationId };
        portalHd.posts.push(post);
        return { status: 201, body: { kind: 'post', id: post.id, at: post.createdAt, author: 'customer', own: true, subject: null, body: post.body,
          attachments: files.filter((file) => ids.includes(file.id)).map(portalFile), position: null } };
      }
      if (tail === '/attachments') {
        if (!field(account, projectId, ATTACHMENT_FIELD, 'read')) return problem(404, 'ticket.not_found');
        if (method === 'GET' && id === undefined) return { ...ok(files.map(portalFile)), headers: { 'X-Attachment-Max-Bytes': String(state.attachmentLimit) } };
        if (method === 'GET') {
          const found = files.find((entry) => entry.id === id && entry.content !== null);
          return found ? { status: 200, raw: found.content, type: 'application/octet-stream', headers: { 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(found.name)}` } } : problem(404, 'attachment.not_found');
        }
        if (method === 'POST') {
          if (!may(account, 'portal.answer', projectId) || !field(account, projectId, ATTACHMENT_FIELD, 'write')) return problem(403, 'auth.forbidden');
          const name = query.get('name');
          const content = Buffer.isBuffer(body) ? body : Buffer.alloc(0);
          if (content.length === 0) return problem(400, 'attachment.empty');
          if (content.length > state.attachmentLimit) return problem(413, 'attachment.too_large');
          if (/\.(exe|bat|js|sh|html)$/i.test(name)) return problem(415, 'attachment.type_blocked');
          const entry = { id: uuid(), ticket: ticket.id, name: name.split(/[\\/]/).pop(), size: content.length, sha256: 'a'.repeat(64), uploadedAt: now(), uploadedBy: account.id,
            content, removed: false, visibility: 'customer', organizationId: account.organizationId };
          state.attachments.push(entry);
          return { status: 201, body: portalFile(entry) };
        }
      }
      return problem(404, 'resource.not_found');
    }
    if (p.startsWith('/portal-accounts')) {
      if (portalOf(account) || !may(account, 'accounts.manage')) return problem(403, 'auth.forbidden');
      const released = (customer) => state.projects.filter((item) => customer.projects.includes(item.id)).map(({ id, key, name }) => ({ id, key, name }));
      const portalView = (item) => ({ id: item.id, username: item.username, displayName: item.displayName, active: item.active, organizationId: item.organizationId,
        contactId: item.contactId, projectIds: state.projects.filter((entry) => isMember(item, entry.id)).map((entry) => entry.id) });
      const setProjects = (item, ids) => {
        for (const entry of state.projects) {
          const members = state.members.get(entry.id) ?? [];
          const rest = members.filter((member) => member.accountId !== item.id);
          state.members.set(entry.id, ids.includes(entry.id) ? [...rest, { accountId: item.id, addedAt: now() }] : rest);
        }
      };
      const check = (customer, contactId, ids) => {
        if (!customer) return problem(400, 'request.invalid', { violations: ['organizationId.unknown'] });
        if (contactId && hd.contacts.find((entry) => entry.id === contactId)?.customerId !== customer.id) return problem(400, 'request.invalid', { violations: ['contactId.not_of_organization'] });
        if (!(ids ?? []).every((id) => customer.projects.includes(id))) return problem(400, 'request.invalid', { violations: ['projectIds.not_released'] });
        return null;
      };
      if (p === '/portal-accounts' && method === 'GET') return ok(state.accounts.filter(portalOf).map(portalView));
      if (p === '/portal-accounts/options' && method === 'GET') {
        return ok(hd.customers.map((customer) => ({ id: customer.id, name: customer.name, projects: released(customer),
          contacts: hd.contacts.filter((entry) => entry.customerId === customer.id).map(({ id, name }) => ({ id, name })) })));
      }
      if (p === '/portal-accounts' && method === 'POST') {
        const refused = check(hd.customers.find((entry) => entry.id === body.organizationId), body.contactId, body.projectIds);
        if (refused) return refused;
        if (state.accounts.some((item) => item.username === body.username)) return problem(409, 'account.username_taken');
        let group = state.groups.find((entry) => entry.purpose === 'portal');
        if (!group) {
          group = { id: uuid(), name: 'Portal', purpose: 'portal', members: [], actions: ['ticket.read', 'ticket.create', 'portal.answer'].map((action) => ({ action, projectId: null })),
            fields: ['title', 'description', 'type', 'status'].map((name) => ({ resourceType: 'ticket', field: name, projectId: null, operation: 'read', effect: 'allow' })) };
          state.groups.push(group);
        }
        const created = { id: uuid(), username: body.username, displayName: body.displayName ?? null, password: `Einmal${counter}Portal`, active: true, passwordChangeRequired: true,
          createdAt: now(), kind: 'portal', organizationId: body.organizationId, contactId: body.contactId ?? null };
        state.accounts.push(created);
        group.members.push(created.id);
        setProjects(created, body.projectIds ?? []);
        logAccount(account, 'account.created', created);
        return ok({ account: portalView(created), oneTimePassword: created.password, oneTimePasswordExpiresAt: '2026-10-24T08:00:00Z' });
      }
      const single = p.match(/^\/portal-accounts\/([^/]+)$/);
      if (single && method === 'PUT') {
        const target = state.accounts.find((item) => item.id === single[1]);
        if (!target) return problem(404, 'account.not_found');
        if (!portalOf(target)) return problem(409, 'account.not_portal');
        if (!Array.isArray(body?.projectIds)) return problem(400, 'request.invalid', { violations: ['projectIds.required'] });
        const refused = check(hd.customers.find((entry) => entry.id === target.organizationId), body.contactId, body.projectIds);
        if (refused) return refused;
        target.contactId = body.contactId ?? null;
        setProjects(target, body.projectIds ?? []);
        return ok(portalView(target));
      }
    }
    if (p === '/directory/accounts') {
      if (portalOf(account)) return problem(403, 'auth.forbidden');
      return ok(state.accounts.map(({ id, username, displayName, active, kind }) => ({ id, username, displayName, active, kind: kind ?? 'internal' })).sort((a, b) => a.username.localeCompare(b.username)));
    }
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
    if (p === '/search/tickets') return ticketSearch(account, query);
    if (p === '/search/wiki') return wikiSearch(account, query);
    if (p === '/search/requirements') return requirementSearch(account, query);
    if (p === '/search/projects') return projectSearch(account, query);
    if (p === '/search/all') return overviewSearch(account, query);
    if (p.startsWith('/permissions')) return permissionsRoute(account, session, method, p, body, query);
    if (p.startsWith('/jobs')) return jobsRoute(account, method, p, query);
    if (p.startsWith('/helpdesk/inbox')) return inboxRoute(account, method, p, query, headers, body);
    if (p === '/helpdesk/replies' && method === 'GET') {
      // As the backend (TASK-058): replies not yet accepted, of the projects in which the person may reply.
      return ok(hd.replies.filter((reply) => reply.state !== 'sent' && mayReply(account, reply.projectId))
        .map((reply) => ({ id: reply.id, projectId: reply.projectId, number: state.tickets.find((entry) => entry.id === reply.ticketId).number, toAddress: reply.toAddress,
          subject: reply.subject, state: reply.state, errorCode: reply.errorCode, createdAt: reply.createdAt, attemptedAt: reply.attemptedAt })));
    }
    if (p === '/helpdesk/answers' && method === 'GET') {
      // As the backend (TASK-067, TASK-088): mails that joined an existing ticket and nobody marked as seen, newest first with
      // the identifier as tie breaker, at most 100, and only those whose ticket still lives in the project of the mail.
      if (hd.answersDown > 0) {
        hd.answersDown -= 1;
        return problem(503, 'server.unavailable');
      }
      const ticketOf = (message) => state.tickets.find((entry) => entry.id === message.ticketId && entry.projectId === message.projectId);
      return ok(hd.messages.filter((message) => message.joinedAt && !message.seenAt && message.ticketId && mayReply(account, message.projectId))
        .sort((a, b) => b.joinedAt.localeCompare(a.joinedAt) || b.id.localeCompare(a.id))
        .slice(0, 100)
        .filter((message) => ticketOf(message))
        .map((message) => ({ id: message.id, projectId: message.projectId, number: ticketOf(message).number,
          fromAddress: message.fromAddress, fromName: message.fromName, subject: message.subject, receivedAt: message.receivedAt, version: message.version })));
    }
    const seen = p.match(/^\/helpdesk\/answers\/([^/]+)\/seen$/);
    if (seen && method === 'POST') {
      // As the backend: the controller asks for If-Match first; then rights, the ticket in the project of the mail, version, state.
      if (!headers['if-match']) return problem(428, 'helpdesk.precondition_required');
      const message = hd.messages.find((entry) => entry.id === seen[1]);
      if (!message || !may(account, 'ticket.read', message.projectId)) return problem(404, 'helpdesk.message_not_found');
      if (!mayReply(account, message.projectId)) return problem(403, 'auth.forbidden');
      if (message.ticketId && !state.tickets.some((entry) => entry.id === message.ticketId && entry.projectId === message.projectId)) {
        return problem(404, 'helpdesk.message_not_found');
      }
      if (headers['if-match'] !== `"${message.version}"`) return problem(412, 'helpdesk.version_conflict');
      if (!message.joinedAt || message.seenAt) return problem(409, 'helpdesk.answer_not_new');
      Object.assign(message, { seenAt: now(), seenBy: account.id, version: message.version + 1 });
      return ok({ id: message.id, version: message.version }, `"${message.version}"`);
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
    if (p.startsWith('/time')) return timeRoute(account, method, p, query, headers, body);
    if (p.startsWith('/wiki')) return wikiRoute(account, method, p, query, headers, body);
    if (p.startsWith('/requirements')) return requirementRoute(account, method, p, query, headers, body);
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
      if (state.tags.some((tag) => tag.projectId === item.id && tag.mergedInto === null && tag.organizationId === null && tagKey(tag.name) === tagKey(body.name))) return problem(409, 'tag.name_taken');
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
      const shown = [...readable(account, item.id), ...[...HELPDESK_FIELDS, PRIORITY_FIELD].filter((name) => field(account, item.id, name, 'read')), ...(customReadable(account, item.id) ? customFieldsOf(item.id).map((entry) => `custom.${entry.key}`) : []), ...(field(account, item.id, TAG_FIELD, 'read') ? [TAG_FIELD] : []), ...(field(account, item.id, ATTACHMENT_FIELD, 'read') ? [ATTACHMENT_FIELD] : []),
        // TASK-097: links of requirements only for readers of the requirements of the project.
        ...(may(account, 'requirement.read', item.id) ? ['requirements'] : [])];
      return ok(state.history.filter((entry) => entry.ticket === ticket.id)
        .map(({ ticket: _ticket, ...entry }) => ({ ...entry, changes: entry.changes.filter((change) => shown.includes(change.field)) }))
        .filter((entry) => entry.changes.length > 0));
    }
    if (tail.startsWith('/attachments')) {
      if (!field(account, item.id, ATTACHMENT_FIELD, 'read')) return problem(403, 'auth.forbidden');
      const id = tail.split('/')[2];
      const mine = state.attachments.filter((entry) => entry.ticket === ticket.id);
      const current = state.helpdesk.ticketCustomers.get(ticket.id)?.customerId ?? null;
      const view = ({ ticket: _ticket, content: _content, removed: _removed, organizationId, ...entry }) => ({ visibility: 'internal', ...entry,
        customerCurrent: entry.visibility === 'customer' && organizationId !== undefined && organizationId !== null && organizationId === current });
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
        const entry = { id: uuid(), ticket: ticket.id, name: name.split(/[\\/]/).pop(), size: content.length, sha256: 'a'.repeat(64), uploadedAt: now(), uploadedBy: account.id, content, removed: false, visibility: 'internal' };
        state.attachments.push(entry);
        state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id, changes: [{ field: ATTACHMENT_FIELD, from: null, to: described(entry) }] });
        return { status: 201, body: view(entry) };
      }
      if (method === 'PATCH') {
        const found = mine.find((entry) => entry.id === id && !entry.removed);
        if (!found) return problem(404, 'attachment.not_found');
        if (!['internal', 'customer'].includes(body?.visibility)) return problem(400, 'request.invalid', { violations: ['visibility.invalid'] });
        found.visibility = body.visibility;
        // Visible to the customer the ticket has now; switching to the customer again releases a file of a former one (TASK-079).
        found.organizationId = body.visibility === 'customer' ? current : null;
        return ok(view(found));
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
        fields: [{ resourceType: 'ticket', fields: [...[...FIELDS, TAG_FIELD, ATTACHMENT_FIELD].sort(), ...customFieldsOf(projectId).map((entry) => `custom.${entry.key}`)].filter((name) => company || field(account, projectId, name, 'write')) },
          { resourceType: 'requirement', fields: [...REQUIREMENT_FIELDS, ...requirementFieldsOf(projectId).map((entry) => `custom.${entry.key}`)]
            .filter((name) => company || requirementField(account, projectId, name, 'write')) }],
        groups: state.groups.map((group) => ({ id: group.id, name: group.name,
          actions: group.actions.filter((rule) => rule.projectId === projectId).map((rule) => ({ ...rule })),
          fields: group.fields.filter((rule) => rule.projectId === projectId).map((rule) => ({ ...rule })) })),
      });
    }
    const delegated = method === 'PUT' && /^\/permissions\/groups\/[^/]+\/(actions|fields)$/.test(p) && body?.projectId && managesProject(account, body.projectId);
    if (!may(account, 'permissions.manage') && !delegated) return problem(403, 'auth.forbidden');
    const detail = (group) => ({ id: group.id, name: group.name, members: [...group.members], actions: group.actions.map((rule) => ({ ...rule })), fields: group.fields.map((rule) => ({ ...rule })) });
    if (p === '/permissions/catalog') return ok({ companyActions: COMPANY_ACTIONS, projectActions: PROJECT_ACTIONS, fields: [{ resourceType: 'ticket', fields: [...FIELDS, TAG_FIELD, ATTACHMENT_FIELD].sort() },
      { resourceType: 'requirement', fields: REQUIREMENT_FIELDS }] });
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
      const resourceType = body.resourceType ?? 'ticket';
      if (!['ticket', 'requirement'].includes(resourceType)) return problem(400, 'request.invalid', { violations: ['resourceType.unknown'] });
      // Custom fields of requirements (TASK-098) need a project whose current configuration has them.
      const ownField = resourceType === 'requirement' && typeof body.field === 'string' && body.field.startsWith('custom.') && body.projectId
        && requirementFieldsOf(body.projectId).some((entry) => `custom.${entry.key}` === body.field);
      if (resourceType === 'requirement' && !REQUIREMENT_FIELDS.includes(body.field) && !ownField) return problem(400, 'request.invalid', { violations: ['field.unknown'] });
      group.fields = group.fields.filter((rule) => !((rule.resourceType ?? 'ticket') === resourceType && rule.field === body.field && rule.operation === body.operation
        && rule.projectId === body.projectId));
      if (body.effect !== 'remove') group.fields.push({ resourceType, field: body.field, projectId: body.projectId, operation: body.operation, effect: body.effect });
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
  // The next position of an answer of the support of the ticket in the portal conversation (TASK-080, F2).
  const nextPosition = (ticketId) => {
    const position = (hd.positions.get(ticketId) ?? 0) + 1;
    hd.positions.set(ticketId, position);
    return position;
  };
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
      if (body.language !== undefined && !['de', 'en'].includes(body.language)) return problem(400, 'request.invalid', { violations: ['language.invalid'] });
      const contact = { id: uuid(), customerId: body.customerId, name: body.name.trim(), addresses, language: body.language ?? 'de', version: 1, createdAt: now(),
        createdBy: account.id, updatedAt: now(), updatedBy: account.id };
      hd.contacts.push(contact);
      return { status: 201, body: contact, etag: '"1"' };
    }
    match = p.match(/^\/helpdesk\/contacts\/([^/]+)$/);
    if (match && method === 'PUT') {
      const contact = hd.contacts.find((entry) => entry.id === match[1]);
      if (!contact) return problem(404, 'contact.not_found');
      const refused = versioned(headers, contact, 'contact');
      if (refused) return refused;
      if (body.language !== undefined && !['de', 'en'].includes(body.language)) return problem(400, 'request.invalid', { violations: ['language.invalid'] });
      Object.assign(contact, { customerId: body.customerId, name: body.name.trim(), addresses: body.addresses.map((value) => value.trim().toLowerCase()),
        language: body.language ?? contact.language ?? 'de', version: contact.version + 1 });
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
      Object.assign(message, { state: 'ticket', ticketId: ticket.id, processedAt: now(), processedBy: account.id, joinedAt: now(), version: message.version + 1 });
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

  // ------------------------------------------------------------------ time entries (TASK-069)
  // As the backend: time.book for own entries, time.read for everybody's, time.manage for booking and changing for others;
  // tickets only with ticket.read, their title only with the read rule of the title; cancelled entries in no sum.
  const timeAccess = (account, projectId) => {
    if (account.kind === 'portal' || !isMember(account, projectId)) return { book: false, read: false, manage: false };
    return { book: may(account, 'time.book', projectId), read: may(account, 'time.read', projectId), manage: may(account, 'time.manage', projectId) };
  };
  const timeAny = (access) => access.book || access.read || access.manage;
  const timeTicket = (account, entry) => {
    if (entry.ticketId === null) return null;
    const ticket = state.tickets.find((item) => item.id === entry.ticketId);
    if (!ticket || !may(account, 'ticket.read', ticket.projectId)) return null;
    return { number: ticket.number, title: field(account, ticket.projectId, 'title', 'read') ? ticket.values.title : null };
  };
  const timeVisible = (account, entry) => {
    const access = timeAccess(account, entry.projectId);
    return access.read || access.manage || (access.book && entry.personId === account.id);
  };
  const timeView = (account, entry) => {
    const access = timeAccess(account, entry.projectId);
    const own = entry.personId === account.id;
    const { ticketId, revisions, ...rest } = entry;
    const ticket = timeTicket(account, entry);
    return { ...rest, projectKey: state.projects.find((item) => item.id === entry.projectId)?.key ?? '', ticket, ticketHidden: ticketId !== null && ticket === null,
      cancelled: entry.cancelledAt !== null, own, changeable: entry.cancelledAt === null && (access.manage || (own && access.book)) };
  };
  // Every answer about one entry carries its revisions, as GET, POST and PUT of the backend.
  const timeDetail = (account, entry) => ({ entry: timeView(account, entry), revisions: entry.revisions.map(({ ticketId, ...revision }) => {
    const ticket = timeTicket(account, { ticketId });
    return { ...revision, ticket, ticketHidden: ticketId !== null && ticket === null };
  }) });
  const timeCheck = (account, projectId, body, own) => {
    const violations = [];
    if (!body.date) violations.push('date.required');
    else if (body.date > new Date(Date.now() + 86400000).toISOString().slice(0, 10)) violations.push('date.future');
    if (!Number.isInteger(body.minutes) || body.minutes < 1 || body.minutes > 1440) violations.push('minutes.invalid');
    const description = (body.description ?? '').trim();
    if (description === '') violations.push('description.required');
    else if (description.length > 1000) violations.push('description.too_long');
    let ticketId = null;
    if (body.ticketNumber !== null && body.ticketNumber !== undefined) {
      const ticket = state.tickets.find((item) => item.projectId === projectId && item.number === body.ticketNumber);
      if (!ticket || !may(account, 'ticket.read', projectId)) violations.push('ticket.unknown');
      else ticketId = ticket.id;
    }
    if (own === false && !(body.reason ?? '').trim()) violations.push('reason.required');
    return { violations, values: { ticketId, date: body.date, minutes: body.minutes, description } };
  };

  // ------------------------------------------------------------------ wiki (TASK-093)
  // As the backend: readers see published pages only, `wiki.edit` and `wiki.publish` see drafts and the history, the server
  // renders the HTML from the checked document and marks links to pages the caller may not read.
  const wiki = state.wiki;
  const wikiAccess = (account, projectId) => ({ read: may(account, 'wiki.read', projectId), edit: may(account, 'wiki.edit', projectId),
    publish: may(account, 'wiki.publish', projectId) });
  const wikiAny = (access) => access.read || access.edit || access.publish;
  const wikiDrafts = (access) => access.edit || access.publish;
  // TASK-094: the page object itself carries the version of its source language, `page.variants` the other languages;
  // revisions without a language belong to the source language.
  const WIKI_LANGUAGES = ['de', 'en'];
  const wikiRevision = (page, number, language = page.language) => wiki.revisions.find((item) => item.pageId === page.id && item.revision === number
    && (item.language ?? page.language) === language);
  const wikiEvent = (page, kind, revision, account, language = page.language) => wiki.events.push({ pageId: page.id, kind, language, revision, actorId: account.id, occurredAt: now() });
  const wikiVariants = (page) => ({ ...(page.variants ?? {}), [page.language]: page });
  const wikiVariant = (page, language) => wikiVariants(page)[language] ?? null;
  const wikiPublished = (page) => Object.values(wikiVariants(page)).some((item) => item.publishedRevision !== null);
  // As the server: with drafts the requested version or else the source, for readers the first published version in the
  // order requested, source, others.
  const wikiShown = (page, requested, drafts) => {
    const versions = wikiVariants(page);
    if (drafts) return versions[requested ?? page.language] ? (requested ?? page.language) : page.language;
    const order = [...new Set([requested ?? page.language, page.language, ...WIKI_LANGUAGES])];
    return order.find((code) => versions[code] && versions[code].publishedRevision !== null) ?? null;
  };
  const wikiTitleIn = (page, requested, drafts) => {
    const code = wikiShown(page, requested, drafts);
    if (code === null) return null;
    const version = wikiVariant(page, code);
    return wikiRevision(page, drafts ? version.draftRevision : version.publishedRevision, code).title;
  };
  const WIKI_BLOCKS = new Set(['paragraph', 'heading', 'blockquote', 'bulletList', 'orderedList', 'codeBlock', 'horizontalRule', 'table', 'image']);
  const WIKI_KNOWN = new Set([...WIKI_BLOCKS, 'listItem', 'tableRow', 'tableHeader', 'tableCell', 'text', 'hardBreak']);
  const WIKI_TAGS = { paragraph: 'p', blockquote: 'blockquote', bulletList: 'ul', listItem: 'li', tableRow: 'tr', tableHeader: 'th', tableCell: 'td' };
  const WIKI_IMAGE = /^\/api\/v1\/wiki\/images\/([0-9a-f-]{36})$/;
  function wikiCheck(content, pageId, projectId) {
    const violations = [];
    const images = [];
    const walk = (node) => {
      if (!node || typeof node !== 'object' || !WIKI_KNOWN.has(node.type)) return violations.push('content.unsupported');
      if (node.type === 'image') {
        const match = WIKI_IMAGE.exec(node.attrs?.src ?? '');
        if (!match) return violations.push('image.external');
        if (!wiki.images.some((image) => image.id === match[1] && image.pageId === pageId)) violations.push('image.unknown');
        images.push(match[1]);
      }
      for (const mark of node.marks ?? []) {
        if (!['bold', 'italic', 'code', 'link'].includes(mark.type)) violations.push('content.unsupported');
        if (mark.type === 'link') {
          const href = mark.attrs?.href ?? '';
          const target = /^wiki:([0-9a-f-]{36})$/.exec(href);
          if (target) {
            if (!wiki.pages.some((item) => item.id === target[1] && item.projectId === projectId)) violations.push('link.unknown_page');
          } else if (/[\u0000-\u001f\u007f-\u009f]/.test(href) || !/^(https?:\/\/[^\s<>"]+|mailto:[^\s<>"]+)$/i.test(href.trim())) violations.push('link.invalid');
        }
      }
      (node.content ?? []).forEach(walk);
    };
    if (!content || content.type !== 'doc' || !Array.isArray(content.content)) violations.push('content.invalid');
    else content.content.forEach((block) => (WIKI_BLOCKS.has(block?.type) ? walk(block) : violations.push('content.unsupported')));
    if (JSON.stringify(content ?? {}).length > 1_000_000) violations.push('content.too_large');
    if (images.length > 50) violations.push('content.too_many_images');
    return [...new Set(violations)];
  }
  function wikiHtml(content, account, projectId) {
    const access = wikiAccess(account, projectId);
    const target = (id) => {
      const page = wiki.pages.find((item) => item.id === id && item.projectId === projectId && !item.archived);
      return page && (wikiDrafts(access) || wikiPublished(page)) ? `#/wiki/${projectId}/${page.id}` : null;
    };
    const render = (node) => {
      const inner = () => (node.content ?? []).map(render).join('');
      switch (node.type) {
        case 'text': {
          let text = escapeHtml(node.text ?? '');
          for (const mark of node.marks ?? []) {
            if (mark.type === 'bold') text = `<strong>${text}</strong>`;
            else if (mark.type === 'italic') text = `<em>${text}</em>`;
            else if (mark.type === 'code') text = `<code>${text}</code>`;
          }
          const link = (node.marks ?? []).find((mark) => mark.type === 'link');
          if (!link) return text;
          const wikiLink = /^wiki:(.+)$/.exec(link.attrs.href);
          if (wikiLink) {
            const address = target(wikiLink[1]);
            return address === null ? '<span class="wiki-unavailable" data-wiki-unavailable="true"></span>'
              : `<a href="${address}" data-wiki-page="${wikiLink[1]}">${text}</a>`;
          }
          return `<a href="${escapeHtml(link.attrs.href)}" rel="noopener noreferrer nofollow" target="_blank">${text}</a>`;
        }
        case 'heading': return `<h${node.attrs?.level ?? 1}>${inner()}</h${node.attrs?.level ?? 1}>`;
        case 'orderedList': {
          const start = node.attrs?.start ?? 1;
          return start === 1 ? `<ol>${inner()}</ol>` : `<ol start="${start}">${inner()}</ol>`;
        }
        case 'codeBlock': {
          const language = node.attrs?.language;
          return typeof language === 'string' && language !== '' ? `<pre><code class="language-${escapeHtml(language)}">${inner()}</code></pre>` : `<pre><code>${inner()}</code></pre>`;
        }
        case 'tableCell':
        case 'tableHeader': {
          const tag = node.type === 'tableHeader' ? 'th' : 'td';
          const colspan = node.attrs?.colspan ?? 1;
          const rowspan = node.attrs?.rowspan ?? 1;
          return `<${tag}${colspan > 1 ? ` colspan="${colspan}"` : ''}${rowspan > 1 ? ` rowspan="${rowspan}"` : ''}>${inner()}</${tag}>`;
        }
        case 'horizontalRule': return '<hr>';
        case 'hardBreak': return '<br>';
        case 'image': return `<img src="${escapeHtml(node.attrs.src)}" alt="${escapeHtml(node.attrs.alt ?? '')}" loading="lazy">`;
        case 'table': return `<table><tbody>${inner()}</tbody></table>`;
        default: return `<${WIKI_TAGS[node.type]}>${inner()}</${WIKI_TAGS[node.type]}>`;
      }
    };
    return (content.content ?? []).map(render).join('');
  }
  const wikiVisible = (account, page) => {
    const access = wikiAccess(account, page.projectId);
    if (page.archived) return access.publish;
    return wikiDrafts(access) || (access.read && wikiPublished(page));
  };
  const wikiPageView = (account, page, requested = null) => {
    const access = wikiAccess(account, page.projectId);
    const drafts = wikiDrafts(access);
    const code = wikiShown(page, requested, drafts);
    const variant = wikiVariant(page, code);
    const published = variant.publishedRevision === null ? null : wikiRevision(page, variant.publishedRevision, code);
    const draft = drafts ? wikiRevision(page, variant.draftRevision, code) : null;
    const versions = wikiVariants(page);
    const languages = WIKI_LANGUAGES.filter((language) => versions[language] && (drafts || versions[language].publishedRevision !== null))
      .map((language) => ({ language, published: versions[language].publishedRevision !== null,
        changed: drafts && versions[language].publishedRevision !== versions[language].draftRevision }));
    // As the server (F1 of the review): readers learn of the source and the translation only while the source is published.
    const sourceVisible = drafts || page.publishedRevision !== null;
    let translation = null;
    if (code !== page.language && sourceVisible) {
      const sourcePublished = page.publishedRevision;
      const translated = (draft ?? published)?.sourceRevision ?? null;
      const outdated = translated !== null && sourcePublished !== null && translated < sourcePublished;
      translation = drafts ? { sourceRevision: translated, sourcePublishedRevision: sourcePublished, outdated }
        : { sourceRevision: null, sourcePublishedRevision: null, outdated };
    }
    const path = [];
    let parent = page.parentId;
    while (parent !== null) {
      const above = wiki.pages.find((item) => item.id === parent);
      if (!above) break;
      const title = wikiTitleIn(above, requested, drafts);
      if (title !== null) path.unshift({ id: above.id, title });
      else if (drafts) break;
      parent = above.parentId;
    }
    return {
      id: page.id, projectId: page.projectId, parentId: page.archived ? page.parentId : path.at(-1)?.id ?? null, language: code, sourceLanguage: sourceVisible ? page.language : null,
      fallback: requested === null ? sourceVisible && code !== page.language : code !== requested, languages, translation, archived: page.archived, version: page.version, access, path,
      published: published && { revision: published.revision, title: published.title, html: wikiHtml(published.content, account, page.projectId),
        publishedAt: variant.publishedAt, publishedBy: variant.publishedBy },
      draft: draft && { revision: draft.revision, title: draft.title, content: draft.content, html: wikiHtml(draft.content, account, page.projectId),
        savedAt: draft.savedAt, savedBy: draft.savedBy, changed: variant.publishedRevision !== variant.draftRevision },
      createdAt: page.createdAt, createdBy: page.createdBy,
    };
  };
  // As the controller: the ETag of the draft of the shown language only for people who see drafts.
  const wikiAnswer = (account, page, requested = null) => {
    const view = wikiPageView(account, page, requested);
    return view.draft ? ok(view, `"${view.draft.revision}"`) : ok(view);
  };
  const wikiTitle = (title, violations) => {
    const clean = (title ?? '').trim();
    if (clean === '') violations.push('title.required');
    else if (clean.length > 200) violations.push('title.too_long');
    return clean;
  };
  const wikiDepth = (id) => {
    let depth = 0;
    for (let current = id; current !== null; current = wiki.pages.find((item) => item.id === current)?.parentId ?? null) depth++;
    return depth;
  };
  const wikiHeight = (id) => 1 + Math.max(0, ...wiki.pages.filter((item) => item.parentId === id && !item.archived).map((item) => wikiHeight(item.id)));
  const wikiIfMatch = (headers, page, variant = page, language = page.language) => {
    if (!headers['if-match']) return problem(428, 'wiki.precondition_required');
    if (state.wikiConflict !== null) {
      // Someone else saved first: their revision becomes the draft.
      const theirs = state.wikiConflict;
      state.wikiConflict = null;
      const revision = { pageId: page.id, language, revision: variant.draftRevision + 1, title: theirs.title, content: theirs.content, savedAt: now(), savedBy: theirs.by,
        basedOn: variant.draftRevision, restoredFrom: null, sourceRevision: wikiRevision(page, variant.draftRevision, language)?.sourceRevision ?? null };
      wiki.revisions.push(revision);
      variant.draftRevision = revision.revision;
    }
    if (headers['if-match'] !== `"${variant.draftRevision}"`) return problem(412, 'wiki.version_conflict');
    return null;
  };
  // ------------------------------------------------------------------ requirements (TASK-096)
  // As the backend: `requirement.read` sees a requirement, its fields after the read rules of the resource type requirement;
  // `requirement.edit` changes what it may write, `requirement.approve` approves exactly the shown revision. A content change
  // after an approval opens the next revision as draft. Every change needs the version as If-Match.
  const req = state.requirements;
  const REQ_CONTENT = ['title', 'description', 'criteria', 'priority'];
  // Custom fields of requirements (TASK-098): drafted and published with the project configuration, each with its own read
  // and write rule `custom.<key>` of the resource type requirement. Values belong to the revision.
  const requirementFieldDefs = (projectId, access) => requirementFieldsOf(projectId)
    .filter((entry) => access.readableFields.includes(`custom.${entry.key}`))
    .map((entry) => ({ key: entry.key, name: entry.name, type: entry.type, required: entry.required === true, active: entry.active !== false,
      options: (entry.options ?? []).map((option) => ({ key: option.key, name: option.name, active: option.active !== false })) }));
  const requirementAccess = (account, projectId) => {
    const read = may(account, 'requirement.read', projectId);
    const readableFields = read ? [...REQUIREMENT_FIELDS, ...requirementFieldsOf(projectId).map((entry) => `custom.${entry.key}`)]
      .filter((name) => requirementField(account, projectId, name, 'read')) : [];
    const edit = read && may(account, 'requirement.edit', projectId);
    return { read, edit, approve: read && may(account, 'requirement.approve', projectId), readableFields,
      writableFields: edit ? readableFields.filter((name) => requirementField(account, projectId, name, 'write')) : [] };
  };
  const requirementRevision = (item, number) => req.revisions.find((entry) => entry.requirementId === item.id && entry.revision === number);
  const requirementEvent = (item, kind, account, extra = {}) => req.events.push({ requirementId: item.id, revision: item.revision, kind, field: null, criterion: null,
    from: null, to: null, reason: null, actorId: account.id, occurredAt: now(), ...extra });
  const requirementContent = (revision, access) => {
    const shown = (name) => access.readableFields.includes(name);
    return { title: shown('title') ? revision.title : null, description: shown('description') ? (revision.description ?? { type: 'doc', content: [] }) : null,
      descriptionHtml: shown('description') ? (revision.description ? wikiHtml(revision.description, null, null) : '') : null,
      criteria: shown('criteria') ? revision.criteria.map((criterion) => ({ ...criterion })) : null, priority: shown('priority') ? revision.priority : null,
      customFields: Object.fromEntries(Object.entries(revision.custom ?? {}).filter(([key]) => shown(`custom.${key}`)).sort(([a], [b]) => a.localeCompare(b))) };
  };
  const requirementView = (account, item) => {
    const access = requirementAccess(account, item.projectId);
    const current = requirementRevision(item, item.revision);
    const approved = item.approvedRevision === null ? null : requirementRevision(item, item.approvedRevision);
    const editors = [...new Set(req.events.filter((event) => event.requirementId === item.id && event.revision === item.revision
      && ['created', 'changed', 'criterion_added', 'criterion_changed', 'criterion_removed', 'criteria_moved'].includes(event.kind)
      && event.field !== 'owner' && (event.field === null
        ? REQ_CONTENT.some((name) => access.readableFields.includes(name)) : access.readableFields.includes(event.field))).map((event) => event.actorId))];
    const key = state.projects.find((entry) => entry.id === item.projectId)?.key ?? '';
    return { id: item.id, projectId: item.projectId, projectKey: key, number: item.number, key: `ANF-${item.number}`, status: item.status, revision: item.revision,
      approvedRevision: item.approvedRevision, approval: approved ? { revision: approved.revision, approvedAt: approved.approvedAt, approvedBy: approved.approvedBy } : null,
      content: requirementContent(current, access), ownerId: access.readableFields.includes('owner') ? item.ownerId : null, access, editors, version: item.version,
      createdAt: item.createdAt, createdBy: item.createdBy, updatedAt: item.updatedAt, updatedBy: item.updatedBy,
      customFields: requirementFieldDefs(item.projectId, access) };
  };
  const requirementAnswer = (account, item, status = 200) => ({ status, body: requirementView(account, item), etag: `"${item.version}"` });
  function addRequirement(by, title, { projectId = project.id, criteria = [], priority = 'normal', description = null, status = 'draft', ownerId = null, approvedBy = null, custom = {} } = {}) {
    req.counters.set(projectId, (req.counters.get(projectId) ?? 0) + 1);
    const item = { id: uuid(), projectId, number: req.counters.get(projectId), status: 'draft', ownerId, revision: 1, approvedRevision: null,
      nextCriterion: criteria.length + 1, version: 1, createdAt: now(), createdBy: by.id, updatedAt: now(), updatedBy: by.id };
    req.items.push(item);
    req.revisions.push({ requirementId: item.id, revision: 1, title, description, priority, criteria: criteria.map((text, index) => ({ id: index + 1, text })), custom: { ...custom },
      openedAt: now(), openedBy: by.id, savedAt: now(), savedBy: by.id, approvedAt: null, approvedBy: null });
    requirementEvent(item, 'created', by);
    if (status === 'review' || status === 'approved') {
      item.status = 'review';
      requirementEvent(item, 'submitted', by, { from: 'draft', to: 'review' });
    }
    if (status === 'approved') {
      const approver = approvedBy ?? by;
      Object.assign(requirementRevision(item, 1), { approvedAt: now(), approvedBy: approver.id });
      Object.assign(item, { status: 'approved', approvedRevision: 1 });
      requirementEvent(item, 'approved', approver, { from: 'review', to: 'approved' });
    }
    return item;
  }
  // The check of WikiDocument.check and RichText.check (TASK-096, DEC-126; S1 and S2 of the review of TASK-099), written out
  // node for node: the first violation of the grammar ends the check like the exception on the server, unknown attributes,
  // marks of nodes that carry none, a second mark of the same kind and the content of a leaf are dropped, and only then are
  // the images and links to wiki pages that remain refused. The codes come without the prefix description.
  const RT_BLOCKS = new Set(['paragraph', 'heading', 'blockquote', 'bulletList', 'orderedList', 'codeBlock', 'horizontalRule', 'table', 'image']);
  const RT_MARKS = new Set(['bold', 'italic', 'code', 'link']);
  const RT_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
  const RT_IMAGE_PATH = '/api/v1/wiki/images/';
  // Java Pattern without UNICODE_CHARACTER_CLASS: \s is only [ \t\n\x0B\f\r], (?i) folds ASCII only.
  const RT_EXTERNAL = /^(?:[Hh][Tt][Tt][Pp][Ss]?:\/\/[^ \t\n\u000b\f\r<>"]+|[Mm][Aa][Ii][Ll][Tt][Oo]:[^ \t\n\u000b\f\r<>"]+)$/;
  const RT_LANGUAGE = /^[a-z0-9+#-]{1,20}$/;
  // Character.isWhitespace of Java, which String.strip and String.isBlank use: the space separators without the no break
  // spaces U+00A0, U+2007 and U+202F, the line and paragraph separators and the controls \t to \r and U+001C to U+001F.
  const rtWhitespace = (code) => (code >= 0x09 && code <= 0x0d) || (code >= 0x1c && code <= 0x20) || code === 0x1680
    || (code >= 0x2000 && code <= 0x2006) || (code >= 0x2008 && code <= 0x200a) || code === 0x2028 || code === 0x2029
    || code === 0x205f || code === 0x3000;
  const javaStrip = (value) => {
    const points = [...value];
    let start = 0;
    let end = points.length;
    while (start < end && rtWhitespace(points[start].codePointAt(0))) start++;
    while (end > start && rtWhitespace(points[end - 1].codePointAt(0))) end--;
    return points.slice(start, end).join('');
  };
  class RichTextInvalid extends Error {}
  function richTextCheck(content) {
    const fail = (code) => { throw new RichTextInvalid(code); };
    const isObject = (node) => node !== null && typeof node === 'object' && !Array.isArray(node);
    const text = (node) => (typeof node === 'string' ? node : null);
    const integer = (attrs, name, fallback) => {
      const value = isObject(attrs) ? attrs[name] : undefined;
      if (value === undefined || value === null) return fallback;
      if (typeof value !== 'number' || !Number.isInteger(value) || value < -2147483648 || value > 2147483647) fail('invalid');
      return value;
    };
    const images = [];
    const pageLinks = [];
    const href = (value) => {
      // eslint-disable-next-line no-control-regex
      if (value === null || javaStrip(value) === '' || value.length > 2000 || /[\u0000-\u001f\u007f-\u009f]/.test(value)) fail('link_invalid');
      const stripped = javaStrip(value);
      if (stripped.startsWith('wiki:')) {
        if (!RT_UUID.test(stripped.slice(5))) fail('link_invalid');
        pageLinks.push(stripped.slice(5));
        return stripped;
      }
      if (!RT_EXTERNAL.test(stripped)) fail('link_invalid');
      return stripped;
    };
    const inline = (list, out, marks) => {
      if (list === undefined || list === null) return;
      if (!Array.isArray(list)) fail('invalid');
      const content = [];
      for (const child of list) {
        const type = isObject(child) ? text(child.type) : null;
        if (type === null) fail('invalid');
        if (!['text', 'hardBreak'].includes(type) || (type === 'hardBreak' && !marks)) fail('unsupported');
        if (type === 'hardBreak') {
          content.push({ type: 'hardBreak' });
          continue;
        }
        const value = text(child.text);
        if (value === null || value === '') fail('invalid');
        const node = { type: 'text', text: value };
        const given = child.marks;
        if (given !== undefined && given !== null) {
          if (!marks || !Array.isArray(given)) fail(marks ? 'invalid' : 'unsupported');
          const kept = [];
          const seen = new Set();
          for (const mark of given) {
            const name = isObject(mark) ? text(mark.type) : null;
            if (name === null) fail('invalid');
            if (!RT_MARKS.has(name)) fail('unsupported');
            if (seen.has(name)) continue;
            seen.add(name);
            kept.push(name === 'link' ? { type: name, attrs: { href: href(isObject(mark.attrs) ? text(mark.attrs.href) : null) } } : { type: name });
          }
          if (kept.length > 0) node.marks = kept;
        }
        content.push(node);
      }
      if (content.length > 0) out.content = content;
    };
    const blocks = (list, out, depth, allowed) => {
      if (list === undefined || list === null) return;
      if (!Array.isArray(list)) fail('invalid');
      if (depth > 30) fail('too_deep');
      for (const child of list) {
        const type = isObject(child) ? text(child.type) : null;
        if (type === null) fail('invalid');
        if (!allowed.has(type)) fail('unsupported');
        out.push(block(type, child, depth));
      }
    };
    const nonEmpty = (node, out, depth, allowed) => {
      const content = [];
      blocks(node.content, content, depth + 1, allowed);
      if (content.length === 0) fail('invalid');
      out.content = content;
    };
    const block = (type, node, depth) => {
      const out = { type };
      const attrs = node.attrs;
      switch (type) {
        case 'paragraph': inline(node.content, out, true); break;
        case 'heading': {
          const level = integer(attrs, 'level', 1);
          if (level < 1 || level > 3) fail('invalid');
          out.attrs = { level };
          inline(node.content, out, true);
          break;
        }
        case 'blockquote': nonEmpty(node, out, depth, RT_BLOCKS); break;
        case 'bulletList': nonEmpty(node, out, depth, new Set(['listItem'])); break;
        case 'orderedList': {
          const start = integer(attrs, 'start', 1);
          if (start < 1 || start > 1_000_000) fail('invalid');
          out.attrs = { start };
          nonEmpty(node, out, depth, new Set(['listItem']));
          break;
        }
        case 'listItem': nonEmpty(node, out, depth, RT_BLOCKS); break;
        case 'codeBlock': {
          const language = isObject(attrs) ? text(attrs.language) : null;
          out.attrs = { language: language !== null && RT_LANGUAGE.test(language) ? language : null };
          inline(node.content, out, false);
          break;
        }
        case 'horizontalRule': break;
        case 'table': nonEmpty(node, out, depth, new Set(['tableRow'])); break;
        case 'tableRow': nonEmpty(node, out, depth, new Set(['tableHeader', 'tableCell'])); break;
        case 'tableHeader':
        case 'tableCell': {
          const colspan = integer(attrs, 'colspan', 1);
          const rowspan = integer(attrs, 'rowspan', 1);
          if (colspan < 1 || colspan > 50 || rowspan < 1 || rowspan > 500) fail('invalid');
          out.attrs = { colspan, rowspan, colwidth: null };
          const widths = isObject(attrs) ? attrs.colwidth : undefined;
          if (Array.isArray(widths) && widths.length === colspan) {
            for (const width of widths) if (!Number.isInteger(width) || width < 1 || width > 5000) fail('invalid');
            out.attrs.colwidth = [...widths];
          }
          nonEmpty(node, out, depth, RT_BLOCKS);
          break;
        }
        case 'image': {
          const src = isObject(attrs) ? text(attrs.src) : null;
          const id = src !== null && src.startsWith(RT_IMAGE_PATH) && RT_UUID.test(src.slice(RT_IMAGE_PATH.length)) ? src.slice(RT_IMAGE_PATH.length) : null;
          if (id === null) fail('image_external');
          const alt = attrs.alt === undefined || attrs.alt === null ? '' : text(attrs.alt);
          if (alt === null || alt.length > 500) fail('invalid');
          out.attrs = { src: RT_IMAGE_PATH + id, alt: javaStrip(alt) };
          images.push(id);
          break;
        }
        default: fail('unsupported');
      }
      return out;
    };
    try {
      if (!isObject(content) || text(content.type) !== 'doc') fail('invalid');
      const document = { type: 'doc', content: [] };
      blocks(content.content, document.content, 1, RT_BLOCKS);
      if (document.content.length === 0) document.content.push({ type: 'paragraph' });
      if (Buffer.byteLength(JSON.stringify(document), 'utf8') > 1024 * 1024) fail('too_large');
      if (images.length > 50) fail('too_many_images');
      const violations = [];
      if (images.length > 0) violations.push('image_not_allowed');
      if (pageLinks.length > 0) violations.push('page_link_not_allowed');
      const blank = document.content.every((node) => node.type === 'paragraph' && node.content === undefined);
      return { violations, document, blank };
    } catch (error) {
      if (error instanceof RichTextInvalid) return { violations: [error.message], document: null, blank: false };
      throw error;
    }
  }
  // The canonical value of a custom field of requirements as ProjectConfiguration.canonical makes it, or undefined when the
  // value does not fit the field (TASK-100, F4): text stripped like Java, at most 200 or 20000 code points and without NUL, a
  // whole number of at most 2^53 - 1 in either direction, an existing day in the form yyyy-mm-dd, a UUID in lower case.
  function requirementCanonical(def, value) {
    switch (def.type) {
      case 'text':
      case 'longtext': {
        if (typeof value !== 'string') return undefined;
        const stripped = javaStrip(value);
        const max = def.type === 'text' ? 200 : 20000;
        return stripped === '' || [...stripped].length > max || stripped.includes('\u0000') ? undefined : stripped;
      }
      case 'integer':
        return Number.isInteger(value) && Math.abs(value) <= Number.MAX_SAFE_INTEGER ? value : undefined;
      case 'date': {
        if (typeof value !== 'string') return undefined;
        const day = javaStrip(value);
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
        if (!match) return undefined;
        const date = new Date(0);
        date.setUTCFullYear(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
        return date.getUTCFullYear() === Number(match[1]) && date.getUTCMonth() === Number(match[2]) - 1 && date.getUTCDate() === Number(match[3]) ? day : undefined;
      }
      case 'boolean':
        return typeof value === 'boolean' ? value : undefined;
      case 'select':
        return typeof value === 'string' && (def.options ?? []).some((option) => option.key === value) ? value : undefined;
      case 'user': {
        if (typeof value !== 'string') return undefined;
        const id = javaStrip(value).toLowerCase();
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id) ? id : undefined;
      }
      default:
        return undefined;
    }
  }
  // Checks like the server (H2 of the TASK-096 review): fixed criterion numbers, owners from the active internal members, the
  // description codes with the prefix description. and only http, https and mailto as link targets; line breaks become LF.
  function requirementChanges(body, access, violations, context) {
    const known = ['title', 'description', 'priority', 'ownerId', 'criteria', 'customFields'];
    for (const name of Object.keys(body ?? {})) if (!known.includes(name)) violations.push(`${name}.unknown`);
    if (body.title !== undefined && (typeof body.title !== 'string' || body.title.trim() === '')) violations.push('title.required');
    else if (body.title !== undefined && body.title.length > 200) violations.push('title.too_long');
    if (body.priority !== undefined && !PRIORITIES.includes(body.priority)) violations.push('priority.invalid');
    if (body.criteria !== undefined) {
      if (!Array.isArray(body.criteria)) violations.push('criteria.invalid');
      else {
        if (body.criteria.length > 50) violations.push('criteria.too_many');
        const seen = new Set();
        for (const criterion of body.criteria) {
          if (criterion?.id != null && (!context.known.has(criterion.id) || seen.has(criterion.id))) violations.push('criteria.unknown');
          if (criterion?.id != null) seen.add(criterion.id);
          if (typeof criterion?.text === 'string') criterion.text = criterion.text.replace(/\r\n?/g, '\n').trim();
          if (typeof criterion?.text !== 'string' || criterion.text === '') violations.push('criteria.text_required');
          else if ([...criterion.text].length > 2000) violations.push('criteria.text_too_long');
          // eslint-disable-next-line no-control-regex
          else if (/[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/.test(criterion.text)) violations.push('criteria.text_invalid');
        }
      }
    }
    if (body.description !== undefined && body.description !== null) {
      // The document is checked and normalized like WikiDocument.check and RichText.check on the server (N1 of the follow up
      // review of TASK-096, S1 and S2 of the review of TASK-099): first the grammar of the wiki editor with its normalization,
      // then images and wiki links in what is left. What the server stores is stored here too.
      const checked = richTextCheck(body.description);
      if (checked.violations.length > 0) violations.push(...checked.violations.map((code) => `description.${code}`));
      else body.description = checked.blank ? null : checked.document;
    }
    if (body.ownerId !== undefined && body.ownerId !== null) {
      const member = (state.members.get(context.projectId) ?? []).some((entry) => entry.accountId === body.ownerId)
        && state.accounts.some((entry) => entry.id === body.ownerId && entry.active && entry.kind !== 'portal');
      if (!member) violations.push('owner.not_member');
    }
    // Custom fields (TASK-098) with the rules of ticket fields: the kind of each value, a new value only for an active field and
    // option, a person from the active internal members; a value the revision already holds stays allowed. Since TASK-100 (F4 of
    // the review of TASK-098) the values are checked and made canonical like RequirementController.customFields and
    // ProjectConfiguration.canonical, which contracts/cases/requirement-custom-values.json holds both to.
    const custom = body.customFields === undefined ? {} : body.customFields;
    const customObject = typeof custom === 'object' && custom !== null && !Array.isArray(custom);
    if (body.customFields !== undefined && (!customObject || Object.keys(custom).length > 30)) violations.push('customFields.invalid');
    const defs = requirementFieldsOf(context.projectId);
    const canonical = {};
    for (const [key, value] of Object.entries(customObject && Object.keys(custom).length <= 30 ? custom : {})) {
      const def = /^[a-z][a-z0-9_]{0,39}$/.test(key) ? defs.find((entry) => entry.key === key) : undefined;
      if (!def) {
        violations.push('customFields.unknown');
        continue;
      }
      if (value === null) {
        canonical[key] = null;
        continue;
      }
      const shaped = requirementCanonical(def, value);
      if (shaped === undefined) {
        violations.push(`custom.${key}.invalid`);
        continue;
      }
      canonical[key] = shaped;
      if (shaped === (context.current ?? {})[key]) continue;
      if (def.active === false) violations.push(`custom.${key}.inactive`);
      else if (def.type === 'select' && (def.options ?? []).find((option) => option.key === shaped)?.active === false) violations.push(`custom.${key}.option_inactive`);
      else if (def.type === 'user' && !((state.members.get(context.projectId) ?? []).some((entry) => entry.accountId === shaped)
        && state.accounts.some((entry) => entry.id === shaped && entry.active && entry.kind !== 'portal'))) violations.push(`custom.${key}.not_member`);
    }
    if (customObject) body.customFields = canonical;
    const fields = known.filter((name) => body[name] !== undefined && name !== 'customFields').map((name) => (name === 'ownerId' ? 'owner' : name));
    return [...fields, ...Object.keys(typeof custom === 'object' && custom !== null ? custom : {}).map((key) => `custom.${key}`)]
      .filter((name) => !access.writableFields.includes(name));
  }
  // Links (TASK-097) as the server: tickets with ticket.read and their title and status after the field rules, a newer revision
  // named until an explicit switch, done after the category of the status; pages with the published title for wiki readers.
  const ticketReadable = (account, projectId) => may(account, 'ticket.read', projectId);
  const requirementLinks = (account, item, language) => {
    const ticketsReadable = ticketReadable(account, item.projectId);
    const shown = readable(account, item.projectId);
    const statuses = configurationOf(item.projectId).statuses;
    const tickets = !ticketsReadable ? [] : req.ticketLinks.filter((link) => link.requirementId === item.id).map((link) => {
      const ticket = state.tickets.find((entry) => entry.id === link.ticketId);
      const status = shown.includes('status') ? ticket.values.status : null;
      const known = status === null ? null : statuses.find((entry) => entry.key === status) ?? null;
      return { ticketId: ticket.id, number: ticket.number, key: ticket.key, title: shown.includes('title') ? ticket.values.title : null, status,
        statusName: known?.name ?? null, done: known?.category === 'done', revision: link.revision, newerRevision: link.revision < item.revision ? item.revision : null,
        linkedAt: link.linkedAt, linkedBy: link.linkedBy };
    }).sort((a, b) => a.number - b.number);
    const wikiReader = may(account, 'wiki.read', item.projectId);
    const pageTitle = (pageId) => {
      const page = wiki.pages.find((entry) => entry.id === pageId && entry.projectId === item.projectId);
      return !wikiReader || !page || page.archived ? null : wikiTitleIn(page, language, false);
    };
    const pages = req.pageLinks.filter((link) => link.requirementId === item.id).map((link) => {
      const title = pageTitle(link.pageId);
      return { pageId: link.pageId, title, available: title !== null, linkedAt: link.linkedAt, linkedBy: link.linkedBy };
    });
    const edit = item.status !== 'discarded' && requirementAccess(account, item.projectId).edit;
    return { requirementId: item.id, revision: item.revision, edit, ticketsReadable, tickets,
      summary: { tickets: tickets.length, done: tickets.filter((entry) => entry.done).length }, pages, pageTitle };
  };
  const linksAnswer = (account, item, language) => {
    const { pageTitle: _pageTitle, ...view } = requirementLinks(account, item, language);
    return ok(view);
  };
  const ticketRequirementChange = (ticket, account, from, to) => state.history.push({ ticket: ticket.id, version: ticket.version, occurredAt: now(), actor: account.id,
    changes: [{ field: 'requirements', from, to }] });
  function requirementLinkRoute(account, item, method, rest, query, body) {
    const language = query.get('language');
    if (language !== null && !WIKI_LANGUAGES.includes(language)) return problem(400, 'request.invalid', { violations: ['language.invalid'] });
    if (rest === '/links' && method === 'GET') return linksAnswer(account, item, language);
    const access = requirementAccess(account, item.projectId);
    if (!access.edit) return problem(403, 'auth.forbidden');
    const ticketPath = rest.match(/^\/tickets(?:\/(\d+)(\/switch)?)?$/);
    const pagePath = rest.match(/^\/pages(?:\/([^/]+))?$/);
    const linking = method === 'POST';
    if (ticketPath && !ticketReadable(account, item.projectId)) return problem(403, 'auth.forbidden');
    if (linking && item.status === 'discarded') return problem(409, 'requirement.discarded');
    const label = (revision) => `ANF-${item.number}:${revision}`;
    if (ticketPath && !ticketPath[1] && method === 'POST') {
      if (!Number.isInteger(body?.number) || body.number < 1) return problem(400, 'request.invalid', { violations: ['number.invalid'] });
      const ticket = state.tickets.find((entry) => entry.projectId === item.projectId && entry.number === body.number);
      if (!ticket) return problem(400, 'request.invalid', { violations: ['ticket.unknown'] });
      if (req.ticketLinks.some((link) => link.requirementId === item.id && link.ticketId === ticket.id)) return problem(409, 'requirement.already_linked');
      if (req.ticketLinks.filter((link) => link.requirementId === item.id).length >= 200) return problem(409, 'requirement.links_too_many');
      req.ticketLinks.push({ requirementId: item.id, ticketId: ticket.id, revision: item.revision, linkedAt: now(), linkedBy: account.id });
      requirementEvent(item, 'ticket_linked', account, { field: 'tickets', to: `${ticket.number}:${item.revision}` });
      ticketRequirementChange(ticket, account, null, label(item.revision));
      return linksAnswer(account, item, language);
    }
    if (ticketPath && ticketPath[1]) {
      const ticket = state.tickets.find((entry) => entry.projectId === item.projectId && entry.number === Number(ticketPath[1]));
      const link = ticket && req.ticketLinks.find((entry) => entry.requirementId === item.id && entry.ticketId === ticket.id);
      if (!link) return problem(404, 'requirement.link_not_found');
      if (ticketPath[2] && method === 'POST') {
        if (!Number.isInteger(body?.revision)) return problem(400, 'request.invalid', { violations: ['revision.required'] });
        if (body.revision !== item.revision) return problem(412, 'requirement.version_conflict');
        if (link.revision !== item.revision) {
          requirementEvent(item, 'ticket_switched', account, { field: 'tickets', from: `${ticket.number}:${link.revision}`, to: `${ticket.number}:${item.revision}` });
          ticketRequirementChange(ticket, account, label(link.revision), label(item.revision));
          link.revision = item.revision;
        }
        return linksAnswer(account, item, language);
      }
      if (!ticketPath[2] && method === 'DELETE') {
        req.ticketLinks.splice(req.ticketLinks.indexOf(link), 1);
        requirementEvent(item, 'ticket_unlinked', account, { field: 'tickets', from: `${ticket.number}:${link.revision}` });
        ticketRequirementChange(ticket, account, label(link.revision), null);
        return linksAnswer(account, item, language);
      }
    }
    if (pagePath && !pagePath[1] && method === 'POST') {
      if (typeof body?.pageId !== 'string') return problem(400, 'request.invalid', { violations: ['pageId.invalid'] });
      if (requirementLinks(account, item, language).pageTitle(body.pageId) === null) return problem(400, 'request.invalid', { violations: ['page.unknown'] });
      if (req.pageLinks.some((link) => link.requirementId === item.id && link.pageId === body.pageId)) return problem(409, 'requirement.already_linked');
      req.pageLinks.push({ requirementId: item.id, pageId: body.pageId, linkedAt: now(), linkedBy: account.id });
      requirementEvent(item, 'page_linked', account, { field: 'pages', to: body.pageId });
      return linksAnswer(account, item, language);
    }
    if (pagePath && pagePath[1] && method === 'DELETE') {
      const link = req.pageLinks.find((entry) => entry.requirementId === item.id && entry.pageId === pagePath[1]);
      if (!link) return problem(404, 'requirement.link_not_found');
      req.pageLinks.splice(req.pageLinks.indexOf(link), 1);
      requirementEvent(item, 'page_unlinked', account, { field: 'pages', from: link.pageId });
      return linksAnswer(account, item, language);
    }
    return problem(404, 'requirement.not_found');
  }
  function requirementRoute(account, method, p, query, headers, body) {
    if (p === '/requirements/projects' && method === 'GET') {
      return ok(state.projects.filter((item) => requirementAccess(account, item.id).read)
        .map((item) => ({ projectId: item.id, key: item.key, name: item.name, access: requirementAccess(account, item.id) })));
    }
    const onTicket = p.match(/^\/requirements\/projects\/([^/]+)\/tickets\/(\d+)$/);
    if (onTicket && method === 'GET') {
      const projectId = onTicket[1];
      if (!ticketReadable(account, projectId)) return problem(404, 'project.not_found');
      const ticket = state.tickets.find((entry) => entry.projectId === projectId && entry.number === Number(onTicket[2]));
      if (!ticket) return problem(404, 'ticket.not_found');
      const access = requirementAccess(account, projectId);
      if (!access.read) return ok({ ticketId: ticket.id, read: false, edit: false, requirements: [] });
      return ok({ ticketId: ticket.id, read: true, edit: access.edit, requirements: req.ticketLinks.filter((link) => link.ticketId === ticket.id).map((link) => {
        const item = req.items.find((entry) => entry.id === link.requirementId);
        return { requirementId: item.id, number: item.number, key: `ANF-${item.number}`,
          title: access.readableFields.includes('title') ? requirementRevision(item, item.revision).title : null, status: item.status, revision: link.revision,
          newerRevision: link.revision < item.revision ? item.revision : null, linkedAt: link.linkedAt, linkedBy: link.linkedBy };
      }).sort((a, b) => a.number - b.number) });
    }
    const inProject = p.match(/^\/requirements\/projects\/([^/]+)\/(requirements|owners)$/);
    if (inProject) {
      const projectId = inProject[1];
      const access = requirementAccess(account, projectId);
      if (!access.read) return problem(404, 'project.not_found');
      if (inProject[2] === 'owners') {
        // The owner or an active custom person field one may write opens the choice (TASK-100, DEC-130).
        const people = access.writableFields.includes('owner') || requirementFieldsOf(projectId)
          .some((field) => field.active !== false && field.type === 'user' && access.writableFields.includes(`custom.${field.key}`));
        if (!access.edit || !people) return problem(403, 'auth.forbidden');
        // A test can let a load of the choice fail once to check the shown error and the retry.
        if (req.failOwners?.()) return problem(503, 'server.unavailable');
        return ok((state.members.get(projectId) ?? []).map((member) => member.accountId)
          .filter((id) => state.accounts.some((entry) => entry.id === id && entry.active && entry.kind !== 'portal')));
      }
      if (method === 'GET') {
        req.listCalls = (req.listCalls ?? 0) + 1;
        const status = query.get('status');
        const priority = query.get('priority');
        const owner = query.get('owner');
        const text = query.get('text');
        if (priority !== null && !access.readableFields.includes('priority')) return problem(400, 'request.invalid', { violations: ['priority.not_readable'] });
        if (owner !== null && !access.readableFields.includes('owner')) return problem(400, 'request.invalid', { violations: ['owner.not_readable'] });
        if (text !== null && !access.readableFields.includes('title')) return problem(400, 'request.invalid', { violations: ['text.not_readable'] });
        const after = Number(query.get('after') ?? 0);
        const limit = Number(query.get('limit') ?? 50);
        const matches = req.items.filter((item) => item.projectId === projectId && item.number > after)
          .filter((item) => (status === null ? item.status !== 'discarded' : item.status === status))
          .filter((item) => priority === null || requirementRevision(item, item.revision).priority === priority)
          .filter((item) => owner === null || item.ownerId === owner)
          .filter((item) => text === null || requirementRevision(item, item.revision).title.toLowerCase().includes(text.toLowerCase()))
          .sort((a, b) => a.number - b.number);
        const page = matches.slice(0, limit);
        const answer = ok({ projectId, projectKey: state.projects.find((item) => item.id === projectId)?.key ?? '', access,
          items: page.map((item) => {
            const view = requirementView(account, item);
            return { id: item.id, number: item.number, key: view.key, status: item.status, revision: item.revision, approvedRevision: item.approvedRevision,
              title: view.content.title, priority: view.content.priority, ownerId: view.ownerId, updatedAt: item.updatedAt };
          }), nextAfter: matches.length > limit ? page.at(-1).number : null, customFields: requirementFieldDefs(projectId, access) });
        // A test can hold back the answer of one list request to check that a late answer of an older filter is dropped.
        return req.delayList ? req.delayList(answer, query) : answer;
      }
      if (method === 'POST') {
        if (!access.edit) return problem(403, 'auth.forbidden');
        const violations = [];
        const denied = requirementChanges(body ?? {}, access, violations, { projectId, known: new Set() });
        if (body?.title === undefined) violations.push('title.required');
        if (violations.length > 0) return problem(400, 'request.invalid', { violations: [...new Set(violations)] });
        if (denied.length > 0 || !access.writableFields.includes('title')) return problem(403, 'auth.forbidden');
        const item = addRequirement(account, body.title, { projectId, priority: body.priority ?? 'normal', description: body.description ?? null,
          criteria: (body.criteria ?? []).map((criterion) => criterion.text), ownerId: body.ownerId ?? null,
          custom: Object.fromEntries(Object.entries(body.customFields ?? {}).filter(([, value]) => value !== null)) });
        return requirementAnswer(account, item, 201);
      }
    }
    const linkPath = p.match(/^\/requirements\/([^/]+)(\/(?:links|tickets|pages).*)$/);
    if (linkPath) {
      const linked = req.items.find((entry) => entry.id === linkPath[1]);
      if (!linked || !requirementAccess(account, linked.projectId).read) return problem(404, 'requirement.not_found');
      return requirementLinkRoute(account, linked, method, linkPath[2], query, body);
    }
    const match = p.match(/^\/requirements\/([^/]+)(\/(submit|approve|return|discard|restore|history|revisions\/(\d+)))?$/);
    const item = req.items.find((entry) => entry.id === match?.[1]);
    if (!item || !requirementAccess(account, item.projectId).read) return problem(404, 'requirement.not_found');
    const access = requirementAccess(account, item.projectId);
    const part = match[3];
    if (!part && method === 'GET') return requirementAnswer(account, item);
    if (part === 'history' && method === 'GET') {
      // TASK-097: ticket links only with ticket.read, page links for every reader with the titles the reader may see.
      const tickets = ticketReadable(account, item.projectId);
      const fieldShown = (event) => event.field === null || access.readableFields.includes(event.field) || event.field === 'pages'
        || (event.field === 'tickets' && tickets);
      const events = req.events.filter((event) => event.requirementId === item.id && fieldShown(event));
      const { pageTitle } = requirementLinks(account, item, query.get('language'));
      const pageTitles = Object.fromEntries(events.filter((event) => event.field === 'pages').map((event) => event.to ?? event.from)
        .map((pageId) => [pageId, pageTitle(pageId)]).filter(([, title]) => title !== null));
      return ok({ requirementId: item.id, revisions: req.revisions.filter((entry) => entry.requirementId === item.id)
        .map(({ revision, openedAt, openedBy, savedAt, savedBy, approvedAt, approvedBy }) => ({ revision, openedAt, openedBy, savedAt, savedBy, approvedAt, approvedBy })),
      events: events.map(({ requirementId, ...event }) => event), pageTitles });
    }
    if (part?.startsWith('revisions/') && method === 'GET') {
      const revision = requirementRevision(item, Number(match[4]));
      if (!revision) return problem(404, 'requirement.revision_not_found');
      return ok({ revision: revision.revision, current: revision.revision === item.revision, savedAt: revision.savedAt, savedBy: revision.savedBy,
        approvedAt: revision.approvedAt, approvedBy: revision.approvedBy, content: requirementContent(revision, access),
        customFields: requirementFieldDefs(item.projectId, access) });
    }
    const needed = part === 'approve' || part === 'return' ? 'approve' : 'edit';
    if (!access[needed]) return problem(403, 'auth.forbidden');
    // TASK-098: every custom field of the project counts as content too.
    if (part === 'approve' && ![...REQ_CONTENT, ...requirementFieldsOf(item.projectId).map((entry) => `custom.${entry.key}`)]
      .every((name) => access.readableFields.includes(name))) return problem(403, 'requirement.content_not_readable');
    if (headers['if-match'] === undefined) return problem(428, 'requirement.precondition_required');
    if (headers['if-match'] !== `"${item.version}"`) return problem(412, 'requirement.version_conflict');
    const touch = () => Object.assign(item, { version: item.version + 1, updatedAt: now(), updatedBy: account.id });
    const move = (kind, to, reason = null) => {
      const from = item.status;
      item.status = to;
      requirementEvent(item, kind, account, { from, to, reason });
      touch();
      return requirementAnswer(account, item);
    };
    if (!part && method === 'PATCH') {
      if (item.status === 'discarded') return problem(409, 'requirement.discarded');
      const violations = [];
      if (Object.keys(body ?? {}).length === 0) return problem(400, 'request.invalid', { violations: ['changes.required'] });
      const denied = requirementChanges(body, access, violations,
        { projectId: item.projectId, known: new Set(requirementRevision(item, item.revision).criteria.map((criterion) => criterion.id)),
          current: requirementRevision(item, item.revision).custom ?? {} });
      if (violations.length > 0) return problem(400, 'request.invalid', { violations: [...new Set(violations)] });
      if (denied.length > 0) return problem(403, 'auth.forbidden');
      let current = requirementRevision(item, item.revision);
      const before = { ...current, criteria: current.criteria.map((criterion) => ({ ...criterion })), custom: { ...(current.custom ?? {}) } };
      const custom = { ...before.custom };
      for (const [key, value] of Object.entries(body.customFields ?? {})) {
        if (value === null) delete custom[key];
        else custom[key] = value;
      }
      const next = { title: body.title ?? current.title, priority: body.priority ?? current.priority,
        description: body.description !== undefined ? body.description : current.description, criteria: current.criteria, custom };
      const sorted = (values) => JSON.stringify(Object.entries(values).sort(([a], [b]) => a.localeCompare(b)));
      if (body.criteria !== undefined) {
        const known = new Set(current.criteria.map((criterion) => criterion.id));
        if (body.criteria.some((criterion) => criterion.id != null && !known.has(criterion.id))) return problem(400, 'request.invalid', { violations: ['criteria.unknown'] });
        next.criteria = body.criteria.map((criterion) => (criterion.id == null ? { id: item.nextCriterion++, text: criterion.text } : { id: criterion.id, text: criterion.text }));
      }
      const contentChanged = next.title !== before.title || next.priority !== before.priority
        || JSON.stringify(next.description) !== JSON.stringify(before.description) || JSON.stringify(next.criteria) !== JSON.stringify(before.criteria)
        || sorted(next.custom) !== sorted(before.custom);
      const ownerChanged = body.ownerId !== undefined && body.ownerId !== item.ownerId;
      if (!contentChanged && !ownerChanged) return requirementAnswer(account, item);
      if (contentChanged && current.approvedAt !== null) {
        const opened = item.revision + 1;
        current = { ...before, revision: opened, openedAt: now(), openedBy: account.id, approvedAt: null, approvedBy: null };
        req.revisions.push(current);
        item.revision = opened;
        item.status = 'draft';
        requirementEvent(item, 'revision_opened', account, { from: String(opened - 1), to: String(opened) });
      }
      if (contentChanged) {
        if (next.title !== before.title) requirementEvent(item, 'changed', account, { field: 'title', from: before.title, to: next.title });
        if (JSON.stringify(next.description) !== JSON.stringify(before.description)) requirementEvent(item, 'changed', account, { field: 'description' });
        if (next.priority !== before.priority) requirementEvent(item, 'changed', account, { field: 'priority', from: before.priority, to: next.priority });
        const old = new Map(before.criteria.map((criterion) => [criterion.id, criterion]));
        const kept = new Set(next.criteria.map((criterion) => criterion.id));
        for (const criterion of before.criteria) if (!kept.has(criterion.id)) requirementEvent(item, 'criterion_removed', account, { field: 'criteria', criterion: criterion.id, from: criterion.text });
        for (const criterion of next.criteria) {
          const previous = old.get(criterion.id);
          if (!previous) requirementEvent(item, 'criterion_added', account, { field: 'criteria', criterion: criterion.id, to: criterion.text });
          else if (previous.text !== criterion.text) requirementEvent(item, 'criterion_changed', account, { field: 'criteria', criterion: criterion.id, from: previous.text, to: criterion.text });
        }
        const order = (list) => list.filter((criterion) => old.has(criterion.id) && kept.has(criterion.id)).map((criterion) => `K${criterion.id}`).join(',');
        if (order(before.criteria) !== order(next.criteria)) requirementEvent(item, 'criteria_moved', account, { field: 'criteria', from: order(before.criteria), to: order(next.criteria) });
        for (const key of [...new Set([...Object.keys(before.custom), ...Object.keys(next.custom)])].sort()) {
          if (before.custom[key] !== next.custom[key]) {
            requirementEvent(item, 'changed', account, { field: `custom.${key}`, from: before.custom[key] === undefined ? null : String(before.custom[key]),
              to: next.custom[key] === undefined ? null : String(next.custom[key]) });
          }
        }
        Object.assign(current, next, { savedAt: now(), savedBy: account.id });
      }
      if (ownerChanged) {
        requirementEvent(item, 'changed', account, { field: 'owner', from: item.ownerId, to: body.ownerId });
        item.ownerId = body.ownerId;
      }
      touch();
      return requirementAnswer(account, item);
    }
    if (part === 'submit' && method === 'POST') {
      if (item.status !== 'draft') return problem(409, 'requirement.status_invalid');
      // TASK-098: an active required field needs a value; who may not read and write it meets a conflict, not a hint at it.
      const required = requirementFieldsOf(item.projectId).filter((entry) => entry.required === true && entry.active !== false);
      if (required.some((entry) => !access.readableFields.includes(`custom.${entry.key}`) || !access.writableFields.includes(`custom.${entry.key}`))) {
        return problem(409, 'requirement.required_field_inaccessible');
      }
      const values = requirementRevision(item, item.revision).custom ?? {};
      const missing = [...(requirementRevision(item, item.revision).criteria.length === 0 ? ['criteria.required'] : []),
        ...required.filter((entry) => values[entry.key] === undefined).map((entry) => `custom.${entry.key}.required_for_review`)];
      if (missing.length > 0) return problem(400, 'request.invalid', { violations: missing });
      return move('submitted', 'review');
    }
    if (part === 'approve' && method === 'POST') {
      if (item.status !== 'review') return problem(409, 'requirement.status_invalid');
      if (body?.revision !== item.revision) return problem(412, 'requirement.version_conflict');
      Object.assign(requirementRevision(item, item.revision), { approvedAt: now(), approvedBy: account.id });
      item.approvedRevision = item.revision;
      return move('approved', 'approved');
    }
    if (part === 'return' && method === 'POST') {
      if (item.status !== 'review') return problem(409, 'requirement.status_invalid');
      if (typeof body?.reason !== 'string' || body.reason.trim() === '') return problem(400, 'request.invalid', { violations: ['reason.required'] });
      return move('returned', 'draft', body.reason);
    }
    if (part === 'discard' && method === 'POST') {
      if (item.status === 'discarded') return problem(409, 'requirement.status_invalid');
      return move('discarded', 'discarded', body?.reason ?? null);
    }
    if (part === 'restore' && method === 'POST') {
      if (item.status !== 'discarded') return problem(409, 'requirement.status_invalid');
      return move('restored', item.approvedRevision === item.revision ? 'approved' : 'draft');
    }
    return problem(404, 'resource.not_found');
  }

  function addWikiPage(by, title, content, { parentId = null, publish = false, projectId = project.id, language = 'de' } = {}) {
    const page = { id: uuid(), projectId, parentId, language, variants: {}, archived: false, version: 1, createdAt: now(), createdBy: by.id, draftRevision: 1,
      publishedRevision: null, publishedAt: null, publishedBy: null };
    wiki.pages.push(page);
    wiki.revisions.push({ pageId: page.id, language, revision: 1, title, content, savedAt: now(), savedBy: by.id, basedOn: null, restoredFrom: null, sourceRevision: null });
    wikiEvent(page, 'created', 1, by);
    if (publish) {
      page.publishedRevision = 1;
      page.publishedAt = now();
      page.publishedBy = by.id;
      wikiEvent(page, 'published', 1, by);
    }
    return page;
  }
  // A language version as `POST /languages` makes it, optionally published, or with a translation of an older source revision.
  function addWikiLanguage(page, language, by, { title, content, publish = false, sourceRevision } = {}) {
    const base = sourceRevision ?? page.publishedRevision ?? page.draftRevision;
    const from = wikiRevision(page, base);
    const variant = { draftRevision: 1, publishedRevision: null, publishedAt: null, publishedBy: null };
    page.variants = { ...(page.variants ?? {}), [language]: variant };
    wiki.revisions.push({ pageId: page.id, language, revision: 1, title: title ?? from.title, content: content ?? from.content, savedAt: now(), savedBy: by.id,
      basedOn: null, restoredFrom: null, sourceRevision: base });
    wikiEvent(page, 'translated', 1, by, language);
    if (publish) {
      Object.assign(variant, { publishedRevision: 1, publishedAt: now(), publishedBy: by.id });
      wikiEvent(page, 'published', 1, by, language);
    }
    return variant;
  }
  // As the search (TASK-102): published revisions, drafts only with drafts=true and a draft right, archived pages only with
  // archived=true and wiki.publish, every language version on its own, one hit per page in the preferred language.
  // The text of a link to a wiki page is left out, a space keeps the words around it apart (as WikiDocument.plainText).
  const wikiPlain = (node) => node.type === 'text' ? ((node.marks ?? []).some((mark) => mark.type === 'link' && /^wiki:/.test(mark.attrs?.href ?? '')) ? ' ' : node.text)
    : node.type === 'hardBreak' ? '\n' : node.type === 'image' ? '' : (node.content ?? []).map(wikiPlain).join('') + (node.type === 'doc' ? '' : '\n');
  // Lower case as Java's toLowerCase(Locale.ROOT) on the server: only the sigma depends on its neighbours. Java decides a final
  // sigma within a word of the word break rules of the JDK, which are not those of Unicode (F4 of the Codex reviews of TASK-106
  // and TASK-107). Letters and numbers of the BMP are word characters, except the ideographs and kana the JDK lists apart;
  // a combining mark belongs to the letter or number before it, a format character is passed over; a single mid letter
  // character such as - _ . ' between two letters and a mid number character such as , . ' between two numbers stay inside
  // the word, while : and · end it. Cased are the cased letters and the few others Java counts. This was compared with Java 21
  // for every BMP character and for 200,000 random texts without a difference; characters outside the BMP next to a sigma
  // still differ, as the JDK takes them apart by code units.
  const javaCased = (ch) => /[\p{Ll}\p{Lu}\p{Lt}ʕʰ-ʸˀˁˠ-ˤͅͺᴬ-ᵡⅠ-ⅿ]/u.test(ch);
  const javaBreaks = /[々ぁ-ゔゝゞァ-ヺー-ヾ一-龥豈-鶴]/u;
  const javaMidLetter = "\"'-._­֊־᐀᠆‐‑‒–—―‧‿⁀⁔⸗⸚⸺⸻"
    + '⹀⹝〜〰゠︱︲︳︴﹍﹎﹏﹘﹣－＿';
  const javaMidNumber = "\"',.٫";
  const javaLower = (text) => {
    const chars = [...text];
    const kinds = [];
    let base = -1;
    chars.forEach((ch, index) => {
      if (ch !== '­' && /\p{Cf}/u.test(ch)) {
        kinds.push('format');
        return;
      }
      if (ch.codePointAt(0) >= 0x10000 || ch === '­' || javaBreaks.test(ch)) kinds.push('break');
      else if (/\p{M}/u.test(ch)) kinds.push(base >= 0 && (kinds[base] === 'letter' || kinds[base] === 'number') ? kinds[base] : 'break');
      else if (/\p{L}/u.test(ch)) kinds.push('letter');
      else if (/\p{N}/u.test(ch)) kinds.push('number');
      else kinds.push('break');
      base = index;
    });
    const near = (index, step) => {
      let at = index + step;
      while (kinds[at] === 'format') at += step;
      return kinds[at];
    };
    const inWord = [];
    chars.forEach((ch, index) => {
      if (kinds[index] === 'format') {
        inWord.push(inWord.length > 0 && inWord.at(-1));
        return;
      }
      inWord.push(kinds[index] !== 'break'
        || (javaMidLetter.includes(ch) && near(index, -1) === 'letter' && near(index, 1) === 'letter')
        || (javaMidNumber.includes(ch) && near(index, -1) === 'number' && near(index, 1) === 'number'));
    });
    const casedAround = (index, step) => {
      for (let at = index + step; inWord[at]; at += step) if (javaCased(chars[at])) return true;
      return false;
    };
    return chars.map((ch, index) => (ch !== 'Σ' ? ch.toLowerCase()
      : casedAround(index, -1) && !casedAround(index, 1) ? 'ς' : 'σ')).join('');
  };
  const searchWords = (text) => javaLower(text.normalize('NFKC')).split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  // As SearchText.indexTerms: the indexed words of a long text stop after 200,000 bytes in UTF-8, after a whole word.
  const indexWords = (text) => {
    const kept = [];
    let bytes = -1;
    for (const word of searchWords(text)) {
      bytes += 1 + Buffer.byteLength(word, 'utf8');
      if (bytes > 200_000) break;
      kept.push(word);
    }
    return kept;
  };
  // As SearchText.parse: the limits count code points after NFKC.
  const searchTextInvalid = (q) => {
    const text = javaStrip(q.normalize('NFKC'));
    const words = searchWords(text);
    return [...text].length > 200 || words.length > 10 || words.some((word) => [...word].length > 50);
  };
  // javaStrip above trims like Java: U+FEFF or a non-breaking space stay, unlike trim() in JavaScript (F3 of the Codex review
  // of TASK-105), so such a text is a given search without words that finds nothing, as on the server.
  // As Nfkc.origins and SearchSnippet.marks (TASK-105 R3, F3 of the Codex reviews of TASK-105 to TASK-107): NFKC of the whole
  // text with the original characters each character of the normal form is made of. Every character is decomposed on its own,
  // each run of marks is put into canonical order by a stable sort, and a starter joins each following character that is not
  // blocked and composes with it; the combining class is compared through NFD as on the server. A word is marked from the first
  // to the last original character of its own; lower case over the whole text as Java gives it, per character for the places.
  const nfkcOrigins = (text) => {
    const decomposed = [];
    for (let index = 0; index < text.length;) {
      const end = index + String.fromCodePoint(text.codePointAt(index)).length;
      for (const part of text.slice(index, end).normalize('NFKD')) decomposed.push([part.codePointAt(0), index, end]);
      index = end;
    }
    const swaps = (first, second) => first !== second
      && String.fromCodePoint(first, second).normalize('NFD') === String.fromCodePoint(second, first);
    const nonStarter = (codePoint) => codePoint >= 0x300 && (swaps(0x301, codePoint) || swaps(codePoint, 0x334));
    const order = (first, second) => (swaps(first, second) ? 1 : swaps(second, first) ? -1 : 0);
    const ordered = [];
    for (let index = 0; index < decomposed.length;) {
      if (!nonStarter(decomposed[index][0])) {
        ordered.push(decomposed[index]);
        index += 1;
        continue;
      }
      const run = [];
      while (index < decomposed.length && nonStarter(decomposed[index][0])) run.push(decomposed[index++]);
      ordered.push(...run.sort((first, second) => order(first[0], second[0])));
    }
    const compose = (starter, next) => {
      const pair = String.fromCodePoint(starter, next).normalize('NFC');
      return [...pair].length === 1 ? pair.codePointAt(0) : -1;
    };
    const result = [];
    let starter = -1;
    let last = null;
    for (const next of ordered) {
      const nextStarter = !nonStarter(next[0]);
      if (starter >= 0) {
        const blocked = last !== null && (nextStarter || !nonStarter(last[0]) || order(last[0], next[0]) >= 0);
        const composite = blocked ? -1 : compose(result[starter][0], next[0]);
        if (composite >= 0) {
          const joined = result[starter];
          result[starter] = [composite, Math.min(joined[1], next[1]), Math.max(joined[2], next[2])];
          continue;
        }
      }
      result.push([...next]);
      if (nextStarter) {
        starter = result.length - 1;
        last = null;
      } else {
        last = next;
      }
    }
    return result.map(([codePoint]) => String.fromCodePoint(codePoint)).join('') === text.normalize('NFKC') ? result : null;
  };
  const searchWordPlaces = (text) => {
    const origins = nfkcOrigins(text);
    if (origins === null) return [];
    const units = [];
    let whole = '';
    for (const [codePoint, from, to] of origins) {
      const ch = String.fromCodePoint(codePoint);
      whole += ch;
      for (let unit = 0; unit < ch.toLowerCase().length; unit += 1) units.push({ from, to });
    }
    const folded = javaLower(whole);
    if (folded.length !== units.length) return [];
    return [...folded.matchAll(/[\p{L}\p{N}]+/gu)].map((match) => {
      const covered = units.slice(match.index, match.index + match[0].length);
      return { word: match[0], start: Math.min(...covered.map((place) => place.from)), end: Math.max(...covered.map((place) => place.to)) };
    });
  };
  const searchMarks = (text, words) => {
    if (words.length === 0) return [];
    const marks = [];
    for (const place of searchWordPlaces(text)) {
      if (!words.some((word) => place.word.startsWith(word))) continue;
      let mark = { start: place.start, end: place.end };
      if (marks.length > 0 && mark.start < marks.at(-1).end) {
        const last = marks.pop();
        mark = { start: Math.min(last.start, mark.start), end: Math.max(last.end, mark.end) };
      }
      marks.push(mark);
    }
    return marks;
  };
  // As SearchSnippet.of: 240 units from 60 before the first found word, cut at spaces, never inside a surrogate pair.
  const searchSnippet = (body, words) => {
    if (body.trim() === '') return null;
    const text = body.replace(/[\r\n\t]/g, ' ');
    const all = searchMarks(text, words);
    const first = all.length === 0 ? -1 : all[0].start;
    let start = first < 0 ? 0 : Math.max(0, first - 60);
    if (start > 0) {
      const space = text.indexOf(' ', start);
      start = space >= 0 && space < first ? space + 1 : start;
    }
    let end = Math.min(text.length, start + 240);
    if (end < text.length) {
      const space = text.lastIndexOf(' ', end);
      end = space > Math.max(start, first) ? space : end;
    }
    const safe = (index) => (index > 0 && index < text.length && /[\udc00-\udfff]/.test(text[index]) ? index - 1 : index);
    start = safe(start);
    end = safe(end);
    const marks = all.filter((mark) => mark.end > start && mark.start < end)
      .map((mark) => ({ start: Math.max(mark.start, start) - start, end: Math.min(mark.end, end) - start }));
    return { text: text.slice(start, end), marks, cutStart: start > 0, cutEnd: end < text.length };
  };
  // As the server (DEC-132): every level above a hit; in the published stage published parents with their published title,
  // every other level unavailable; in the draft stage the draft titles.
  const wikiSearchPath = (page, preferred, drafts) => {
    const path = [];
    const seen = new Set();
    let parent = page.parentId;
    while (parent !== null && !seen.has(parent)) {
      seen.add(parent);
      const above = wiki.pages.find((item) => item.id === parent);
      if (above === undefined || above.archived) {
        // An archived parent is a placeholder and the path goes on with its own parent (TASK-105 R2).
        path.unshift({ id: null, title: null, available: false });
        parent = above?.parentId ?? null;
        continue;
      }
      const title = wikiTitleIn(above, preferred, drafts);
      path.unshift(title === null ? { id: null, title: null, available: false } : { id: above.id, title, available: true });
      parent = above.parentId;
    }
    return path;
  };
  function wikiSearch(account, query) {
    const violations = [];
    const language = query.get('language');
    const preferred = query.get('preferred') ?? 'de';
    if (language !== null && !WIKI_LANGUAGES.includes(language)) violations.push('language.invalid');
    if (!WIKI_LANGUAGES.includes(preferred)) violations.push('preferred.invalid');
    for (const flag of ['drafts', 'archived']) if (query.has(flag) && !['true', 'false'].includes(query.get(flag))) violations.push(`${flag}.invalid`);
    const q = query.get('q') ?? '';
    if (searchTextInvalid(q)) violations.push('q.invalid');
    const offset = Number(query.get('offset') ?? 0);
    const limit = Number(query.get('limit') ?? 20);
    if (!Number.isInteger(offset) || offset < 0 || offset > 1000 || !Number.isInteger(limit) || limit < 1 || limit > 50) violations.push('paging.invalid');
    if (violations.length > 0) return problem(400, 'request.invalid', { violations });
    const drafts = query.get('drafts') === 'true';
    const archived = query.get('archived') === 'true';
    const projects = state.projects.filter((item) => wikiAny(wikiAccess(account, item.id))).map((item) => item.id);
    const filter = query.getAll('project');
    if (filter.some((id) => !projects.includes(id))) return problem(400, 'request.invalid', { violations: ['project.unknown'] });
    if ((drafts && !projects.some((id) => wikiDrafts(wikiAccess(account, id)))) || (archived && !projects.some((id) => wikiAccess(account, id).publish))) {
      return problem(403, 'auth.forbidden');
    }
    const words = searchWords(q);
    // A text of punctuation only finds nothing, as on the server; no text at all lists every readable page.
    if (javaStrip(q) !== '' && words.length === 0) return ok({ total: 0, items: [] });
    const hits = [];
    for (const page of wiki.pages) {
      const access = wikiAccess(account, page.projectId);
      if (!projects.includes(page.projectId) || (filter.length > 0 && !filter.includes(page.projectId)) || (drafts && !wikiDrafts(access))) continue;
      if (page.archived && !(archived && access.publish)) continue;
      const candidates = [];
      for (const [code, variant] of Object.entries(wikiVariants(page))) {
        if (language !== null && code !== language) continue;
        const number = drafts ? variant.draftRevision : variant.publishedRevision;
        if (number === null) continue;
        const revision = wikiRevision(page, number, code);
        const body = wikiPlain(revision.content).trim();
        const titleTerms = searchWords(revision.title);
        const bodyTerms = indexWords(body);
        const has = (terms, word) => terms.some((term) => term.startsWith(word));
        if (!words.every((word) => has(titleTerms, word) || has(bodyTerms, word))) continue;
        // As the rank of the server: every word counts where it is found, in the title twice (an approximation of ts_rank).
        const rank = words.reduce((sum, word) => sum + (has(titleTerms, word) ? 2 : 0) + (has(bodyTerms, word) ? 1 : 0), 0);
        candidates.push({ code, revision, body, rank, publishedAt: drafts ? null : variant.publishedAt ?? now() });
      }
      if (candidates.length === 0) continue;
      const rank = (item) => (item.code === preferred ? 0 : item.code === page.language ? 1 : 2);
      const shown = candidates.sort((a, b) => rank(a) - rank(b))[0];
      hits.push({ id: page.id, projectId: page.projectId, projectKey: state.projects.find((item) => item.id === page.projectId).key, title: shown.revision.title,
        titleMarks: searchMarks(shown.revision.title, words), language: shown.code, stage: drafts ? 'draft' : 'published', revision: shown.revision.revision,
        publishedAt: shown.publishedAt, archived: page.archived, path: wikiSearchPath(page, preferred, drafts),
        snippet: searchSnippet(shown.body, words), rank: shown.rank });
    }
    // As the server: rank first, then the title in lower case (compared like the collation of the database), the title and the
    // identifier.
    const plainOrder = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
    hits.sort((a, b) => b.rank - a.rank || a.title.toLowerCase().localeCompare(b.title.toLowerCase()) || plainOrder(a.title, b.title) || plainOrder(a.id, b.id));
    for (const hit of hits) delete hit.rank;
    return ok({ total: hits.length, items: hits.slice(offset, offset + limit) });
  }

  // As the server (TASK-103, DEC-133): the current revision or, with stage=approved, the newest approved one; words only in
  // readable fields, filters on unreadable fields match nothing, the key ANF-n with or without the project key, discarded
  // requirements only with a status filter, the excerpt from the first readable field that holds a word.
  function ticketSearch(account, query) {
    const words = (query.get('q') ?? '').toLowerCase().split(/\s+/).filter(Boolean);
    const items = state.tickets.filter((ticket) => may(account, 'ticket.read', ticket.projectId))
      .filter((ticket) => words.every((word) => readable(account, ticket.projectId).some((name) => String(ticket.values[name] ?? '').toLowerCase().split(/\W+/).some((part) => part.startsWith(word))) || ticket.key.toLowerCase().startsWith(word)))
      .filter((ticket) => tagFilterMatches(account, ticket, query))
      // As the server: a text that is exactly a key puts that ticket first.
      .sort((x, y) => Number(y.key === (query.get('q') ?? '').trim().toUpperCase()) - Number(x.key === (query.get('q') ?? '').trim().toUpperCase()))
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
    // Unknown and merged tags and tags of a hidden catalog answer alike, as the server checks them (TASK-086, Codex H2).
    if (tags.some((id) => {
      const tag = state.tags.find((candidate) => candidate.id === id);
      return !tag || tag.mergedInto || (tag.projectId !== null && !catalogVisible(account, tag.projectId));
    })) return problem(400, 'request.invalid', { violations: ['tag.unknown'] });
    const offset = Number(query.get('offset') ?? 0);
    const limit = Number(query.get('limit') ?? 20);
    return ok({ total: items.length, items: items.slice(offset, offset + limit) });
  }
  // As the server (TASK-029, TASK-104): projects by key, name and description, company tags, an exact key first.
  function projectSearch(account, query) {
    const q = (query.get('q') ?? '').toLowerCase().trim();
    if (searchTextInvalid(q)) return problem(400, 'request.invalid', { violations: ['q.invalid'] });
    const tags = query.getAll('tag');
    const tagMatch = query.get('tagMatch') ?? 'all';
    const projectTags = (item) => state.projectTags.filter((entry) => entry.project === item.id).map((entry) => entry.tag);
    const words = searchWords(q);
    const found = (item) => { const terms = searchWords(`${item.key} ${item.name} ${item.description ?? ''}`); return words.every((word) => terms.some((term) => term.startsWith(word))); };
    const items = state.projects.filter((item) => projectVisible(account, item) && found(item))
      .filter((item) => tags.length === 0 && tagMatch !== 'untagged' ? true
        : tagMatch === 'untagged' ? projectTags(item).length === 0
          : tagMatch === 'any' ? tags.some((tag) => projectTags(item).includes(tag))
            : tagMatch === 'none' ? !tags.some((tag) => projectTags(item).includes(tag)) : tags.every((tag) => projectTags(item).includes(tag)))
      .sort((x, y) => Number(y.key.toLowerCase() === q) - Number(x.key.toLowerCase() === q) || x.key.localeCompare(y.key))
      .map((item) => ({ id: item.id, key: item.key, name: item.name, description: item.description,
        tags: projectTags(item).map((id) => state.tags.find((tag) => tag.id === id)).filter(Boolean).map((tag) => ({ id: tag.id, name: tag.name })) }));
    const offset = Number(query.get('offset') ?? 0);
    const limit = Number(query.get('limit') ?? 20);
    return ok({ total: items.length, items: items.slice(offset, offset + limit) });
  }
  // As the server (TASK-104, DEC-134): five hits and the total per area, null without a right in any project, and the object
  // a key names exactly when the person may read it.
  function overviewSearch(account, query) {
    const q = query.get('q') ?? '';
    const preferred = query.get('preferred') ?? 'de';
    const violations = [];
    if (searchTextInvalid(q)) violations.push('q.invalid');
    if (!WIKI_LANGUAGES.includes(preferred)) violations.push('preferred.invalid');
    if (violations.length > 0) return problem(400, 'request.invalid', { violations });
    const five = (extra = {}) => new URLSearchParams({ ...(q === '' ? {} : { q }), limit: '5', ...extra });
    const area = (allowed, answer) => (allowed ? answer().body : null);
    const tickets = area(state.projects.some((item) => may(account, 'ticket.read', item.id)), () => ticketSearch(account, five()));
    const projects = area(may(account, 'projects.manage') || state.projects.some((item) => projectVisible(account, item)), () => projectSearch(account, five()));
    const wiki = area(state.projects.some((item) => wikiAny(wikiAccess(account, item.id))), () => wikiSearch(account, five({ preferred })));
    const requirements = area(state.projects.some((item) => requirementAccess(account, item.id).read), () => requirementSearch(account, five()));
    const text = q.normalize('NFKC').trim().toUpperCase();
    let direct = null;
    const ticketKey = text.match(/^([A-Z][A-Z0-9]{1,9})-([0-9]{1,18})$/);
    const requirementKey = text.match(/^([A-Z][A-Z0-9]{1,9})\s+ANF-([0-9]{1,18})$/);
    if (ticketKey && tickets) {
      const hit = tickets.items.find((item) => item.key === `${ticketKey[1]}-${Number(ticketKey[2])}`);
      if (hit) direct = { kind: 'ticket', id: hit.id, projectId: hit.projectId, key: hit.key, number: hit.number };
    } else if (requirementKey && requirements) {
      const hit = requirements.items.find((item) => item.projectKey === requirementKey[1] && item.number === Number(requirementKey[2]));
      if (hit) direct = { kind: 'requirement', id: hit.id, projectId: hit.projectId, key: `${hit.projectKey} ${hit.key}`, number: hit.number };
    } else if (/^[A-Z][A-Z0-9]{1,9}$/.test(text) && projects) {
      const hit = projects.items.find((item) => item.key === text);
      if (hit) direct = { kind: 'project', id: hit.id, projectId: hit.id, key: hit.key, number: null };
    }
    return ok({ tickets, projects, wiki, requirements, direct });
  }
  function requirementSearch(account, query) {
    const violations = [];
    const stage = query.get('stage') ?? 'current';
    if (!['current', 'approved'].includes(stage)) violations.push('stage.invalid');
    const statuses = query.getAll('status');
    const priorities = query.getAll('priority');
    const owners = query.getAll('owner');
    if (statuses.some((value) => !['draft', 'review', 'approved', 'discarded'].includes(value))) violations.push('status.invalid');
    if (priorities.some((value) => !['low', 'normal', 'high', 'critical'].includes(value))) violations.push('priority.invalid');
    if (owners.some((value) => !/^[0-9a-f-]{36}$/.test(value))) violations.push('owner.invalid');
    const everApproved = query.get('approved');
    if (everApproved !== null && !['true', 'false'].includes(everApproved)) violations.push('approved.invalid');
    const custom = query.getAll('custom').map((entry) => [entry.slice(0, entry.indexOf('=')), entry.slice(entry.indexOf('=') + 1)]);
    const q = query.get('q') ?? '';
    if (searchTextInvalid(q)) violations.push('q.invalid');
    const offset = Number(query.get('offset') ?? 0);
    const limit = Number(query.get('limit') ?? 20);
    if (!Number.isInteger(offset) || offset < 0 || offset > 1000 || !Number.isInteger(limit) || limit < 1 || limit > 50) violations.push('paging.invalid');
    if (violations.length > 0) return problem(400, 'request.invalid', { violations });
    const projects = state.projects.filter((item) => requirementAccess(account, item.id).read).map((item) => item.id);
    const filter = query.getAll('project');
    if (filter.some((id) => !projects.includes(id))) return problem(400, 'request.invalid', { violations: ['project.unknown'] });
    const words = searchWords(q);
    const key = q.normalize('NFKC').trim().toUpperCase().match(/^(?:([A-Z][A-Z0-9]{1,9})\s+)?ANF-([0-9]{1,18})$/);
    const found = (text) => { const terms = searchWords(text ?? ''); return (word) => terms.some((term) => term.startsWith(word)); };
    const hits = [];
    for (const item of req.items) {
      if (!projects.includes(item.projectId) || (filter.length > 0 && !filter.includes(item.projectId))) continue;
      const number = stage === 'approved' ? item.approvedRevision : item.revision;
      if (number === null) continue;
      const access = requirementAccess(account, item.projectId);
      const shown = (name) => access.readableFields.includes(name);
      const revision = requirementRevision(item, number);
      const projectKey = state.projects.find((entry) => entry.id === item.projectId).key;
      if (statuses.length === 0 ? item.status === 'discarded' : !statuses.includes(item.status)) continue;
      if (priorities.length > 0 && !(shown('priority') && priorities.includes(revision.priority))) continue;
      if (owners.length > 0 && !(shown('owner') && owners.includes(item.ownerId))) continue;
      if (everApproved !== null && (item.approvedRevision !== null) !== (everApproved === 'true')) continue;
      if (custom.some(([field, value]) => !shown(`custom.${field}`) || String((revision.custom ?? {})[field] ?? '') !== value)) continue;
      const description = revision.description ? wikiPlain(revision.description).trim() : '';
      const texts = [...(shown('description') ? [['description', null, description]] : []),
        ...(shown('criteria') ? revision.criteria.map((criterion) => ['criteria', criterion.id, criterion.text]) : []),
        ...requirementFieldsOf(item.projectId).filter((entry) => ['text', 'longtext'].includes(entry.type) && shown(`custom.${entry.key}`))
          .map((entry) => [`custom.${entry.key}`, null, String((revision.custom ?? {})[entry.key] ?? '')])];
      const byKey = key !== null && Number(key[2]) === item.number && (key[1] === undefined || key[1] === projectKey);
      const byWords = words.length > 0 && words.every((word) => (shown('title') && found(revision.title)(word)) || texts.some(([, , text]) => found(text)(word)));
      if (javaStrip(q) !== '' && !byKey && !byWords) continue;
      const source = texts.find(([, , text]) => words.some(found(text)));
      const snippet = source ? { field: source[0], criterion: source[1], excerpt: searchSnippet(source[2], words) }
        : shown('description') && description !== '' ? { field: 'description', criterion: null, excerpt: searchSnippet(description, words) } : null;
      hits.push({ rank: byKey ? 1000 : 0, hit: { id: item.id, projectId: item.projectId, projectKey, key: `ANF-${item.number}`, number: item.number,
        title: shown('title') ? revision.title : null, titleMarks: shown('title') ? searchMarks(revision.title, words) : [], status: item.status, stage,
        revision: number, currentRevision: item.revision, approvedRevision: item.approvedRevision, approved: item.approvedRevision === number,
        priority: shown('priority') ? revision.priority : null, owner: shown('owner') ? item.ownerId : null,
        customFields: Object.fromEntries(Object.entries(revision.custom ?? {}).filter(([field]) => shown(`custom.${field}`)).map(([field, value]) => [field, String(value)])),
        snippet } });
    }
    hits.sort((a, b) => b.rank - a.rank || a.hit.projectKey.localeCompare(b.hit.projectKey) || b.hit.number - a.hit.number);
    return ok({ total: hits.length, items: hits.slice(offset, offset + limit).map((entry) => entry.hit) });
  }
  function wikiRoute(account, method, p, query, headers, body) {
    if (p === '/wiki/projects' && method === 'GET') {
      return ok(state.projects.filter((item) => isMember(account, item.id)).map((item) => ({ projectId: item.id, key: item.key, name: item.name, access: wikiAccess(account, item.id) }))
        .filter((item) => wikiAny(item.access)));
    }
    // As the server: an unknown language is refused before anything else.
    const requested = query.get('language');
    if (requested !== null && !WIKI_LANGUAGES.includes(requested)) return problem(400, 'request.invalid', { violations: ['language.invalid'] });
    let match = p.match(/^\/wiki\/projects\/([^/]+)\/pages$/);
    if (match) {
      const projectId = match[1];
      const access = wikiAccess(account, projectId);
      if (!wikiAny(access)) return problem(404, 'project.not_found');
      if (method === 'GET') {
        const archived = query.get('archived') === 'true';
        if (archived && !access.publish) return problem(403, 'auth.forbidden');
        const drafts = archived || wikiDrafts(access);
        const pages = wiki.pages.filter((item) => item.projectId === projectId && item.archived === archived && wikiShown(item, requested, drafts) !== null)
          .map((item) => {
            const code = wikiShown(item, requested, drafts);
            const variant = wikiVariant(item, code);
            const shown = wikiRevision(item, drafts ? variant.draftRevision : variant.publishedRevision, code);
            let parent = item.parentId;
            while (!drafts && parent !== null && !wikiPublished(wiki.pages.find((above) => above.id === parent))) {
              parent = wiki.pages.find((above) => above.id === parent)?.parentId ?? null;
            }
            return { id: item.id, parentId: parent, title: shown.title, language: code, published: variant.publishedRevision !== null,
              changed: wikiDrafts(access) && variant.publishedRevision !== variant.draftRevision, archived: item.archived, version: item.version,
              savedAt: drafts ? shown.savedAt : null, savedBy: drafts ? shown.savedBy : null };
          })
          .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
        return ok({ projectId, access, pages });
      }
      if (method === 'POST') {
        if (!access.edit) return problem(403, 'auth.forbidden');
        const violations = [];
        const title = wikiTitle(body.title, violations);
        const parentId = body.parentId ?? null;
        if (parentId !== null) {
          const parent = wiki.pages.find((item) => item.id === parentId && item.projectId === projectId && !item.archived);
          if (!parent) violations.push('parent.unknown');
          else if (wikiDepth(parentId) >= 6) violations.push('parent.too_deep');
        }
        const language = body.language ?? 'de';
        if (!WIKI_LANGUAGES.includes(language)) violations.push('language.invalid');
        if (violations.length > 0) return problem(400, 'request.invalid', { violations });
        const page = addWikiPage(account, title, body.content ?? { type: 'doc', content: [{ type: 'paragraph' }] }, { parentId, projectId, language });
        return { ...wikiAnswer(account, page), status: 201 };
      }
    }
    match = p.match(/^\/wiki\/images\/([^/]+)$/);
    if (match && method === 'GET') {
      const image = wiki.images.find((item) => item.id === match[1]);
      const page = image && wiki.pages.find((item) => item.id === image.pageId);
      // As the server: drafts for `wiki.edit` and `wiki.publish`, readers only while the published revision shows the image.
      const access = page ? wikiAccess(account, page.projectId) : null;
      const shown = page && !page.archived && Object.entries(wikiVariants(page)).some(([code, variant]) => variant.publishedRevision !== null
        && JSON.stringify(wikiRevision(page, variant.publishedRevision, code).content).includes(`/api/v1/wiki/images/${image.id}"`));
      const visible = page && wikiVisible(account, page) && (wikiDrafts(access) || (access.read && shown));
      return visible ? { status: 200, raw: image.content, type: image.mediaType } : problem(404, 'wiki.image_not_found');
    }
    match = p.match(/^\/wiki\/pages\/([^/]+)(\/.*)?$/);
    if (!match) return problem(404, 'resource.not_found');
    // N1 of the follow up review: as the server, the body of a new language version is checked before the page is looked up,
    // so an unknown or unpublished page answers a missing or unknown language with 400 like any other page.
    if (match[2] === '/languages' && method === 'POST') {
      if (body?.language === undefined || body?.language === null) return problem(400, 'request.invalid', { violations: ['language.required'] });
      if (!WIKI_LANGUAGES.includes(body.language)) return problem(400, 'request.invalid', { violations: ['language.invalid'] });
    }
    const page = wiki.pages.find((item) => item.id === match[1]);
    if (!page || !wikiVisible(account, page)) return problem(404, 'wiki.page_not_found');
    const access = wikiAccess(account, page.projectId);
    const rest = match[2] ?? '';
    if (rest === '' && method === 'GET') return wikiAnswer(account, page, requested);
    const code = requested ?? page.language;
    const variant = wikiVariant(page, code);
    if (rest === '/languages' && method === 'POST') {
      // The language of the body was checked above, before the page, the right and the archive (H1 of the review, N1 of the follow up).
      if (!access.edit) return problem(403, 'auth.forbidden');
      if (page.archived) return problem(409, 'wiki.page_archived');
      if (wikiVariant(page, body.language)) return problem(409, 'wiki.language_exists');
      addWikiLanguage(page, body.language, account);
      return { ...wikiAnswer(account, page, body.language), status: 201 };
    }
    if (rest === '/draft' && method === 'PUT') {
      if (!access.edit) return problem(403, 'auth.forbidden');
      if (page.archived) return problem(409, 'wiki.page_archived');
      if (!variant) return problem(404, 'wiki.language_not_found');
      const refused = wikiIfMatch(headers, page, variant, code);
      if (refused) return refused;
      const violations = [];
      const title = wikiTitle(body.title, violations);
      violations.push(...wikiCheck(body.content, page.id, page.projectId));
      let sourceRevision = null;
      if (code === page.language) {
        if (body.sourceRevision !== undefined && body.sourceRevision !== null) violations.push('source_revision.invalid');
      } else if (body.sourceRevision === undefined || body.sourceRevision === null) {
        sourceRevision = wikiRevision(page, variant.draftRevision, code)?.sourceRevision ?? null;
      } else if (!Number.isInteger(body.sourceRevision) || body.sourceRevision < 1 || body.sourceRevision > page.draftRevision) {
        violations.push('source_revision.invalid');
      } else {
        sourceRevision = body.sourceRevision;
      }
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      const revision = { pageId: page.id, language: code, revision: variant.draftRevision + 1, title, content: body.content, savedAt: now(), savedBy: account.id,
        basedOn: variant.draftRevision, restoredFrom: null, sourceRevision };
      wiki.revisions.push(revision);
      variant.draftRevision = revision.revision;
      wikiEvent(page, 'saved', revision.revision, account, code);
      return wikiAnswer(account, page, code);
    }
    if (rest === '/publish' && method === 'POST') {
      if (!access.publish) return problem(403, 'auth.forbidden');
      if (page.archived) return problem(409, 'wiki.page_archived');
      if (!variant) return problem(404, 'wiki.language_not_found');
      const refused = wikiIfMatch(headers, page, variant, code);
      if (refused) return refused;
      if (body.revision !== variant.draftRevision) return problem(412, 'wiki.version_conflict');
      if (variant.publishedRevision !== variant.draftRevision) {
        Object.assign(variant, { publishedRevision: variant.draftRevision, publishedAt: now(), publishedBy: account.id });
        wikiEvent(page, 'published', variant.draftRevision, account, code);
      }
      return wikiAnswer(account, page, code);
    }
    if (rest === '/withdraw' && method === 'POST') {
      if (!access.publish) return problem(403, 'auth.forbidden');
      if (page.archived) return problem(409, 'wiki.page_archived');
      if (!variant) return problem(404, 'wiki.language_not_found');
      if (variant.publishedRevision === null) return problem(409, 'wiki.not_published');
      const before = variant.publishedRevision;
      Object.assign(variant, { publishedRevision: null, publishedAt: null, publishedBy: null });
      wikiEvent(page, 'withdrawn', before, account, code);
      return wikiAnswer(account, page, code);
    }
    if (rest === '/history' && method === 'GET') {
      if (!wikiDrafts(access)) return problem(404, 'wiki.page_not_found');
      if (!variant) return problem(404, 'wiki.language_not_found');
      const revisions = wiki.revisions.filter((item) => item.pageId === page.id && (item.language ?? page.language) === code).sort((a, b) => b.revision - a.revision)
        .map(({ content: _content, pageId: _page, language: _language, ...item }) => ({ ...item, sourceRevision: item.sourceRevision ?? null,
          published: item.revision === variant.publishedRevision }));
      const events = wiki.events.filter((item) => item.pageId === page.id).map(({ pageId: _page, ...item }) => item).reverse();
      return ok({ pageId: page.id, language: code, draftRevision: variant.draftRevision, publishedRevision: variant.publishedRevision, revisions, events });
    }
    match = rest.match(/^\/revisions\/(\d+)(\/restore)?$/);
    if (match) {
      if (!wikiDrafts(access)) return problem(404, 'wiki.page_not_found');
      if (!variant) return problem(404, 'wiki.language_not_found');
      const old = wikiRevision(page, Number(match[1]), code);
      if (!old) return problem(404, 'wiki.revision_not_found');
      if (!match[2] && method === 'GET') {
        return ok({ revision: old.revision, language: code, title: old.title, content: old.content, html: wikiHtml(old.content, account, page.projectId), savedAt: old.savedAt,
          savedBy: old.savedBy, published: old.revision === variant.publishedRevision, sourceRevision: old.sourceRevision ?? null });
      }
      if (match[2] && method === 'POST') {
        if (!access.edit) return problem(403, 'auth.forbidden');
        const refused = wikiIfMatch(headers, page, variant, code);
        if (refused) return refused;
        const revision = { ...old, language: code, revision: variant.draftRevision + 1, savedAt: now(), savedBy: account.id, basedOn: variant.draftRevision,
          restoredFrom: old.revision };
        wiki.revisions.push(revision);
        variant.draftRevision = revision.revision;
        wikiEvent(page, 'reverted', revision.revision, account, code);
        return wikiAnswer(account, page, code);
      }
    }
    if (rest === '/parent' && method === 'PUT') {
      if (!access.edit) return problem(403, 'auth.forbidden');
      if (body.version !== page.version) return problem(412, 'wiki.version_conflict');
      const parentId = body.parentId ?? null;
      const violations = [];
      if (parentId !== null) {
        let current = parentId;
        while (current !== null) {
          if (current === page.id) { violations.push('parent.cycle'); break; }
          current = wiki.pages.find((item) => item.id === current)?.parentId ?? null;
        }
        if (!wiki.pages.some((item) => item.id === parentId && item.projectId === page.projectId && !item.archived)) violations.push('parent.unknown');
        else if (violations.length === 0 && wikiDepth(parentId) + wikiHeight(page.id) > 6) violations.push('parent.too_deep');
      }
      if (violations.length > 0) return problem(400, 'request.invalid', { violations });
      Object.assign(page, { parentId, version: page.version + 1 });
      wikiEvent(page, 'moved', null, account, null);
      return wikiAnswer(account, page, requested);
    }
    if ((rest === '/archive' || rest === '/unarchive') && method === 'POST') {
      if (!access.publish) return problem(403, 'auth.forbidden');
      if (body.version !== page.version) return problem(412, 'wiki.version_conflict');
      const archive = rest === '/archive';
      if (page.archived === archive) return problem(409, archive ? 'wiki.page_archived' : 'wiki.page_not_archived');
      if (archive) {
        for (const child of wiki.pages.filter((item) => item.parentId === page.id && !item.archived)) Object.assign(child, { parentId: page.parentId, version: child.version + 1 });
      } else if (page.parentId !== null && wiki.pages.find((item) => item.id === page.parentId)?.archived !== false) {
        page.parentId = null;
      }
      Object.assign(page, { archived: archive, version: page.version + 1 });
      wikiEvent(page, archive ? 'archived' : 'unarchived', null, account, null);
      return wikiAnswer(account, page, requested);
    }
    if (rest === '/images' && method === 'POST') {
      if (!access.edit) return problem(403, 'auth.forbidden');
      const content = Buffer.isBuffer(body) ? body : Buffer.alloc(0);
      if (content.length === 0) return problem(400, 'image.empty');
      if (content.length > 5 * 1024 * 1024) return problem(413, 'image.too_large');
      // The same signatures as the server: PNG, JPEG and WebP.
      const mediaType = content.subarray(0, 8).equals(PNG.subarray(0, 8)) ? 'image/png'
        : content.length >= 3 && content[0] === 0xff && content[1] === 0xd8 && content[2] === 0xff ? 'image/jpeg'
          : content.length >= 12 && content.subarray(0, 4).toString('latin1') === 'RIFF' && content.subarray(8, 12).toString('latin1') === 'WEBP' ? 'image/webp'
            : null;
      if (mediaType === null) return problem(415, 'image.type_unsupported');
      const image = { id: uuid(), pageId: page.id, size: content.length, content, mediaType };
      wiki.images.push(image);
      return { status: 201, body: { id: image.id, src: `/api/v1/wiki/images/${image.id}`, mediaType, size: image.size } };
    }
    return problem(404, 'resource.not_found');
  }

  function timeRoute(account, method, p, query, headers, body) {
    if (p === '/time/projects' && method === 'GET') {
      return ok(state.projects.map((item) => ({ projectId: item.id, key: item.key, name: item.name, access: timeAccess(account, item.id) }))
        .filter((item) => timeAny(item.access)).sort((a, b) => a.key.localeCompare(b.key)));
    }
    let match = p.match(/^\/time\/projects\/([^/]+)\/(people|entries)$/);
    if (match) {
      const projectId = match[1];
      const access = timeAccess(account, projectId);
      if (!timeAny(access)) return problem(404, 'project.not_found');
      if (match[2] === 'people' && method === 'GET') {
        if (!access.read && !access.manage) return problem(403, 'auth.forbidden');
        return ok((state.members.get(projectId) ?? []).map((member) => member.accountId));
      }
      if (match[2] === 'entries' && method === 'POST') {
        const person = body.personId ?? account.id;
        const own = person === account.id;
        if (own ? !(access.book || access.manage) : !access.manage) return problem(403, 'auth.forbidden');
        const { violations, values } = timeCheck(account, projectId, body, null);
        if (!own && !isMember({ id: person }, projectId)) violations.push('person.not_member');
        if (violations.length > 0) return problem(400, 'request.invalid', { violations });
        const at = now();
        const entry = { id: uuid(), projectId, personId: person, ...values, cancelledAt: null, cancelledBy: null, createdAt: at, createdBy: account.id,
          updatedAt: at, updatedBy: account.id, version: 1, revisions: [{ revision: 1, kind: 'created', occurredAt: at, actorId: account.id, ...values, reason: null }] };
        state.time.push(entry);
        return { status: 201, body: timeDetail(account, entry), etag: '"1"' };
      }
    }
    if (p === '/time/entries' && method === 'GET') {
      const project = query.get('project');
      const mine = query.get('mine') === 'true';
      if (mine && query.get('person') && query.get('person') !== account.id) return problem(400, 'request.invalid', { violations: ['person.with_mine'] });
      // As the backend (TASK-092): mine limits to the own entries; count and sums cover every match, only the rows stop at 500.
      const person = mine ? account.id : query.get('person');
      const ticketNumber = query.get('ticket');
      const from = query.get('from');
      const to = query.get('to');
      if (from && to && to < from) return problem(400, 'request.invalid', { violations: ['to.before_from'] });
      const entries = state.time.filter((entry) => timeVisible(account, entry)
        && (!project || entry.projectId === project) && (!person || entry.personId === person)
        && (!ticketNumber || state.tickets.find((item) => item.id === entry.ticketId)?.number === Number(ticketNumber))
        && (!from || entry.date >= from) && (!to || entry.date <= to) && (query.get('cancelled') === 'true' || entry.cancelledAt === null))
        .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
      const counted = entries.filter((entry) => entry.cancelledAt === null);
      const perPerson = new Map();
      const perTicket = new Map();
      const perDate = new Map();
      for (const entry of counted) {
        perPerson.set(entry.personId, (perPerson.get(entry.personId) ?? 0) + entry.minutes);
        perDate.set(entry.date, (perDate.get(entry.date) ?? 0) + entry.minutes);
        const ticket = timeTicket(account, entry);
        const key = `${entry.projectId}#${ticket?.number ?? ''}`;
        const sum = perTicket.get(key) ?? { projectId: entry.projectId, projectKey: state.projects.find((item) => item.id === entry.projectId)?.key ?? '', ticket, minutes: 0 };
        sum.minutes += entry.minutes;
        perTicket.set(key, sum);
      }
      return ok({ entries: entries.slice(0, 500).map((entry) => timeView(account, entry)), truncated: entries.length > 500, count: entries.length,
        totalMinutes: counted.reduce((total, entry) => total + entry.minutes, 0), perPerson: [...perPerson].map(([personId, minutes]) => ({ personId, minutes })),
        perTicket: [...perTicket.values()],
        perDate: [...perDate].sort(([a], [b]) => a.localeCompare(b)).map(([date, minutes]) => ({ date, minutes })) });
    }
    match = p.match(/^\/time\/entries\/([^/]+)(\/cancel)?$/);
    if (match) {
      const entry = state.time.find((item) => item.id === match[1]);
      if (!entry || !timeVisible(account, entry)) return problem(404, 'time.entry_not_found');
      if (method === 'GET' && !match[2]) {
        return ok(timeDetail(account, entry), `"${entry.version}"`);
      }
      const view = timeView(account, entry);
      if (!headers['if-match']) return problem(428, 'time.precondition_required');
      if (state.timeConflict) {
        state.timeConflict = false;
        return problem(412, 'time.version_conflict');
      }
      if (headers['if-match'] !== `"${entry.version}"`) return problem(412, 'time.version_conflict');
      if (entry.cancelledAt !== null) return problem(409, 'time.entry_cancelled');
      if (!view.changeable) return problem(403, 'auth.forbidden');
      const at = now();
      if (match[2] && method === 'POST') {
        if (!view.own && !(body?.reason ?? '').trim()) return problem(400, 'request.invalid', { violations: ['reason.required'] });
        Object.assign(entry, { cancelledAt: at, cancelledBy: account.id, updatedAt: at, updatedBy: account.id, version: entry.version + 1 });
        entry.revisions.push({ revision: entry.revisions.length + 1, kind: 'cancelled', occurredAt: at, actorId: account.id, ticketId: entry.ticketId, date: entry.date,
          minutes: entry.minutes, description: entry.description, reason: (body?.reason ?? '').trim() || null });
        return ok(timeDetail(account, entry), `"${entry.version}"`);
      }
      if (!match[2] && method === 'PUT') {
        const { violations, values } = timeCheck(account, entry.projectId, body, view.own);
        if (violations.length > 0) return problem(400, 'request.invalid', { violations });
        // A hidden ticket stays unless a number is given or its removal is asked for (TASK-092, F2).
        if ((body.ticketNumber ?? null) === null && view.ticketHidden && body.removeHiddenTicket !== true) values.ticketId = entry.ticketId;
        Object.assign(entry, values, { updatedAt: at, updatedBy: account.id, version: entry.version + 1 });
        entry.revisions.push({ revision: entry.revisions.length + 1, kind: 'corrected', occurredAt: at, actorId: account.id, ...values, reason: (body.reason ?? '').trim() || null });
        return ok(timeDetail(account, entry), `"${entry.version}"`);
      }
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
          sentAt: message.sentAt, fromAddress: message.fromAddress, fromName: message.fromName, to: message.to, cc: message.cc, subject: message.subject, body: message.body, attachments: message.attachments,
          newAnswer: Boolean(message.joinedAt && !message.seenAt), version: message.version })) : null,
        replies: readableHere.includes('mail') ? hd.replies.filter((reply) => reply.ticketId === ticket.id).map(replyView) : null,
        replyAllowed: Boolean(contact) && mayReply(account, item.id),
        portalAnswerAllowed: Boolean(link?.customerId) && mayReply(account, item.id),
        answerSeenAllowed: mayReply(account, item.id) };
    };
    const senders = () => hd.mailboxes.filter((mailbox) => mailbox.enabled && mailbox.projectId === item.id && mailbox.smtpHost && mailbox.address);
    const contactOfTicket = () => {
      const link = hd.ticketCustomers.get(ticket.id);
      return link?.contactId ? hd.contacts.find((entry) => entry.id === link.contactId) : null;
    };
    if (tail === '/helpdesk/portal-posts') {
      const postView = (post) => ({ id: post.id, authorKind: post.authorKind, authorId: post.authorId, body: post.body, createdAt: post.createdAt,
        attachments: state.attachments.filter((file) => post.attachmentIds.includes(file.id) && !file.removed).map(({ id, name, size, uploadedAt }) => ({ id, name, size, uploadedAt })) });
      if (method === 'GET') {
        if (!readableHere.includes('mail')) return problem(403, 'auth.forbidden');
        return ok(hd.posts.filter((post) => post.ticketId === ticket.id).map(postView));
      }
      if (!mayReply(account, item.id)) return problem(403, 'auth.forbidden');
      if (!hd.ticketCustomers.get(ticket.id)?.customerId) return problem(409, 'helpdesk.portal_no_customer');
      if (!body.body?.trim()) return problem(400, 'request.invalid', { violations: ['body.invalid'] });
      const post = { id: uuid(), ticketId: ticket.id, authorKind: 'support', authorId: account.id, body: body.body.trim(), createdAt: now(), attachmentIds: [],
        organizationId: hd.ticketCustomers.get(ticket.id).customerId, position: nextPosition(ticket.id) };
      hd.posts.push(post);
      return { status: 201, body: postView(post) };
    }
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
      if ((tail === '/helpdesk/replies' || tail === '/helpdesk/reply/preview') && method === 'POST') {
        const mailbox = senders().find((entry) => entry.id === body.mailboxId);
        const violations = [];
        if (!mailbox) violations.push('mailboxId.unavailable');
        if (!contact.addresses.includes(body.to)) violations.push('to.not_of_contact');
        if (!body.subject?.trim() || /[\r\n]/.test(body.subject)) violations.push('subject.invalid');
        if (!body.body?.trim()) violations.push('body.invalid');
        if (violations.length > 0) return problem(400, 'request.invalid', { violations });
        const code = contact.language === 'en' ? 'en' : 'de';
        const project = state.projects.find((entry) => entry.id === item.id);
        const mail = renderMail(code, state.mailTemplate[code] ?? BUILT_IN[code], state.mailTemplate.applicationUrl, state.appearance.name, state.appearance.accent,
          state.appearance.logo !== null, { contact: contact.name, number: `${project?.key ?? ''}-${ticket.number}`, title: ticket.values?.title ?? '',
            sender: account.displayName ?? account.username, mailbox: mailbox.address, body: body.body });
        if (tail === '/helpdesk/reply/preview') return ok(mail);
        const reply = { id: uuid(), ticketId: ticket.id, mail, projectId: item.id, contactId: contact.id, mailboxId: mailbox.id, fromAddress: mailbox.address, toAddress: body.to,
          subject: body.subject.trim(), body: body.body, state: 'pending', errorCode: null, sends: 1, createdAt: now(), createdBy: account.id, attemptedAt: null, sentAt: null, version: 1,
          customerId: contact.customerId, portalPosition: null };
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
        Object.assign(reply, { state: 'sent', sentAt: now(), version: reply.version + 1, portalPosition: nextPosition(ticket.id) });
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
      joinedAt: null, seenAt: null, seenBy: null, version: 1, ...extra };
    hd.messages.push(message);
    return message;
  };

  function sessionBody(session) {
    const account = session.account;
    if (!account) return { authenticated: false, username: null, displayName: null, level: null, expiresAt: null, passwordChangeBy: null, csrfToken: TOKEN };
    return { authenticated: true, username: account.username, displayName: account.displayName, kind: account.kind ?? 'internal', level: account.passwordChangeRequired ? 'password_change_required' : 'full',
      expiresAt: '2026-10-22T08:00:00Z', passwordChangeBy: account.passwordChangeRequired ? '2026-10-21T08:30:00Z' : null, csrfToken: TOKEN,
      rights: account.passwordChangeRequired ? [] : companyRights(account) };
  }
  function mailTemplateBody() {
    const language = (code) => ({ ...(state.mailTemplate[code] ?? BUILT_IN[code]), stored: state.mailTemplate[code] !== null, builtIn: BUILT_IN[code] });
    return { applicationUrl: state.mailTemplate.applicationUrl, de: language('de'), en: language('en'), placeholders: PLACEHOLDERS,
      version: state.mailTemplate.version, updatedAt: '2026-11-03T06:00:00Z' };
  }

  function checkTexts(code, texts) {
    if (!texts) return problem(400, 'mail_template.languages_missing');
    const invalid = [];
    const unknown = [];
    for (const part of ['greeting', 'signature', 'footer']) {
      const text = String(texts[part] ?? '');
      if ([...text].length > 2000 || /[\u0000-\u0008\u000b-\u001f\u007f]/.test(text)) invalid.push(`${code}.${part}`);
      for (const token of unknownPlaceholders(text)) unknown.push(`${code}.${part}: ${token}`);
    }
    if (invalid.length > 0) return problem(400, 'mail_template.text_invalid', { violations: invalid });
    if (unknown.length > 0) return problem(400, 'mail_template.placeholder_unknown', { violations: unknown });
    return null;
  }

  function checkAddress(address) {
    const value = address?.trim() ?? '';
    if (value === '') return null;
    try {
      const url = new URL(value);
      if (url.protocol === 'https:' && url.hostname !== '' && url.username === '' && !/\s/.test(value)) return null;
    } catch {
      // not an address
    }
    return problem(400, 'mail_template.address_invalid');
  }

  function appearanceBody() {
    return { name: state.appearance.name, defaultName: 'Task Manager', accentColor: state.appearance.accent, defaultAccentColor: '#f97316', logo: state.appearance.logo && (({ content, ...shown }) => shown)(state.appearance.logo), version: state.appearance.version, updatedAt: '2026-10-19T06:00:00Z' };
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
      // A test can hold the answer of one path until it resolves the promise (TASK-091, R1: a list that is still loading).
      if (state.hold?.[url.pathname]) await state.hold[url.pathname];
      const unsafe = !['GET', 'HEAD'].includes(request.method());
      const answer = unsafe && headers['x-csrf-token'] !== TOKEN ? problem(403, 'auth.csrf_invalid')
        : anonymousAppearance && url.pathname === '/api/v1/session' ? ok({ authenticated: false })
          : await handle(session, request.method(), url.pathname, url.searchParams, headers, json ?? {});
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
    // A preview of a mail (TASK-082) is a POST that changes nothing; it is no write.
    writes: () => state.requests.filter((item) => item.method !== 'GET' && !/\/(mail-template|reply)\/preview$/.test(item.path)),
    group: (name) => state.groups.find((group) => group.name === name),
    addJob,
    addMail,
    addTicket,
    addWikiPage,
    addRequirement,
    // The requirement routes without a browser, for a change of someone else in between (TASK-096).
    requirementCall: (account, method, path, body = {}, { headers = {}, query = '' } = {}) => requirementRoute(account, method, path, new URLSearchParams(query), headers, body),
    addWikiLanguage,
    // The wiki routes without a browser, for checks of the same answers as the server (H1 of the TASK-094 review).
    wikiCall: (account, method, path, body = {}, { headers = {}, query = '' } = {}) => wikiRoute(account, method, path, new URLSearchParams(query), headers, body),
    wikiSearchCall: (account, query) => wikiSearch(account, new URLSearchParams(query)),
    // A prepared cycle of a ticket, as the server would report it (TASK-068).
    addCycle: (ticket, cycle) => sla.cycles.set(ticket.id, [...(sla.cycles.get(ticket.id) ?? []), cycle]),
    addReply: (ticket, extra = {}) => {
      const link = hd.ticketCustomers.get(ticket.id);
      const contact = hd.contacts.find((entry) => entry.id === link?.contactId);
      const reply = { id: uuid(), ticketId: ticket.id, projectId: ticket.projectId, contactId: contact?.id ?? null, mailboxId: hd.mailboxes[0]?.id ?? uuid(),
        fromAddress: hd.mailboxes[0]?.address ?? 'support@firma.example', toAddress: contact?.addresses[0] ?? 'lea@nordwerk.example', subject: 'Re: Drucker defekt',
        body: 'Wir kümmern uns.', state: 'pending', errorCode: null, sends: 1, createdAt: now(), createdBy: anna.id, attemptedAt: null, sentAt: null, version: 1,
        // The customer of the contact when the reply is written, as the server keeps it (TASK-079, F1; TASK-080, H3).
        customerId: contact?.customerId ?? null, portalPosition: null, ...extra };
      if (reply.state === 'sent' && reply.portalPosition === null) reply.portalPosition = nextPosition(ticket.id);
      hd.replies.push(reply);
      return reply;
    },
    // An answer of the support through the portal at the next position of its ticket (TASK-080).
    addPortalAnswer: (ticket, body, extra = {}) => {
      const post = { id: uuid(), ticketId: ticket.id, authorKind: 'support', authorId: anna.id, body, createdAt: now(), attachmentIds: [],
        organizationId: hd.ticketCustomers.get(ticket.id)?.customerId ?? null, position: nextPosition(ticket.id), ...extra };
      hd.posts.push(post);
      return post;
    },
    deactivate: (account) => { account.active = false; },
  };
}
