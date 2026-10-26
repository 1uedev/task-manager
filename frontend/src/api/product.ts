import { guarded, INVALID_RESPONSE, parseSession, type Outcome, type ServerApi, type SessionState } from './client.ts';
import type { components as FileTypes } from './generated/files';
import type { components as IdentityTypes } from './generated/identity';
import type { components as PermissionTypes } from './generated/permissions';
import type { components as ProjectTypes } from './generated/projects';
import type { components as SearchTypes } from './generated/search';
import type { components as TagTypes } from './generated/tags';
import type { components as TicketTypes } from './generated/tickets';
import { readProblem, type ProblemInfo } from './problem.ts';

// The product API of TASK-030. Types come from the published contracts; every answer is checked again at runtime for
// the fields the interface relies on, so a server that breaks the contract yields a problem instead of a broken page.

export type Account = IdentityTypes['schemas']['Account'];
export type IssuedAccount = IdentityTypes['schemas']['IssuedAccount'];
export type DirectoryEntry = IdentityTypes['schemas']['DirectoryEntry'];
export type AccountAuditEntry = IdentityTypes['schemas']['AuditEntry'];
export type LdapSettings = IdentityTypes['schemas']['LdapSettings'];
export type LdapSettingsRequest = IdentityTypes['schemas']['LdapSettingsRequest'];
export type LdapTestResult = IdentityTypes['schemas']['LdapTestResult'];
export type ProjectSummary = ProjectTypes['schemas']['ProjectSummary'];
export type Project = ProjectTypes['schemas']['Project'];
export type Configuration = ProjectTypes['schemas']['Configuration'];
export type Member = ProjectTypes['schemas']['Member'];
export type ConfigurationDraft = ProjectTypes['schemas']['Draft'];
export type ConfigurationUsage = ProjectTypes['schemas']['Usage'];
export type Ticket = TicketTypes['schemas']['Ticket'];
export type HistoryEntry = TicketTypes['schemas']['HistoryEntry'];
export type Tag = TagTypes['schemas']['Tag'];
export type Attachment = FileTypes['schemas']['Attachment'];
export type TicketHit = SearchTypes['schemas']['TicketHit'];
export type ProjectHit = SearchTypes['schemas']['ProjectHit'];
export type Group = PermissionTypes['schemas']['Group'];
export type GroupDetail = PermissionTypes['schemas']['GroupDetail'];
export type Catalog = PermissionTypes['schemas']['Catalog'];
export type AuditEntry = PermissionTypes['schemas']['AuditEntry'];
export type ProjectRights = PermissionTypes['schemas']['ProjectRights'];
export type ProjectGroup = PermissionTypes['schemas']['ProjectGroup'];

export interface Versioned<T> {
  readonly value: T;
  // The representation version from the ETag header, sent back as If-Match; null when the endpoint has none.
  readonly etag: string | null;
}

export type Result<T> = Outcome<Versioned<T>>;

export interface LoginProblem extends ProblemInfo {
  readonly retryAfter: number | null;
  readonly violations: readonly string[];
}

interface Raw {
  readonly data?: unknown;
  readonly error?: unknown;
  readonly response: Response;
}

const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);
const hasStrings = (value: unknown, ...keys: string[]) => record(value) && keys.every((key) => typeof value[key] === 'string');
const listOf = <T>(check: (item: unknown) => boolean) => (value: unknown): T[] | null =>
  Array.isArray(value) && value.every(check) ? (value as T[]) : null;
const one = <T>(check: (item: unknown) => boolean) => (value: unknown): T | null => (check(value) ? (value as T) : null);
const nothing = (): true => true;

export const isAccount = (v: unknown) => hasStrings(v, 'id', 'username', 'authKind') && typeof (v as Account).active === 'boolean';
export const isDirectoryEntry = (v: unknown) => hasStrings(v, 'id', 'username') && typeof (v as DirectoryEntry).active === 'boolean';
export const isProjectSummary = (v: unknown) => hasStrings(v, 'id', 'key', 'name') && typeof (v as ProjectSummary).manageable === 'boolean';
export const isProject = (v: unknown) => isProjectSummary(v) && record((v as Project).configuration)
  && Array.isArray((v as Project).configuration.statuses) && Array.isArray((v as Project).configuration.ticketTypes);
