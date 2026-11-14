package taskmanager.git.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the git module (TASK-111). Nobody else reads them. */
@Repository
class GitStore {
    private final JdbcClient jdbc;

    GitStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ providers

    /** The providers of the company that are not removed, by name. */
    List<Provider> providers(UUID companyId) {
        return jdbc.sql("select * from git_provider where company_id = ? and removed_at is null order by lower(name), id").param(companyId)
            .query((rs, row) -> provider(rs)).list();
    }

    /** A provider of the company, removed or not. */
    Optional<Provider> provider(UUID companyId, UUID id) {
        return jdbc.sql("select * from git_provider where company_id = ? and id = ?").params(companyId, id)
            .query((rs, row) -> provider(rs)).optional();
    }

    /** A provider by its identifier alone, for work that runs without a request. */
    Optional<Provider> provider(UUID id) {
        return jdbc.sql("select * from git_provider where id = ?").param(id).query((rs, row) -> provider(rs)).optional();
    }

    Optional<Provider> lockProvider(UUID companyId, UUID id) {
        return jdbc.sql("select * from git_provider where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> provider(rs)).optional();
    }

    boolean providerNameTaken(UUID companyId, String name, UUID except) {
        return jdbc.sql("select count(*) from git_provider where company_id = ? and removed_at is null and lower(name) = lower(?) and id <> ?")
            .params(companyId, name, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0;
    }

    void insertProvider(Provider p) {
        jdbc.sql("""
                insert into git_provider (id, company_id, kind, name, base_url, client_id, client_secret_cipher, ca_certificate, version,
                    created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(p.id(), p.companyId(), p.kind().key(), p.name(), p.baseUrl(), p.clientId(), p.secretCipher(), p.caCertificate(),
            p.version(), ts(p.createdAt()), p.createdBy(), ts(p.updatedAt()), p.updatedBy()).update();
    }

    void updateProvider(Provider p) {
        jdbc.sql("""
                update git_provider set name = ?, base_url = ?, client_id = ?, client_secret_cipher = ?, ca_certificate = ?, version = ?,
                    updated_at = ?, updated_by = ?
                where id = ?
                """).params(p.name(), p.baseUrl(), p.clientId(), p.secretCipher(), p.caCertificate(), p.version(), ts(p.updatedAt()),
            p.updatedBy(), p.id()).update();
    }

    void removeProvider(UUID id, long version, Instant now, UUID actor) {
        jdbc.sql("update git_provider set removed_at = ?, removed_by = ?, version = ?, updated_at = ?, updated_by = ?, client_secret_cipher = null where id = ?")
            .params(ts(now), actor, version, ts(now), actor, id).update();
    }

    // ------------------------------------------------------------------------------------------------ connections

    List<Connection> connections(UUID companyId, UUID accountId) {
        return jdbc.sql("select * from git_connection where company_id = ? and account_id = ?").params(companyId, accountId)
            .query((rs, row) -> connection(rs)).list();
    }

    Optional<Connection> connection(UUID providerId, UUID accountId) {
        return jdbc.sql("select * from git_connection where provider_id = ? and account_id = ?").params(providerId, accountId)
            .query((rs, row) -> connection(rs)).optional();
    }

    Optional<Connection> lockConnection(UUID providerId, UUID accountId) {
        return jdbc.sql("select * from git_connection where provider_id = ? and account_id = ? for update").params(providerId, accountId)
            .query((rs, row) -> connection(rs)).optional();
    }

    void saveConnection(Connection c) {
        jdbc.sql("""
                insert into git_connection (provider_id, account_id, company_id, external_user_id, state, access_cipher, refresh_cipher,
                    access_expires_at, refresh_expires_at, connected_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (provider_id, account_id) do update set external_user_id = excluded.external_user_id, state = excluded.state,
                    access_cipher = excluded.access_cipher, refresh_cipher = excluded.refresh_cipher,
                    access_expires_at = excluded.access_expires_at, refresh_expires_at = excluded.refresh_expires_at,
                    connected_at = excluded.connected_at, updated_at = excluded.updated_at
                """).params(c.providerId(), c.accountId(), c.companyId(), c.externalUserId(), c.state(), c.accessCipher(), c.refreshCipher(),
            ts(c.accessExpiresAt()), ts(c.refreshExpiresAt()), ts(c.connectedAt()), ts(c.updatedAt())).update();
    }

    /** Forgets the tokens and asks the person to connect again; the connection stays visible as "renew". */
    void markRenew(UUID providerId, UUID accountId, Instant now) {
        jdbc.sql("""
                update git_connection set state = 'renew', access_cipher = null, refresh_cipher = null, access_expires_at = null,
                    refresh_expires_at = null, updated_at = ?
                where provider_id = ? and account_id = ?
                """).params(ts(now), providerId, accountId).update();
    }

    int deleteConnection(UUID providerId, UUID accountId) {
        return jdbc.sql("delete from git_connection where provider_id = ? and account_id = ?").params(providerId, accountId).update();
    }

    /** Deletes every connection of a provider; returns the accounts whose connection ended. */
    List<UUID> deleteConnectionsOfProvider(UUID providerId) {
        return jdbc.sql("delete from git_connection where provider_id = ? returning account_id").param(providerId).query(UUID.class).list();
    }

    /** Deletes every connection of an account; returns the providers whose connection ended. */
    List<UUID> deleteConnectionsOfAccount(UUID accountId) {
        return jdbc.sql("delete from git_connection where account_id = ? returning provider_id").param(accountId).query(UUID.class).list();
    }

    // ------------------------------------------------------------------------------------------------ OAuth states

    void insertState(byte[] hash, UUID companyId, UUID providerId, UUID accountId, String verifier, Instant now, Instant expires) {
        jdbc.sql("delete from git_oauth_state where expires_at < ?").param(ts(now)).update();
        jdbc.sql("""
                insert into git_oauth_state (state_hash, company_id, provider_id, account_id, verifier, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """).params(hash, companyId, providerId, accountId, verifier, ts(now), ts(expires)).update();
    }

    /** Takes a state once: it is deleted whether it is still valid or not. */
    Optional<OAuthState> takeState(byte[] hash) {
        return jdbc.sql("delete from git_oauth_state where state_hash = ? returning company_id, provider_id, account_id, verifier, expires_at")
            .param(hash)
            .query((rs, row) -> new OAuthState(rs.getObject("company_id", UUID.class), rs.getObject("provider_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getString("verifier"), instant(rs, "expires_at"))).optional();
    }

    record OAuthState(UUID companyId, UUID providerId, UUID accountId, String verifier, Instant expiresAt) {
    }

    // ------------------------------------------------------------------------------------------------ releases

    List<Release> releases(UUID companyId, UUID projectId) {
        return jdbc.sql("""
                select r.* from git_repository r join git_provider p on p.id = r.provider_id
                where r.company_id = ? and r.project_id = ? and p.removed_at is null
                order by r.released_at, r.id
                """).params(companyId, projectId).query((rs, row) -> release(rs)).list();
    }

    Optional<Release> release(UUID companyId, UUID projectId, UUID id) {
        return jdbc.sql("select * from git_repository where company_id = ? and project_id = ? and id = ?").params(companyId, projectId, id)
            .query((rs, row) -> release(rs)).optional();
    }

    boolean released(UUID projectId, UUID providerId, String externalId) {
        return jdbc.sql("select count(*) from git_repository where project_id = ? and provider_id = ? and external_id = ?")
            .params(projectId, providerId, externalId).query(Long.class).single() > 0;
    }

    /**
     * Whether the repository is released for the project, with the release row held {@code FOR SHARE} until the commit, so a
     * withdrawal waits for a link that is being created and a link is never stored after its release was withdrawn (TASK-114, F9).
     */
    boolean shareReleased(UUID projectId, UUID providerId, String externalId) {
        return !jdbc.sql("select id from git_repository where project_id = ? and provider_id = ? and external_id = ? for share")
            .params(projectId, providerId, externalId).query((rs, row) -> rs.getObject("id", UUID.class)).list().isEmpty();
    }

    /** Whether any project released a repository of the provider or any ticket or requirement links one of its objects. */
    boolean providerInUse(UUID providerId) {
        return jdbc.sql("""
                select exists (select 1 from git_repository where provider_id = ?) or exists (select 1 from git_link where provider_id = ?)
                """).params(providerId, providerId).query(Boolean.class).single();
    }

    long releaseCount(UUID projectId) {
        return jdbc.sql("select count(*) from git_repository where project_id = ?").param(projectId).query(Long.class).single();
    }

    void insertRelease(Release r) {
        jdbc.sql("""
                insert into git_repository (id, company_id, project_id, provider_id, external_id, released_at, released_by)
                values (?, ?, ?, ?, ?, ?, ?)
                """).params(r.id(), r.companyId(), r.projectId(), r.providerId(), r.externalId(), ts(r.releasedAt()), r.releasedBy()).update();
    }

    void deleteRelease(UUID id) {
        jdbc.sql("delete from git_repository where id = ?").param(id).update();
    }

    /** The provider held {@code FOR SHARE}, so it is not removed while a link to it is created. */
    Optional<Provider> shareProvider(UUID companyId, UUID id) {
        return jdbc.sql("select * from git_provider where company_id = ? and id = ? for share").params(companyId, id)
            .query((rs, row) -> provider(rs)).optional();
    }

    // ------------------------------------------------------------------------------------------------ links

    List<GitLink> links(UUID companyId, String subjectKind, UUID subjectId) {
        return jdbc.sql("select * from git_link where company_id = ? and subject_kind = ? and subject_id = ? order by linked_at, id")
            .params(companyId, subjectKind, subjectId).query((rs, row) -> link(rs)).list();
    }

    /** Serializes the changes of the links of one subject until the commit, for the limit per subject. */
    void lockLinks(String subjectKind, UUID subjectId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))").param("git_link:" + subjectKind + ":" + subjectId)
            .query((rs, row) -> 1).list();
    }

    boolean linked(String subjectKind, UUID subjectId, UUID providerId, String repositoryId, String objectKind, String objectKey) {
        return jdbc.sql("""
                select count(*) from git_link where subject_kind = ? and subject_id = ? and provider_id = ? and repository_id = ?
                    and object_kind = ? and object_key = ?
                """).params(subjectKind, subjectId, providerId, repositoryId, objectKind, objectKey).query(Long.class).single() > 0;
    }

    long linkCount(String subjectKind, UUID subjectId) {
        return jdbc.sql("select count(*) from git_link where subject_kind = ? and subject_id = ?").params(subjectKind, subjectId)
            .query(Long.class).single();
    }

    void insertLink(GitLink l) {
        jdbc.sql("""
                insert into git_link (id, company_id, subject_kind, subject_id, project_id, provider_id, repository_id, object_kind, object_key,
                    linked_at, linked_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(l.id(), l.companyId(), l.subjectKind(), l.subjectId(), l.projectId(), l.providerId(), l.repositoryId(), l.objectKind(),
            l.objectKey(), ts(l.linkedAt()), l.linkedBy()).update();
    }

    Optional<GitLink> deleteLink(UUID companyId, String subjectKind, UUID subjectId, UUID id) {
        return jdbc.sql("delete from git_link where company_id = ? and subject_kind = ? and subject_id = ? and id = ? returning *")
            .params(companyId, subjectKind, subjectId, id).query((rs, row) -> link(rs)).optional();
    }

    // ------------------------------------------------------------------------------------------------ audit

    void audit(UUID companyId, String subjectKind, UUID subjectId, String operation, String detail, UUID actor, Instant now) {
        jdbc.sql("""
                insert into git_audit_event (company_id, subject_kind, subject_id, operation, detail, actor, occurred_at)
                values (?, ?, ?, ?, ?::jsonb, ?, ?)
                """).params(companyId, subjectKind, subjectId, operation, detail, actor, ts(now)).update();
    }

    // ------------------------------------------------------------------------------------------------ mapping

    private static Provider provider(ResultSet rs) throws SQLException {
        return new Provider(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), Provider.Kind.of(rs.getString("kind")),
            rs.getString("name"), rs.getString("base_url"), rs.getString("client_id"), rs.getBytes("client_secret_cipher"),
            rs.getString("ca_certificate"), rs.getLong("version"), instant(rs, "created_at"), rs.getObject("created_by", UUID.class),
            instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class), instant(rs, "removed_at"));
    }

    private static Connection connection(ResultSet rs) throws SQLException {
        return new Connection(rs.getObject("provider_id", UUID.class), rs.getObject("account_id", UUID.class),
            rs.getObject("company_id", UUID.class), rs.getString("external_user_id"), rs.getString("state"), rs.getBytes("access_cipher"),
            rs.getBytes("refresh_cipher"), instant(rs, "access_expires_at"), instant(rs, "refresh_expires_at"), instant(rs, "connected_at"),
            instant(rs, "updated_at"));
    }

    private static Release release(ResultSet rs) throws SQLException {
        return new Release(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("provider_id", UUID.class), rs.getString("external_id"), instant(rs, "released_at"), rs.getObject("released_by", UUID.class));
    }

    private static GitLink link(ResultSet rs) throws SQLException {
        return new GitLink(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("subject_kind"),
            rs.getObject("subject_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getObject("provider_id", UUID.class),
            rs.getString("repository_id"), rs.getString("object_kind"), rs.getString("object_key"), instant(rs, "linked_at"),
            rs.getObject("linked_by", UUID.class));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
