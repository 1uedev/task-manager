import { guarded, INVALID_RESPONSE, parseSession, type Outcome, type ServerApi, type SessionState } from './client.ts';
import type { components as FileTypes } from './generated/files';
import type { components as HelpdeskTypes } from './generated/helpdesk';
import type { components as IdentityTypes } from './generated/identity';
import type { components as JobTypes } from './generated/jobs';
import type { components as PermissionTypes } from './generated/permissions';
import type { components as ProjectTypes } from './generated/projects';
import type { components as SearchTypes } from './generated/search';
import type { components as SlaTypes } from './generated/sla';
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
export type TagColor = TagTypes['schemas']['Color'];
export type TagUsage = TagTypes['schemas']['Usage'];
export type TagMergePreview = TagTypes['schemas']['MergePreview'];
export type TagSuggestions = TagTypes['schemas']['Suggestions'];
export type Attachment = FileTypes['schemas']['Attachment'];
export type TicketHit = SearchTypes['schemas']['TicketHit'];
export type ProjectHit = SearchTypes['schemas']['ProjectHit'];
export type Group = PermissionTypes['schemas']['Group'];
export type GroupDetail = PermissionTypes['schemas']['GroupDetail'];
export type Catalog = PermissionTypes['schemas']['Catalog'];
export type AuditEntry = PermissionTypes['schemas']['AuditEntry'];
export type ProjectRights = PermissionTypes['schemas']['ProjectRights'];
export type ProjectGroup = PermissionTypes['schemas']['ProjectGroup'];
export type Job = JobTypes['schemas']['Job'];
export type JobState = JobTypes['schemas']['JobState'];
export type JobSummary = JobTypes['schemas']['JobSummary'];
export type HelpdeskStatus = HelpdeskTypes['schemas']['HelpdeskStatus'];
export type Customer = HelpdeskTypes['schemas']['Customer'];
export type Contact = HelpdeskTypes['schemas']['Contact'];
export type Mailbox = HelpdeskTypes['schemas']['Mailbox'];
export type MailboxRequest = HelpdeskTypes['schemas']['MailboxRequest'];
export type MailboxTest = HelpdeskTypes['schemas']['MailboxTest'];
export type MailMessage = HelpdeskTypes['schemas']['Message'];
export type MessageState = HelpdeskTypes['schemas']['MessageState'];
export type InboxSummary = HelpdeskTypes['schemas']['InboxSummary'];
export type Candidate = HelpdeskTypes['schemas']['Candidate'];
export type CreatedFromMail = HelpdeskTypes['schemas']['CreatedTicket'];
export type TicketHelpdesk = HelpdeskTypes['schemas']['TicketHelpdesk'];
export type CustomerOption = HelpdeskTypes['schemas']['CustomerOption'];
export type Reply = HelpdeskTypes['schemas']['Reply'];
export type ReplyForm = HelpdeskTypes['schemas']['ReplyForm'];
export type OpenReply = HelpdeskTypes['schemas']['OpenReply'];
export type SlaCalendar = SlaTypes['schemas']['Calendar'];
export type SlaCalendarRequest = SlaTypes['schemas']['CalendarRequest'];
export type SlaRule = SlaTypes['schemas']['Rule'];
export type SlaRuleRequest = SlaTypes['schemas']['RuleRequest'];
export type SlaBudgets = SlaTypes['schemas']['Budgets'];
export type SlaOptions = SlaTypes['schemas']['Options'];
export type SlaPriority = SlaTypes['schemas']['Priority'];
export type SlaClock = SlaTypes['schemas']['Clock'];
export type SlaCycle = SlaTypes['schemas']['Cycle'];
export type SlaStage = SlaTypes['schemas']['Stage'];
export type SlaReachedStage = SlaTypes['schemas']['ReachedStage'];
export type SlaEscalation = SlaTypes['schemas']['EscalationItem'];
export type TicketSla = SlaTypes['schemas']['TicketSla'];

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
const isSuggestions = (v: unknown) => record(v) && Array.isArray(v.items) && v.items.every(isTag) && typeof v.createCompany === 'boolean'
  && typeof v.createProject === 'boolean';
