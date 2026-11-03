import { useEffect, useId, useRef, useState } from 'react';
import type { ProblemInfo } from '../api/problem.ts';
import { previewMailTemplate, readMailTemplate, resetMailTemplate, saveMailTemplate, type MailPreview, type MailTemplate, type MailTexts } from '../api/product.ts';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { LoadedView } from '../app/ui.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { ProblemView } from '../states/StateViews.tsx';
import { MailView } from './MailView.tsx';

type Code = 'de' | 'en';
type Saving = ReturnType<typeof saveMailTemplate>;
type Texts = Readonly<Record<Code, MailTexts>>;
const PARTS = ['greeting', 'signature', 'footer'] as const;
const PART_MAX = 2000;
const HTTPS = 'https://';

const textsOf = (template: MailTemplate): Texts => ({
  de: { greeting: template.de.greeting, signature: template.de.signature, footer: template.de.footer },
  en: { greeting: template.en.greeting, signature: template.en.signature, footer: template.en.footer },
});
const same = (a: Texts, b: Texts) => (['de', 'en'] as const).every((code) => PARTS.every((part) => a[code][part] === b[code][part]));

// The reason of a refused template in words: the parts and placeholders the server named.
function useTemplateProblem(): (problem: ProblemInfo) => string | null {
  const { t } = useI18n();
  return (problem) => {
    const list = (problem.violations ?? []).map((item) => {
      const [where, token] = item.split(': ');
      const [code, part] = (where ?? '').split('.');
      const label = `${t(code === 'en' ? 'mailTemplate.language.en' : 'mailTemplate.language.de')}, ${t(`mailTemplate.part.${part ?? 'greeting'}` as MessageKey)}`;
      return token === undefined ? label : `${label}: ${token}`;
    }).join('; ');
    switch (problem.code) {
      case 'mail_template.placeholder_unknown': return t('mailTemplate.error.placeholder', { list });
      case 'mail_template.text_invalid': return t('mailTemplate.error.text', { list, max: PART_MAX });
      case 'mail_template.address_invalid': return t('mailTemplate.error.address');
      default: return null;
    }
  };
}

// The mail template "Antwort an Kunden" (TASK-082, DEC-112): greeting, signature and footer in German and English as plain
// text with placeholders, the address of the application for the portal link, a preview with synthetic values in the
// unsaved name and color of the page, and the way back to the built-in template. Only for the right appearance.manage; the
// server checks it again.
export function MailTemplateSection({ name, accentColor }: { readonly name: string | null; readonly accentColor: string }) {
  const { t } = useI18n();
  const { api, brand } = useBrand();
  const [loaded, reload, replace] = useLoad(() => readMailTemplate(api), [api]);
  // The editor starts again from each saved version; the confirmation of the save stays above it.
  const [done, setDone] = useState<MessageKey | null>(null);
  return (
    <section className="card mail-template" aria-labelledby="mail-template-heading" data-testid="mail-template">
      <h2 id="mail-template-heading">{t('mailTemplate.title')}</h2>
      <p className="hint">{t('mailTemplate.intro')}</p>
      <LoadedView loaded={loaded} retry={reload}>
        {(template, etag) => (
          <Editor key={template.version} template={template} etag={etag ?? `"${template.version}"`} name={name} accentColor={accentColor}
            logoUrl={brand.logoUrl} onConflict={reload} onEdit={() => setDone(null)} onSaved={(next, success) => {
              replace(next);
              setDone(success);
            }} />
        )}
      </LoadedView>
      <div aria-live="polite">{done !== null && <p className="success" role="status" data-testid="mail-template-saved">{t(done)}</p>}</div>
    </section>
  );
}