export const isMember = (v: unknown) => hasStrings(v, 'accountId');
export const isTicket = (v: unknown) => hasStrings(v, 'id', 'projectId', 'key') && typeof (v as Ticket).number === 'number'
  && typeof (v as Ticket).version === 'number' && record((v as Ticket).fields) && record((v as Ticket).customFields)
  && Array.isArray((v as Ticket).writableFields);
export const isHistoryEntry = (v: unknown) => record(v) && typeof v.version === 'number' && Array.isArray(v.changes);
export const isAttachment = (v: unknown) => hasStrings(v, 'id', 'name', 'sha256', 'uploadedAt', 'uploadedBy') && typeof (v as Attachment).size === 'number';
export const isTag = (v: unknown) => hasStrings(v, 'id', 'name', 'catalog') && typeof (v as Tag).archived === 'boolean';
export const isGroup = (v: unknown) => hasStrings(v, 'id', 'name');
export const isGroupDetail = (v: unknown) => isGroup(v) && Array.isArray((v as GroupDetail).members)
  && Array.isArray((v as GroupDetail).actions) && Array.isArray((v as GroupDetail).fields);
const isPage = (item: (v: unknown) => boolean) => (v: unknown) => record(v) && typeof v.total === 'number' && Array.isArray(v.items) && v.items.every(item);

async function run<T>(call: () => Promise<Raw>, parse: (value: unknown, headers: Headers) => T | null): Promise<Result<T>> {
  return guarded(async () => {
    const { data, error, response } = await call();
    if (!response.ok) {
      return { kind: 'problem', problem: readProblem(response.status, error, response.headers.get('X-Correlation-Id')) };
    }
    const value = parse(response.status === 204 ? undefined : data, response.headers);
    if (value === null) return { kind: 'problem', problem: INVALID_RESPONSE };
    return { kind: 'ok', data: { value, etag: response.headers.get('ETag') } };
  });
}

const ifMatch = (etag: string) => ({ header: { 'If-Match': etag } });

// ---------------------------------------------------------------- session

// Login and password change carry details the generic problem drops: the waiting time and the reasons of a policy.
async function sessionCall(call: () => Promise<Raw>): Promise<Outcome<SessionState> | { kind: 'refused'; problem: LoginProblem }> {
  try {
    const { data, error, response } = await call();
    if (!response.ok) {
      const base = readProblem(response.status, error, response.headers.get('X-Correlation-Id'));
      const retry = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
      const raw = record(error) && Array.isArray(error.violations) ? error.violations : [];
      return { kind: 'refused', problem: { ...base, retryAfter: Number.isFinite(retry) ? retry : null, violations: raw.filter((v): v is string => typeof v === 'string') } };
    }
    const session = parseSession(data);
    return session ? { kind: 'ok', data: session } : { kind: 'problem', problem: INVALID_RESPONSE };
  } catch (failure) {
    if (failure instanceof DOMException && failure.name === 'AbortError') throw failure;
    return { kind: 'problem', problem: { kind: 'network', status: null, code: null, correlationId: null } };
  }
}

export const login = (api: ServerApi, username: string, password: string) =>
  sessionCall(() => api.identity.POST('/session', { body: { username, password } }));

export const changePassword = (api: ServerApi, currentPassword: string, newPassword: string) =>
  sessionCall(() => api.identity.POST('/session/password', { body: { currentPassword, newPassword } }));

export const logout = (api: ServerApi) => run(() => api.identity.DELETE('/session'), nothing);

// ---------------------------------------------------------------- accounts and directory

export const listAccounts = (api: ServerApi) => run(() => api.identity.GET('/accounts'), listOf<Account>(isAccount));
export const createAccount = (api: ServerApi, username: string, displayName: string | null) =>
  run(() => api.identity.POST('/accounts', { body: { username, displayName } }),
    one<IssuedAccount>((v) => record(v) && isAccount(v.account) && typeof v.oneTimePassword === 'string'));
export const deactivateAccount = (api: ServerApi, id: string) =>
  run(() => api.identity.POST('/accounts/{accountId}/deactivate', { params: { path: { accountId: id } } }), one<Account>(isAccount));
