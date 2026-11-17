package taskmanager.ai.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import taskmanager.ai.AiUseCase;

/** The tables of the ai module (TASK-125). Nobody else reads them. */
@Repository
class AiStore {
    private final JdbcClient jdbc;

    AiStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ providers

    List<AiProvider> providers(UUID companyId) {
        return jdbc.sql("select * from ai_provider where company_id = ? and removed_at is null order by lower(name), id").param(companyId)
            .query((rs, row) -> provider(rs)).list();
    }

    Optional<AiProvider> provider(UUID companyId, UUID id) {
        return jdbc.sql("select * from ai_provider where company_id = ? and id = ?").params(companyId, id).query((rs, row) -> provider(rs))
            .optional();
    }

    Optional<AiProvider> lockProvider(UUID companyId, UUID id) {
        return jdbc.sql("select * from ai_provider where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> provider(rs)).optional();
    }

    boolean providerNameTaken(UUID companyId, String name, UUID except) {
        return jdbc.sql("select count(*) from ai_provider where company_id = ? and removed_at is null and lower(name) = lower(?) and id <> ?")
            .params(companyId, name, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0;
    }

    void insertProvider(AiProvider p) {
        jdbc.sql("""
                insert into ai_provider (id, company_id, kind, name, base_url, api_key_cipher, ca_certificate, data_path, enabled, version,
                    created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(p.id(), p.companyId(), p.kind().key(), p.name(), p.baseUrl(), p.keyCipher(), p.caCertificate(), p.dataPath().key(),
            p.enabled(), p.version(), ts(p.createdAt()), p.createdBy(), ts(p.updatedAt()), p.updatedBy()).update();
    }

    void updateProvider(AiProvider p) {
        jdbc.sql("""
                update ai_provider set name = ?, base_url = ?, api_key_cipher = ?, ca_certificate = ?, data_path = ?, enabled = ?, version = ?,
                    updated_at = ?, updated_by = ?
                where id = ?
                """).params(p.name(), p.baseUrl(), p.keyCipher(), p.caCertificate(), p.dataPath().key(), p.enabled(), p.version(),
            ts(p.updatedAt()), p.updatedBy(), p.id()).update();
    }

    void removeProvider(UUID id, long version, Instant now, UUID actor) {
        jdbc.sql("""
                update ai_provider set removed_at = ?, removed_by = ?, version = ?, updated_at = ?, updated_by = ?, api_key_cipher = null,
                    enabled = false
                where id = ?
                """).params(ts(now), actor, version, ts(now), actor, id).update();
    }

    // ------------------------------------------------------------------------------------------------ models

    List<AiModel> models(UUID companyId) {
        return jdbc.sql("select * from ai_model where company_id = ? and removed_at is null order by lower(name), id").param(companyId)
            .query((rs, row) -> model(rs)).list();
    }

    Optional<AiModel> model(UUID companyId, UUID id) {
        return jdbc.sql("select * from ai_model where company_id = ? and id = ?").params(companyId, id).query((rs, row) -> model(rs)).optional();
    }

    Optional<AiModel> lockModel(UUID companyId, UUID id) {
        return jdbc.sql("select * from ai_model where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> model(rs)).optional();
    }

    boolean modelNameTaken(UUID providerId, String name, UUID except) {
        return jdbc.sql("select count(*) from ai_model where provider_id = ? and removed_at is null and lower(name) = lower(?) and id <> ?")
            .params(providerId, name, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0;
    }

    void insertModel(AiModel m) {
        jdbc.sql("""
                insert into ai_model (id, company_id, provider_id, name, model_key, context_tokens, output_tokens, enabled, version,
                    created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(m.id(), m.companyId(), m.providerId(), m.name(), m.modelKey(), m.contextTokens(), m.outputTokens(), m.enabled(),
            m.version(), ts(m.createdAt()), m.createdBy(), ts(m.updatedAt()), m.updatedBy()).update();
    }

    void updateModel(AiModel m) {
        jdbc.sql("""
                update ai_model set name = ?, model_key = ?, context_tokens = ?, output_tokens = ?, enabled = ?, version = ?, updated_at = ?,
                    updated_by = ?
                where id = ?
                """).params(m.name(), m.modelKey(), m.contextTokens(), m.outputTokens(), m.enabled(), m.version(), ts(m.updatedAt()), m.updatedBy(),
            m.id()).update();
    }

    void removeModel(UUID id, long version, Instant now, UUID actor) {
        jdbc.sql("update ai_model set removed_at = ?, removed_by = ?, version = ?, updated_at = ?, updated_by = ?, enabled = false where id = ?")
            .params(ts(now), actor, version, ts(now), actor, id).update();
    }

    /** Removes the models of a removed access; returns their identifiers. */
    List<UUID> removeModelsOfProvider(UUID providerId, Instant now, UUID actor) {
        return jdbc.sql("""
                update ai_model set removed_at = ?, removed_by = ?, version = version + 1, updated_at = ?, updated_by = ?, enabled = false
                where provider_id = ? and removed_at is null
                returning id
                """).params(ts(now), actor, ts(now), actor, providerId).query(UUID.class).list();
    }

    // ------------------------------------------------------------------------------------------------ skills

    /** Serializes every change of the skill of one use case and language until the commit. */
    void lockSkill(UUID companyId, AiUseCase useCase, String language) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))").param("ai.skill:" + companyId + ":" + useCase.key() + ":" + language)
            .query((rs, row) -> 1).single();
    }

    List<SkillVersion> skillVersions(UUID companyId, AiUseCase useCase, String language) {
        return jdbc.sql("select * from ai_skill_version where company_id = ? and use_case = ? and language = ? order by number desc")
            .params(companyId, useCase.key(), language).query((rs, row) -> skill(rs)).list();
    }

    List<SkillVersion> skillsInState(UUID companyId, SkillVersion.State state) {
        return jdbc.sql("select * from ai_skill_version where company_id = ? and state = ?").params(companyId, state.key())
            .query((rs, row) -> skill(rs)).list();
    }

    Optional<SkillVersion> skill(UUID companyId, AiUseCase useCase, String language, SkillVersion.State state) {
        return jdbc.sql("select * from ai_skill_version where company_id = ? and use_case = ? and language = ? and state = ?")
            .params(companyId, useCase.key(), language, state.key()).query((rs, row) -> skill(rs)).optional();
    }

    int nextSkillNumber(UUID companyId, AiUseCase useCase, String language) {
        return jdbc.sql("select coalesce(max(number), 0) + 1 from ai_skill_version where company_id = ? and use_case = ? and language = ?")
            .params(companyId, useCase.key(), language).query(Integer.class).single();
    }

    void insertSkill(SkillVersion s) {
        jdbc.sql("""
                insert into ai_skill_version (id, company_id, use_case, language, number, name, content, state, created_at, created_by,
                    updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(s.id(), s.companyId(), s.useCase().key(), s.language(), s.number(), s.name(), s.content(), s.state().key(),
            ts(s.createdAt()), s.createdBy(), ts(s.updatedAt()), s.updatedBy()).update();
    }

    void updateDraft(UUID id, String name, String content, Instant now, UUID actor) {
        jdbc.sql("update ai_skill_version set name = ?, content = ?, updated_at = ?, updated_by = ? where id = ? and state = 'draft'")
            .params(name, content, ts(now), actor, id).update();
    }

    void deleteDraft(UUID id) {
        jdbc.sql("delete from ai_skill_version where id = ? and state = 'draft'").param(id).update();
    }

    void archive(UUID id, Instant now) {
        jdbc.sql("update ai_skill_version set state = 'archived', archived_at = ? where id = ? and state = 'active'").params(ts(now), id).update();
    }

    void activate(UUID id, Instant now, UUID actor) {
        jdbc.sql("update ai_skill_version set state = 'active', activated_at = ?, activated_by = ? where id = ? and state = 'draft'")
            .params(ts(now), actor, id).update();
    }

    // ------------------------------------------------------------------------------------------------ assignments and limits

    Map<AiUseCase, Assignment> assignments(UUID companyId) {
        Map<AiUseCase, Assignment> result = new EnumMap<>(AiUseCase.class);
        for (AiUseCase useCase : AiUseCase.values()) {
            result.put(useCase, new Assignment(useCase, null, false, 0, null, null));
        }
        jdbc.sql("select * from ai_assignment where company_id = ?").param(companyId).query((rs, row) -> assignment(rs)).list()
            .forEach(a -> result.put(a.useCase(), a));
        return result;
    }

    Optional<Assignment> lockAssignment(UUID companyId, AiUseCase useCase) {
        return jdbc.sql("select * from ai_assignment where company_id = ? and use_case = ? for update").params(companyId, useCase.key())
            .query((rs, row) -> assignment(rs)).optional();
    }

    void saveAssignment(UUID companyId, Assignment a) {
        jdbc.sql("""
                insert into ai_assignment (company_id, use_case, model_id, enabled, version, updated_at, updated_by) values (?, ?, ?, ?, ?, ?, ?)
                on conflict (company_id, use_case) do update set model_id = excluded.model_id, enabled = excluded.enabled,
                    version = excluded.version, updated_at = excluded.updated_at, updated_by = excluded.updated_by
                """).params(companyId, a.useCase().key(), a.modelId(), a.enabled(), a.version(), ts(a.updatedAt()), a.updatedBy()).update();
    }

    /** Takes removed models out of their assignments; returns the use cases it changed. */
    List<String> clearAssignments(List<UUID> modelIds, Instant now, UUID actor) {
        if (modelIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                update ai_assignment set model_id = null, enabled = false, version = version + 1, updated_at = :now, updated_by = :actor
                where model_id in (:ids)
                returning use_case
                """).param("now", ts(now)).param("actor", actor).param("ids", modelIds).query(String.class).list();
    }

    Limits limits(UUID companyId) {
        return jdbc.sql("select * from ai_limit where company_id = ?").param(companyId)
            .query((rs, row) -> new Limits(rs.getInt("person_daily"), rs.getInt("company_daily"), rs.getLong("version"),
                instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class)))
            .optional().orElse(Limits.defaults());
    }

