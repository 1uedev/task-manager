-- The conversation of the customer portal (TASK-076, DEC-106): posts of portal accounts and portal answers of the support,
-- the attachments a customer post carries, and when a portal account last opened a request.
create table helpdesk_portal_post (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    ticket_id uuid not null,
    -- customer: a post of a portal account; support: an answer of an internal account through the portal
    author_kind varchar(10) not null check (author_kind in ('customer', 'support')),
    author_id uuid not null,
    body text not null check (char_length(body) between 1 and 100000),
    created_at timestamptz not null
);
create index helpdesk_portal_post_ticket on helpdesk_portal_post (ticket_id, created_at, id);

create table helpdesk_portal_post_attachment (
    post_id uuid not null references helpdesk_portal_post (id),
    attachment_id uuid not null,
    primary key (post_id, attachment_id)
);
create unique index helpdesk_portal_post_attachment_once on helpdesk_portal_post_attachment (attachment_id);

create table helpdesk_portal_seen (
    account_id uuid not null,
    ticket_id uuid not null,
    seen_at timestamptz not null,
    primary key (account_id, ticket_id)
);
