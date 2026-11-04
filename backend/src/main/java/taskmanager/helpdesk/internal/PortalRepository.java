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

    /**
     * A post of the conversation: {@code customer} from a portal account, {@code support} as answer of an internal account.
     * {@code organizationId} is the organization it was written for (TASK-079, DEC-109): of the portal account, or the customer
     * of the ticket when the support answered. Only the portal of that organization shows it.
     */
    /**
     * A post of the conversation. An answer of the support carries its position (TASK-080, F2), see
     * {@link HelpdeskRepository#nextPortalPosition}; a post of the customer has none.
     */
    record Post(UUID id, UUID companyId, UUID projectId, UUID ticketId, String authorKind, UUID authorId, String body, Instant createdAt,
            UUID organizationId, Long position) {
        static final String CUSTOMER = "customer";
        static final String SUPPORT = "support";
    }

    void insert(Post post) {
        jdbc.sql("""
                insert into helpdesk_portal_post (id, company_id, project_id, ticket_id, author_kind, author_id, body, created_at,
                    organization_id, position)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(post.id(), post.companyId(), post.projectId(), post.ticketId(), post.authorKind(), post.authorId(), post.body(),
                Timestamp.from(post.createdAt()), post.organizationId(), post.position()).update();
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

    /** The posts of the ticket written for the organization, oldest first. */
    List<Post> postsFor(UUID ticketId, UUID organizationId) {
        return jdbc.sql("select * from helpdesk_portal_post where ticket_id = ? and organization_id = ? order by created_at, id")
            .params(ticketId, organizationId).query(PortalRepository::post).list();
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

    /**
     * Records that the account saw the conversation of the request up to the given position of the answers of the support
     * (TASK-080, F2), at the given time; an earlier mark never wins.
     */
    void seen(UUID accountId, UUID ticketId, long position, Instant now) {
        jdbc.sql("""
                insert into helpdesk_portal_seen (account_id, ticket_id, seen_at, seen_position) values (?, ?, ?, ?)
                on conflict (account_id, ticket_id) do update set seen_at = greatest(helpdesk_portal_seen.seen_at, excluded.seen_at),
                    seen_position = greatest(helpdesk_portal_seen.seen_position, excluded.seen_position)
                """).params(accountId, ticketId, Timestamp.from(now), position).update();
    }

    /** What the portal list shows of the conversation of a request. */
    record Activity(UUID ticketId, Instant lastActivityAt, Instant lastSupportAt, Instant seenAt, long lastSupportPosition, long seenPosition) {
    }

    /**
     * For each ticket: the time of the last entry the customer sees (mail of the organization, sent mail answer or post), of the
     * last answer of the support (sent mail answer or support post), and when the account last opened it; and the highest
     * position of an answer of the support for the organization and the one the account acknowledged (TASK-080, F2).
     */
    List<Activity> activity(UUID accountId, UUID organizationId, Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select t.id,
                    greatest((select max(m.received_at) from helpdesk_message m where m.ticket_id = t.id and m.state = 'ticket' and m.customer_id = o.id),
                             (select max(r.sent_at) from helpdesk_reply r where r.ticket_id = t.id and r.state = 'sent' and r.customer_id = o.id),
                             (select max(p.created_at) from helpdesk_portal_post p where p.ticket_id = t.id and p.organization_id = o.id)) as last_activity,
                    greatest((select max(r.sent_at) from helpdesk_reply r where r.ticket_id = t.id and r.state = 'sent' and r.customer_id = o.id),
                             (select max(p.created_at) from helpdesk_portal_post p where p.ticket_id = t.id and p.author_kind = 'support'
                                 and p.organization_id = o.id)) as last_support,
                    coalesce(greatest((select max(r.portal_position) from helpdesk_reply r where r.ticket_id = t.id and r.state = 'sent'
                                           and r.customer_id = o.id),
                                      (select max(p.position) from helpdesk_portal_post p where p.ticket_id = t.id and p.author_kind = 'support'
                                           and p.organization_id = o.id)), 0) as last_support_position,
                    s.seen_at as seen, coalesce(s.seen_position, 0) as seen_position
                from unnest(?) as t(id) cross join (select ?::uuid as id) as o
                    left join helpdesk_portal_seen s on s.account_id = ? and s.ticket_id = t.id
                """).params(ticketIds.toArray(UUID[]::new), organizationId, accountId)
            .query((rs, row) -> new Activity(rs.getObject("id", UUID.class), instant(rs, "last_activity"), instant(rs, "last_support"),
                instant(rs, "seen"), rs.getLong("last_support_position"), rs.getLong("seen_position"))).list();
    }

    private static Post post(ResultSet rs, int row) throws SQLException {
        return new Post(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("ticket_id", UUID.class), rs.getString("author_kind"), rs.getObject("author_id", UUID.class), rs.getString("body"),
            instant(rs, "created_at"), rs.getObject("organization_id", UUID.class), rs.getObject("position", Long.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
