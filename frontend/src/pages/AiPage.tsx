import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  activateAiSkill, AI_USE_CASES, cancelAiRun, checkAiModel, checkAiProvider, createAiModel, createAiProvider, discardAiSkillDraft, listAiAssignments,
  listAiModels, listAiProviders, listAiRuns, readAiLimits, readAiRun, readAiSkill, readAiStatus, readAiUsage, removeAiModel, removeAiProvider,
  restoreAiSkill, saveAiAssignment, saveAiLimits, saveAiSkillDraft, trialAiSkill, updateAiModel, updateAiProvider, type AiAssignment, type AiLanguage,
  type AiModel, type AiModelInput, type AiProvider, type AiProviderInput, type AiRun, type AiSkill, type AiStatus, type AiUseCase,
} from '../api/product.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { EmptyText, LoadedView, NoticeView, PageHead, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

export const AI_RIGHT = 'ai.manage';

type Kind = AiProvider['kind'];
const KINDS: readonly Kind[] = ['openai', 'anthropic', 'ollama'];
const LANGUAGES: readonly AiLanguage[] = ['de', 'en'];

// The text for a code of the AI module; an unknown code gets the general one.
export function useAiReason(): (code: string | null | undefined) => string {
  const { t } = useI18n();
  return (code) => {
    const key = `ai.reason.${(code ?? '').replace(/^ai\./, '')}`;
    return Object.hasOwn(de, key) ? t(key as MessageKey) : t('ai.reason.unknown');
  };
}

// A refused request: the reason of its own code or of its first violation when the page knows it, otherwise the general text.
export function useAiProblem(): (problem: ProblemInfo) => string {
  const reason = useAiReason();
  const text = useProblemText();
  return (problem) => {
    const named = [...((problem as { violations?: readonly string[] }).violations ?? []), problem.code]
      .find((code) => code !== null && code !== undefined && code.startsWith('ai.') && Object.hasOwn(de, `ai.reason.${code.slice(3)}`));
    return named ? reason(named) : text(problem);
  };
}

// The page "KI" (TASK-125) for ai.manage: accesses and models, the model of each use case, the skills with draft, activation
// and trial, the daily limits with the usage, and the record of the executions without content. API keys are never shown.
export function AiPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [status, reloadStatus] = useLoad(() => readAiStatus(api), [api]);
  const [round, setRound] = useState(0);
  const changed = () => {
    setRound((value) => value + 1);
    reloadStatus();
  };
  return (
    <>
      <PageHead title={t('ai.title')} intro={t('ai.intro')} />
      <LoadedView loaded={status} retry={reloadStatus}>
        {(value) => (
          <>
            {!value.secretKey && <p className="mode-notice" role="status" data-testid="ai-no-key">{t('ai.noSecretKey')}</p>}
            {!value.manage ? <EmptyText text={t('ai.noRight')} /> : (
              <>
                <UseCases status={value} round={round} onChanged={changed} />
                <Accesses round={round} onChanged={changed} />
                <Skills />
                <LimitsAndUsage status={value} onChanged={changed} />
                <Records round={round} />
              </>
            )}
          </>
        )}
      </LoadedView>
    </>
  );
}

// ---------------------------------------------------------------- use cases and their model

