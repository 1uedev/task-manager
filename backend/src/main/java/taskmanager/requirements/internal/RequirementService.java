package taskmanager.requirements.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.projects.ProjectConfiguration.CustomField;
import taskmanager.wiki.RichText;
import tools.jackson.databind.JsonNode;

/**
 * Requirements (TASK-096, DEC-126) after the rules Holger confirmed on 10 November 2026:
 * <ul>
 *   <li>{@code requirement.read}: read requirements, their revisions and history;</li>
 *   <li>{@code requirement.edit}: create, change, submit for review, discard and restore;</li>
 *   <li>{@code requirement.approve}: approve exactly the shown revision of a requirement under review, or return it with a reason.</li>
 * </ul>
 * Both changing actions presuppose {@code requirement.read}; without it the requirements of a project do not exist for the
 * caller. Every field of the resource type {@code requirement} has read and write rules of its own; status, number and revision
 * are visible with the read action alone. Approving needs the read rule of every content field, so nobody approves what they
 * cannot see.
 *
 * <p>Revisions after DEC-030: saving while the current revision is not approved changes it and writes the history. The first
 * content change (title, description, criteria, priority) after an approval opens the next revision as draft; the approved
 * revision stays as it is, with the person and time of its approval. The owner is organisational and opens no revision.
 *
 * <p>Custom fields (TASK-098, DEC-128): the project configuration names the custom fields of requirements; their values belong
 * to the revision, count as content, follow the field rules {@code custom.<key>} of the resource type {@code requirement} and
 * appear in history, comparison and approval. A required active field needs a value when the requirement is given for review.
 *
 * <p>Locks, in this order: rights (held until the commit by {@code Permissions.require}), the project row of the project
 * adapter, the counter of the project when a requirement is created, the requirement row. Every change carries the version
 * of the requirement and is refused with 412 when the requirement is newer.
 */
@Service
public class RequirementService {
    public static final String READ = "requirement.read";
    public static final String EDIT = "requirement.edit";
    public static final String APPROVE = "requirement.approve";
    public static final String RESOURCE_TYPE = "requirement";
    public static final String KEY_PREFIX = "ANF-";
    public static final Set<String> FIELDS = Set.of("title", "description", "criteria", "priority", "owner");
    /** The content of a revision; a change of one of them after an approval opens a new revision. */
    public static final Set<String> CONTENT = Set.of("title", "description", "criteria", "priority");
    public static final List<String> PRIORITIES = List.of("low", "normal", "high", "critical");
    public static final List<String> STATUSES = List.of("draft", "review", "approved", "discarded");
    public static final int TITLE_MAX = 200;
    public static final int CRITERIA_MAX = 50;
    public static final int CRITERION_MAX = 2000;
    public static final int REASON_MAX = 2000;
    public static final int LIMIT_DEFAULT = 50;
    public static final int LIMIT_MAX = 200;
    private static final Set<String> EDITING = Set.of("created", "changed", "criterion_added", "criterion_changed", "criterion_removed",
        "criteria_moved");

    private final RequirementRepository repository;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final Clock clock;
    private final RequirementLinks links;

    RequirementService(RequirementRepository repository, ProjectAccess projects, Permissions permissions, IdentityVerification identity, Clock clock,
            RequirementLinks links) {
        this.links = links;
        this.repository = repository;
        this.projects = projects;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
    }

    /** The requirement actions of the caller in one project with the fields they may read and write. */
    public record Access(boolean read, boolean edit, boolean approve, Set<String> readable, Set<String> writable) {
        static final Access NONE = new Access(false, false, false, Set.of(), Set.of());

        public Access {
            readable = Set.copyOf(readable);
            writable = Set.copyOf(writable);
        }
    }

    public record ProjectView(UUID projectId, String key, String name, Access access) {
    }

    public record CriterionView(int id, String text) {
    }

    /** The content of one revision; every part the caller may not read is {@code null}. */
    public record Content(String title, JsonNode description, String descriptionHtml, List<CriterionView> criteria, String priority,
            Map<String, Object> custom) {
    }

    public record Approval(int revision, Instant approvedAt, UUID approvedBy) {
    }

    /**
     * A requirement as the caller may see it. {@code approval} is the newest approval, {@code content} the current revision;
     * {@code editors} are the people who changed the content of the current revision.
     */
    public record RequirementView(UUID id, UUID projectId, String projectKey, long number, String key, String status, int revision,
            Integer approvedRevision, Approval approval, Content content, UUID ownerId, Access access, List<UUID> editors, long version,
            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, List<CustomField> customFields) {
    }

    public record Item(UUID id, long number, String key, String status, int revision, Integer approvedRevision, String title, String priority,
            UUID ownerId, Instant updatedAt) {
    }

    public record ListPage(UUID projectId, String projectKey, Access access, List<Item> items, Long nextAfter, List<CustomField> customFields) {
    }

