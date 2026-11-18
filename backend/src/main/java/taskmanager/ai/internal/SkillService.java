package taskmanager.ai.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.ai.AiUseCase;
import taskmanager.platform.ApiProblemException;

/**
 * The skills per use case and language (TASK-125, rules 6 to 8). A skill is a Markdown text of the administration with a
 * name; it grants nothing and runs nothing. An active version never changes: editing works on the one draft, which becomes
 * active only on an explicit activation, and the former active version is archived. Without an active version the built-in
 * skill of the application applies; "restore" archives the active version to return to it. Every change needs
 * {@code ai.manage} and the tag of the draft as it was shown, so two administrators never overwrite each other unnoticed. The
 * tag is the number of the draft and its revision, which every save counts up; a discarded draft keeps its row, so a tag is
 * never valid twice (TASK-129, F11).
 */
@Service
public class SkillService {
    static final List<String> LANGUAGES = List.of("de", "en");
    static final int CONTENT_MAX = 20_000;

    private final AiStore store;
    private final AiAccess access;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    SkillService(AiStore store, AiAccess access, Clock clock, ApplicationEventPublisher events) {
        this.store = store;
        this.access = access;
        this.clock = clock;
        this.events = events;
    }

    /** The skill that applies: the active version of the company or the built-in one. */
    public record Applied(String label, String name, String content, boolean builtin, Instant activatedAt, long selection) {
        /** The label with the selection counter of its slot: a label that applies again later is a different stamp (TASK-130, F3). */
        String stampLabel() {
            return label + "@" + selection;
        }
    }

    /** One use case and language: what applies, the draft and every stored version, newest first. */
    public record Slot(AiUseCase useCase, String language, Applied applied, SkillVersion draft, List<SkillVersion> versions) {
        /** The tag of the draft for If-Match: {@code none} without a draft. */
        public String draftTag() {
            return tag(draft);
        }
    }

    @Transactional
    public List<Slot> slots() {
        UUID companyId = access.requireManage();
        List<Slot> result = new ArrayList<>();
        for (AiUseCase useCase : AiUseCase.values()) {
            for (String language : LANGUAGES) {
                result.add(slot(companyId, useCase, language));
            }
        }
        return result;
    }

    @Transactional
    public Slot slot(AiUseCase useCase, String language) {
        return slot(access.requireManage(), useCase, language(language));
    }

