-- Images other modules keep in the file store (TASK-093, DEC-123): for now the images of wiki pages. The content lies in the
-- file store under the identifier of the row, like the content of attachments, and the clean up of the files module counts
-- these rows as references. Only PNG, JPEG and WebP are accepted, recognized by their first bytes.
create table file_image (
    id uuid primary key,
    company_id uuid not null,
    owner text not null check (owner in ('wiki')),
    media_type text not null check (media_type in ('image/png', 'image/jpeg', 'image/webp')),
    size bigint not null check (size > 0),
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null,
    created_by uuid not null
);
create index file_image_company on file_image (company_id);
