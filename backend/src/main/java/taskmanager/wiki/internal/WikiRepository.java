package taskmanager.wiki.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the wiki module. Nobody else reads them. */
@Repository
class WikiRepository {
    private static final String PAGE = "p.id, p.company_id, p.project_id, p.parent_id, p.language, p.created_at, p.created_by, p.archived_at, "
        + "p.archived_by, p.version";
    private static final String VARIANT = "v.page_id, v.language, v.draft_revision, v.published_revision, v.published_at, v.published_by";

    private final JdbcClient jdbc;

    WikiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Page(UUID id, UUID companyId, UUID projectId, UUID parentId, String language, Instant createdAt, UUID createdBy, Instant archivedAt,
            UUID archivedBy, long version) {
        boolean archived() {
            return archivedAt != null;
        }
    }

    record Variant(UUID pageId, String language, int draftRevision, Integer publishedRevision, Instant publishedAt, UUID publishedBy) {
        boolean published() {
            return publishedRevision != null;
        }
    }

    record Revision(UUID pageId, String language, int revision, String title, String content, int schemaVersion, Instant savedAt, UUID savedBy,
            Integer basedOn, Integer restoredFrom) {
    }

    /** A page of the tree with the titles of its draft and of its publication. */
    record TreeRow(Page page, Variant variant, String draftTitle, String publishedTitle, Instant savedAt, UUID savedBy) {
    }

    record Event(long id, String kind, String language, Integer revision, UUID actorId, Instant occurredAt) {
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Page page(ResultSet rs) throws SQLException {
        return new Page(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("parent_id", UUID.class), rs.getString("language"), instant(rs.getTimestamp("created_at")), rs.getObject("created_by", UUID.class),
            instant(rs.getTimestamp("archived_at")), rs.getObject("archived_by", UUID.class), rs.getLong("version"));
    }

    private static Variant variant(ResultSet rs) throws SQLException {
        return new Variant(rs.getObject("page_id", UUID.class), rs.getString("language"), rs.getInt("draft_revision"), integer(rs, "published_revision"),
            instant(rs.getTimestamp("published_at")), rs.getObject("published_by", UUID.class));
    }

    private static Revision revision(ResultSet rs, boolean content) throws SQLException {
        return new Revision(rs.getObject("page_id", UUID.class), rs.getString("language"), rs.getInt("revision"), rs.getString("title"),
            content ? rs.getString("content") : null, rs.getInt("schema_version"), instant(rs.getTimestamp("saved_at")), rs.getObject("saved_by", UUID.class),
            integer(rs, "based_on"), integer(rs, "restored_from"));
    }

    // ------------------------------------------------------------------------------------------------ pages

    /** Serializes the changes of the tree of one project (create, move, archive, restore) until the commit. */
    void lockTree(UUID projectId) {
        jdbc.sql("select pg_advisory_xact_lock(?)").param(projectId.getMostSignificantBits() ^ projectId.getLeastSignificantBits())
            .query((rs, n) -> 1).list();
    }

    Optional<Page> find(UUID companyId, UUID id) {
        return jdbc.sql("select " + PAGE + " from wiki_page p where p.company_id = ? and p.id = ?").params(companyId, id)
            .query((rs, n) -> page(rs)).optional();
    }

    Optional<Page> lock(UUID companyId, UUID id) {
        return jdbc.sql("select " + PAGE + " from wiki_page p where p.company_id = ? and p.id = ? for update").params(companyId, id)
            .query((rs, n) -> page(rs)).optional();
    }