export const reactivateAccount = (api: ServerApi, id: string) =>
  run(() => api.identity.POST('/accounts/{accountId}/reactivate', { params: { path: { accountId: id } } }), one<Account>(isAccount));
export const resetPassword = (api: ServerApi, id: string) =>
  run(() => api.identity.POST('/accounts/{accountId}/password-reset', { params: { path: { accountId: id } } }),
    one<IssuedAccount>((v) => record(v) && isAccount(v.account) && typeof v.oneTimePassword === 'string'));
export const convertAccountToLocal = (api: ServerApi, id: string) =>
  run(() => api.identity.POST('/accounts/{accountId}/convert-to-local', { params: { path: { accountId: id } } }),
    one<IssuedAccount>((v) => record(v) && isAccount(v.account) && typeof v.oneTimePassword === 'string'));
export const renameAccount = (api: ServerApi, id: string, displayName: string | null) =>
  run(() => api.identity.PUT('/accounts/{accountId}', { params: { path: { accountId: id } }, body: { displayName } }), one<Account>(isAccount));
export const clearLoginDelay = (api: ServerApi, id: string) =>
  run(() => api.identity.POST('/accounts/{accountId}/clear-login-delay', { params: { path: { accountId: id } } }), one<Account>(isAccount));
export const ACCOUNT_AUDIT_PAGE = 50;
// Newest first; the next page starts below the smallest identifier of the previous one (TASK-045).
export const readAccountAudit = (api: ServerApi, before: number | null) =>
  run(() => api.identity.GET('/accounts/audit', { params: { query: { limit: ACCOUNT_AUDIT_PAGE, ...(before === null ? {} : { before }) } } }),
    listOf<AccountAuditEntry>((v) => hasStrings(v, 'occurredAt', 'actor', 'event') && typeof (v as AccountAuditEntry).id === 'number'));

// The connection to the LDAP directory (TASK-046). The password of the search account is never part of a request or an answer.
const isLdapSettings = (v: unknown) => record(v) && typeof v.configured === 'boolean' && typeof v.enabled === 'boolean' && typeof v.bindPasswordSet === 'boolean';
export const readLdapSettings = (api: ServerApi) => run(() => api.identity.GET('/settings/ldap'), one<LdapSettings>(isLdapSettings));
export const saveLdapSettings = (api: ServerApi, body: LdapSettingsRequest) =>
  run(() => api.identity.PUT('/settings/ldap', { body }), one<LdapSettings>(isLdapSettings));
// Checks the given values without storing them, or the stored ones when none are given.
export const testLdapSettings = (api: ServerApi, settings: LdapSettingsRequest | null, username: string | null) =>
  run(() => api.identity.POST('/settings/ldap/test', { body: { ...(settings === null ? {} : { settings }), ...(username === null ? {} : { username }) } }),
    one<LdapTestResult>((v) => record(v) && typeof v.ok === 'boolean'));
export const readDirectory = (api: ServerApi) => run(() => api.identity.GET('/directory/accounts'), listOf<DirectoryEntry>(isDirectoryEntry));

// ---------------------------------------------------------------- projects

export const listProjects = (api: ServerApi) => run(() => api.projects.GET('/projects'), listOf<ProjectSummary>(isProjectSummary));
export const createProject = (api: ServerApi, key: string, name: string, description: string | null) =>
  run(() => api.projects.POST('/projects', { body: { key, name, description } }), one<Project>(isProject));
export const readProject = (api: ServerApi, id: string) =>
  run(() => api.projects.GET('/projects/{id}', { params: { path: { id } } }), one<Project>(isProject));
export const updateProject = (api: ServerApi, id: string, etag: string, name: string, description: string | null) =>
  run(() => api.projects.PUT('/projects/{id}', { params: { path: { id }, ...ifMatch(etag) }, body: { name, description } }), one<Project>(isProject));

// The configuration is edited as a draft and published as the next version (TASK-044). The draft has a revision of its own,
// sent back as If-Match; publishing needs the version of the project.
export const readDraft = (api: ServerApi, id: string) =>
  run(() => api.projects.GET('/projects/{id}/configuration/draft', { params: { path: { id } } }),
    one<ConfigurationDraft>((v) => record(v) && typeof v.revision === 'number' && typeof v.baseVersion === 'number' && record(v.configuration)));