    @Transactional
    public Slot saveDraft(UUID actor, AiUseCase useCase, String language, String expectedTag, String name, String content) {
        UUID companyId = access.requireManage();
        String lang = language(language);
        store.lockSkill(companyId, useCase, lang);
        Optional<SkillVersion> draft = store.skill(companyId, useCase, lang, SkillVersion.State.DRAFT);
        if (!tag(draft.orElse(null)).equals(expectedTag)) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        List<String> violations = new ArrayList<>();
        String cleanName = ConfigService.clean(name);
        if (cleanName == null || cleanName.length() > ConfigService.NAME_MAX) {
            violations.add("name.invalid");
        }
        String text = content == null ? null : AiFrame.clean(content, "\u0000").strip();
        if (text == null || text.isEmpty() || text.length() > CONTENT_MAX) {
            violations.add("content.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        if (draft.isPresent()) {
            store.updateDraft(draft.get().id(), cleanName, text, now, actor);
            store.audit(companyId, "skill", slotId(useCase, lang), "skill.draft_changed", ConfigService.json(new Numbered(draft.get().number())), actor,
                now);
        } else {
            int number = store.nextSkillNumber(companyId, useCase, lang);
            store.insertSkill(new SkillVersion(UUID.randomUUID(), companyId, useCase, lang, number, 1, cleanName, text, SkillVersion.State.DRAFT,
                now, actor, now, actor, null, null, null));
            store.audit(companyId, "skill", slotId(useCase, lang), "skill.draft_created", ConfigService.json(new Numbered(number)), actor, now);
        }
        events.publishEvent(new AiConfigurationChanged(companyId));
        return slot(companyId, useCase, lang);
    }

    @Transactional
    public Slot discardDraft(UUID actor, AiUseCase useCase, String language, String expectedTag) {
        UUID companyId = access.requireManage();
        String lang = language(language);
        store.lockSkill(companyId, useCase, lang);
        SkillVersion draft = store.skill(companyId, useCase, lang, SkillVersion.State.DRAFT)
            .orElseThrow(() -> ApiProblemException.of(404, "ai.skill_draft_not_found"));
        if (!tag(draft).equals(expectedTag)) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        Instant now = clock.instant();
        store.discardDraft(draft.id(), now, actor);
        store.audit(companyId, "skill", slotId(useCase, lang), "skill.draft_discarded", ConfigService.json(new Numbered(draft.number())), actor,
            now);
        events.publishEvent(new AiConfigurationChanged(companyId));
        return slot(companyId, useCase, lang);
    }

    @Transactional
    public Slot activate(UUID actor, AiUseCase useCase, String language, String expectedTag) {
        UUID companyId = access.requireManage();
        String lang = language(language);
        store.lockSkill(companyId, useCase, lang);
        SkillVersion draft = store.skill(companyId, useCase, lang, SkillVersion.State.DRAFT)
            .orElseThrow(() -> ApiProblemException.of(404, "ai.skill_draft_not_found"));
        if (!tag(draft).equals(expectedTag)) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        Instant now = clock.instant();
        store.skill(companyId, useCase, lang, SkillVersion.State.ACTIVE).ifPresent(active -> store.archive(active.id(), now));
        store.activate(draft.id(), now, actor);
        store.raiseSelection(companyId, useCase, lang);
        store.audit(companyId, "skill", slotId(useCase, lang), "skill.activated", ConfigService.json(new Numbered(draft.number())), actor, now);
        events.publishEvent(new AiConfigurationChanged(companyId));
        return slot(companyId, useCase, lang);
    }

    /** Archives the active version, so the built-in skill applies again. */
    @Transactional
    public Slot restore(UUID actor, AiUseCase useCase, String language) {
        UUID companyId = access.requireManage();
        String lang = language(language);
        store.lockSkill(companyId, useCase, lang);
        SkillVersion active = store.skill(companyId, useCase, lang, SkillVersion.State.ACTIVE)
            .orElseThrow(() -> ApiProblemException.of(409, "ai.skill_builtin_applies"));
        Instant now = clock.instant();
        store.archive(active.id(), now);
        store.raiseSelection(companyId, useCase, lang);
        store.audit(companyId, "skill", slotId(useCase, lang), "skill.restored", ConfigService.json(new Numbered(active.number())), actor, now);
        events.publishEvent(new AiConfigurationChanged(companyId));
        return slot(companyId, useCase, lang);
    }

    /** What applies for an execution, read in the transaction of its start. */
    Applied applied(UUID companyId, AiUseCase useCase, String language) {
        return store.skill(companyId, useCase, language, SkillVersion.State.ACTIVE)
            .map(active -> new Applied(active.label(), active.name(), active.content(), false, active.activatedAt(),
                store.selection(companyId, useCase, language)))
            .orElseGet(() -> new Applied(BuiltinSkills.LABEL, BuiltinSkills.name(useCase, language), BuiltinSkills.content(useCase, language), true,
                null, store.selection(companyId, useCase, language)));
    }

    /** What a trial uses: the draft when there is one, otherwise what applies. */
    Applied forTrial(UUID companyId, AiUseCase useCase, String language) {
        return store.skill(companyId, useCase, language, SkillVersion.State.DRAFT)
            .map(draft -> new Applied(draft.label(), draft.name(), draft.content(), false, null, store.selection(companyId, useCase, language)))
            .orElseGet(() -> applied(companyId, useCase, language));
    }

    private Slot slot(UUID companyId, AiUseCase useCase, String language) {
        List<SkillVersion> versions = store.skillVersions(companyId, useCase, language);
        SkillVersion draft = versions.stream().filter(v -> v.state() == SkillVersion.State.DRAFT).findFirst().orElse(null);
        return new Slot(useCase, language, applied(companyId, useCase, language), draft, versions);
    }

    static String language(String language) {
        if (language == null || !LANGUAGES.contains(language)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("language.invalid"));
        }
        return language;
    }

    static String tag(SkillVersion draft) {
        return draft == null ? "none" : draft.number() + "-" + draft.revision();
    }

    private static String slotId(AiUseCase useCase, String language) {
        return useCase.key() + ":" + language;
    }

    private record Numbered(int number) {
    }
}