const isMergePreview = (v: unknown) => record(v) && isTag(v.source) && isTag(v.target) && record(v.usage);
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
// A name the catalog does not hold yet is created there in the same request (TASK-028).
export const assignTicketTagByName = (api: ServerApi, projectId: string, number: number, name: string, catalog: 'company' | 'project') =>
  run(() => api.tags.POST('/projects/{projectId}/tickets/{number}/tags', { params: { path: { projectId, number } }, body: { name, catalog } }), nothing);
export const removeTicketTag = (api: ServerApi, projectId: string, number: number, tagId: string) =>
  run(() => api.tags.DELETE('/projects/{projectId}/tickets/{number}/tags/{tagId}', { params: { path: { projectId, number, tagId } } }), nothing);

// The catalog a page maintains: the company catalog, or the catalog of one project (TASK-072).
export type TagScope = { readonly kind: 'company' } | { readonly kind: 'project'; readonly projectId: string };

export interface CatalogQuery {
  readonly q: string;
  readonly archived: boolean;
  readonly after: string | null;
}

export const CATALOG_PAGE = 50;

export const catalogTags = (api: ServerApi, scope: TagScope, query: CatalogQuery) => {
  const params = { ...(query.q.trim() === '' ? {} : { q: query.q.trim() }), archived: query.archived, usage: true, limit: CATALOG_PAGE,
    ...(query.after === null ? {} : { after: query.after }) };
  return scope.kind === 'company'
    ? run(() => api.tags.GET('/tags', { params: { query: params } }), listOf<Tag>(isTag))
    : run(() => api.tags.GET('/projects/{projectId}/catalog/tags', { params: { path: { projectId: scope.projectId }, query: params } }), listOf<Tag>(isTag));
};

export interface TagChange {
  readonly name?: string;
  readonly description?: string | null;
  readonly color?: TagColor | null;
  readonly archived?: boolean;
}

export const changeTag = (api: ServerApi, scope: TagScope, id: string, etag: string, change: TagChange) => scope.kind === 'company'
  ? run(() => api.tags.PATCH('/tags/{tagId}', { params: { path: { tagId: id }, ...ifMatch(etag) }, body: change }), one<Tag>(isTag))
  : run(() => api.tags.PATCH('/projects/{projectId}/catalog/tags/{tagId}', { params: { path: { projectId: scope.projectId, tagId: id }, ...ifMatch(etag) },
    body: change }), one<Tag>(isTag));

export const previewMerge = (api: ServerApi, scope: TagScope, id: string, into: string) => scope.kind === 'company'
  ? run(() => api.tags.GET('/tags/{tagId}/merge', { params: { path: { tagId: id }, query: { into } } }), one<TagMergePreview>(isMergePreview))
  : run(() => api.tags.GET('/projects/{projectId}/catalog/tags/{tagId}/merge', { params: { path: { projectId: scope.projectId, tagId: id }, query: { into } } }),
    one<TagMergePreview>(isMergePreview));

// The version of the source goes along as If-Match; the answer is the target.
export const mergeTag = (api: ServerApi, scope: TagScope, id: string, version: number, into: string) => scope.kind === 'company'
  ? run(() => api.tags.POST('/tags/{tagId}/merge', { params: { path: { tagId: id }, ...ifMatch(`"${version}"`) }, body: { into } }), one<Tag>(isTag))
  : run(() => api.tags.POST('/projects/{projectId}/catalog/tags/{tagId}/merge', { params: { path: { projectId: scope.projectId, tagId: id },
    ...ifMatch(`"${version}"`) }, body: { into } }), one<Tag>(isTag));

// Suggestions while typing: from the company catalog for projects, with the catalog of the project for its tickets.
export const tagSuggestions = (api: ServerApi, projectId: string | null, q: string) => {
  const query = q.trim() === '' ? {} : { q: q.trim() };
  return projectId === null
    ? run(() => api.tags.GET('/tags/suggestions', { params: { query } }), one<TagSuggestions>(isSuggestions))
    : run(() => api.tags.GET('/projects/{projectId}/catalog/suggestions', { params: { path: { projectId }, query } }), one<TagSuggestions>(isSuggestions));
};

