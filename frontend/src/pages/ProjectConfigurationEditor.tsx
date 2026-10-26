import { useEffect, useMemo, useState } from 'react';
import {
  discardDraft, publishDraft, readDraft, readUsage, saveDraft, type Configuration, type ConfigurationUsage, type Project,
} from '../api/product.ts';
import {
  CUSTOM_PREFIX, FIELD_KINDS, customFieldsOf, keyFromName, mappingRows, optionsOf, workflowOf, workflowsOf, type CustomField, type CustomOption,
  type FieldKind, type Transition, type Workflow,
} from '../app/configuration.ts';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { de } from '../i18n/de.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';

// The editor of a project configuration (TASK-044): ticket types, statuses, the workflows with their required fields, and
// the custom fields. Changes go to a draft that is saved on the server and published as the next version; the server
// checks everything again and the editor only shows its reasons.

type Names = Readonly<Record<string, string>>;
type Mapping = Readonly<Record<string, string>>;
const CATEGORIES = ['open', 'in_progress', 'done'] as const;
const STANDARD_REQUIRABLE = ['description', 'assignee'] as const;

const etagNumber = (etag: string | null): number | null => {
  const value = Number((etag ?? '').replace(/"/g, ''));
  return Number.isInteger(value) && value > 0 ? value : null;
};

/** The configuration with the optional members filled in, so the editor never has to ask whether they exist. */
function whole(config: Configuration): Configuration & { workflows: Workflow[]; customFields: CustomField[] } {
  return {
    ...config,
    transitions: config.transitions.map((transition) => ({ ...transition, requiredFields: transition.requiredFields ?? [] })),
    workflows: workflowsOf(config).map((workflow) => ({
      ...workflow, transitions: workflow.transitions.map((transition) => ({ ...transition, requiredFields: transition.requiredFields ?? [] })),
    })),
    customFields: customFieldsOf(config).map((field) => ({ ...field, options: [...optionsOf(field)] })),
  };
}

type Whole = ReturnType<typeof whole>;

export function ProjectConfigurationEditor({ project, etag, onPublished }: {
  readonly project: Project;
  readonly etag: string;
  readonly onPublished: (project: Project, etag: string | null) => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [draft, reload] = useLoad(() => readDraft(api, project.id), [api, project.id, project.configurationVersion]);
  // The editor starts afresh with every published version, so the note about the publication lives here.
  const [published, setPublished] = useState<number | null>(null);
  if (draft.state === 'loading') return <LoadingView />;
  if (draft.state === 'problem' && draft.problem.status !== 404) return <ProblemView problem={draft.problem} onAction={reload} actionLabel="states.retry" />;
  const start = draft.state === 'ok'
    ? { configuration: draft.value.configuration, revision: draft.etag, base: draft.value.baseVersion }
    : { configuration: project.configuration, revision: null, base: project.configurationVersion };
  // A draft that started from an older published version cannot be saved or published; it is shown so it can be discarded.
  return (
    <>
      {published !== null && <p role="status" className="success">{t('config.published', { version: published })}</p>}
      <Editor key={`${project.configurationVersion}-${start.revision ?? 'published'}`} project={project} etag={etag} start={start}
        onPublished={(next, nextEtag) => {
          setPublished(next.configurationVersion);
          onPublished(next, nextEtag);
        }}
        onEdited={() => setPublished(null)} reload={reload} />
    </>
  );
}

function Editor({ project, etag, start, onPublished, onEdited, reload }: {
  readonly project: Project;
  readonly etag: string;
  readonly start: { readonly configuration: Configuration; readonly revision: string | null; readonly base: number };
  readonly onPublished: (project: Project, etag: string | null) => void;
  readonly onEdited: () => void;
  readonly reload: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [config, setConfig] = useState<Whole>(() => whole(start.configuration));
  const [saved, setSaved] = useState(() => JSON.stringify(whole(start.configuration)));
  const [revision, setRevision] = useState(start.revision);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [reasons, setReasons] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const [mapping, setMapping] = useState<readonly string[] | null>(null);
  const dirty = JSON.stringify(config) !== saved;
  useUnsavedGuard(dirty);

  const published = useMemo(() => whole(project.configuration), [project.configuration]);
  const typeNames = useMemo<Names>(() => Object.fromEntries(config.ticketTypes.map((item) => [item.key, item.name])), [config.ticketTypes]);
  const statusNames = useMemo<Names>(() => Object.fromEntries(config.statuses.map((item) => [item.key, item.name])), [config.statuses]);
  const fieldNames = useMemo<Names>(() => ({
    description: t('field.description'), assignee: t('field.assignee'),
    ...Object.fromEntries(config.customFields.map((field) => [`${CUSTOM_PREFIX}${field.key}`, field.name])),
  }), [config.customFields, t]);
  const update = (change: (current: Whole) => Whole) => {
    onEdited();
    setConfig(change);
    setNotice(null);
    setReasons([]);
    setMapping(null);
  };

  /** Saves the draft; the revision to publish comes from the answer. */
  async function save(): Promise<string | null> {
    const result = await saveDraft(api, project.id, revision, start.base, config);
    if (result.kind === 'ok') {
      setRevision(result.data.etag);
      setSaved(JSON.stringify(config));
      return result.data.etag;
    }
    if (result.problem.status === 412 || result.problem.code === 'project.draft_stale') {
      setNotice({ kind: 'info', text: t(result.problem.code === 'project.draft_stale' ? 'config.stale' : 'config.draftConflict') });
    } else {
      setNotice({ kind: 'info', text: text(result.problem) });
      setReasons(result.problem.violations ?? []);
    }
    return null;
  }

  async function onSave() {
    setBusy(true);
    setNotice(null);
    setReasons([]);
    if (await save() !== null) setNotice({ kind: 'success', text: t('config.draftSaved') });
    setBusy(false);
  }

  async function onDiscard() {
    if (revision === null) {
      setConfig(whole(project.configuration));
      setSaved(JSON.stringify(whole(project.configuration)));
      return setNotice({ kind: 'success', text: t('config.discarded') });
    }
    setBusy(true);
    const result = await discardDraft(api, project.id, revision);
    setBusy(false);
    if (result.kind === 'ok') {
      setNotice({ kind: 'success', text: t('config.discarded') });
      return reload();
    }
    setNotice({ kind: 'info', text: text(result.problem, { 'project.draft_conflict': 'config.draftConflict' }) });
  }

  async function onPublish(statusMapping: Mapping, typeMapping: Mapping) {
    setBusy(true);
    setNotice(null);
    setReasons([]);
    const current = dirty || revision === null ? await save() : revision;
    const number = etagNumber(current);
    if (current === null || number === null) return setBusy(false);
    const result = await publishDraft(api, project.id, etag, number, statusMapping, typeMapping);
    setBusy(false);
    if (result.kind === 'ok') {
      setMapping(null);
      return onPublished(result.data.value, result.data.etag);
    }
    const problem = result.problem;
    if (problem.code === 'project.configuration_in_use') {
      setNotice({ kind: 'info', text: t('config.inUse') });
      return setMapping(problem.violations ?? []);
    }
    if (problem.status === 412 && problem.code === 'project.version_conflict') {
      setNotice({ kind: 'info', text: t('settings.conflict') });
      return;
    }
    setNotice({ kind: 'info', text: text(problem, {
      'project.draft_stale': 'config.stale', 'project.draft_conflict': 'config.draftConflict', 'project.configuration_incompatible': 'config.incompatible',
    }) });
    setReasons(problem.violations ?? []);
  }

  return (
    <section aria-labelledby="settings-config" data-testid="configuration-editor">
      <h2 id="settings-config">{t('settings.configuration')}</h2>
      <p className="hint">{t(revision === null ? 'config.hintPublished' : 'config.hintDraft', { version: project.configurationVersion })}</p>
      <NoticeView notice={notice} />
      {reasons.length > 0 && (
        <ul className="field-error" data-testid="config-reasons">{reasons.map((code) => <li key={code}>{describeReason(t, code, typeNames, statusNames, fieldNames)}</li>)}</ul>
      )}
      <TypesEditor config={config} update={update} />
      <StatusesEditor config={config} update={update} />
      <WorkflowSection config={config} update={update} fieldNames={fieldNames} statusNames={statusNames} typeNames={typeNames} />
      <StandardFields config={config} update={update} />
      <CustomFieldsEditor config={config} published={published} update={update} />
      {mapping !== null && (
        <MappingPanel project={project} reasons={mapping} config={config} typeNames={typeNames} statusNames={statusNames} busy={busy}
          onConfirm={(statusMapping, typeMapping) => void onPublish(statusMapping, typeMapping)} onCancel={() => setMapping(null)} />
      )}
      <p className="actions">
        <button type="button" disabled={busy || !dirty} onClick={() => void onSave()}>{t('config.saveDraft')}</button>
        <button type="button" disabled={busy} onClick={() => void onPublish({}, {})}>{t('config.publish')}</button>
        <button type="button" className="secondary" disabled={busy || (!dirty && revision === null)} onClick={() => void onDiscard()}>{t('config.discard')}</button>
      </p>
    </section>
  );
}

type Edit = (change: (current: Whole) => Whole) => void;

// ---------------------------------------------------------------- types and statuses

function TypesEditor({ config, update }: { readonly config: Whole; readonly update: Edit }) {
  const { t } = useI18n();
  const [name, setName] = useState('');
  return (
    <fieldset>
      <legend>{t('settings.types')}</legend>
      <ul className="item-list">
        {config.ticketTypes.map((item, index) => (
          <li key={item.key} className="item-row">
            <TextField label={t('config.nameOf', { key: item.key })} value={item.name} maxLength={80}
              onChange={(value) => update((c) => ({ ...c, ticketTypes: c.ticketTypes.map((x, i) => (i === index ? { ...x, name: value } : x)) }))} />
            <button type="button" className="secondary" disabled={config.ticketTypes.length === 1}
              onClick={() => update((c) => removeType(c, item.key))}>{t('config.remove', { name: item.name })}</button>
          </li>
        ))}
      </ul>
      <div className="inline-form">
        <TextField label={t('config.newType')} value={name} onChange={setName} maxLength={80} />
        <button type="button" disabled={name.trim() === ''} onClick={() => {
          update((c) => ({ ...c, ticketTypes: [...c.ticketTypes, { key: keyFromName(name, c.ticketTypes.map((x) => x.key)), name: name.trim() }] }));
          setName('');
        }}>{t('config.add')}</button>
      </div>
    </fieldset>
  );
}

function StatusesEditor({ config, update }: { readonly config: Whole; readonly update: Edit }) {
  const { t } = useI18n();
  const [name, setName] = useState('');
  return (
    <fieldset>
      <legend>{t('settings.statuses')}</legend>
      <ul className="item-list">
        {config.statuses.map((item, index) => (
          <li key={item.key} className="item-row">
            <TextField label={t('config.nameOf', { key: item.key })} value={item.name} maxLength={80}
              onChange={(value) => update((c) => ({ ...c, statuses: c.statuses.map((x, i) => (i === index ? { ...x, name: value } : x)) }))} />
            <SelectField label={t('config.category')} value={item.category}
              onChange={(value) => update((c) => ({ ...c, statuses: c.statuses.map((x, i) => (i === index ? { ...x, category: value as typeof item.category } : x)) }))}
              options={CATEGORIES.map((category) => ({ value: category, label: t(`config.category.${category}` as MessageKey) }))} />
            <button type="button" className="secondary" disabled={config.statuses.length === 1}
              onClick={() => update((c) => removeStatus(c, item.key))}>{t('config.remove', { name: item.name })}</button>
          </li>
        ))}
      </ul>
      <div className="inline-form">
        <TextField label={t('config.newStatus')} value={name} onChange={setName} maxLength={80} />
        <button type="button" disabled={name.trim() === ''} onClick={() => {
          update((c) => ({ ...c, statuses: [...c.statuses, { key: keyFromName(name, c.statuses.map((x) => x.key)), name: name.trim(), category: 'in_progress' }] }));
          setName('');
        }}>{t('config.add')}</button>
      </div>
    </fieldset>
  );
}

// ---------------------------------------------------------------- workflows

function WorkflowSection({ config, update, fieldNames, statusNames, typeNames }: {
  readonly config: Whole; readonly update: Edit; readonly fieldNames: Names; readonly statusNames: Names; readonly typeNames: Names;
}) {
  const { t } = useI18n();
  return (
    <fieldset>
      <legend>{t('config.workflows')}</legend>
      <p className="hint">{t('config.workflowsHint')}</p>
      <WorkflowEditor title={t('config.defaultWorkflow')} config={config} fieldNames={fieldNames} statusNames={statusNames}
        value={{ initialStatus: config.initialStatus, transitions: config.transitions }}
        onChange={(next) => update((c) => ({ ...c, initialStatus: next.initialStatus, transitions: next.transitions }))} />
      {config.ticketTypes.map((type) => {
        const own = workflowOf(config, type.key).own;
        return (
          <div key={type.key} data-testid={`workflow-${type.key}`}>
            <label className="check">
              <input type="checkbox" checked={own} onChange={(event) => update((c) => {
                if (!event.target.checked) return { ...c, workflows: c.workflows.filter((w) => w.type !== type.key) };
                return { ...c, workflows: [...c.workflows, { type: type.key, initialStatus: c.initialStatus, transitions: c.transitions.map((x) => ({ ...x })) }] };
              })} />
              <span>{t('config.ownWorkflow', { type: typeNames[type.key] ?? type.key })}</span>
            </label>
            {own && (
              <WorkflowEditor title={t('config.workflowOf', { type: typeNames[type.key] ?? type.key })} config={config} fieldNames={fieldNames}
                statusNames={statusNames} value={workflowOf(config, type.key)}
                onChange={(next) => update((c) => ({ ...c, workflows: c.workflows.map((w) => (w.type === type.key ? { ...w, ...next } : w)) }))} />
            )}
          </div>
        );
      })}
    </fieldset>
  );
}

function WorkflowEditor({ title, config, value, onChange, fieldNames, statusNames }: {
  readonly title: string;
  readonly config: Whole;
  readonly value: { readonly initialStatus: string; readonly transitions: readonly Transition[] };
  readonly onChange: (next: { initialStatus: string; transitions: Transition[] }) => void;
  readonly fieldNames: Names;
  readonly statusNames: Names;
}) {
  const { t } = useI18n();
  const [from, setFrom] = useState(config.statuses[0]?.key ?? '');
  const [to, setTo] = useState(config.statuses[1]?.key ?? config.statuses[0]?.key ?? '');
  const statusOptions = config.statuses.map((status) => ({ value: status.key, label: status.name }));
  const emit = (initialStatus: string, transitions: readonly Transition[]) => onChange({ initialStatus, transitions: [...transitions] });
  const requirable = [...STANDARD_REQUIRABLE, ...config.customFields.map((field) => `${CUSTOM_PREFIX}${field.key}`)];
  const valid = from !== to && !value.transitions.some((x) => x.from === from && x.to === to);
  return (
    <div className="panel" role="group" aria-label={title}>
      <h3>{title}</h3>
      <SelectField label={t('config.initialStatus')} value={value.initialStatus} onChange={(next) => emit(next, value.transitions)} options={statusOptions} />
      {value.transitions.length === 0 ? <EmptyText text={t('config.noTransitions')} /> : (
        <ul className="item-list" data-testid="transitions">
          {value.transitions.map((transition) => (
            <li key={`${transition.from}>${transition.to}`} className="item-row">
              <span className="item-title">{t('config.transition', { from: statusNames[transition.from] ?? transition.from, to: statusNames[transition.to] ?? transition.to })}</span>
              <fieldset>
                <legend>{t('config.requires')}</legend>
                {requirable.map((field) => (
                  <label key={field} className="check">
                    <input type="checkbox" checked={(transition.requiredFields ?? []).includes(field)} onChange={(event) => emit(value.initialStatus,
                      value.transitions.map((x) => (x === transition ? { ...x, requiredFields: event.target.checked
                        ? [...(x.requiredFields ?? []), field] : (x.requiredFields ?? []).filter((y) => y !== field) } : x)))} />
                    <span>{fieldNames[field] ?? field}</span>
                  </label>
                ))}
              </fieldset>
              <button type="button" className="secondary" onClick={() => emit(value.initialStatus, value.transitions.filter((x) => x !== transition))}>
                {t('config.removeTransition', { from: statusNames[transition.from] ?? transition.from, to: statusNames[transition.to] ?? transition.to })}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="inline-form">
        <SelectField label={t('config.from')} value={from} onChange={setFrom} options={statusOptions} />
        <SelectField label={t('config.to')} value={to} onChange={setTo} options={statusOptions} />
        <button type="button" disabled={!valid} onClick={() => emit(value.initialStatus, [...value.transitions, { from, to, requiredFields: [] }])}>{t('config.addTransition')}</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- fixed fields and custom fields

function StandardFields({ config, update }: { readonly config: Whole; readonly update: Edit }) {
  const { t } = useI18n();
  return (
    <fieldset>
      <legend>{t('config.requiredOnCreate')}</legend>
      {STANDARD_REQUIRABLE.map((key) => (
        <label key={key} className="check">
          <input type="checkbox" checked={config.fields.find((field) => field.key === key)?.required ?? false}
            onChange={(event) => update((c) => ({ ...c, fields: c.fields.map((field) => (field.key === key ? { ...field, required: event.target.checked } : field)) }))} />
          <span>{t(`field.${key}` as MessageKey)}</span>
        </label>
      ))}
    </fieldset>
  );
}

function CustomFieldsEditor({ config, published, update }: { readonly config: Whole; readonly published: Whole; readonly update: Edit }) {
  const { t } = useI18n();
  const [name, setName] = useState('');
  const [kind, setKind] = useState<FieldKind>('text');
  const locked = (key: string) => published.customFields.some((field) => field.key === key);
  const change = (key: string, next: (field: CustomField) => CustomField) =>
    update((c) => ({ ...c, customFields: c.customFields.map((field) => (field.key === key ? next(field) : field)) }));
  return (
    <fieldset data-testid="custom-fields">
      <legend>{t('config.customFields')}</legend>
      <p className="hint">{t('config.customFieldsHint')}</p>
      {config.customFields.length === 0 ? <EmptyText text={t('config.noCustomFields')} /> : (
        <ul className="item-list">
          {config.customFields.map((field) => (
            <li key={field.key} className="panel" data-testid={`custom-field-${field.key}`}>
              <TextField label={t('config.nameOf', { key: `${CUSTOM_PREFIX}${field.key}` })} value={field.name} maxLength={80}
                onChange={(value) => change(field.key, (x) => ({ ...x, name: value }))} />
              {locked(field.key)
                ? <p className="hint">{t('config.kindFixed', { kind: t(`config.kind.${field.type}` as MessageKey) })}</p>
                : <SelectField label={t('config.kind')} value={field.type} onChange={(value) => change(field.key, (x) => ({ ...x, type: value as FieldKind, options: value === 'select' ? x.options ?? [] : [] }))}
                    options={FIELD_KINDS.map((item) => ({ value: item, label: t(`config.kind.${item}` as MessageKey) }))} />}
              <label className="check">
                <input type="checkbox" checked={field.required} onChange={(event) => change(field.key, (x) => ({ ...x, required: event.target.checked }))} />
                <span>{t('config.requiredOnCreateField')}</span>
              </label>
              <label className="check">
                <input type="checkbox" checked={field.active} onChange={(event) => change(field.key, (x) => ({ ...x, active: event.target.checked }))} />
                <span>{t('config.active')}</span>
              </label>
              {field.type === 'select' && <OptionsEditor field={field} publishedField={published.customFields.find((x) => x.key === field.key)}
                onChange={(options) => change(field.key, (x) => ({ ...x, options }))} />}
              {!locked(field.key) && (
                <button type="button" className="secondary" onClick={() => update((c) => removeCustomField(c, field.key))}>{t('config.remove', { name: field.name })}</button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className="inline-form">
        <TextField label={t('config.newField')} value={name} onChange={setName} maxLength={80} />
        <SelectField label={t('config.kind')} value={kind} onChange={(value) => setKind(value as FieldKind)}
          options={FIELD_KINDS.map((item) => ({ value: item, label: t(`config.kind.${item}` as MessageKey) }))} />
        <button type="button" disabled={name.trim() === ''} onClick={() => {
          const key = keyFromName(name, config.customFields.map((x) => x.key));
          update((c) => ({ ...c, customFields: [...c.customFields, {
            key, name: name.trim(), type: kind, required: false, active: true,
            options: kind === 'select' ? [{ key: 'option_1', name: t('config.firstOption'), active: true }] : [],
          }] }));
          setName('');
        }}>{t('config.add')}</button>
      </div>
    </fieldset>
  );
}

function OptionsEditor({ field, publishedField, onChange }: {
  readonly field: CustomField; readonly publishedField: CustomField | undefined; readonly onChange: (options: CustomOption[]) => void;
}) {
  const { t } = useI18n();
  const [name, setName] = useState('');
  const options = optionsOf(field);
  const locked = (key: string) => optionsOf(publishedField ?? { key: '', name: '', type: 'select', required: false, active: true }).some((option) => option.key === key);
  return (
    <div role="group" aria-label={t('config.options', { name: field.name })}>
      <h4>{t('config.options', { name: field.name })}</h4>
      <ul className="item-list">
        {options.map((option) => (
          <li key={option.key} className="item-row">
            <TextField label={t('config.nameOf', { key: option.key })} value={option.name} maxLength={80}
              onChange={(value) => onChange(options.map((x) => (x.key === option.key ? { ...x, name: value } : x)))} />
            <label className="check">
              <input type="checkbox" checked={option.active} onChange={(event) => onChange(options.map((x) => (x.key === option.key ? { ...x, active: event.target.checked } : x)))} />
              <span>{t('config.optionActive')}</span>
            </label>
            {!locked(option.key) && options.length > 1 && (
              <button type="button" className="secondary" onClick={() => onChange(options.filter((x) => x.key !== option.key))}>{t('config.remove', { name: option.name })}</button>
            )}
          </li>
        ))}
      </ul>
      <div className="inline-form">
        <TextField label={t('config.newOption')} value={name} onChange={setName} maxLength={80} />
        <button type="button" disabled={name.trim() === ''} onClick={() => {
          onChange([...options, { key: keyFromName(name, options.map((x) => x.key)), name: name.trim(), active: true }]);
          setName('');
        }}>{t('config.add')}</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- publishing a configuration that tickets still use

/** Asks where the tickets go that use a type or status the new version no longer has (F-05 of DOMAIN_MODEL.md). */
function MappingPanel({ project, reasons, config, typeNames, statusNames, busy, onConfirm, onCancel }: {
  readonly project: Project;
  readonly reasons: readonly string[];
  readonly config: Whole;
  readonly typeNames: Names;
  readonly statusNames: Names;
  readonly busy: boolean;
  readonly onConfirm: (statusMapping: Mapping, typeMapping: Mapping) => void;
  readonly onCancel: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [usage] = useLoad(() => readUsage(api, project.id), [api, project.id]);
  const types = reasons.filter((reason) => reason.startsWith('type.in_use:')).map((reason) => reason.slice('type.in_use:'.length));
  const statuses = [...new Set(reasons.filter((reason) => reason.startsWith('status.')).map((reason) => reason.slice(reason.indexOf(':') + 1)))];
  const [statusMapping, setStatusMapping] = useState<Record<string, string>>({});
  const [typeMapping, setTypeMapping] = useState<Record<string, string>>({});
  const oldNames = useOldNames(project);
  const rows = useMemo(() => mappingRows(statuses, usage.state === 'ok' ? usage.value : [], config), [reasons, usage, config]);
  useEffect(() => {
    setStatusMapping((current) => Object.fromEntries(rows.map((row) => [row.key, current[row.key] ?? row.targets[0] ?? ''])));
    setTypeMapping((current) => Object.fromEntries(types.map((key) => [key, current[key] ?? config.ticketTypes[0]?.key ?? ''])));
  }, [rows]);
  const count = (predicate: (use: ConfigurationUsage) => boolean) => usage.state === 'ok' ? usage.value.filter(predicate).reduce((sum, use) => sum + use.count, 0) : 0;
  return (
    <div role="group" aria-labelledby="mapping-title" className="panel" data-testid="mapping-panel">
      <h3 id="mapping-title">{t('config.mappingTitle')}</h3>
      <p className="hint">{t('config.mappingHint')}</p>
      {types.map((key) => (
        <SelectField key={`type-${key}`} label={t('config.mapType', { name: oldNames.types[key] ?? typeNames[key] ?? key, count: count((use) => use.type === key) })}
          value={typeMapping[key] ?? ''} onChange={(value) => setTypeMapping((current) => ({ ...current, [key]: value }))}
          options={config.ticketTypes.map((item) => ({ value: item.key, label: item.name }))} />
      ))}
      {rows.map((row) => {
        const name = oldNames.statuses[row.status] ?? statusNames[row.status] ?? row.status;
        const label = row.type === null
          ? t('config.mapStatus', { name, count: count((use) => use.status === row.status) })
          : t('config.mapStatusOfType', { name, type: oldNames.types[row.type] ?? typeNames[row.type] ?? row.type,
            count: count((use) => use.status === row.status && use.type === row.type) });
        return (
          <SelectField key={`status-${row.key}`} label={label}
            value={statusMapping[row.key] ?? ''} onChange={(value) => setStatusMapping((current) => ({ ...current, [row.key]: value }))}
            options={config.statuses.filter((item) => row.targets.includes(item.key)).map((item) => ({ value: item.key, label: item.name }))} />
        );
      })}
      <p className="actions">
        <button type="button" disabled={busy} onClick={() => onConfirm(statusMapping, typeMapping)}>{t('config.mappingConfirm')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('config.mappingCancel')}</button>
      </p>
    </div>
  );
}

/** The names the published version gave to types and statuses, for the ones the draft removed. */
function useOldNames(project: Project): { readonly types: Names; readonly statuses: Names } {
  return useMemo(() => ({
    types: Object.fromEntries(project.configuration.ticketTypes.map((item) => [item.key, item.name])),
    statuses: Object.fromEntries(project.configuration.statuses.map((item) => [item.key, item.name])),
  }), [project.configuration]);
}

// ---------------------------------------------------------------- changes of the working copy

function removeType(config: Whole, key: string): Whole {
  return { ...config, ticketTypes: config.ticketTypes.filter((type) => type.key !== key), workflows: config.workflows.filter((workflow) => workflow.type !== key) };
}

function without(transitions: readonly Transition[], status: string): Transition[] {
  return transitions.filter((transition) => transition.from !== status && transition.to !== status);
}

/** Removes a status with every step that touches it; a first status that goes is replaced by the first one left. */
function removeStatus(config: Whole, key: string): Whole {
  const statuses = config.statuses.filter((status) => status.key !== key);
  const first = statuses[0]?.key ?? config.initialStatus;
  return {
    ...config,
    statuses,
    initialStatus: config.initialStatus === key ? first : config.initialStatus,
    transitions: without(config.transitions, key),
    workflows: config.workflows.map((workflow) => ({
      ...workflow, initialStatus: workflow.initialStatus === key ? first : workflow.initialStatus, transitions: without(workflow.transitions, key),
    })),
  };
}

/** Only a field that was never published can be removed; the others are deactivated. Steps that required it stop requiring it. */
function removeCustomField(config: Whole, key: string): Whole {
  const id = `${CUSTOM_PREFIX}${key}`;
  const strip = (transitions: readonly Transition[]) => transitions.map((x) => ({ ...x, requiredFields: (x.requiredFields ?? []).filter((field) => field !== id) }));
  return {
    ...config,
    customFields: config.customFields.filter((field) => field.key !== key),
    transitions: strip(config.transitions),
    workflows: config.workflows.map((workflow) => ({ ...workflow, transitions: strip(workflow.transitions) })),
  };
}

// ---------------------------------------------------------------- reasons

type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

/** A refused configuration or publication in words; a reason the interface does not know is shown as its code. */
export function describeReason(t: Translate, code: string, typeNames: Names, statusNames: Names, fieldNames: Names): string {
  const [head = '', subject] = code.split(':');
  const specific = `config.reason.${head}`;
  const label = subject === undefined ? '' : (head.startsWith('type.') ? typeNames[subject] : statusNames[subject]) ?? subject;
  // Asked of the catalog first, so a reason without a text is shown as its code and not reported as a missing translation.
  if (Object.hasOwn(de, specific)) return t(specific as MessageKey, { name: label });
  const field = /^(custom\.[a-z0-9_]+)\./.exec(head)?.[1];
  return field === undefined ? code : `${fieldNames[field] ?? field}: ${code}`;
}