    public record RevisionInfo(int revision, Instant openedAt, UUID openedBy, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy) {
    }

    public record RevisionContent(int revision, boolean current, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy, Content content,
            List<CustomField> customFields) {
    }

    public record EventView(String kind, int revision, String field, Integer criterion, String from, String to, String reason, UUID actorId,
            Instant occurredAt) {
    }

    /**
     * The history; {@code pageTitles} names the published title of each linked page of the events that the caller may read
     * (TASK-097), a page without one is not available to the caller.
     */
    public record History(UUID requirementId, List<RevisionInfo> revisions, List<EventView> events, Map<UUID, String> pageTitles) {
    }

    /** A criterion of a request: {@code id} names an existing criterion, {@code null} a new one. */
    public record CriterionInput(Integer id, String text) {
    }

    /**
     * The fields a request names. A field that is not named keeps its value; a named {@code null} empties the description, the
     * owner or a custom field. {@code custom} holds the named custom fields by key (TASK-098).
     */
    public record Changes(boolean hasTitle, String title, boolean hasDescription, JsonNode description, boolean hasPriority, String priority,
            boolean hasOwner, String owner, boolean hasCriteria, List<CriterionInput> criteria, Map<String, Object> custom) {
        public Changes {
            custom = custom == null ? Map.of() : Collections.unmodifiableMap(new java.util.LinkedHashMap<>(custom));
        }

        public Changes(boolean hasTitle, String title, boolean hasDescription, JsonNode description, boolean hasPriority, String priority,
                boolean hasOwner, String owner, boolean hasCriteria, List<CriterionInput> criteria) {
            this(hasTitle, title, hasDescription, description, hasPriority, priority, hasOwner, owner, hasCriteria, criteria, Map.of());
        }

        Set<String> fields() {
            Set<String> fields = new HashSet<>();
            if (hasTitle) {
                fields.add("title");
            }
            if (hasDescription) {
                fields.add("description");
            }
            if (hasPriority) {
                fields.add("priority");
            }
            if (hasOwner) {
                fields.add("owner");
            }
            if (hasCriteria) {
                fields.add("criteria");
            }
            custom.keySet().forEach(key -> fields.add(ProjectConfiguration.CUSTOM_PREFIX + key));
            return fields;
        }
    }

    // ------------------------------------------------------------------------------------------------ reading

    @Transactional
    public List<ProjectView> projects(AccessContext actor) {
        List<ProjectView> result = new ArrayList<>();
        if (actor.external()) {
            return result;
        }
        for (UUID projectId : projects.memberships(actor.companyId(), actor.userId())) {
            Access access = access(actor, projectId);
            if (access.read()) {
                projects.summary(actor.companyId(), projectId)
                    .ifPresent(summary -> result.add(new ProjectView(projectId, summary.key(), summary.name(), access)));
            }
        }
        result.sort(Comparator.comparing(ProjectView::key));
        return result;
    }

