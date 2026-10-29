-- Replies of the helpdesk over SMTP (TASK-058, DEC-091). A person confirms a reply to the contact of a ticket; the reply is
-- written together with its send job in one transaction (outbox), so a restart neither loses it nor creates a second one.
-- The job sends it over the SMTP server of the mailbox and records the outcome:
--   pending   waits for the job, also after a failure that certainly sent nothing and that the next attempt may repair
--   sending   an attempt has begun to talk to the server; found again by a later attempt, the outcome is unknown
--   sent      the server accepted the mail; that proves neither delivery nor reading
--   failed    certainly not sent: refused by the server, the last attempt or a precondition that no longer holds
--   uncertain the server may have accepted it, the answer is missing; no attempt repeats it by itself
-- Only a person sends a failed or uncertain reply again, and an uncertain one only with the duplicate risk confirmed.
create table helpdesk_reply (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    ticket_id uuid not null,
    mailbox_id uuid not null references helpdesk_mailbox (id),
    contact_id uuid not null references helpdesk_contact (id),
    from_address text not null check (char_length(from_address) between 3 and 254),
    to_address text not null check (char_length(to_address) between 3 and 254),
    subject text not null check (char_length(subject) between 1 and 998),
    body text not null check (char_length(body) between 1 and 100000),
    -- the own Message-ID; a mail of the customer that names it in In-Reply-To or References answers this reply
    message_id text not null check (char_length(message_id) between 3 and 998),
    in_reply_to text check (in_reply_to is null or char_length(in_reply_to) <= 998),
    references_header text check (references_header is null or char_length(references_header) <= 20000),
    state text not null check (state in ('pending', 'sending', 'sent', 'failed', 'uncertain')),
    -- a stable code of the last failure, never the text of the mail server
    error_code text check (error_code is null or error_code ~ '^[a-z][a-z0-9_.]{0,63}$'),
    -- the job of the current send and how often a person confirmed sending
    job_id uuid not null,
    sends integer not null check (sends >= 1),
    attempted_at timestamptz,
    sent_at timestamptz,
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    version bigint not null check (version > 0),
    check ((state = 'sent') = (sent_at is not null))
);
create unique index helpdesk_reply_message_id on helpdesk_reply (message_id);
create index helpdesk_reply_ticket on helpdesk_reply (ticket_id, created_at);
create index helpdesk_reply_open on helpdesk_reply (company_id, project_id, created_at desc) where state <> 'sent';

-- A mail that answers a reply of a ticket but could not be added to it by itself waits in the review queue with that ticket,
-- so a person can add it there.
alter table helpdesk_message add column related_ticket_id uuid;