export const saveDraft = (api: ServerApi, id: string, revisionEtag: string | null, baseVersion: number, configuration: Configuration) =>
  run(() => api.projects.PUT('/projects/{id}/configuration/draft', {
    params: { path: { id }, ...(revisionEtag === null ? {} : { header: { 'If-Match': revisionEtag } }) }, body: { baseVersion, configuration },
  }), one<ConfigurationDraft>((v) => record(v) && typeof v.revision === 'number' && record(v.configuration)));
export const discardDraft = (api: ServerApi, id: string, revisionEtag: string) =>
  run(() => api.projects.DELETE('/projects/{id}/configuration/draft', { params: { path: { id }, ...ifMatch(revisionEtag) } }), nothing);
export const publishDraft = (api: ServerApi, id: string, projectEtag: string, draftRevision: number,
  statusMapping: Readonly<Record<string, string>>, typeMapping: Readonly<Record<string, string>>) =>
  run(() => api.projects.POST('/projects/{id}/configuration/publish', {
    params: { path: { id }, ...ifMatch(projectEtag) }, body: { draftRevision, statusMapping: { ...statusMapping }, typeMapping: { ...typeMapping } },
  }), one<Project>(isProject));
export const readUsage = (api: ServerApi, id: string) =>
  run(() => api.projects.GET('/projects/{id}/configuration/usage', { params: { path: { id } } }),
    listOf<ConfigurationUsage>((v) => hasStrings(v, 'type', 'status') && typeof (v as ConfigurationUsage).count === 'number'));
export const listMembers = (api: ServerApi, id: string) =>
  run(() => api.projects.GET('/projects/{id}/members', { params: { path: { id } } }), listOf<Member>(isMember));
export const addMember = (api: ServerApi, id: string, etag: string, accountId: string) =>
  run(() => api.projects.PUT('/projects/{id}/members/{accountId}', { params: { path: { id, accountId }, ...ifMatch(etag) } }), listOf<Member>(isMember));
export const removeMember = (api: ServerApi, id: string, etag: string, accountId: string) =>
  run(() => api.projects.DELETE('/projects/{id}/members/{accountId}', { params: { path: { id, accountId }, ...ifMatch(etag) } }), listOf<Member>(isMember));

// ---------------------------------------------------------------- tickets

export type CustomValue = string | number | boolean;
export type CustomValues = Readonly<Record<string, CustomValue | null>>;

export interface TicketDraft {
  readonly type: string;
  readonly title: string;
  readonly description?: string | null;
  readonly assignee?: string | null;
  // Values of custom fields by key; null empties a field in a change.
  readonly customFields?: CustomValues;
}

export type TicketChange = Partial<{ title: string; description: string | null; type: string; status: string; assignee: string | null;
  customFields: CustomValues }>;

export const listTickets = (api: ServerApi, projectId: string, before?: number) =>
  run(() => api.tickets.GET('/projects/{projectId}/tickets', { params: { path: { projectId }, query: { limit: 50, ...(before ? { before } : {}) } } }),
    listOf<Ticket>(isTicket));
export const createTicket = (api: ServerApi, projectId: string, draft: TicketDraft) =>
  run(() => api.tickets.POST('/projects/{projectId}/tickets', { params: { path: { projectId } }, body: draft }), one<Ticket>(isTicket));
export const readTicket = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.tickets.GET('/projects/{projectId}/tickets/{number}', { params: { path: { projectId, number } } }), one<Ticket>(isTicket));
export const updateTicket = (api: ServerApi, projectId: string, number: number, etag: string, change: TicketChange) =>
  run(() => api.tickets.PATCH('/projects/{projectId}/tickets/{number}', { params: { path: { projectId, number }, ...ifMatch(etag) }, body: change }),
    one<Ticket>(isTicket));
export const readHistory = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.tickets.GET('/projects/{projectId}/tickets/{number}/history', { params: { path: { projectId, number } } }),
    listOf<HistoryEntry>(isHistoryEntry));

// ---------------------------------------------------------------- tags