    @Transactional
    public ListPage list(AccessContext actor, UUID projectId, String status, String priority, String owner, String text, Long after, Integer limit) {
        Access access = projectAccess(actor, projectId);
        List<String> violations = new ArrayList<>();
        if (status != null && !STATUSES.contains(status)) {
            violations.add("status.invalid");
        }
        if (priority != null) {
            if (!PRIORITIES.contains(priority)) {
                violations.add("priority.invalid");
            } else if (!access.readable().contains("priority")) {
                violations.add("priority.not_readable");
            }
        }
        UUID ownerId = null;
        if (owner != null) {
            try {
                ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException invalid) {
                violations.add("owner.invalid");
            }
            if (!access.readable().contains("owner")) {
                violations.add("owner.not_readable");
            }
        }
        String search = text == null || text.isBlank() ? null : text.strip();
        if (search != null) {
            if (search.length() > TITLE_MAX) {
                violations.add("text.too_long");
            } else if (!access.readable().contains("title")) {
                violations.add("text.not_readable");
            }
        }
        long start = after == null ? 0 : after;
        if (start < 0) {
            violations.add("after.invalid");
        }
        int size = limit == null ? LIMIT_DEFAULT : limit;
        if (size < 1 || size > LIMIT_MAX) {
            violations.add("limit.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        List<RequirementRepository.Row> rows = repository.list(actor.companyId(), projectId, status, priority, ownerId, search, start, size);
        Set<String> readable = access.readable();
        List<Item> items = rows.stream().map(row -> {
            RequirementRepository.Requirement r = row.requirement();
            return new Item(r.id(), r.number(), KEY_PREFIX + r.number(), r.status(), r.currentRevision(), r.approvedRevision(),
                readable.contains("title") ? row.title() : null, readable.contains("priority") ? row.priority() : null,
                readable.contains("owner") ? r.ownerId() : null, r.updatedAt());
        }).toList();
        Long next = rows.size() == size ? rows.getLast().requirement().number() : null;
        String key = projects.key(projectId).orElseThrow(RequirementService::projectNotFound);
        return new ListPage(projectId, key, access, items, next, readableFields(configuration(projectId), access.readable()));
    }

    @Transactional
    public RequirementView get(AccessContext actor, UUID id) {
        RequirementRepository.Requirement requirement = visible(actor, id);
        return view(requirement, access(actor, requirement.projectId()));
    }

    @Transactional
    public History history(AccessContext actor, UUID id) {
        return history(actor, id, null);
    }

    /**
     * The history with the events of links (TASK-097): those of tickets only for a caller with {@code ticket.read}, those of
     * pages for every reader, with the published title in {@code language} only for a caller who may read the page.
     */
    @Transactional
    public History history(AccessContext actor, UUID id, String language) {
        RequirementRepository.Requirement requirement = visible(actor, id);
        Set<String> readable = access(actor, requirement.projectId()).readable();
        List<RevisionInfo> revisions = repository.revisions(requirement.id()).stream()
            .map(v -> new RevisionInfo(v.revision(), v.openedAt(), v.openedBy(), v.savedAt(), v.savedBy(), v.approvedAt(), v.approvedBy()))
            .toList();
        List<RequirementRepository.Event> all = repository.events(requirement.id());
        RequirementLinks.HistoryLinks shown = links.historyLinks(actor, requirement.projectId(), all, language);
        List<EventView> events = all.stream()
            .filter(e -> e.field() == null || readable.contains(e.field()) || "pages".equals(e.field())
                || ("tickets".equals(e.field()) && shown.tickets()))
            .map(e -> new EventView(e.kind(), e.revision(), e.field(), e.criterion(), e.fromValue(), e.toValue(), e.reason(), e.actorId(),
                e.occurredAt()))
            .toList();
        return new History(requirement.id(), revisions, events, shown.pageTitles());
    }

    @Transactional
    public RevisionContent revision(AccessContext actor, UUID id, int number) {
        RequirementRepository.Requirement requirement = visible(actor, id);
        Set<String> readable = access(actor, requirement.projectId()).readable();
        RequirementRepository.Revision revision = repository.revision(requirement.id(), number)
            .orElseThrow(() -> ApiProblemException.of(404, "requirement.revision_not_found"));
        ProjectConfiguration configuration = configuration(requirement.projectId());
        return new RevisionContent(revision.revision(), revision.revision() == requirement.currentRevision(), revision.savedAt(), revision.savedBy(),
            revision.approvedAt(), revision.approvedBy(), content(revision, readable, configuration), readableFields(configuration, readable));
    }

    /** The people who may become owner: active internal members of the project, for callers who may change the owner. */
    @Transactional
    public List<UUID> owners(AccessContext actor, UUID projectId) {
        Access access = projectAccess(actor, projectId);
        if (!access.edit() || !access.writable().contains("owner")) {
            throw forbidden();
        }
        return projects.members(projectId).stream().filter(identity::accountIsActiveInternal).toList();
    }

    // ------------------------------------------------------------------------------------------------ changes

    @Transactional
    public RequirementView create(AccessContext actor, UUID projectId, Changes input) {
        Access access = projectAccess(actor, projectId);
        if (!access.edit()) {
            throw forbidden();
        }
        unknownFields(configuration(projectId), input.custom().keySet());
        Set<String> written = input.fields();
        written.add("title");
        require(actor, projectId, EDIT, Set.of(), written);
        List<String> violations = new ArrayList<>();
        String title = title(input.title(), violations);
        String description = input.hasDescription() ? description(input.description(), violations) : null;
        String priority = input.hasPriority() ? priority(input.priority(), violations) : "normal";
        UUID owner = input.hasOwner() ? owner(projectId, input.owner(), null, violations) : null;
        List<RequirementRepository.Criterion> criteria = new ArrayList<>();
        int next = 1;
        if (input.hasCriteria()) {
            for (CriterionInput criterion : checkedCriteria(input.criteria(), Set.of(), violations)) {
                criteria.add(new RequirementRepository.Criterion(next++, criterion.text()));
            }
        }
        Map<String, String> custom = customValues(projectId, configuration(projectId), input.custom(), Map.of(), violations);
        if (!violations.isEmpty()) {
            throw invalid(violations);
        }
        Instant now = clock.instant();
        long number = repository.nextNumber(projectId);
        UUID id = UUID.randomUUID();
        repository.insert(new RequirementRepository.Requirement(id, actor.companyId(), projectId, number, "draft", owner, 1, null, next, now,
            actor.userId(), now, actor.userId(), 1));
        repository.insert(new RequirementRepository.Revision(id, 1, title, description, priority, List.copyOf(criteria), now, actor.userId(), now,
            actor.userId(), null, null, custom));
        repository.event(actor.companyId(), id, 1, "created", null, null, null, null, null, actor.userId(), now);
        return view(repository.find(actor.companyId(), id).orElseThrow(), access(actor, projectId));
    }

    /**
     * Changes the named fields. A content change of an approved current revision opens the next revision as draft; otherwise
     * the current revision changes. A request that changes nothing leaves version and history as they are.
     */
    @Transactional
    public RequirementView update(AccessContext actor, UUID id, long expectedVersion, Changes changes) {
        RequirementRepository.Requirement found = visible(actor, id);
        Set<String> written = changes.fields();
        if (written.isEmpty()) {
            throw invalid(List.of("changes.required"));
        }
        if (!access(actor, found.projectId()).edit()) {
            throw forbidden();
        }
        ProjectConfiguration configuration = configuration(found.projectId());
        unknownFields(configuration, changes.custom().keySet());
        require(actor, found.projectId(), EDIT, Set.of(), written);
        RequirementRepository.Requirement requirement = locked(actor, id, expectedVersion);
        if ("discarded".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.discarded");
        }
        RequirementRepository.Revision current = repository.revision(requirement.id(), requirement.currentRevision()).orElseThrow();
        List<String> violations = new ArrayList<>();
        String title = changes.hasTitle() ? title(changes.title(), violations) : current.title();
        String description = changes.hasDescription() ? description(changes.description(), violations) : current.description();
        String priority = changes.hasPriority() ? priority(changes.priority(), violations) : current.priority();
        UUID owner = changes.hasOwner() ? owner(requirement.projectId(), changes.owner(), requirement.ownerId(), violations) : requirement.ownerId();
        int next = requirement.nextCriterion();
        List<RequirementRepository.Criterion> criteria = current.criteria();
        if (changes.hasCriteria()) {
            Set<Integer> known = new HashSet<>();
            current.criteria().forEach(criterion -> known.add(criterion.id()));
            List<RequirementRepository.Criterion> updated = new ArrayList<>();
            for (CriterionInput criterion : checkedCriteria(changes.criteria(), known, violations)) {
                updated.add(new RequirementRepository.Criterion(criterion.id() == null ? next++ : criterion.id(), criterion.text()));
            }
            criteria = List.copyOf(updated);
        }
        Map<String, String> custom = customValues(requirement.projectId(), configuration, changes.custom(), current.custom(), violations);
        if (!violations.isEmpty()) {
            throw invalid(violations);
        }
        boolean content = !title.equals(current.title()) || !Objects.equals(description, current.description()) || !priority.equals(current.priority())
            || !criteria.equals(current.criteria()) || !custom.equals(current.custom());
        boolean ownerChanged = !Objects.equals(owner, requirement.ownerId());
        if (!content && !ownerChanged) {
            return view(requirement, access(actor, requirement.projectId()));
        }
        Instant now = clock.instant();
        UUID by = actor.userId();
        int revision = requirement.currentRevision();
        String status = requirement.status();
        if (content) {
            if (current.approved()) {
                revision = current.revision() + 1;
                repository.insert(new RequirementRepository.Revision(requirement.id(), revision, title, description, priority, criteria, now, by, now, by,
                    null, null, custom));
                repository.event(actor.companyId(), requirement.id(), revision, "revision_opened", null, null, String.valueOf(current.revision()),
                    String.valueOf(revision), null, by, now);
                status = "draft";
            } else {
                repository.update(new RequirementRepository.Revision(requirement.id(), revision, title, description, priority, criteria,
                    current.openedAt(), current.openedBy(), now, by, null, null, custom));
            }
            recordContent(actor, requirement.id(), revision, current, title, description, priority, criteria, custom, now);
        }
        if (ownerChanged) {
            repository.event(actor.companyId(), requirement.id(), revision, "changed", "owner", null, text(requirement.ownerId()), text(owner), null, by,
                now);
        }
        repository.update(requirement.with(status, owner, revision, requirement.approvedRevision(), next, now, by));
        return view(repository.find(actor.companyId(), id).orElseThrow(), access(actor, requirement.projectId()));
    }

    /** Gives a draft for review; it needs at least one criterion. */
    @Transactional
    public RequirementView submit(AccessContext actor, UUID id, long expectedVersion) {
        RequirementRepository.Requirement requirement = changing(actor, id, expectedVersion, EDIT, Set.of());
        if (!"draft".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.status_invalid");
        }
        RequirementRepository.Revision current = repository.revision(requirement.id(), requirement.currentRevision()).orElseThrow();
        // A required custom field the caller may not read or write is a conflict of the configuration and the rights, whatever it
        // holds, so the answer never tells whether a hidden field has a value (as for the transitions of tickets).
        ProjectConfiguration configuration = configuration(requirement.projectId());
        Access access = access(actor, requirement.projectId());
        List<CustomField> required = configuration.requirementFields().stream().filter(field -> field.required() && field.active()).toList();
        for (CustomField field : required) {
            if (!access.readable().contains(field.id()) || !access.writable().contains(field.id())) {
                throw ApiProblemException.of(409, "requirement.required_field_inaccessible");
            }
        }
        List<String> missing = new ArrayList<>();
        if (current.criteria().isEmpty()) {
            missing.add("criteria.required");
        }
        required.stream().filter(field -> !current.custom().containsKey(field.key()))
            .forEach(field -> missing.add(field.id() + ".required_for_review"));
        if (!missing.isEmpty()) {
            throw invalid(missing);
        }
        return status(actor, requirement, "submitted", "review", requirement.approvedRevision(), null);
    }

    /** Approves exactly {@code revision}, which has to be the current revision under review that the caller saw. */
    @Transactional
    public RequirementView approve(AccessContext actor, UUID id, long expectedVersion, int revision) {
        RequirementRepository.Requirement found = visible(actor, id);
        Access access = access(actor, found.projectId());
        if (!access.approve()) {
            throw forbidden();
        }
        Set<String> content = new HashSet<>(CONTENT);
        content.addAll(configuration(found.projectId()).requirementFieldIds());
        if (!access.readable().containsAll(content)) {
            throw ApiProblemException.of(403, "requirement.content_not_readable");
        }
        require(actor, found.projectId(), APPROVE, content, Set.of());
        RequirementRepository.Requirement requirement = locked(actor, id, expectedVersion);
        if (!"review".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.status_invalid");
        }
        if (revision != requirement.currentRevision()) {
            throw versionConflict();
        }
        RequirementRepository.Revision current = repository.revision(requirement.id(), revision).orElseThrow();
        Instant now = clock.instant();
        repository.update(current.approvedBy(now, actor.userId()));
        return status(actor, requirement, "approved", "approved", revision, null);
    }

    /** Returns a requirement under review to draft with a reason. */
    @Transactional
    public RequirementView returnToDraft(AccessContext actor, UUID id, long expectedVersion, String reason) {
        List<String> violations = new ArrayList<>();
        String clean = reason(reason, true, violations);
        RequirementRepository.Requirement requirement = changing(actor, id, expectedVersion, APPROVE, Set.of());
        if (!"review".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.status_invalid");
        }
        if (!violations.isEmpty()) {
            throw invalid(violations);
        }
        return status(actor, requirement, "returned", "draft", requirement.approvedRevision(), clean);
    }

    /** Discards a requirement in any status; it stays with its history and approvals. */
    @Transactional
    public RequirementView discard(AccessContext actor, UUID id, long expectedVersion, String reason) {
        List<String> violations = new ArrayList<>();
        String clean = reason(reason, false, violations);
        RequirementRepository.Requirement requirement = changing(actor, id, expectedVersion, EDIT, Set.of());
        if ("discarded".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.status_invalid");
        }
        if (!violations.isEmpty()) {
            throw invalid(violations);
        }
        return status(actor, requirement, "discarded", "discarded", requirement.approvedRevision(), clean);
    }

    /**
     * Brings a discarded requirement back to draft; when its current revision is approved, back to approved, because that
     * revision stays approved unchanged.
     */
    @Transactional
    public RequirementView restore(AccessContext actor, UUID id, long expectedVersion) {
        RequirementRepository.Requirement requirement = changing(actor, id, expectedVersion, EDIT, Set.of());
        if (!"discarded".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.status_invalid");
        }
        boolean approved = Objects.equals(requirement.approvedRevision(), requirement.currentRevision());
        return status(actor, requirement, "restored", approved ? "approved" : "draft", requirement.approvedRevision(), null);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private RequirementView status(AccessContext actor, RequirementRepository.Requirement requirement, String kind, String status, Integer approved,
            String reason) {
        Instant now = clock.instant();
        repository.event(actor.companyId(), requirement.id(), requirement.currentRevision(), kind, null, null, requirement.status(), status, reason,
            actor.userId(), now);
        repository.update(requirement.with(status, requirement.ownerId(), requirement.currentRevision(), approved, requirement.nextCriterion(), now,
            actor.userId()));
        return view(repository.find(actor.companyId(), requirement.id()).orElseThrow(), access(actor, requirement.projectId()));
    }

    /** Writes the history of a content change field by field and criterion by criterion. */
    private void recordContent(AccessContext actor, UUID id, int revision, RequirementRepository.Revision before, String title, String description,
            String priority, List<RequirementRepository.Criterion> criteria, Map<String, String> custom, Instant now) {
        UUID by = actor.userId();
        UUID company = actor.companyId();
        Set<String> keys = new TreeSet<>(before.custom().keySet());
        keys.addAll(custom.keySet());
        for (String key : keys) {
            String old = before.custom().get(key);
            String fresh = custom.get(key);
            if (!Objects.equals(old, fresh)) {
                repository.event(company, id, revision, "changed", ProjectConfiguration.CUSTOM_PREFIX + key, null, old, fresh, null, by, now);
            }
        }
        if (!title.equals(before.title())) {
            repository.event(company, id, revision, "changed", "title", null, before.title(), title, null, by, now);
        }
        if (!Objects.equals(description, before.description())) {
            repository.event(company, id, revision, "changed", "description", null, null, null, null, by, now);
        }
        if (!priority.equals(before.priority())) {
            repository.event(company, id, revision, "changed", "priority", null, before.priority(), priority, null, by, now);
        }
        Map<Integer, String> old = new HashMap<>();
        before.criteria().forEach(criterion -> old.put(criterion.id(), criterion.text()));
        Map<Integer, String> fresh = new HashMap<>();
        criteria.forEach(criterion -> fresh.put(criterion.id(), criterion.text()));
        for (RequirementRepository.Criterion criterion : before.criteria()) {
            if (!fresh.containsKey(criterion.id())) {
                repository.event(company, id, revision, "criterion_removed", "criteria", criterion.id(), criterion.text(), null, null, by, now);
            }
        }
        for (RequirementRepository.Criterion criterion : criteria) {
            String previous = old.get(criterion.id());
            if (previous == null) {
                repository.event(company, id, revision, "criterion_added", "criteria", criterion.id(), null, criterion.text(), null, by, now);
            } else if (!previous.equals(criterion.text())) {
                repository.event(company, id, revision, "criterion_changed", "criteria", criterion.id(), previous, criterion.text(), null, by, now);
            }
        }
        List<Integer> kept = before.criteria().stream().map(RequirementRepository.Criterion::id).filter(fresh::containsKey).toList();
        List<Integer> order = criteria.stream().map(RequirementRepository.Criterion::id).filter(old::containsKey).toList();
        if (!kept.equals(order)) {
            repository.event(company, id, revision, "criteria_moved", "criteria", null, ids(kept), ids(order), null, by, now);
        }
    }

    private static String ids(List<Integer> ids) {
        return String.join(",", ids.stream().map(id -> "K" + id).toList());
    }

    private RequirementView view(RequirementRepository.Requirement requirement, Access access) {
        RequirementRepository.Revision current = repository.revision(requirement.id(), requirement.currentRevision()).orElseThrow();
        Approval approval = null;
        if (requirement.approvedRevision() != null) {
            RequirementRepository.Revision approved = requirement.approvedRevision() == current.revision() ? current
                : repository.revision(requirement.id(), requirement.approvedRevision()).orElseThrow();
            approval = new Approval(approved.revision(), approved.approvedAt(), approved.approvedBy());
        }
        Set<UUID> editors = new LinkedHashSet<>();
        ProjectConfiguration configuration = configuration(requirement.projectId());
        // The creation wrote every content field; it names its author only to a caller who may read one of them (R2), custom
        // fields included (TASK-098).
        boolean contentReadable = CONTENT.stream().anyMatch(access.readable()::contains)
            || configuration.requirementFieldIds().stream().anyMatch(access.readable()::contains);
        for (RequirementRepository.Event event : repository.events(requirement.id())) {
            // Only changes of fields the caller may read name their author (Holger's choice on H3 of the TASK-096 review).
            if (event.revision() == current.revision() && EDITING.contains(event.kind()) && !"owner".equals(event.field())
                && (event.field() == null ? contentReadable : access.readable().contains(event.field()))) {
                editors.add(event.actorId());
            }
        }
        String key = projects.key(requirement.projectId()).orElseThrow(RequirementService::projectNotFound);
        return new RequirementView(requirement.id(), requirement.projectId(), key, requirement.number(), KEY_PREFIX + requirement.number(),
            requirement.status(), requirement.currentRevision(), requirement.approvedRevision(), approval,
            content(current, access.readable(), configuration), access.readable().contains("owner") ? requirement.ownerId() : null, access,
            List.copyOf(editors), requirement.version(), requirement.createdAt(), requirement.createdBy(), requirement.updatedAt(),
            requirement.updatedBy(), readableFields(configuration, access.readable()));
    }

    /** The custom fields of requirements the caller may read, in the order of the configuration. */
    private static List<CustomField> readableFields(ProjectConfiguration configuration, Set<String> readable) {
        return configuration.requirementFields().stream().filter(field -> readable.contains(field.id())).toList();
    }

    private static Content content(RequirementRepository.Revision revision, Set<String> readable, ProjectConfiguration configuration) {
        Map<String, Object> custom = new java.util.LinkedHashMap<>();
        for (CustomField field : configuration.requirementFields()) {
            String value = revision.custom().get(field.key());
            if (value != null && readable.contains(field.id())) {
                custom.put(field.key(), typed(field, value));
            }
        }
        RichText description = readable.contains("description") && revision.description() != null ? RichText.stored(revision.description()) : null;
        boolean descriptionReadable = readable.contains("description");
        return new Content(readable.contains("title") ? revision.title() : null,
            descriptionReadable ? (description == null ? RichText.check(null).tree() : description.tree()) : null,
            descriptionReadable ? (description == null ? "" : description.html()) : null,
            readable.contains("criteria") ? revision.criteria().stream().map(c -> new CriterionView(c.id(), c.text())).toList() : null,
            readable.contains("priority") ? revision.priority() : null, custom);
    }

    /** A stored value as its kind says: a whole number or a boolean as such, everything else as text. */
    private static Object typed(CustomField field, String value) {
        return switch (field.type()) {
            case ProjectConfiguration.INTEGER -> Long.parseLong(value);
            case ProjectConfiguration.BOOLEAN -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    /** A key the configuration does not know is a wrong request, not a missing right (as for tickets). */
    private static void unknownFields(ProjectConfiguration configuration, Set<String> keys) {
        if (keys.stream().anyMatch(key -> configuration.requirementField(key).isEmpty())) {
            throw invalid(List.of("customFields.unknown"));
        }
    }

    /**
     * The custom values after the named changes. A value the revision already holds is accepted as it is even when its field or
     * option became inactive or the account left the project; only a new value is checked against that (F-06 of DOMAIN_MODEL.md).
     * Emptying is always possible; a required field is checked when the requirement is given for review.
     */
    private Map<String, String> customValues(UUID projectId, ProjectConfiguration configuration, Map<String, Object> changes,
            Map<String, String> current, List<String> violations) {
        Map<String, String> custom = new TreeMap<>(current);
        for (var change : changes.entrySet()) {
            CustomField field = configuration.requirementField(change.getKey()).orElseThrow();
            if (change.getValue() == null) {
                custom.remove(change.getKey());
                continue;
            }
            String value = ProjectConfiguration.canonical(field, change.getValue());
            if (value == null) {
                violations.add(field.id() + ".invalid");
                continue;
            }
            if (!value.equals(current.get(change.getKey()))) {
                if (!field.active()) {
                    violations.add(field.id() + ".inactive");
                    continue;
                }
                if (ProjectConfiguration.SELECT.equals(field.type()) && !field.option(value).orElseThrow().active()) {
                    violations.add(field.id() + ".option_inactive");
                    continue;
                }
                if (ProjectConfiguration.USER.equals(field.type()) && (!projects.isMember(projectId, UUID.fromString(value))
                        || !identity.accountIsActiveInternal(UUID.fromString(value)))) {
                    violations.add(field.id() + ".not_member");
                    continue;
                }
            }
            custom.put(change.getKey(), value);
        }
        return custom;
    }

    /** The configuration in force; read after the rights check locked the project, it stays current until the commit. */
    private ProjectConfiguration configuration(UUID projectId) {
        return projects.currentConfigurationVersion(projectId).flatMap(version -> projects.configuration(projectId, version))
            .orElseThrow(RequirementService::projectNotFound);
    }

    private static String title(String title, List<String> violations) {
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty()) {
            violations.add("title.required");
        } else if (clean.codePointCount(0, clean.length()) > TITLE_MAX) {
            violations.add("title.too_long");
        } else if (clean.codePoints().anyMatch(Character::isISOControl)) {
            violations.add("title.invalid");
        }
        return clean;
    }

    /** The stored description, or {@code null} when it is empty. */
    private static String description(JsonNode content, List<String> violations) {
        if (content == null || content.isNull()) {
            return null;
        }
        try {
            RichText text = RichText.check(content);
            return text.blank() ? null : text.json();
        } catch (RichText.Invalid invalid) {
            // content.x becomes description.x; the codes of a link or image keep their kind, for example description.link_invalid.
            invalid.violations().forEach(violation -> violations.add("description." + (violation.startsWith("content.")
                ? violation.substring("content.".length()) : violation.replace('.', '_'))));
            return null;
        }
    }

    private static String priority(String priority, List<String> violations) {
        if (priority == null || !PRIORITIES.contains(priority)) {
            violations.add("priority.invalid");
            return "normal";
        }
        return priority;
    }

    /** A new owner has to be an active internal member of the project; the current owner may stay. */
    private UUID owner(UUID projectId, String raw, UUID current, List<String> violations) {
        if (raw == null) {
            return null;
        }
        UUID account;
        try {
            account = UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            violations.add("owner.not_member");
            return current;
        }
        if (account.equals(current)) {
            return current;
        }
        if (!projects.isMember(projectId, account) || !identity.accountIsActiveInternal(account)) {
            violations.add("owner.not_member");
            return current;
        }
        return account;
    }

    /** Checks the list of criteria: at most 50, each text 1 to 2000 characters, every known number at most once. */
    private static List<CriterionInput> checkedCriteria(List<CriterionInput> criteria, Set<Integer> known, List<String> violations) {
        List<CriterionInput> result = new ArrayList<>();
        if (criteria == null) {
            violations.add("criteria.invalid");
            return result;
        }
        if (criteria.size() > CRITERIA_MAX) {
            violations.add("criteria.too_many");
        }
        Set<Integer> seen = new HashSet<>();
        for (CriterionInput criterion : criteria) {
            if (criterion == null) {
                violations.add("criteria.invalid");
                continue;
            }
            if (criterion.id() != null && (!known.contains(criterion.id()) || !seen.add(criterion.id()))) {
                violations.add("criteria.unknown");
                continue;
            }
            // Line breaks of every platform count as one line feed before the checks (H1 of the TASK-096 review).
            String text = criterion.text() == null ? "" : lines(criterion.text()).strip();
            if (text.isEmpty()) {
                violations.add("criteria.text_required");
            } else if (text.codePointCount(0, text.length()) > CRITERION_MAX) {
                violations.add("criteria.text_too_long");
            } else if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
                violations.add("criteria.text_invalid");
            }
            result.add(new CriterionInput(criterion.id(), text));
        }
        return result;
    }

    private static String lines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String reason(String reason, boolean required, List<String> violations) {
        String clean = reason == null ? "" : lines(reason).strip();
        if (clean.isEmpty()) {
            if (required) {
                violations.add("reason.required");
            }
            return null;
        }
        if (clean.codePointCount(0, clean.length()) > REASON_MAX) {
            violations.add("reason.too_long");
        } else if (clean.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
            violations.add("reason.invalid");
        }
        return clean;
    }

    private static String text(UUID value) {
        return value == null ? null : value.toString();
    }

    /** A requirement the caller may read, else 404 like an unknown one. */
    private RequirementRepository.Requirement visible(AccessContext actor, UUID id) {
        RequirementRepository.Requirement requirement = repository.find(actor.companyId(), id).orElseThrow(RequirementService::notFound);
        if (!access(actor, requirement.projectId()).read()) {
            throw notFound();
        }
        return requirement;
    }

    /** The locked requirement after its action was required, at the version the caller saw. */
    private RequirementRepository.Requirement changing(AccessContext actor, UUID id, long expectedVersion, String action, Set<String> read) {
        RequirementRepository.Requirement found = visible(actor, id);
        Access access = access(actor, found.projectId());
        if (EDIT.equals(action) ? !access.edit() : !access.approve()) {
            throw forbidden();
        }
        require(actor, found.projectId(), action, read, Set.of());
        return locked(actor, id, expectedVersion);
    }

    private RequirementRepository.Requirement locked(AccessContext actor, UUID id, long expectedVersion) {
        RequirementRepository.Requirement requirement = repository.lock(actor.companyId(), id).orElseThrow(RequirementService::notFound);
        if (requirement.version() != expectedVersion) {
            throw versionConflict();
        }
        return requirement;
    }

    private Access projectAccess(AccessContext actor, UUID projectId) {
        Access access = access(actor, projectId);
        if (!access.read()) {
            throw projectNotFound();
        }
        return access;
    }

    private Access access(AccessContext actor, UUID projectId) {
        if (actor.external() || projectId == null) {
            return Access.NONE;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false);
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        if (!read.allowed()) {
            return Access.NONE;
        }
        // The custom fields are known only from the configuration, which is read after the first check locked the project; their
        // rules are asked separately, as for tickets (TASK-098).
        List<String> custom = configuration(projectId).requirementFieldIds();
        Set<String> readable = new HashSet<>(read.readableFields());
        if (!custom.isEmpty()) {
            readable.addAll(permissions.inspect(actor, resource, READ, Set.copyOf(custom)).readableFields());
        }
        Decision edit = permissions.inspect(actor, resource, EDIT, FIELDS);
        Set<String> writable = new HashSet<>(edit.allowed() ? edit.writableFields() : Set.of());
        if (edit.allowed() && !custom.isEmpty()) {
            writable.addAll(permissions.inspect(actor, resource, EDIT, Set.copyOf(custom)).writableFields());
        }
        boolean approve = permissions.inspect(actor, resource, APPROVE, Set.of()).allowed();
        return new Access(true, edit.allowed(), approve, readable, writable);
    }

    private void require(AccessContext actor, UUID projectId, String action, Set<String> read, Set<String> write) {
        try {
            permissions.require(actor, projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false), action, read, write);
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
    }

    private static ApiProblemException invalid(List<String> violations) {
        return ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
    }

    private static ApiProblemException versionConflict() {
        return ApiProblemException.of(412, "requirement.version_conflict");
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "requirement.not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
