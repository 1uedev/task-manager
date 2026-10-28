-- Helpdesk intake over IMAP (TASK-057, DEC-086): customer organizations and their contacts, mailboxes, the mails that
-- arrived with their state in the review queue, the customer and contact of a ticket and the audit trail. Projects and
-- tickets are referenced by identifier only; the helpdesk module never reads their tables.

-- A customer organization of the company and the projects it is released for.
create table helpdesk_customer (
    id uuid primary key,
    company_id uuid not null,
    name text not null check (char_length(name) between 1 and 120),
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);
create unique index helpdesk_customer_name on helpdesk_customer (company_id, lower(name));

create table helpdesk_customer_project (
    customer_id uuid not null references helpdesk_customer (id),
    project_id uuid not null,
    primary key (customer_id, project_id)
);

-- A person of exactly one customer organization. A contact is no account and has no right of its own.
create table helpdesk_contact (
    id uuid primary key,
    company_id uuid not null,
    customer_id uuid not null references helpdesk_customer (id),
    name text not null check (char_length(name) between 1 and 120),
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);
create index helpdesk_contact_customer on helpdesk_contact (customer_id);

-- Each address belongs to at most one contact of the company, in lower case, so a sender is never assigned ambiguously.
create table helpdesk_contact_address (
    company_id uuid not null,
    address text not null check (char_length(address) between 3 and 254 and address = lower(address)),
    contact_id uuid not null references helpdesk_contact (id),
    primary key (company_id, address)
);
create index helpdesk_contact_address_contact on helpdesk_contact_address (contact_id);

-- A mailbox that the intake reads over IMAP. The password is encrypted with the key of the operation (AES-GCM, the
-- identifier of the mailbox as associated data) and never leaves the server. The SMTP settings are stored for the replies
-- of TASK-058 and not used yet.
create table helpdesk_mailbox (
    id uuid primary key,
    company_id uuid not null,
    name text not null check (char_length(name) between 1 and 120),
    enabled boolean not null,
    project_id uuid not null,
    ticket_type text not null,
    imap_host text not null check (char_length(imap_host) between 1 and 253),
    imap_port integer not null check (imap_port between 1 and 65535),
    imap_security text not null check (imap_security in ('tls', 'starttls', 'none')),
    username text not null check (char_length(username) between 1 and 254),
    password_cipher bytea,
    folder text not null check (char_length(folder) between 1 and 200),
    smtp_host text check (smtp_host is null or char_length(smtp_host) between 1 and 253),
    smtp_port integer check (smtp_port is null or smtp_port between 1 and 65535),
    smtp_security text check (smtp_security is null or smtp_security in ('tls', 'starttls', 'none')),
    address text check (address is null or char_length(address) between 3 and 254),
    -- the authserv-id of the own receiving server in Authentication-Results; without it no sender counts as confirmed
    authserv_id text check (authserv_id is null or char_length(authserv_id) between 1 and 253),
    max_bytes bigint not null check (max_bytes > 0),
    -- where the intake stands in the folder
    uid_validity bigint,
    last_uid bigint not null default 0 check (last_uid >= 0),
    -- the last poll and its outcome, without secrets
    polled_at timestamptz,
    succeeded_at timestamptz,
    error_code text,
    error_at timestamptz,
    notice text,
    notice_at timestamptz,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);
create unique index helpdesk_mailbox_name on helpdesk_mailbox (company_id, lower(name));

-- One row per mail of a mailbox; the origin (mailbox, UIDVALIDITY, UID) is unique, so a mail that is fetched again or
-- by a second executor adds nothing. The raw mail is kept for the review queue and dropped once processed.
create table helpdesk_message (
    id uuid primary key,
    company_id uuid not null,
    mailbox_id uuid not null references helpdesk_mailbox (id),
    project_id uuid not null,
    uid_validity bigint not null,
    uid bigint not null check (uid > 0),
    message_id text check (message_id is null or char_length(message_id) <= 998),
    received_at timestamptz not null,
    sent_at timestamptz,
    from_address text check (from_address is null or char_length(from_address) <= 254),
    from_name text check (from_name is null or char_length(from_name) <= 200),
    to_addresses text,
    cc_addresses text,
    subject text,
    body text,
    size_bytes bigint not null check (size_bytes >= 0),
    -- the attachments as JSON: name, size and what became of them
    attachments jsonb not null default '[]',
    raw bytea,
    state text not null check (state in ('ticket', 'review', 'discarded', 'automatic')),
    reason text,
    ticket_id uuid,
    contact_id uuid,
    customer_id uuid,
    processed_at timestamptz,
    processed_by uuid,
    discard_reason text check (discard_reason is null or char_length(discard_reason) between 1 and 500),
    version bigint not null check (version > 0),
    unique (mailbox_id, uid_validity, uid),
    check ((state = 'ticket') = (ticket_id is not null))
);
create index helpdesk_message_queue on helpdesk_message (company_id, project_id, state, received_at desc, id);
create index helpdesk_message_ticket on helpdesk_message (ticket_id) where ticket_id is not null;
create index helpdesk_message_mailbox_message_id on helpdesk_message (mailbox_id, message_id) where message_id is not null;

-- The customer and contact of a ticket. A contact always belongs to the customer named here.
create table helpdesk_ticket_customer (
    ticket_id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    customer_id uuid references helpdesk_customer (id),
    contact_id uuid references helpdesk_contact (id),
    check (customer_id is not null or contact_id is null)
);

-- Every change of a customer, contact or mailbox and every processing in the review queue, with time and account.
create table helpdesk_audit_event (
    id bigint generated always as identity primary key,
    company_id uuid not null,
    target_type text not null,
    target_id uuid not null,
    operation text not null,
    detail text,
    actor_account_id uuid,
    occurred_at timestamptz not null
);
create index helpdesk_audit_event_target on helpdesk_audit_event (target_id, id);