export const companyTags = (api: ServerApi) => run(() => api.tags.GET('/tags'), listOf<Tag>(isTag));
export const projectTags = (api: ServerApi, projectId: string) =>
  run(() => api.tags.GET('/projects/{projectId}/catalog/tags', { params: { path: { projectId } } }), listOf<Tag>(isTag));
export const createProjectTag = (api: ServerApi, projectId: string, name: string) =>
  run(() => api.tags.POST('/projects/{projectId}/catalog/tags', { params: { path: { projectId } }, body: { name } }), one<Tag>(isTag));
export const createCompanyTag = (api: ServerApi, name: string) => run(() => api.tags.POST('/tags', { body: { name } }), one<Tag>(isTag));
export const ticketTags = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.tags.GET('/projects/{projectId}/tickets/{number}/tags', { params: { path: { projectId, number } } }), listOf<Tag>(isTag));
export const assignTicketTag = (api: ServerApi, projectId: string, number: number, tagId: string) =>
  run(() => api.tags.POST('/projects/{projectId}/tickets/{number}/tags', { params: { path: { projectId, number } }, // The default catalog is not sent; the server reads it only together with a name.
    body: { tagId } as { tagId: string; catalog: 'project' } }), nothing);
export const removeTicketTag = (api: ServerApi, projectId: string, number: number, tagId: string) =>
  run(() => api.tags.DELETE('/projects/{projectId}/tickets/{number}/tags/{tagId}', { params: { path: { projectId, number, tagId } } }), nothing);

// ---------------------------------------------------------------- attachments (TASK-051)

/** The attachments of a ticket and the largest file the server accepts, or null when it does not say (review F8 of TASK-051). */
export interface AttachmentList {
  readonly items: readonly Attachment[];
  readonly maxBytes: number | null;
}

export const listAttachments = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.files.GET('/projects/{projectId}/tickets/{number}/attachments', { params: { path: { projectId, number } } }),
    (value, headers): AttachmentList | null => {
      const items = listOf<Attachment>(isAttachment)(value);
      const limit = Number(headers.get('X-Attachment-Max-Bytes'));
      return items === null ? null : { items, maxBytes: Number.isSafeInteger(limit) && limit > 0 ? limit : null };
    });
// The file is the raw body of the request; the server cleans the name, so it is sent as it is (encoded by the client library).
export const uploadAttachment = (api: ServerApi, projectId: string, number: number, file: File) =>
  run(() => api.files.POST('/projects/{projectId}/tickets/{number}/attachments', {
    params: { path: { projectId, number }, query: { name: file.name } },
    body: file as unknown as string,
    bodySerializer: (body) => body as unknown as BodyInit,
    headers: { 'Content-Type': 'application/octet-stream' },
  }), one<Attachment>(isAttachment));
export const removeAttachment = (api: ServerApi, projectId: string, number: number, id: string) =>
  run(() => api.files.DELETE('/projects/{projectId}/tickets/{number}/attachments/{id}', { params: { path: { projectId, number, id } } }), nothing);
// A link, not a request: the browser saves the file itself, with the session cookie and the headers of the download.
export const attachmentUrl = (projectId: string, number: number, id: string) => `/api/v1/projects/${projectId}/tickets/${number}/attachments/${id}`;

/**
 * Asks with HEAD whether a download can succeed, so a missing file, a withdrawn right or an unreachable store is explained
 * instead of ending in a cancelled download without a word (review F4 of TASK-051). The download itself stays a link.
 */
export const checkAttachment = (api: ServerApi, projectId: string, number: number, id: string) =>
  guarded(async (): Promise<Result<true>> => {
    const response = await api.head(`/projects/${projectId}/tickets/${number}/attachments/${id}`);
    return response.ok ? { kind: 'ok', data: { value: true, etag: null } }
      : { kind: 'problem', problem: readProblem(response.status, null, response.headers.get('X-Correlation-Id')) };
  });

// ---------------------------------------------------------------- search

export interface TicketQuery {
  readonly q?: string;
  readonly project?: readonly string[];
  readonly status?: readonly string[];
  readonly tag?: readonly string[];
  readonly offset?: number;
}

