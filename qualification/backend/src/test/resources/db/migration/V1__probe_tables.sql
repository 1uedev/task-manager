CREATE TABLE probe_record (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL
);
CREATE TABLE probe_projection (
    record_id UUID PRIMARY KEY REFERENCES probe_record(id),
    body TEXT NOT NULL
);
