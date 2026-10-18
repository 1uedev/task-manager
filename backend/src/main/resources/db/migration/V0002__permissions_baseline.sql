-- Owner: permissions. No users, default grants, bootstrap account or business tables.
CREATE TABLE permissions_revision (
    company_id uuid PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

CREATE TABLE permissions_group (
    company_id uuid NOT NULL REFERENCES permissions_revision(company_id),
    id uuid NOT NULL,
    name varchar(80) NOT NULL CHECK (length(trim(name)) > 0),
    PRIMARY KEY (company_id, id)
);
CREATE UNIQUE INDEX permissions_group_name ON permissions_group(company_id, lower(name));

CREATE TABLE permissions_membership (
    company_id uuid NOT NULL,
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    PRIMARY KEY (company_id, group_id, user_id),
    FOREIGN KEY (company_id, group_id) REFERENCES permissions_group(company_id, id)
);
CREATE INDEX permissions_membership_user ON permissions_membership(company_id, user_id);

CREATE TABLE permissions_action (
    company_id uuid NOT NULL,
    group_id uuid NOT NULL,
    project_id uuid,
    action_key varchar(120) NOT NULL CHECK (action_key ~ '^[a-z][a-z0-9_.]*$'),
    UNIQUE NULLS NOT DISTINCT (company_id, group_id, project_id, action_key),
    FOREIGN KEY (company_id, group_id) REFERENCES permissions_group(company_id, id)
);

CREATE TABLE permissions_field (
    company_id uuid NOT NULL,
    group_id uuid NOT NULL,
    project_id uuid,
    resource_type varchar(120) NOT NULL CHECK (resource_type ~ '^[a-z][a-z0-9_.]*$'),
    field_key varchar(120) NOT NULL CHECK (field_key ~ '^[a-z][a-z0-9_.]*$'),
    operation varchar(5) NOT NULL CHECK (operation IN ('READ', 'WRITE')),
    effect varchar(5) NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    UNIQUE NULLS NOT DISTINCT (company_id, group_id, project_id, resource_type, field_key, operation),
    FOREIGN KEY (company_id, group_id) REFERENCES permissions_group(company_id, id)
);

CREATE TABLE permissions_audit (
    company_id uuid NOT NULL REFERENCES permissions_revision(company_id),
    id uuid NOT NULL,
    actor_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    operation varchar(40) NOT NULL,
    group_id uuid NOT NULL,
    subject_id uuid,
    project_id uuid,
    action_key varchar(120),
    resource_type varchar(120),
    field_key varchar(120),
    effect varchar(10),
    rights_version bigint NOT NULL CHECK (rights_version > 0),
    PRIMARY KEY (company_id, id),
    UNIQUE (company_id, rights_version)
);
CREATE INDEX permissions_audit_time ON permissions_audit(company_id, rights_version DESC);
