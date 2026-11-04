-- The read mark of the portal conversation by position (TASK-080, DEC-110, finding F2 of docs/REVIEW_TASK-079_PR-239.md).
-- A time read before the commit of an answer does not tell whether the answer was visible when the conversation was read:
-- an answer that was still being written could carry an earlier time than an entry shown and so count as read. Every answer
-- of the support that reaches the portal (a support post, a mail answer once sent) now takes the next position of its ticket
-- from a counter row that stays locked until the commit, so positions become visible in their order. The portal acknowledges
-- the highest position it showed, never more than the counter it read before the entries; an answer with a higher position
-- stays new.
create table helpdesk_portal_position (
    ticket_id uuid primary key,
    position bigint not null check (position > 0)
);
alter table helpdesk_portal_post add column position bigint check (position is null or position > 0);
alter table helpdesk_reply add column portal_position bigint check (portal_position is null or portal_position > 0);
alter table helpdesk_portal_seen add column seen_position bigint not null default 0 check (seen_position >= 0);

-- Existing answers in the order of their times; a mark covers the answers up to its time, as it did before.
create temporary table helpdesk_portal_numbered on commit drop as
    select ticket_id, kind, id, at, row_number() over (partition by ticket_id order by at, kind, id) as position
    from (select p.ticket_id, 'post' as kind, p.id, p.created_at as at from helpdesk_portal_post p where p.author_kind = 'support'
          union all
          select r.ticket_id, 'reply' as kind, r.id, r.sent_at as at from helpdesk_reply r where r.state = 'sent' and r.sent_at is not null) entries;
update helpdesk_portal_post p set position = n.position from helpdesk_portal_numbered n where n.kind = 'post' and n.id = p.id;
update helpdesk_reply r set portal_position = n.position from helpdesk_portal_numbered n where n.kind = 'reply' and n.id = r.id;
insert into helpdesk_portal_position (ticket_id, position) select ticket_id, max(position) from helpdesk_portal_numbered group by ticket_id;
update helpdesk_portal_seen s set seen_position = coalesce(
    (select max(n.position) from helpdesk_portal_numbered n where n.ticket_id = s.ticket_id and n.at <= s.seen_at), 0);
alter table helpdesk_portal_post add constraint helpdesk_portal_post_position check ((position is null) = (author_kind = 'customer'));
