package taskmanager.helpdesk.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the portal conversation (TASK-076): posts, the attachments of customer posts and when a request was last opened. */
@Repository
class PortalRepository {
    private final JdbcClient jdbc;

    PortalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A post of the conversation: {@code customer} from a portal account, {@code support} as answer of an internal account. */
    record Post(UUID id, UUID companyId, UUID projectId, UUID ticketId, String authorKind, UUID authorId, String body, Instant createdAt) {
        static final String CUSTOMER = "customer";
        static final String SUPPORT = "support";
    }

    void insert(Post post) {
        jdbc.sql("""
                insert into helpdesk_portal_post (id, company_id, project_id, ticket_id, author_kind, author_id, body, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(post.id(), post.companyId(), post.projectId(), post.ticketId(), post.authorKind(), post.authorId(), post.body(),
                Timestamp.from(post.createdAt())).update();
    }

    /**
     * Serializes the posts of one ticket until the transaction ends, so that two posts never both take the same file. A lock of
     * its own: the ticket row is only held {@code FOR SHARE}, and the lock of customer changes is taken in another order.
     */
    void lockPosts(UUID ticketId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended('helpdesk.portal_post:' || ?::text, 0))").param(ticketId.toString())
            .query((rs, row) -> 1).list();
    }

    void link(UUID postId, UUID attachmentId) {
        jdbc.sql("insert into helpdesk_portal_post_attachment (post_id, attachment_id) values (?, ?)").params(postId, attachmentId).update();
    }

    /** The posts of the ticket, oldest first. */
    List<Post> posts(UUID ticketId) {
        return jdbc.sql("select * from helpdesk_portal_post where ticket_id = ? order by created_at, id").param(ticketId)
            .query(PortalRepository::post).list();
    }

    /** The attachments of the posts, in the order they were linked. */
    Map<UUID, List<UUID>> attachmentsOf(Collection<UUID> postIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        jdbc.sql("select post_id, attachment_id from helpdesk_portal_post_attachment where post_id = any (?) order by post_id, attachment_id")
            .param(postIds.toArray(UUID[]::new))
            .query((rs, row) -> {
                result.computeIfAbsent(rs.getObject(1, UUID.class), key -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
                return 1;
            }).list();
        return result;
    }

    /** Which of the attachments a post already carries. */
    Set<UUID> linked(Collection<UUID> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select attachment_id from helpdesk_portal_post_attachment where attachment_id = any (?)")
            .param(attachmentIds.toArray(UUID[]::new)).query(UUID.class).list());
    }

    /** Records that the account opened the request now. */
    void seen(UUID accountId, UUID ticketId, Instant now) {
        jdbc.sql("""
                insert into helpdesk_portal_seen (account_id, ticket_id, seen_at) values (?, ?, ?)
                on conflict (account_id, ticket_id) do update set seen_at = greatest(helpdesk_portal_seen.seen_at, excluded.seen_at)
                """).params(accountId, ticketId, Timestamp.from(now)).update();
    }

    /** What the portal list shows of the conversation of a request. */
    record Activity(UUID ticketId, Instant lastActivityAt, Instant lastSupportAt, Instant seenAt) {
    }

    /**
     * For each ticket: the time of the last entry the customer sees (mail of the organization, sent mail answer or post), of the
     * last answer of the support (sent mail answer or support post), and when the account last opened it.
     */
    List<Activity> activity(UUID accountId, UUID organizationId, Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select t.id,
                    greatest((select max(m.received_at) from helpdesk_message m where m.ticket_id = t.id and m.state = 'ticket' and m.customer_id = ?),
                             (select max(r.sent_at) from helpdesk_reply r where r.ticket_id = t.id and r.state = 'sent'),
                             (select max(p.created_at) from helpdesk_portal_post p where p.ticket_id = t.id)) as last_activity,
                    greatest((select max(r.sent_at) from helpdesk_reply r where r.ticket_id = t.id and r.state = 'sent'),
                             (select max(p.created_at) from helpdesk_portal_post p where p.ticket_id = t.id and p.author_kind = 'support')) as last_support,
                    (select s.seen_at from helpdesk_portal_seen s where s.account_id = ? and s.ticket_id = t.id) as seen
                from unnest(?) as t(id)
                """).params(organizationId, accountId, ticketIds.toArray(UUID[]::new))
            .query((rs, row) -> new Activity(rs.getObject("id", UUID.class), instant(rs, "last_activity"), instant(rs, "last_support"),
                instant(rs, "seen"))).list();
    }

    private static Post post(ResultSet rs, int row) throws SQLException {
        return new Post(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("ticket_id", UUID.class), rs.getString("author_kind"), rs.getObject("author_id", UUID.class), rs.getString("body"),
            instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