function Editor({ template, etag, name, accentColor, logoUrl, onSaved, onConflict, onEdit }: {
  readonly template: MailTemplate;
  readonly etag: string;
  readonly name: string | null;
  readonly accentColor: string;
  readonly logoUrl: string | null;
  readonly onSaved: (next: { readonly value: MailTemplate; readonly etag: string | null }, success: MessageKey) => void;
  readonly onConflict: () => void;
  readonly onEdit: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const explain = useTemplateProblem();
  const saved = textsOf(template);
  const [texts, setTexts] = useState<Texts>(saved);
  const [address, setAddress] = useState(template.applicationUrl ?? '');
  const [code, setCode] = useState<Code>('de');
  const [busy, setBusy] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [notice, setNotice] = useState<{ readonly kind: 'problem'; readonly problem: ProblemInfo } | null>(null);
  const [preview, setPreview] = useState<{ readonly mail: MailPreview } | { readonly problem: ProblemInfo } | null>(null);
  const ids = { address: useId(), addressHint: useId(), group: useId() };
  const dirty = !same(texts, saved) || address.trim() !== (template.applicationUrl ?? '');
  useUnsavedGuard(dirty);

  // The preview follows the input after a short pause; an older answer never replaces a newer one.
  const round = useRef(0);
  useEffect(() => {
    const ticket = ++round.current;
    const timer = setTimeout(() => {
      void previewMailTemplate(api, { language: code, applicationUrl: address.trim() === '' ? null : address.trim(), texts: texts[code], accentColor,
        ...(name === null ? {} : { name }) }).then((result) => {
        if (ticket !== round.current) return;
        setPreview(result.kind === 'ok' ? { mail: result.data.value } : { problem: result.problem });
      });
    }, 300);
    return () => clearTimeout(timer);
  }, [api, code, texts, address, name, accentColor]);

  function edit(part: (typeof PARTS)[number], value: string) {
    setTexts((current) => ({ ...current, [code]: { ...current[code], [part]: value } }));
    setNotice(null);
    onEdit();
  }

  async function finish(call: () => Saving, success: MessageKey) {
    setBusy(true);
    setNotice(null);
    const result = await call();
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'problem', problem: result.problem });
    onSaved({ value: result.data.value, etag: result.data.etag }, success);
  }

  const language = template[code];
  const reason = notice !== null ? explain(notice.problem) : null;
  const previewReason = preview !== null && 'problem' in preview ? explain(preview.problem) : null;

  return (
    <div className="mail-template-grid">
      <div>
        <p className="label-small" id={ids.group}>{t('mailTemplate.language')}</p>
        <div className="segmented" role="group" aria-labelledby={ids.group}>
          {(['de', 'en'] as const).map((item) => (
            <button key={item} type="button" aria-pressed={code === item} className={code === item ? 'active' : undefined}
              data-testid={`mail-language-${item}`} onClick={() => setCode(item)}>
              {t(item === 'de' ? 'mailTemplate.language.de' : 'mailTemplate.language.en')}
            </button>
          ))}
        </div>
        <p className="hint" data-testid="mail-template-state">
          {t(language.stored ? 'mailTemplate.stored' : 'mailTemplate.builtIn')}
        </p>
        {PARTS.map((part) => (
          <PartField key={`${code}-${part}`} part={part} value={texts[code][part]} onChange={(value) => edit(part, value)} lang={code} />
        ))}
        <div className="field">
          <label htmlFor={ids.address}>{t('mailTemplate.address')}</label>
          <p className="hint" id={ids.addressHint}>{t('mailTemplate.addressHint')}</p>
          <input id={ids.address} type="url" inputMode="url" value={address} placeholder={HTTPS} spellCheck={false} autoComplete="url"
            aria-describedby={ids.addressHint} data-testid="mail-address" onChange={(event) => {
              setAddress(event.target.value);
              setNotice(null);
              onEdit();
            }} />
        </div>
        <details className="placeholders">
          <summary>{t('mailTemplate.placeholders')}</summary>
          <dl>
            {template.placeholders.map((placeholder) => (
              <div key={placeholder}>
                <dt><code>{`{{${placeholder}}}`}</code></dt>
                <dd>{t(`mailTemplate.placeholder.${placeholder}` as MessageKey)}</dd>
              </div>
            ))}
          </dl>
        </details>
        <p className="actions">
          <button type="button" disabled={busy || !dirty} onClick={() => void finish(() => saveMailTemplate(api, etag,
            address.trim() === '' ? null : address.trim(), texts.de, texts.en), 'mailTemplate.saved')}>
            {t('mailTemplate.save')}
          </button>
          {!confirmReset
            ? <button type="button" className="secondary" disabled={busy || (!template.de.stored && !template.en.stored)}
                onClick={() => setConfirmReset(true)}>{t('mailTemplate.reset')}</button>
            : <>
              <button type="button" disabled={busy} onClick={() => {
                setConfirmReset(false);
                void finish(() => resetMailTemplate(api, etag), 'mailTemplate.resetDone');
              }}>{t('mailTemplate.resetConfirm')}</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmReset(false)}>{t('appearance.resetAll.cancel')}</button>
            </>}
        </p>
        <div aria-live="polite">
          {notice !== null && (reason !== null
            ? <p className="state state-problem" role="alert" data-testid="mail-template-problem">{reason}</p>
            : notice.problem.kind === 'conflict'
              ? <ProblemView problem={notice.problem} actionLabel="states.reload" onAction={onConflict} />
              : <ProblemView problem={notice.problem} />)}
        </div>
      </div>
      <div className="mail-template-preview">
        <p className="label-small">{t('mailTemplate.preview', { language: t(code === 'de' ? 'mailTemplate.language.de' : 'mailTemplate.language.en') })}</p>
        <p className="hint">{t('mailTemplate.previewHint')}</p>
        {preview !== null && 'mail' in preview && <MailView mail={preview.mail} logoUrl={logoUrl} testId="mail-template-preview" />}
        {previewReason !== null && <p className="field-error" data-testid="mail-preview-problem">{previewReason}</p>}
      </div>
    </div>
  );
}

function PartField({ part, value, onChange, lang }: { readonly part: (typeof PARTS)[number]; readonly value: string; readonly onChange: (value: string) => void; readonly lang: Code }) {
  const { t } = useI18n();
  const id = useId();
  const length = [...value].length;
  const over = length > PART_MAX;
  return (
    <div className="field">
      <label htmlFor={id}>{t(`mailTemplate.part.${part}` as MessageKey)}</label>
      <textarea id={id} value={value} rows={part === 'greeting' ? 2 : 4} lang={lang} spellCheck data-testid={`mail-part-${part}`}
        aria-invalid={over || undefined} aria-describedby={`${id}-count`} onChange={(event) => onChange(event.target.value)} />
      <p id={`${id}-count`} className={over ? 'field-error' : 'hint'}>{t('mailTemplate.count', { count: length, max: PART_MAX })}</p>
    </div>
  );
}