    void saveLimits(UUID companyId, Limits l) {
        jdbc.sql("""
                insert into ai_limit (company_id, person_daily, company_daily, version, updated_at, updated_by) values (?, ?, ?, ?, ?, ?)
                on conflict (company_id) do update set person_daily = excluded.person_daily, company_daily = excluded.company_daily,
                    version = excluded.version, updated_at = excluded.updated_at, updated_by = excluded.updated_by
                """).params(companyId, l.personDaily(), l.companyDaily(), l.version(), ts(l.updatedAt()), l.updatedBy()).update();
    }

    /** Serializes the count of the daily limits of the company with every start until the commit. */
    void lockLimits(UUID companyId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))").param("ai.limit:" + companyId).query((rs, row) -> 1).single();
    }

    // ------------------------------------------------------------------------------------------------ runs

    void insertRun(Run r) {
        jdbc.sql("""
                insert into ai_run (id, company_id, account_id, kind, use_case, language, project_id, object_kind, object_id, object_version,
                    skill_version, provider_id, model_id, model_key, data_path, state, code, truncated, started_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(r.id(), r.companyId(), r.accountId(), r.kind(), r.useCase().key(), r.language(), r.projectId(), r.objectKind(),
            r.objectId(), r.objectVersion(), r.skillVersion(), r.providerId(), r.modelId(), r.modelKey(), r.dataPath(), r.state(), r.code(),
            r.truncated(), ts(r.startedAt())).update();
    }

    Optional<Run> run(UUID id) {
        return jdbc.sql("select * from ai_run where id = ?").param(id).query((rs, row) -> run(rs)).optional();
    }

    Optional<Run> lockRun(UUID id) {
        return jdbc.sql("select * from ai_run where id = ? for update").param(id).query((rs, row) -> run(rs)).optional();
    }

    /** Moves a waiting run to running unless it was cancelled meanwhile; returns whether it did. */
    boolean markRunning(UUID id) {
        return jdbc.sql("update ai_run set state = 'running' where id = ? and state = 'queued' and not cancel_requested").param(id).update() == 1;
    }

    /** Ends an open run; returns whether it was still open. */
    boolean finish(UUID id, String state, String code, boolean truncated, Integer inputTokens, Integer outputTokens, Instant now) {
        return jdbc.sql("""
                update ai_run set state = ?, code = ?, truncated = ?, input_tokens = ?, output_tokens = ?, finished_at = ?
                where id = ? and state in ('queued', 'running')
                """).params(state, code, truncated, inputTokens, outputTokens, ts(now), id).update() == 1;
    }

    void requestCancel(UUID id) {
        jdbc.sql("update ai_run set cancel_requested = true where id = ? and state in ('queued', 'running')").param(id).update();
    }

    long countSince(UUID companyId, UUID accountId, Instant from) {
        if (accountId == null) {
            return jdbc.sql("select count(*) from ai_run where company_id = ? and started_at >= ?").params(companyId, ts(from))
                .query(Long.class).single();
        }
        return jdbc.sql("select count(*) from ai_run where company_id = ? and account_id = ? and started_at >= ?")
            .params(companyId, accountId, ts(from)).query(Long.class).single();
    }

    /** The newest runs of the company before a position, newest first. */
    List<Run> runs(UUID companyId, Instant beforeAt, UUID beforeId, int limit) {
        if (beforeAt == null) {
            return jdbc.sql("select * from ai_run where company_id = ? order by started_at desc, id desc limit ?").params(companyId, limit)
                .query((rs, row) -> run(rs)).list();
        }
        return jdbc.sql("select * from ai_run where company_id = ? and (started_at, id) < (?, ?) order by started_at desc, id desc limit ?")
            .params(companyId, ts(beforeAt), beforeId, limit).query((rs, row) -> run(rs)).list();
    }

    List<Usage> usage(UUID companyId, Instant from) {
        return jdbc.sql("""
                select (started_at at time zone 'UTC')::date as day, use_case, count(*) as runs,
                    count(*) filter (where state = 'succeeded') as succeeded,
                    coalesce(sum(input_tokens), 0) as input_tokens, coalesce(sum(output_tokens), 0) as output_tokens
                from ai_run where company_id = ? and started_at >= ?
                group by 1, 2 order by 1 desc, 2
                """).params(companyId, ts(from))
            .query((rs, row) -> new Usage(rs.getObject("day", LocalDate.class), rs.getString("use_case"), rs.getLong("runs"),
                rs.getLong("succeeded"), rs.getLong("input_tokens"), rs.getLong("output_tokens")))
            .list();
    }

    /** Ends runs that are open longer than a process may run them, as after a stop of the process; returns how many. */
    int failStale(Instant startedBefore, Instant now) {
        return jdbc.sql("""
                update ai_run set state = 'failed', code = 'ai.interrupted', finished_at = ?
                where state in ('queued', 'running') and started_at < ?
                """).params(ts(now), ts(startedBefore)).update();
    }

    // ------------------------------------------------------------------------------------------------ suggestions

    void insertSuggestion(UUID runId, UUID companyId, UUID accountId, String sources, String content, Instant now, Instant expires) {
        jdbc.sql("""
                insert into ai_suggestion (run_id, company_id, account_id, sources, content, created_at, expires_at)
                values (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """).params(runId, companyId, accountId, sources, content, ts(now), ts(expires)).update();
    }

    record Suggestion(UUID runId, UUID accountId, String sources, String content, Instant createdAt, Instant expiresAt) {
    }

    Optional<Suggestion> suggestion(UUID runId) {
        return jdbc.sql("select * from ai_suggestion where run_id = ?").param(runId)
            .query((rs, row) -> new Suggestion(rs.getObject("run_id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("sources"),
                rs.getString("content"), instant(rs, "created_at"), instant(rs, "expires_at")))
            .optional();
    }

    int deleteExpired(Instant now) {
        return jdbc.sql("delete from ai_suggestion where expires_at <= ?").param(ts(now)).update();
    }

    int deleteSuggestionsOf(UUID accountId) {
        return jdbc.sql("delete from ai_suggestion where account_id = ?").param(accountId).update();
    }

    // ------------------------------------------------------------------------------------------------ audit

    void audit(UUID companyId, String subjectKind, String subjectId, String operation, String detail, UUID actor, Instant now) {
        jdbc.sql("""
                insert into ai_audit_event (company_id, subject_kind, subject_id, operation, detail, actor, occurred_at)
                values (?, ?, ?, ?, ?::jsonb, ?, ?)
                """).params(companyId, subjectKind, subjectId, operation, detail, actor, ts(now)).update();
    }

    // ------------------------------------------------------------------------------------------------ mapping

    private static AiProvider provider(ResultSet rs) throws SQLException {
        return new AiProvider(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), AiProvider.Kind.of(rs.getString("kind")),
            rs.getString("name"), rs.getString("base_url"), rs.getBytes("api_key_cipher"), rs.getString("ca_certificate"),
            AiProvider.DataPath.of(rs.getString("data_path")), rs.getBoolean("enabled"), rs.getLong("version"), instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class), instant(rs, "removed_at"));
    }

    private static AiModel model(ResultSet rs) throws SQLException {
        return new AiModel(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("provider_id", UUID.class),
            rs.getString("name"), rs.getString("model_key"), rs.getInt("context_tokens"), rs.getInt("output_tokens"), rs.getBoolean("enabled"),
            rs.getLong("version"), instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"),
            rs.getObject("updated_by", UUID.class), instant(rs, "removed_at"));
    }

    private static SkillVersion skill(ResultSet rs) throws SQLException {
        return new SkillVersion(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
            AiUseCase.of(rs.getString("use_case")).orElseThrow(), rs.getString("language"), rs.getInt("number"), rs.getString("name"),
            rs.getString("content"), SkillVersion.State.of(rs.getString("state")), instant(rs, "created_at"), rs.getObject("created_by", UUID.class),
            instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class), instant(rs, "activated_at"), rs.getObject("activated_by", UUID.class),
            instant(rs, "archived_at"));
    }

    private static Assignment assignment(ResultSet rs) throws SQLException {
        return new Assignment(AiUseCase.of(rs.getString("use_case")).orElseThrow(), rs.getObject("model_id", UUID.class), rs.getBoolean("enabled"),
            rs.getLong("version"), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class));
    }

    private static Run run(ResultSet rs) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("account_id", UUID.class),
            rs.getString("kind"), AiUseCase.of(rs.getString("use_case")).orElseThrow(), rs.getString("language"),
            rs.getObject("project_id", UUID.class), rs.getString("object_kind"), rs.getObject("object_id", UUID.class),
            (Long) rs.getObject("object_version"), rs.getString("skill_version"), rs.getObject("provider_id", UUID.class),
            rs.getObject("model_id", UUID.class), rs.getString("model_key"), rs.getString("data_path"), rs.getString("state"), rs.getString("code"),
            rs.getBoolean("truncated"), (Integer) rs.getObject("input_tokens"), (Integer) rs.getObject("output_tokens"), instant(rs, "started_at"),
            instant(rs, "finished_at"), rs.getBoolean("cancel_requested"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
