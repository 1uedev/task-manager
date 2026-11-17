-- Fixes after the review of the AI foundation (TASK-129, DEC-158).

-- F1: a stored API key is bound to the target it was entered for, the canonical address and the own CA of its access. The
-- application compares the fingerprint before every use and drops the key when address or CA change without a new key.
-- Keys stored before this migration are bound to the address and CA they have now.
alter table ai_provider add column key_target text check (key_target is null or key_target ~ '^[0-9a-f]{64}$');
update ai_provider set key_target = encode(sha256(convert_to(base_url || chr(10) || coalesce(ca_certificate, ''), 'UTF8')), 'hex')
    where api_key_cipher is not null;
alter table ai_provider add constraint ai_provider_key_bound check ((api_key_cipher is null) = (key_target is null));

-- F11: every save of a draft gets the next revision, and a discarded draft keeps its number, so neither a tag nor the label
-- in the record of a trial ever repeats.
alter table ai_skill_version add column revision integer not null default 1 check (revision > 0);
alter table ai_skill_version drop constraint ai_skill_version_state_check;
alter table ai_skill_version add constraint ai_skill_version_state_check check (state in ('draft', 'active', 'archived', 'discarded'));

-- F4: what an execution was planned with (versions of assignment, access, model and the skill that applied). Before the
-- call, when its answer arrives and when the suggestion is read the application compares it with the configuration of that
-- moment. Executions without it, from before this migration, count as changed.
alter table ai_run add column config_stamp text check (config_stamp is null or char_length(config_stamp) <= 300);

-- F12: a start refused for a business reason is recorded without access or model when there is none; it does not count
-- against the daily limits.
alter table ai_run alter column skill_version drop not null;
alter table ai_run alter column provider_id drop not null;
alter table ai_run alter column model_id drop not null;
alter table ai_run alter column model_key drop not null;
alter table ai_run alter column data_path drop not null;
alter table ai_run add constraint ai_run_planned check (state = 'refused' or (skill_version is not null and provider_id is not null
    and model_id is not null and model_key is not null and data_path is not null));