// Tags by identifier, archived and merged ones included, so a history names tags that left the catalog.
export const lookupTags = (api: ServerApi, projectId: string, ids: readonly string[]) =>
  run(() => api.tags.GET('/projects/{projectId}/catalog/lookup', { params: { path: { projectId }, query: { id: [...ids] } } }), listOf<Tag>(isTag));

// Bulk change (TASK-073): each ticket is checked and changed for itself; the answer counts and names what was readable.
export type TagBulkResult = TagTypes['schemas']['BulkResult'];
export const TAG_BULK_TICKETS = 500;
export const bulkTicketTags = (api: ServerApi, action: 'add' | 'remove', tags: readonly string[], tickets: readonly string[]) =>
  run(() => api.tags.POST('/tags/bulk', { body: { action, tags: [...tags], tickets: [...tickets] } }),
    one<TagBulkResult>((v) => record(v) && typeof v.changed === 'number' && Array.isArray(v.items)));

export const assignedProjectTags = (api: ServerApi, projectId: string) =>
  run(() => api.tags.GET('/projects/{projectId}/tags', { params: { path: { projectId } } }), listOf<Tag>(isTag));
export const assignProjectTag = (api: ServerApi, projectId: string, target: { readonly tagId: string } | { readonly name: string }) =>
  run(() => api.tags.POST('/projects/{projectId}/tags', { params: { path: { projectId } }, body: target as { tagId: string } }), nothing);
export const removeProjectTag = (api: ServerApi, projectId: string, tagId: string) =>
  run(() => api.tags.DELETE('/projects/{projectId}/tags/{tagId}', { params: { path: { projectId, tagId } } }), nothing);

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

// How the chosen tags filter (TASK-073): all of them, at least one, none of them, or tickets without any tag.
export type TagMatch = 'all' | 'any' | 'none' | 'untagged';
export const TAG_FILTER_MAX = 10;

export interface TicketQuery {
  readonly q?: string;
  readonly project?: readonly string[];
  readonly status?: readonly string[];
  readonly tag?: readonly string[];
  readonly tagMatch?: TagMatch;
  readonly limit?: number;
  readonly offset?: number;
}