function UseCases({ status, round, onChanged }: { readonly status: AiStatus; readonly round: number; readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const reason = useAiReason();
  const problem = useAiProblem();
  const [assignments, reload] = useLoad(() => listAiAssignments(api), [api, round]);
  const [models] = useLoad(() => listAiModels(api), [api, round]);
  const [providers] = useLoad(() => listAiProviders(api), [api, round]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  async function save(assignment: AiAssignment, modelId: string | null, enabled: boolean) {
    setNotice(null);
    setBusy(true);
    const result = await saveAiAssignment(api, assignment.useCase, assignment.version, modelId, enabled);
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) reload();
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    setNotice({ kind: 'success', text: t('ai.assignmentSaved', { useCase: t(`ai.useCase.${assignment.useCase}` as MessageKey) }) });
    onChanged();
  }

  const modelList = models.state === 'ok' ? models.value : [];
  const providerOf = (model: AiModel) => (providers.state === 'ok' ? providers.value.find((p) => p.id === model.providerId) : undefined);
  return (
    <section aria-labelledby="ai-use-cases" className="panel" data-testid="ai-use-cases">
      <h2 id="ai-use-cases">{t('ai.useCases')}</h2>
      <p className="hint">{t('ai.useCasesIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={assignments} retry={reload}>
        {(list) => (
          <ul className="item-list">
            {list.map((assignment) => {
              const state = status.useCases.find((entry) => entry.useCase === assignment.useCase);
              const label = t(`ai.useCase.${assignment.useCase}` as MessageKey);
              return (
                <li key={assignment.useCase} className="item-row" data-use-case={assignment.useCase}>
                  <span className="item-title">{label}</span>
                  <span className="hint" data-testid="ai-availability">
                    {state?.available
                      ? t(state.provider?.dataPath === 'external' ? 'ai.availableExternal' : 'ai.availableLocal', { provider: state.provider?.name ?? '' })
                      : reason(state?.reason)}
                  </span>
                  <SelectField label={t('ai.modelFor', { useCase: label })} value={assignment.modelId ?? ''} disabled={busy}
                    onChange={(value) => void save(assignment, value === '' ? null : value, value === '' ? false : assignment.enabled)}
                    options={[{ value: '', label: t('ai.noModel') }, ...modelList.map((model) => ({
                      value: model.id, label: `${model.name} (${providerOf(model)?.name ?? ''})`,
                    }))]} />
                  <label className="check">
                    <input type="checkbox" checked={assignment.enabled} disabled={busy || assignment.modelId === null}
                      onChange={(event) => void save(assignment, assignment.modelId ?? null, event.target.checked)} />
                    {t('ai.enabledFor', { useCase: label })}
                  </label>
                </li>
              );
            })}
          </ul>
        )}
      </LoadedView>
    </section>
  );
}

// ---------------------------------------------------------------- accesses and models

function Accesses({ round, onChanged }: { readonly round: number; readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const reason = useAiReason();
  const [providers, reloadProviders] = useLoad(() => listAiProviders(api), [api, round]);
  const [models, reloadModels] = useLoad(() => listAiModels(api), [api, round]);
  const [editing, setEditing] = useState<AiProvider | 'new' | null>(null);
  const [editingModel, setEditingModel] = useState<{ readonly provider: AiProvider; readonly model: AiModel | null } | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  const done = (text: string) => {
    setEditing(null);
    setEditingModel(null);
    setNotice({ kind: 'success', text });
    onChanged();
    heading.current?.focus();
  };

  async function check(provider: AiProvider) {
    setNotice(null);
    const result = await checkAiProvider(api, provider.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    const value = result.data.value;
    setNotice(value.ok ? { kind: 'success', text: t('ai.checkOk', { name: provider.name, count: String(value.models ?? 0) }) }
      : { kind: 'info', text: t('ai.checkFailed', { name: provider.name, reason: reason(value.code) }) });
  }

  async function checkModel(model: AiModel) {
    setNotice(null);
    const result = await checkAiModel(api, model.id);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    const value = result.data.value;
    setNotice(value.ok ? { kind: 'success', text: t('ai.modelCheckOk', { name: model.name }) }
      : { kind: 'info', text: t('ai.checkFailed', { name: model.name, reason: reason(value.code) }) });
  }

  async function remove(provider: AiProvider) {
    if (!globalThis.confirm(t('ai.removeConfirm', { name: provider.name }))) return;
    setNotice(null);
    const result = await removeAiProvider(api, provider.id, provider.version);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) reloadProviders();
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    done(t('ai.removed', { name: provider.name }));
  }

  async function removeModel(model: AiModel) {
    if (!globalThis.confirm(t('ai.removeModelConfirm', { name: model.name }))) return;
    setNotice(null);
    const result = await removeAiModel(api, model.id, model.version);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) reloadModels();
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    done(t('ai.modelRemoved', { name: model.name }));
  }

  const modelsOf = (provider: AiProvider) => (models.state === 'ok' ? models.value.filter((model) => model.providerId === provider.id) : []);
  return (
    <section aria-labelledby="ai-accesses" className="panel" data-testid="ai-accesses">
      <h2 id="ai-accesses" ref={heading} tabIndex={-1}>{t('ai.accesses')}</h2>
      <p className="hint">{t('ai.accessesIntro')}</p>
      <NoticeView notice={notice} />
      <LoadedView loaded={providers} retry={reloadProviders}>
        {(list) => (
          <>
            {list.length === 0 ? <EmptyText text={t('ai.noAccesses')} /> : (
              <ul className="item-list" data-testid="ai-provider-list">
                {list.map((provider) => (
                  <li key={provider.id} className="item-row" data-provider={provider.id}>
                    <span className="item-title">{provider.name}</span>
                    <span className="hint">
                      {t(`ai.kind.${provider.kind}` as MessageKey)} · {provider.baseUrl} · {t(`ai.dataPath.${provider.dataPath}` as MessageKey)}
                      {' · '}{provider.kind === 'ollama' && !provider.keySet ? t('ai.keyNone') : provider.keySet ? t('ai.keySet') : t('ai.keyMissing')}
                      {!provider.enabled && <> · {t('ai.disabled')}</>}
                    </span>
                    <button type="button" className="secondary" onClick={() => void check(provider)}>{t('ai.check', { name: provider.name })}</button>
                    <button type="button" className="secondary" onClick={() => setEditing(provider)}>{t('ai.edit', { name: provider.name })}</button>
                    <button type="button" className="secondary" onClick={() => void remove(provider)}>{t('ai.remove', { name: provider.name })}</button>
                    <ul className="item-list" data-testid="ai-model-list">
                      {modelsOf(provider).map((model) => (
                        <li key={model.id} className="item-row" data-model={model.id}>
                          <span className="item-title">{model.name}</span>
                          <span className="hint">
                            <code>{model.modelKey}</code> · {t('ai.tokens', { context: String(model.contextTokens), output: String(model.outputTokens) })}
                            {!model.enabled && <> · {t('ai.disabled')}</>}
                          </span>
                          <button type="button" className="secondary" onClick={() => void checkModel(model)}>{t('ai.check', { name: model.name })}</button>
                          <button type="button" className="secondary" onClick={() => setEditingModel({ provider, model })}>{t('ai.edit', { name: model.name })}</button>
                          <button type="button" className="secondary" onClick={() => void removeModel(model)}>{t('ai.remove', { name: model.name })}</button>
                        </li>
                      ))}
                    </ul>
                    <button type="button" className="secondary" onClick={() => setEditingModel({ provider, model: null })}>
                      {t('ai.addModel', { name: provider.name })}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {editing === null && editingModel === null && <p className="actions"><button type="button" onClick={() => setEditing('new')}>{t('ai.add')}</button></p>}
            {editing !== null && (
              <ProviderForm key={editing === 'new' ? 'new' : `${editing.id}/${editing.version}`} provider={editing === 'new' ? null : editing}
                onCancel={() => setEditing(null)} onSaved={(saved, created) => done(t(created ? 'ai.created' : 'ai.saved', { name: saved.name }))}
                onConflict={reloadProviders} />
            )}
            {editingModel !== null && (
              <ModelForm key={editingModel.model === null ? `new/${editingModel.provider.id}` : `${editingModel.model.id}/${editingModel.model.version}`}
                provider={editingModel.provider} model={editingModel.model} onCancel={() => setEditingModel(null)}
                onSaved={(saved, created) => done(t(created ? 'ai.created' : 'ai.saved', { name: saved.name }))} onConflict={reloadModels} />
            )}
          </>
        )}
      </LoadedView>
    </section>
  );
}

function ProviderForm({ provider, onCancel, onSaved, onConflict }: {
  readonly provider: AiProvider | null;
  readonly onCancel: () => void;
  readonly onSaved: (saved: AiProvider, created: boolean) => void;
  readonly onConflict: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const [kind, setKind] = useState<Kind>(provider?.kind ?? 'openai');
  const [name, setName] = useState(provider?.name ?? '');
  const initialUrl = provider === null || provider.kind === 'anthropic' ? '' : provider.baseUrl;
  const [baseUrl, setBaseUrl] = useState(initialUrl);
  const [apiKey, setApiKey] = useState('');
  const [removeKey, setRemoveKey] = useState(false);
  const [ca, setCa] = useState(provider?.caCertificate ?? '');
  const [dataPath, setDataPath] = useState<'local' | 'external'>(provider?.dataPath ?? 'external');
  const [enabled, setEnabled] = useState(provider?.enabled ?? true);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [violations, setViolations] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const first = useRef<HTMLDivElement>(null);
  const ownUrl = kind === 'ollama' || (kind === 'openai' && baseUrl.trim() !== '');
  const dirty = name !== (provider?.name ?? '') || baseUrl !== initialUrl || apiKey !== '' || removeKey || ca !== (provider?.caCertificate ?? '')
    || enabled !== (provider?.enabled ?? true) || (ownUrl && dataPath !== (provider?.dataPath ?? 'external'));
  useUnsavedGuard(dirty);

  useEffect(() => {
    first.current?.querySelector<HTMLElement>('select, input')?.focus();
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setViolations([]);
    const body: AiProviderInput = {
      kind,
      name: name.trim(),
      baseUrl: kind === 'anthropic' || baseUrl.trim() === '' ? null : baseUrl.trim(),
      caCertificate: kind !== 'anthropic' && ca.trim() !== '' ? ca.trim() : null,
      dataPath: ownUrl ? dataPath : null,
      enabled,
      ...(apiKey !== '' ? { apiKey } : removeKey ? { apiKey: '' } : provider === null ? { apiKey: null } : {}),
    };
    setBusy(true);
    const result = provider === null ? await createAiProvider(api, body) : await updateAiProvider(api, provider.id, provider.version, body);
    setBusy(false);
    if (result.kind === 'ok') return onSaved(result.data.value, provider === null);
    if (result.problem.status === 412) onConflict();
    setViolations((result.problem as { violations?: readonly string[] }).violations ?? []);
    setNotice({ kind: 'info', text: problem(result.problem) });
  }

  const error = (field: string) => (violations.includes(`${field}.invalid`) || violations.includes(`${field}.required`)
    ? t(`ai.invalid.${field}` as MessageKey) : null);

  return (
    <form onSubmit={(event) => void save(event)} noValidate className="panel" data-testid="ai-provider-form">
      <h3>{provider === null ? t('ai.add') : t('ai.editTitle', { name: provider.name })}</h3>
      <div ref={first}>
        <SelectField label={t('ai.kind')} value={kind} disabled={provider !== null} onChange={(value) => setKind(KINDS.find((k) => k === value) ?? 'openai')}
          options={KINDS.map((value) => ({ value, label: t(`ai.kind.${value}` as MessageKey) }))} />
      </div>
      <TextField label={t('ai.name')} value={name} onChange={setName} maxLength={80} required error={error('name')} autoComplete="off" />
      {kind !== 'anthropic' && (
        <TextField label={t('ai.baseUrl')} hint={t(kind === 'ollama' ? 'ai.baseUrlHintOllama' : 'ai.baseUrlHintOpenai')} value={baseUrl}
          onChange={setBaseUrl} maxLength={200} required={kind === 'ollama'} error={error('baseUrl')} autoComplete="off" />
      )}
      {kind === 'anthropic' && <p className="hint">{t('ai.anthropicFixed')}</p>}
      {ownUrl && (
        <SelectField label={t('ai.dataPath')} hint={t('ai.dataPathHint')} value={dataPath}
          onChange={(value) => setDataPath(value === 'local' ? 'local' : 'external')}
          options={[{ value: 'external', label: t('ai.dataPath.external') }, { value: 'local', label: t('ai.dataPath.local') }]} />
      )}
      {error('dataPath') && <p className="field-error" role="alert">{error('dataPath')}</p>}
      <TextField label={t('ai.apiKey')} hint={provider?.keySet ? t('ai.apiKeyKeep') : t(kind === 'ollama' ? 'ai.apiKeyOptional' : 'ai.apiKeyHint')}
        value={apiKey} onChange={setApiKey} maxLength={1000} type="password" error={error('apiKey')} autoComplete="new-password" />
      {provider?.keySet && (
        <label className="check"><input type="checkbox" checked={removeKey} onChange={(event) => setRemoveKey(event.target.checked)} />{t('ai.removeKey')}</label>
      )}
      {kind !== 'anthropic' && (
        <TextField label={t('ai.caCertificate')} hint={t('ai.caCertificateHint')} value={ca} onChange={setCa} multiline error={error('caCertificate')} />
      )}
      <label className="check"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />{t('ai.enabled')}</label>
      <NoticeView notice={notice} />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('ai.save')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('ai.cancel')}</button>
      </p>
    </form>
  );
}

function ModelForm({ provider, model, onCancel, onSaved, onConflict }: {
  readonly provider: AiProvider;
  readonly model: AiModel | null;
  readonly onCancel: () => void;
  readonly onSaved: (saved: AiModel, created: boolean) => void;
  readonly onConflict: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const [name, setName] = useState(model?.name ?? '');
  const [modelKey, setModelKey] = useState(model?.modelKey ?? '');
  const [context, setContext] = useState(String(model?.contextTokens ?? 32000));
  const [output, setOutput] = useState(String(model?.outputTokens ?? 2000));
  const [enabled, setEnabled] = useState(model?.enabled ?? true);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [violations, setViolations] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const first = useRef<HTMLDivElement>(null);
  useUnsavedGuard(name !== (model?.name ?? '') || modelKey !== (model?.modelKey ?? '') || context !== String(model?.contextTokens ?? 32000)
    || output !== String(model?.outputTokens ?? 2000) || enabled !== (model?.enabled ?? true));

  useEffect(() => {
    first.current?.querySelector<HTMLElement>('input')?.focus();
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setViolations([]);
    const body: AiModelInput = {
      providerId: provider.id, name: name.trim(), modelKey: modelKey.trim(), contextTokens: Number.parseInt(context, 10) || 0,
      outputTokens: Number.parseInt(output, 10) || 0, enabled,
    };
    setBusy(true);
    const result = model === null ? await createAiModel(api, body) : await updateAiModel(api, model.id, model.version, body);
    setBusy(false);
    if (result.kind === 'ok') return onSaved(result.data.value, model === null);
    if (result.problem.status === 412) onConflict();
    setViolations((result.problem as { violations?: readonly string[] }).violations ?? []);
    setNotice({ kind: 'info', text: problem(result.problem) });
  }

  const error = (field: string) => (violations.includes(`${field}.invalid`) ? t(`ai.invalid.${field}` as MessageKey) : null);
  return (
    <form onSubmit={(event) => void save(event)} noValidate className="panel" data-testid="ai-model-form">
      <h3>{model === null ? t('ai.addModel', { name: provider.name }) : t('ai.editTitle', { name: model.name })}</h3>
      <div ref={first}>
        <TextField label={t('ai.name')} value={name} onChange={setName} maxLength={80} required error={error('name')} autoComplete="off" />
      </div>
      <TextField label={t('ai.modelKey')} hint={t('ai.modelKeyHint')} value={modelKey} onChange={setModelKey} maxLength={200} required
        error={error('modelKey')} autoComplete="off" />
      <TextField label={t('ai.contextTokens')} value={context} onChange={setContext} maxLength={8} required error={error('contextTokens')} />
      <TextField label={t('ai.outputTokens')} value={output} onChange={setOutput} maxLength={6} required error={error('outputTokens')} />
      <label className="check"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />{t('ai.enabled')}</label>
      <NoticeView notice={notice} />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('ai.save')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('ai.cancel')}</button>
      </p>
    </form>
  );
}

// ---------------------------------------------------------------- skills

function Skills() {
  const { t } = useI18n();
  const [useCase, setUseCase] = useState<AiUseCase>('ticket_summary');
  const [language, setLanguage] = useState<AiLanguage>('de');
  return (
    <section aria-labelledby="ai-skills" className="panel" data-testid="ai-skills">
      <h2 id="ai-skills">{t('ai.skills')}</h2>
      <p className="hint">{t('ai.skillsIntro')}</p>
      <SelectField label={t('ai.useCase')} value={useCase} onChange={(value) => setUseCase(AI_USE_CASES.find((u) => u === value) ?? 'ticket_summary')}
        options={AI_USE_CASES.map((value) => ({ value, label: t(`ai.useCase.${value}` as MessageKey) }))} />
      <SelectField label={t('ai.language')} value={language} onChange={(value) => setLanguage(value === 'en' ? 'en' : 'de')}
        options={LANGUAGES.map((value) => ({ value, label: t(`ai.language.${value}` as MessageKey) }))} />
      <SkillEditor key={`${useCase}/${language}`} useCase={useCase} language={language} />
    </section>
  );
}

function SkillEditor({ useCase, language }: { readonly useCase: AiUseCase; readonly language: AiLanguage }) {
  const { t, language: shown } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const [skill, reload, replace] = useLoad(() => readAiSkill(api, useCase, language), [api, useCase, language]);
  const [name, setName] = useState('');
  const [content, setContent] = useState('');
  const [loadedTag, setLoadedTag] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const value = skill.state === 'ok' ? skill.value : null;
  const baseName = value?.draft?.name ?? value?.applied.name ?? '';
  const baseContent = value?.draft?.content ?? value?.applied.content ?? '';

  // Takes the stored text over when the draft changed on the server, never over own unsaved input of the same draft.
  useEffect(() => {
    if (value === null || value.draftTag === loadedTag) return;
    setLoadedTag(value.draftTag);
    setName(baseName);
    setContent(baseContent);
  }, [value?.draftTag]);
  const dirty = value !== null && (name !== baseName || content !== baseContent);
  useUnsavedGuard(dirty);

  async function act(action: 'save' | 'discard' | 'activate' | 'restore') {
    if (value === null) return;
    if (action === 'discard' && !globalThis.confirm(t('ai.discardConfirm'))) return;
    if (action === 'restore' && !globalThis.confirm(t('ai.restoreConfirm'))) return;
    setNotice(null);
    setBusy(true);
    const etag = `"${value.draftTag}"`;
    const result = action === 'save' ? await saveAiSkillDraft(api, useCase, language, etag, name.trim(), content)
      : action === 'discard' ? await discardAiSkillDraft(api, useCase, language, etag)
        : action === 'activate' ? await activateAiSkill(api, useCase, language, etag) : await restoreAiSkill(api, useCase, language);
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) {
        setNotice({ kind: 'info', text: t('ai.skillConflict') });
        return reload();
      }
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    setLoadedTag(null);
    replace(result.data);
    const done: Record<typeof action, MessageKey> = { save: 'ai.skillSaved', discard: 'ai.skillDiscarded', activate: 'ai.skillActivated', restore: 'ai.skillRestored' };
    setNotice({ kind: 'success', text: t(done[action], { label: result.data.value.applied.label }) });
  }

  return (
    <LoadedView loaded={skill} retry={reload}>
      {(current: AiSkill) => (
        <div data-testid="ai-skill-editor">
          <p className="hint" data-testid="ai-skill-applied">
            {current.applied.builtin ? t('ai.skillBuiltin', { label: current.applied.label })
              : t('ai.skillActive', { label: current.applied.label, date: current.applied.activatedAt ? formatDateTime(current.applied.activatedAt, shown) : '' })}
            {current.draft !== null && <> · {t('ai.skillDraft', { number: String(current.draft.number) })}</>}
          </p>
          <NoticeView notice={notice} />
          <TextField label={t('ai.skillName')} value={name} onChange={setName} maxLength={80} required />
          <TextField label={t('ai.skillContent')} hint={t('ai.skillContentHint')} value={content} onChange={setContent} multiline maxLength={20000} required />
          <p className="actions">
            <button type="button" disabled={busy || !dirty} onClick={() => void act('save')}>{t('ai.skillSave')}</button>
            <button type="button" className="secondary" disabled={busy || current.draft === null || dirty} onClick={() => void act('activate')}>
              {t('ai.skillActivate')}
            </button>
            <button type="button" className="secondary" disabled={busy || current.draft === null} onClick={() => void act('discard')}>{t('ai.skillDiscard')}</button>
            <button type="button" className="secondary" disabled={busy || current.applied.builtin} onClick={() => void act('restore')}>{t('ai.skillRestore')}</button>
          </p>
          {current.versions.length > 0 && (
            <details>
              <summary>{t('ai.skillVersions')}</summary>
              <ul className="item-list" data-testid="ai-skill-versions">
                {current.versions.map((version) => (
                  <li key={version.number} className="item-row">
                    <span className="item-title">{version.label} · {version.name}</span>
                    <span className="hint">{t(`ai.skillState.${version.state}` as MessageKey)}
                      {version.activatedAt ? ` · ${formatDateTime(version.activatedAt, shown)}` : ''}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
          <Trial useCase={useCase} language={language} withDraft={current.draft !== null && !dirty} />
        </div>
      )}
    </LoadedView>
  );
}

// A trial with a synthetic sample: runs the draft, else the applied version, and shows the checked answer as plain text.
function Trial({ useCase, language, withDraft }: { readonly useCase: AiUseCase; readonly language: AiLanguage; readonly withDraft: boolean }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const reason = useAiReason();
  const [sample, setSample] = useState('');
  const [goal, setGoal] = useState('');
  const [run, setRun] = useState<AiRun | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const alive = useRef(true);
  useEffect(() => () => {
    alive.current = false;
  }, []);

  async function start(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setRun(null);
    setBusy(true);
    const started = await trialAiSkill(api, useCase, language, sample, goal.trim() === '' ? null : goal.trim());
    if (!alive.current) return;
    if (started.kind !== 'ok') {
      setBusy(false);
      return setNotice({ kind: 'info', text: problem(started.problem) });
    }
    const id = started.data.value.runId;
    for (;;) {
      const read = await readAiRun(api, id);
      if (!alive.current) return;
      if (read.kind !== 'ok') {
        setBusy(false);
        return setNotice({ kind: 'info', text: problem(read.problem) });
      }
      setRun(read.data.value);
      if (read.data.value.state !== 'queued' && read.data.value.state !== 'running') break;
      await new Promise((resolve) => setTimeout(resolve, 700));
      if (!alive.current) return;
    }
    setBusy(false);
  }

  async function cancel() {
    if (run === null) return;
    const result = await cancelAiRun(api, run.id);
    if (result.kind === 'ok') setRun(result.data.value);
  }

  return (
    <form onSubmit={(event) => void start(event)} noValidate className="panel" data-testid="ai-trial">
      <h3>{t('ai.trial')}</h3>
      <p className="hint">{t(withDraft ? 'ai.trialDraft' : 'ai.trialApplied')}</p>
      <TextField label={t('ai.trialSample')} hint={t('ai.trialSampleHint')} value={sample} onChange={setSample} multiline maxLength={20000} required />
      <TextField label={t('ai.trialGoal')} value={goal} onChange={setGoal} maxLength={500} />
      <NoticeView notice={notice} />
      <p className="actions">
        <button type="submit" disabled={busy || sample.trim() === ''}>{t('ai.trialStart')}</button>
        {busy && run !== null && <button type="button" className="secondary" onClick={() => void cancel()}>{t('ai.trialCancel')}</button>}
      </p>
      {run !== null && (
        <div role="status" data-testid="ai-trial-result">
          <p className="hint">
            {run.state === 'succeeded' ? t('ai.trialDone', { label: run.skillVersion, model: run.modelKey })
              : run.state === 'queued' || run.state === 'running' ? t('ai.trialRunning')
                : t('ai.trialFailed', { reason: reason(run.code ?? run.state) })}
            {run.truncated && <> · {t('ai.truncated')}</>}
          </p>
          {run.content && <pre className="ai-output">{JSON.stringify(run.content, null, 2)}</pre>}
        </div>
      )}
    </form>
  );
}

// ---------------------------------------------------------------- limits, usage and record

function LimitsAndUsage({ status, onChanged }: { readonly status: AiStatus; readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const problem = useAiProblem();
  const [limits, reload] = useLoad(() => readAiLimits(api), [api]);
  const [usage, reloadUsage] = useLoad(() => readAiUsage(api, 30), [api]);
  const [person, setPerson] = useState('');
  const [company, setCompany] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (limits.state !== 'ok') return;
    setPerson(String(limits.value.personDaily));
    setCompany(String(limits.value.companyDaily));
  }, [limits.state === 'ok' ? limits.value.version : -1]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (limits.state !== 'ok' || limits.etag === null) return;
    setNotice(null);
    setBusy(true);
    const result = await saveAiLimits(api, limits.etag, Number.parseInt(person, 10), Number.parseInt(company, 10));
    setBusy(false);
    if (result.kind !== 'ok') {
      if (result.problem.status === 412) reload();
      return setNotice({ kind: 'info', text: problem(result.problem) });
    }
    setNotice({ kind: 'success', text: t('ai.limitsSaved') });
    reload();
    reloadUsage();
    onChanged();
  }

  return (
    <section aria-labelledby="ai-limits" className="panel" data-testid="ai-limits">
      <h2 id="ai-limits">{t('ai.limits')}</h2>
      <p className="hint" data-testid="ai-today">{t('ai.today', { company: String(status.today.company), companyLimit: String(status.today.companyLimit) })}</p>
      <LoadedView loaded={limits} retry={reload}>
        {() => (
          <form onSubmit={(event) => void save(event)} noValidate>
            <TextField label={t('ai.personDaily')} value={person} onChange={setPerson} maxLength={6} required />
            <TextField label={t('ai.companyDaily')} value={company} onChange={setCompany} maxLength={7} required />
            <NoticeView notice={notice} />
            <p className="actions"><button type="submit" disabled={busy}>{t('ai.limitsSave')}</button></p>
          </form>
        )}
      </LoadedView>
      <h3>{t('ai.usage')}</h3>
      <LoadedView loaded={usage} retry={reloadUsage}>
        {(list) => {
          if (list.length === 0) return <EmptyText text={t('ai.noUsage')} />;
          return (
          <div className="table-scroll"><table data-testid="ai-usage">
            <thead><tr><th>{t('ai.day')}</th><th>{t('ai.useCase')}</th><th>{t('ai.runs')}</th><th>{t('ai.succeeded')}</th><th>{t('ai.tokensIn')}</th><th>{t('ai.tokensOut')}</th></tr></thead>
            <tbody>
              {list.map((row) => (
                <tr key={`${row.day}/${row.useCase}`}>
                  <td>{row.day}</td><td>{t(`ai.useCase.${row.useCase}` as MessageKey)}</td><td>{row.runs}</td><td>{row.succeeded}</td>
                  <td>{row.inputTokens}</td><td>{row.outputTokens}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
          );
        }}
      </LoadedView>
    </section>
  );
}

function Records({ round }: { readonly round: number }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const reason = useAiReason();
  const problem = useAiProblem();
  const [runs, setRuns] = useState<AiRun[] | null>(null);
  const [next, setNext] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const request = useRef(0);

  async function load(before: string | null) {
    const own = ++request.current;
    const result = await listAiRuns(api, before);
    if (own !== request.current) return;
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: problem(result.problem) });
    setNotice(null);
    setRuns((current) => (before === null || current === null ? result.data.value.runs : [...current, ...result.data.value.runs]));
    setNext(result.data.value.next);
  }

  useEffect(() => {
    void load(null);
  }, [api, round]);

  return (
    <section aria-labelledby="ai-records" className="panel" data-testid="ai-records">
      <h2 id="ai-records">{t('ai.records')}</h2>
      <p className="hint">{t('ai.recordsIntro')}</p>
      <NoticeView notice={notice} />
      <p className="actions"><button type="button" className="secondary" onClick={() => void load(null)}>{t('ai.refresh')}</button></p>
      {runs !== null && (runs.length === 0 ? <EmptyText text={t('ai.noRuns')} /> : (
        <div className="table-scroll"><table data-testid="ai-run-list">
          <thead><tr><th>{t('ai.startedAt')}</th><th>{t('ai.useCase')}</th><th>{t('ai.kindOfRun')}</th><th>{t('ai.model')}</th><th>{t('ai.state')}</th></tr></thead>
          <tbody>
            {runs.map((run) => (
              <tr key={run.id}>
                <td>{formatDateTime(run.startedAt, language)}</td>
                <td>{t(`ai.useCase.${run.useCase}` as MessageKey)} ({run.language}) · {run.skillVersion}</td>
                <td>{t(run.kind === 'test' ? 'ai.kindTest' : 'ai.kindUse')}</td>
                <td><code>{run.modelKey}</code> · {t(`ai.dataPath.${run.dataPath}` as MessageKey)}</td>
                <td>{run.state === 'succeeded' ? t('ai.runSucceeded') : run.state === 'queued' || run.state === 'running' ? t('ai.trialRunning') : reason(run.code ?? run.state)}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      ))}
      {next !== null && <p className="actions"><button type="button" className="secondary" onClick={() => void load(next)}>{t('ai.more')}</button></p>}
    </section>
  );
}
