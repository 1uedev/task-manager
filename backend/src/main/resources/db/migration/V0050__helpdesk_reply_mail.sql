-- Language of contacts and the finished mail of each reply (TASK-082, DEC-112), owned by the helpdesk module.

-- The language of a contact; mails to the contact follow it. Existing contacts speak German.
alter table helpdesk_contact add column language text not null default 'de' check (language in ('de', 'en'));

-- Images embedded in sent replies, kept once per content. A reply keeps the logo of its confirmation, even after the logo
-- of the appearance changed.
create table helpdesk_reply_image (
    digest text primary key check (digest ~ '^[0-9a-f]{64}$'),
    content_type text not null check (content_type in ('image/png', 'image/jpeg', 'image/webp')),
    content bytea not null check (octet_length(content) between 1 and 524288)
);

-- The finished mail of a reply, written at its confirmation with the template and appearance valid then. A waiting or
-- repeated send uses it as it is. Replies confirmed before this migration have none and go as plain text of their body.
create table helpdesk_reply_mail (
    reply_id uuid primary key references helpdesk_reply (id),
    language text not null check (language in ('de', 'en')),
    text_body text not null check (char_length(text_body) between 1 and 1000000),
    html_body text not null check (char_length(html_body) between 1 and 8000000),
    logo_digest text references helpdesk_reply_image (digest)
);