export const searchTickets = (api: ServerApi, query: TicketQuery) =>
  run(() => api.search.GET('/search/tickets', {
    params: { query: { ...(query.q ? { q: query.q } : {}), ...(query.project?.length ? { project: [...query.project] } : {}),
      ...(query.status?.length ? { status: [...query.status] } : {}),
      ...(query.tagMatch !== 'untagged' && query.tag?.length ? { tag: [...query.tag] } : {}),
      // Any or none without a tag asks nothing, as all without a tag does.
      ...(query.tagMatch === 'untagged' || ((query.tagMatch === 'any' || query.tagMatch === 'none') && query.tag?.length) ? { tagMatch: query.tagMatch } : {}),
      limit: query.limit ?? 20, offset: query.offset ?? 0 } },
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

// ---------------------------------------------------------------- background jobs (TASK-054)

export const JOB_STATES: readonly JobState[] = ['queued', 'running', 'succeeded', 'failed', 'cancelled'];
export const JOBS_PAGE = 50;
const isJob = (v: unknown) => hasStrings(v, 'id', 'kind', 'state', 'runAt', 'createdAt') && typeof (v as Job).position === 'number'
  && typeof (v as Job).attempts === 'number' && typeof (v as Job).maxAttempts === 'number';
const isJobSummary = (v: unknown) => record(v) && record(v.states) && JOB_STATES.every((state) => typeof (v.states as Record<string, unknown>)[state] === 'number')
  && typeof v.activeWorkers === 'number' && typeof v.backlog === 'boolean';
export const readJobSummary = (api: ServerApi) => run(() => api.jobs.GET('/jobs/summary'), one<JobSummary>(isJobSummary));
// Newest first; the next page starts below the smallest position of the previous one.
export const listJobs = (api: ServerApi, state: JobState | null, before: number | null) =>
  run(() => api.jobs.GET('/jobs', { params: { query: { limit: JOBS_PAGE, ...(state === null ? {} : { state }), ...(before === null ? {} : { before }) } } }),
    listOf<Job>(isJob));
export const retryJob = (api: ServerApi, id: string) => run(() => api.jobs.POST('/jobs/{id}/retry', { params: { path: { id } } }), one<Job>(isJob));
export const cancelJob = (api: ServerApi, id: string) => run(() => api.jobs.POST('/jobs/{id}/cancel', { params: { path: { id } } }), one<Job>(isJob));

// ---------------------------------------------------------------- helpdesk (TASK-057)

export const HELPDESK_STATES: readonly MessageState[] = ['review', 'automatic', 'ticket', 'discarded'];
export const INBOX_PAGE = 50;
const isCustomer = (v: unknown) => hasStrings(v, 'id', 'name') && Array.isArray((v as Customer).projects) && typeof (v as Customer).version === 'number';
const isContact = (v: unknown) => hasStrings(v, 'id', 'customerId', 'name') && Array.isArray((v as Contact).addresses);
const isMailbox = (v: unknown) => hasStrings(v, 'id', 'name', 'projectId', 'imapHost', 'username') && typeof (v as Mailbox).passwordSet === 'boolean'
  && record((v as Mailbox).status);
const isMessage = (v: unknown) => hasStrings(v, 'id', 'projectId', 'receivedAt', 'state') && typeof (v as MailMessage).version === 'number'
  && Array.isArray((v as MailMessage).attachments);
const isTicketHelpdesk = (v: unknown) => record(v) && Array.isArray(v.readable) && Array.isArray(v.writable);

export const readHelpdeskStatus = (api: ServerApi) =>
  run(() => api.helpdesk.GET('/helpdesk/status'), one<HelpdeskStatus>((v) => record(v) && typeof v.secretKey === 'boolean'));
export const listCustomers = (api: ServerApi) => run(() => api.helpdesk.GET('/helpdesk/customers'), listOf<Customer>(isCustomer));
export const createCustomer = (api: ServerApi, name: string, projects: readonly string[]) =>
  run(() => api.helpdesk.POST('/helpdesk/customers', { body: { name, projects: [...projects] } }), one<Customer>(isCustomer));
export const updateCustomer = (api: ServerApi, id: string, version: number, name: string, projects: readonly string[]) =>
  run(() => api.helpdesk.PUT('/helpdesk/customers/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) }, body: { name, projects: [...projects] } }),
    one<Customer>(isCustomer));
export const listContacts = (api: ServerApi) => run(() => api.helpdesk.GET('/helpdesk/contacts'), listOf<Contact>(isContact));
export const createContact = (api: ServerApi, customerId: string, name: string, addresses: readonly string[]) =>
  run(() => api.helpdesk.POST('/helpdesk/contacts', { body: { customerId, name, addresses: [...addresses] } }), one<Contact>(isContact));
export const updateContact = (api: ServerApi, id: string, version: number, customerId: string, name: string, addresses: readonly string[]) =>
  run(() => api.helpdesk.PUT('/helpdesk/contacts/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) }, body: { customerId, name, addresses: [...addresses] } }),
    one<Contact>(isContact));
export const listMailboxes = (api: ServerApi) => run(() => api.helpdesk.GET('/helpdesk/mailboxes'), listOf<Mailbox>(isMailbox));
export const createMailbox = (api: ServerApi, body: MailboxRequest) =>
  run(() => api.helpdesk.POST('/helpdesk/mailboxes', { body }), one<Mailbox>(isMailbox));
export const updateMailbox = (api: ServerApi, id: string, version: number, body: MailboxRequest) =>
  run(() => api.helpdesk.PUT('/helpdesk/mailboxes/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) }, body }), one<Mailbox>(isMailbox));
export const testMailbox = (api: ServerApi, id: string) =>
  run(() => api.helpdesk.POST('/helpdesk/mailboxes/{id}/test', { params: { path: { id } } }), one<MailboxTest>((v) => record(v) && typeof v.ok === 'boolean'));

// Newest first; the next page starts below the receipt time of the last mail.
export const listInbox = (api: ServerApi, state: MessageState, before: string | null) =>
  run(() => api.helpdesk.GET('/helpdesk/inbox', { params: { query: { state, limit: INBOX_PAGE, ...(before === null ? {} : { before }) } } }),
    listOf<MailMessage>(isMessage));
export const readInboxSummary = (api: ServerApi) =>
  run(() => api.helpdesk.GET('/helpdesk/inbox/summary'), one<InboxSummary>((v) => record(v) && typeof v.review === 'number' && typeof v.automatic === 'number'));
export const readInboxMessage = (api: ServerApi, id: string) =>
  run(() => api.helpdesk.GET('/helpdesk/inbox/{id}', { params: { path: { id } } }), one<MailMessage>(isMessage));
export const listCandidates = (api: ServerApi, id: string) =>
  run(() => api.helpdesk.GET('/helpdesk/inbox/{id}/candidates', { params: { path: { id } } }),
    listOf<Candidate>((v) => hasStrings(v, 'contactId', 'contactName', 'customerId', 'customerName')));
export const ticketFromMessage = (api: ServerApi, id: string, etag: string, contactId: string | null) =>
  run(() => api.helpdesk.POST('/helpdesk/inbox/{id}/ticket', { params: { path: { id }, ...ifMatch(etag) }, body: { contactId } }),
    one<CreatedFromMail>((v) => hasStrings(v, 'ticketId', 'projectId') && typeof (v as CreatedFromMail).number === 'number'));
export const discardMessage = (api: ServerApi, id: string, etag: string, reason: string) =>
  run(() => api.helpdesk.POST('/helpdesk/inbox/{id}/discard', { params: { path: { id }, ...ifMatch(etag) }, body: { reason } }), one<MailMessage>(isMessage));

export const readTicketHelpdesk = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.helpdesk.GET('/projects/{projectId}/tickets/{number}/helpdesk', { params: { path: { projectId, number } } }),
    one<TicketHelpdesk>(isTicketHelpdesk));
export const ticketCustomerOptions = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.helpdesk.GET('/projects/{projectId}/tickets/{number}/helpdesk/options', { params: { path: { projectId, number } } }),
    listOf<CustomerOption>((v) => hasStrings(v, 'id', 'name') && Array.isArray((v as CustomerOption).contacts)));
export const setTicketCustomer = (api: ServerApi, projectId: string, number: number, customerId: string | null, contactId: string | null) =>
  run(() => api.helpdesk.PUT('/projects/{projectId}/tickets/{number}/helpdesk/customer', { params: { path: { projectId, number } }, body: { customerId, contactId } }),
    one<TicketHelpdesk>(isTicketHelpdesk));

// ---------------------------------------------------------------- replies (TASK-058)

const isReply = (v: unknown) => hasStrings(v, 'id', 'toAddress', 'subject', 'state') && typeof (v as Reply).version === 'number';
export const readReplyForm = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.helpdesk.GET('/projects/{projectId}/tickets/{number}/helpdesk/reply', { params: { path: { projectId, number } } }),
    one<ReplyForm>((v) => record(v) && Array.isArray(v.mailboxes) && Array.isArray(v.recipients)));
export const sendReply = (api: ServerApi, projectId: string, number: number, mailboxId: string, to: string, subject: string, body: string) =>
  run(() => api.helpdesk.POST('/projects/{projectId}/tickets/{number}/helpdesk/replies', { params: { path: { projectId, number } },
    body: { mailboxId, to, subject, body } }), one<Reply>(isReply));
export const sendReplyAgain = (api: ServerApi, projectId: string, number: number, replyId: string, version: number, duplicateAccepted: boolean) =>
  run(() => api.helpdesk.POST('/projects/{projectId}/tickets/{number}/helpdesk/replies/{replyId}/send', {
    params: { path: { projectId, number, replyId }, ...ifMatch(`"${version}"`) }, body: { duplicateAccepted } }), one<Reply>(isReply));
export const settleReply = (api: ServerApi, projectId: string, number: number, replyId: string, version: number) =>
  run(() => api.helpdesk.POST('/projects/{projectId}/tickets/{number}/helpdesk/replies/{replyId}/settle', {
    params: { path: { projectId, number, replyId }, ...ifMatch(`"${version}"`) } }), one<Reply>(isReply));
export const listOpenReplies = (api: ServerApi) =>
  run(() => api.helpdesk.GET('/helpdesk/replies'), listOf<OpenReply>((v) => hasStrings(v, 'id', 'projectId', 'state') && typeof (v as OpenReply).number === 'number'));
export const attachMessage = (api: ServerApi, id: string, etag: string) =>
  run(() => api.helpdesk.POST('/helpdesk/inbox/{id}/attach', { params: { path: { id }, ...ifMatch(etag) } }),
    one<CreatedFromMail>((v) => hasStrings(v, 'ticketId', 'projectId') && typeof (v as CreatedFromMail).number === 'number'));

// ---------------------------------------------------------------- service deadlines (TASK-068)

export const SLA_PRIORITIES: readonly SlaPriority[] = ['low', 'normal', 'high', 'critical'];
const isSlaCalendar = (v: unknown) => hasStrings(v, 'id', 'name', 'timeZone') && typeof (v as SlaCalendar).version === 'number'
  && Array.isArray((v as SlaCalendar).week);
const isSlaRule = (v: unknown) => hasStrings(v, 'id', 'name', 'projectId', 'calendarId') && typeof (v as SlaRule).version === 'number'
  && record((v as SlaRule).budgets);
const isTicketSla = (v: unknown) => record(v) && Array.isArray(v.readable) && Array.isArray(v.writable) && typeof v.now === 'string';

export const listSlaCalendars = (api: ServerApi) => run(() => api.sla.GET('/sla/calendars'), listOf<SlaCalendar>(isSlaCalendar));
export const createSlaCalendar = (api: ServerApi, body: SlaCalendarRequest) =>
  run(() => api.sla.POST('/sla/calendars', { body }), one<SlaCalendar>(isSlaCalendar));
export const updateSlaCalendar = (api: ServerApi, id: string, version: number, body: SlaCalendarRequest) =>
  run(() => api.sla.PUT('/sla/calendars/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) }, body }), one<SlaCalendar>(isSlaCalendar));
export const listSlaRules = (api: ServerApi) => run(() => api.sla.GET('/sla/rules'), listOf<SlaRule>(isSlaRule));
export const readSlaOptions = (api: ServerApi) =>
  run(() => api.sla.GET('/sla/options'), one<SlaOptions>((v) => record(v) && Array.isArray(v.projects) && Array.isArray(v.customers) && Array.isArray(v.groups)));
export const createSlaRule = (api: ServerApi, body: SlaRuleRequest) => run(() => api.sla.POST('/sla/rules', { body }), one<SlaRule>(isSlaRule));
export const updateSlaRule = (api: ServerApi, id: string, version: number, body: SlaRuleRequest) =>
  run(() => api.sla.PUT('/sla/rules/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) }, body }), one<SlaRule>(isSlaRule));
export const removeSlaRule = (api: ServerApi, id: string, version: number) =>
  run(() => api.sla.DELETE('/sla/rules/{id}', { params: { path: { id }, ...ifMatch(`"${version}"`) } }), nothing);

const isSlaEscalation = (v: unknown) => hasStrings(v, 'projectId', 'projectKey', 'kind', 'groupId', 'groupName', 'thresholdAt', 'reachedAt')
  && typeof (v as SlaEscalation).number === 'number' && typeof (v as SlaEscalation).percent === 'number';
export const listSlaEscalations = (api: ServerApi) => run(() => api.sla.GET('/sla/escalations'), listOf<SlaEscalation>(isSlaEscalation));
export const readTicketSla = (api: ServerApi, projectId: string, number: number) =>
  run(() => api.sla.GET('/projects/{projectId}/tickets/{number}/sla', { params: { path: { projectId, number } } }), one<TicketSla>(isTicketSla));
export const setTicketPriority = (api: ServerApi, projectId: string, number: number, priority: SlaPriority) =>
  run(() => api.sla.PUT('/projects/{projectId}/tickets/{number}/sla/priority', { params: { path: { projectId, number } }, body: { priority } }),
    one<TicketSla>(isTicketSla));