    void insert(Page page) {
        jdbc.sql("""
                insert into wiki_page (id, company_id, project_id, parent_id, language, created_at, created_by, archived_at, archived_by, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(page.id(), page.companyId(), page.projectId(), page.parentId(), page.language(), ts(page.createdAt()), page.createdBy(),
                ts(page.archivedAt()), page.archivedBy(), page.version())
            .update();
    }

    void update(Page page) {
        jdbc.sql("update wiki_page set parent_id = ?, archived_at = ?, archived_by = ?, version = ? where id = ?")
            .params(page.parentId(), ts(page.archivedAt()), page.archivedBy(), page.version(), page.id()).update();
    }

    /** Moves the pages below {@code from} that are not archived to {@code to} and raises their versions. */
    void reparentChildren(UUID from, UUID to) {
        jdbc.sql("update wiki_page set parent_id = ?, version = version + 1 where parent_id = ? and archived_at is null").params(to, from).update();
    }

    /** Every page of the project with its main language version, archived ones only when asked for. */
    List<TreeRow> tree(UUID companyId, UUID projectId, boolean archived) {
        return jdbc.sql("select " + PAGE + ", " + VARIANT + """
                , d.title as draft_title, d.saved_at, d.saved_by, r.title as published_title
                from wiki_page p
                join wiki_variant v on v.page_id = p.id and v.language = p.language
                join wiki_revision d on d.page_id = p.id and d.language = p.language and d.revision = v.draft_revision
                left join wiki_revision r on r.page_id = p.id and r.language = p.language and r.revision = v.published_revision
                where p.company_id = ? and p.project_id = ? and (p.archived_at is null) <> ?
                """)
            .params(companyId, projectId, archived)
            .query((rs, n) -> new TreeRow(page(rs), variant(rs), rs.getString("draft_title"), rs.getString("published_title"),
                instant(rs.getTimestamp("saved_at")), rs.getObject("saved_by", UUID.class)))
            .list();
    }

    // ------------------------------------------------------------------------------------------------ variants and revisions

    Optional<Variant> variant(UUID pageId, String language) {
        return jdbc.sql("select " + VARIANT + " from wiki_variant v where v.page_id = ? and v.language = ?").params(pageId, language)
            .query((rs, n) -> variant(rs)).optional();
    }

    Optional<Variant> lockVariant(UUID pageId, String language) {
        return jdbc.sql("select " + VARIANT + " from wiki_variant v where v.page_id = ? and v.language = ? for update").params(pageId, language)
            .query((rs, n) -> variant(rs)).optional();
    }

    void insert(Variant variant) {
        jdbc.sql("""
                insert into wiki_variant (page_id, language, draft_revision, published_revision, published_at, published_by)
                values (?, ?, ?, ?, ?, ?)
                """)
            .params(variant.pageId(), variant.language(), variant.draftRevision(), variant.publishedRevision(), ts(variant.publishedAt()),
                variant.publishedBy())
            .update();
    }

    void update(Variant variant) {
        jdbc.sql("""
                update wiki_variant set draft_revision = ?, published_revision = ?, published_at = ?, published_by = ?
                where page_id = ? and language = ?
                """)
            .params(variant.draftRevision(), variant.publishedRevision(), ts(variant.publishedAt()), variant.publishedBy(), variant.pageId(),
                variant.language())
            .update();
    }

    void insert(Revision revision, Collection<UUID> images) {
        jdbc.sql("""
                insert into wiki_revision (page_id, language, revision, title, content, schema_version, saved_at, saved_by, based_on, restored_from)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(revision.pageId(), revision.language(), revision.revision(), revision.title(), revision.content(), revision.schemaVersion(),
                ts(revision.savedAt()), revision.savedBy(), revision.basedOn(), revision.restoredFrom())
            .update();
        for (UUID image : images) {
            jdbc.sql("insert into wiki_revision_image (page_id, language, revision, image_id) values (?, ?, ?, ?)")
                .params(revision.pageId(), revision.language(), revision.revision(), image).update();
        }
    }

    Optional<Revision> revision(UUID pageId, String language, int revision) {
        return jdbc.sql("select * from wiki_revision where page_id = ? and language = ? and revision = ?").params(pageId, language, revision)
            .query((rs, n) -> revision(rs, true)).optional();
    }

    /** The revisions of one language without their content, newest first. */
    List<Revision> revisions(UUID pageId, String language) {
        return jdbc.sql("""
                select page_id, language, revision, title, schema_version, saved_at, saved_by, based_on, restored_from
                from wiki_revision where page_id = ? and language = ? order by revision desc
                """)
            .params(pageId, language).query((rs, n) -> revision(rs, false)).list();
    }

    // ------------------------------------------------------------------------------------------------ images

    void insertImage(UUID id, UUID companyId, UUID pageId, Instant at, UUID actor) {
        jdbc.sql("insert into wiki_image (id, company_id, page_id, uploaded_at, uploaded_by) values (?, ?, ?, ?, ?)")
            .params(id, companyId, pageId, ts(at), actor).update();
    }

    /** The page an image was uploaded to. */
    Optional<UUID> imagePage(UUID companyId, UUID imageId) {
        return jdbc.sql("select page_id from wiki_image where company_id = ? and id = ?").params(companyId, imageId).query(UUID.class).optional();
    }

    /** Which of the images were uploaded to the page. */
    Set<UUID> imagesOf(UUID pageId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select id from wiki_image where page_id = ? and id = any (?)").params(pageId, ids.toArray(UUID[]::new))
            .query(UUID.class).list());
    }

    /** Whether a published revision of the page shows the image. */
    boolean imagePublished(UUID pageId, UUID imageId) {
        return jdbc.sql("""
                select exists (select 1 from wiki_variant v join wiki_revision_image i
                    on i.page_id = v.page_id and i.language = v.language and i.revision = v.published_revision
                    where v.page_id = ? and i.image_id = ?)
                """)
            .params(pageId, imageId).query(Boolean.class).single();
    }

    // ------------------------------------------------------------------------------------------------ events

    void event(UUID companyId, UUID pageId, String kind, String language, Integer revision, UUID actor, Instant at) {
        jdbc.sql("insert into wiki_event (company_id, page_id, kind, language, revision, actor_id, occurred_at) values (?, ?, ?, ?, ?, ?, ?)")
            .params(companyId, pageId, kind, language, revision, actor, ts(at)).update();
    }

    List<Event> events(UUID pageId) {
        return jdbc.sql("select id, kind, language, revision, actor_id, occurred_at from wiki_event where page_id = ? order by id desc").param(pageId)
            .query((rs, n) -> new Event(rs.getLong("id"), rs.getString("kind"), rs.getString("language"), integer(rs, "revision"),
                rs.getObject("actor_id", UUID.class), instant(rs.getTimestamp("occurred_at"))))
            .list();
    }
}
