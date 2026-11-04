-- Mail template "Antwort an Kunden" (TASK-082, DEC-112).
--
-- One row with greeting, signature and footer per language and the address of the application for {{portal.url}}. A
-- language whose three texts are null uses the built-in template; the application checks every text when it is saved.
create table appearance_mail_template (
    id smallint primary key check (id = 1),
    application_url text check (application_url is null or (char_length(application_url) between 9 and 2000 and application_url ~ '^https://')),
    greeting_de text check (greeting_de is null or char_length(greeting_de) <= 2000),
    signature_de text check (signature_de is null or char_length(signature_de) <= 2000),
    footer_de text check (footer_de is null or char_length(footer_de) <= 2000),
    greeting_en text check (greeting_en is null or char_length(greeting_en) <= 2000),
    signature_en text check (signature_en is null or char_length(signature_en) <= 2000),
    footer_en text check (footer_en is null or char_length(footer_en) <= 2000),
    version bigint not null check (version > 0),
    updated_at timestamptz not null,
    updated_by uuid,
    check ((greeting_de is null) = (signature_de is null) and (signature_de is null) = (footer_de is null)),
    check ((greeting_en is null) = (signature_en is null) and (signature_en is null) = (footer_en is null))
);

insert into appearance_mail_template (id, version, updated_at) values (1, 1, now());