export const searchTickets = (api: ServerApi, query: TicketQuery) =>
  run(() => api.search.GET('/search/tickets', {
    params: { query: { ...(query.q ? { q: query.q } : {}), ...(query.project?.length ? { project: [...query.project] } : {}),
      ...(query.status?.length ? { status: [...query.status] } : {}), ...(query.tag?.length ? { tag: [...query.tag] } : {}),
      limit: 20, offset: query.offset ?? 0 } },
    querySerializer: { array: { style: 'form', explode: true } },
  }), one<{ total: number; items: TicketHit[] }>(isPage((v) => hasStrings(v, 'id', 'key', 'projectId'))));
export const searchProjects = (api: ServerApi, q: string) =>
  run(() => api.search.GET('/search/projects', { params: { query: { q, limit: 20 } } }),
    one<{ total: number; items: ProjectHit[] }>(isPage((v) => hasStrings(v, 'id', 'key', 'name'))));

// ---------------------------------------------------------------- groups and rights

export const permissionCatalog = (api: ServerApi) =>
  run(() => api.permissions.GET('/permissions/catalog'), one<Catalog>((v) => record(v) && Array.isArray(v.companyActions) && Array.isArray(v.projectActions) && Array.isArray(v.fields)));
export const listGroups = (api: ServerApi) => run(() => api.permissions.GET('/permissions/groups'), listOf<Group>(isGroup));
export const createGroup = (api: ServerApi, name: string) =>
  run(() => api.permissions.POST('/permissions/groups', { body: { name } }), one<GroupDetail>(isGroupDetail));
export const readGroup = (api: ServerApi, groupId: string) =>
  run(() => api.permissions.GET('/permissions/groups/{groupId}', { params: { path: { groupId } } }), one<GroupDetail>(isGroupDetail));

// A change answers with the group, or with nothing when the person took the right to manage groups from themselves.
const detailOrNothing = (value: unknown): GroupDetail | 'gone' | null => (value === undefined ? 'gone' : isGroupDetail(value) ? (value as GroupDetail) : null);

export const setGroupMember = (api: ServerApi, groupId: string, accountId: string, enabled: boolean) =>
  run(() => (enabled
    ? api.permissions.PUT('/permissions/groups/{groupId}/members/{accountId}', { params: { path: { groupId, accountId } } })
    : api.permissions.DELETE('/permissions/groups/{groupId}/members/{accountId}', { params: { path: { groupId, accountId } } })), detailOrNothing);
export const setGroupAction = (api: ServerApi, groupId: string, action: string, projectId: string | null, enabled: boolean) =>
  run(() => api.permissions.PUT('/permissions/groups/{groupId}/actions', { params: { path: { groupId } }, body: { action, projectId, enabled } }), detailOrNothing);
// A fixed field of a ticket, or a custom field of a project as custom.<key>.
export type TicketField = string;

export const AUDIT_PAGE = 50;
const isAuditEntry = (v: unknown) => hasStrings(v, 'id', 'actorId', 'occurredAt', 'operation') && typeof (v as AuditEntry).rightsVersion === 'number';
// Newest first; the next page starts below the lowest rights version of the previous one (TASK-043).
export const readAudit = (api: ServerApi, before: number | null) =>
  run(() => api.permissions.GET('/permissions/audit', { params: { query: { limit: AUDIT_PAGE, ...(before === null ? {} : { before }) } } }),
    listOf<AuditEntry>(isAuditEntry));
export const readProjectRights = (api: ServerApi, projectId: string) =>
  run(() => api.permissions.GET('/permissions/projects/{projectId}/groups', { params: { path: { projectId } } }),
    one<ProjectRights>((v) => record(v) && Array.isArray(v.actions) && Array.isArray(v.fields) && Array.isArray(v.groups)
      && v.groups.every((group) => isGroup(group) && Array.isArray((group as ProjectGroup).actions) && Array.isArray((group as ProjectGroup).fields))));

export const setGroupField = (api: ServerApi, groupId: string, field: TicketField, projectId: string | null,
  operation: 'read' | 'write', effect: 'allow' | 'deny' | 'remove') =>
  run(() => api.permissions.PUT('/permissions/groups/{groupId}/fields', { params: { path: { groupId } }, body: { resourceType: 'ticket', field, projectId, operation, effect } }), detailOrNothing);
