import { useEffect, useId, useRef, useState, type DragEvent } from 'react';
import { BRAND_NAME_MAX, changeAccentColor, changeAppearance, isBrandName, isLogoType, LOGO_MAX_BYTES, removeLogo, replaceLogo, resetAppearance } from '../api/client.ts';
import type { ProblemInfo } from '../api/problem.ts';
import { CheckIcon, ShieldIcon, UploadIcon } from '../app/Icons.tsx';
import { ACCENT_PRESETS, contrastRatio, isAccentColor, textOn } from '../brand/brand.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { formatNumber } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';

type Pending = ReturnType<typeof changeAccentColor>;
type Notice = { readonly kind: 'saved'; readonly text: MessageKey } | { readonly kind: 'problem'; readonly problem: ProblemInfo } | { readonly kind: 'invalid'; readonly text: MessageKey };

// Synthetic content of the sample mail, in the language of the mail and not of the interface (TASK-081). The frame around
// the reply follows with the mail template of TASK-082.
const MAIL_SAMPLE = {
  de: { subject: 'Antwort auf Anfrage NW-12', greeting: 'Guten Tag Erika Musterfrau,', body: 'vielen Dank für Ihre Nachricht. Die Störung ist behoben.', closing: 'Freundliche Grüße' },
  en: { subject: 'Reply to request NW-12', greeting: 'Hello Jane Example,', body: 'thank you for your message. The problem has been fixed.', closing: 'Kind regards' },
} as const;

function PreviewMark({ logo, name }: { readonly logo: string | null; readonly name: string }) {
  return logo !== null ? <img src={logo} alt="" className="preview-logo" /> : <span className="preview-mark">{[...name][0]}</span>;
}

// Company name, accent color and logo. Only an account with the right to change them saves them, and the server checks that
// again; everyone else gets a preview in this window that is never sent anywhere.
export function AppearancePage() {
  const { t, language } = useI18n();
  const { connection, api, savedAccent, defaultAccent, savedName, defaultName, preview, previewName, accept, reload, brand } = useBrand();
  const admin = connection.state === 'ready' && connection.mayChangeAppearance;
  const loaded = connection.state === 'ready' ? connection.loaded : null;
  const [draft, setDraft] = useState(savedAccent);
  const [hex, setHex] = useState(savedAccent.toUpperCase());
  const [name, setName] = useState(savedName);
  const [confirmReset, setConfirmReset] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [dropping, setDropping] = useState(false);
  const [localLogo, setLocalLogo] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const ids = { custom: useId(), hex: useId(), hexError: useId(), notice: useId(), name: useId(), nameError: useId(), nameHint: useId() };

  // The saved color arrives after the first render; take it over as long as nothing was edited.
  const edited = useRef(false);
  useEffect(() => {
    if (edited.current) return;
    setDraft(savedAccent);
    setHex(savedAccent.toUpperCase());
  }, [savedAccent]);

  const nameEdited = useRef(false);
  useEffect(() => {
    if (nameEdited.current) return;
    setName(savedName);
  }, [savedName]);

  // The whole interface shows the color and name being edited; leaving the page shows the saved ones again.
  useEffect(() => {
    preview(draft === savedAccent ? null : draft);
  }, [draft, savedAccent, preview]);
  useEffect(() => () => preview(null), [preview]);
  const trimmedName = name.trim();
  const nameValid = isBrandName(trimmedName);
  useEffect(() => {
    previewName(nameValid && trimmedName !== savedName ? trimmedName : null);
  }, [trimmedName, nameValid, savedName, previewName]);
  useEffect(() => () => previewName(null), [previewName]);
  useEffect(() => () => {
    if (localLogo !== null) URL.revokeObjectURL(localLogo);
  }, [localLogo]);

  function choose(color: string) {
    edited.current = true;
    setDraft(color.toLowerCase());
    setHex(color.toUpperCase());
    setNotice(null);
  }

  async function run(call: () => Pending, success: MessageKey) {
    setBusy(true);
    setNotice(null);
    const outcome = await call();
    setBusy(false);
    if (outcome.kind === 'ok') {
      accept(outcome.data);
      edited.current = false;
      nameEdited.current = false;
      setDraft(outcome.data.appearance.accentColor);
      setHex(outcome.data.appearance.accentColor.toUpperCase());
      setName(outcome.data.appearance.name);
      setNotice({ kind: 'saved', text: success });
    } else {
      setNotice({ kind: 'problem', problem: outcome.problem });
    }
  }

  function takeFile(file: File | undefined) {
    if (!file) return;
    if (!isLogoType(file.type)) return setNotice({ kind: 'invalid', text: 'appearance.logo.wrongType' });
    if (file.size > LOGO_MAX_BYTES) return setNotice({ kind: 'invalid', text: 'appearance.logo.tooLarge' });
    if (admin && loaded) {
      const type = file.type;
      void run(() => replaceLogo(api, loaded.etag, file, type), 'appearance.logo.saved');
      return;
    }
    setLocalLogo(URL.createObjectURL(file));
    setNotice({ kind: 'saved', text: 'appearance.logo.previewOnly' });
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDropping(false);
    takeFile(event.dataTransfer.files[0]);
  };

  const hexValid = isAccentColor(hex);
  const ink = textOn(draft);
  const ratio = contrastRatio(ink, draft);
  const shownLogo = localLogo ?? brand.logoUrl;
  const mode: MessageKey | null = admin ? null
    : connection.state === 'ready' ? 'appearance.mode.readonly'
    : connection.state === 'anonymous' ? 'appearance.mode.anonymous'
    : connection.state === 'offline' ? 'appearance.mode.offline'
    : null;

  return (
    <>
      <p className="badge-admin"><ShieldIcon />{t('appearance.admin')}</p>
      <h1 tabIndex={-1}>{t('appearance.title')}</h1>
      <p>{t('appearance.intro')}</p>

      {connection.state === 'loading' && <LoadingView />}
      {mode !== null && <p className="mode-notice" data-testid="appearance-mode">{t(mode)}</p>}

      <div className="appearance-grid">
        <div className="appearance-settings">
          <section className="card" aria-labelledby="name-heading">
            <h2 id="name-heading">{t('appearance.name.title')}</h2>
            <label htmlFor={ids.name} className="label-small">{t('appearance.name.label')}</label>
            <input id={ids.name} value={name} autoComplete="organization" data-testid="brand-name-input"
              aria-invalid={!nameValid || undefined} aria-describedby={nameValid ? ids.nameHint : `${ids.nameError} ${ids.nameHint}`}
              onChange={(event) => {
                nameEdited.current = true;
                setName(event.target.value);
                setNotice(null);
              }} />
            {!nameValid && <p className="field-error" id={ids.nameError}>{t('appearance.name.invalid', { max: BRAND_NAME_MAX })}</p>}
            <p className="hint" id={ids.nameHint}>{t('appearance.name.hint')}</p>
            <p className="actions">
              <button type="button" disabled={!admin || busy || !nameValid || trimmedName === savedName || !loaded}
                onClick={() => loaded && nameValid && void run(() => changeAppearance(api, loaded.etag, { name: trimmedName }), 'appearance.name.saved')}>
                {t('appearance.name.save')}
              </button>
              <button type="button" className="secondary" disabled={busy || trimmedName === defaultName} onClick={() => {
                nameEdited.current = true;
                setName(defaultName);
                setNotice(null);
              }}>
                {t('appearance.name.reset')}
              </button>
            </p>
          </section>

          <section className="card" aria-labelledby="accent-heading">
            <h2 id="accent-heading">{t('appearance.color.title')}</h2>
            <p className="label-small" id="presets-label">{t('appearance.color.presets')}</p>
            <div className="swatches" role="group" aria-labelledby="presets-label">
              {ACCENT_PRESETS.map(({ id, color }) => (
                <button key={id} type="button" aria-pressed={draft === color} className="swatch-button"
                  style={{ background: color, color: textOn(color) }} title={t(`appearance.preset.${id}` as MessageKey)} onClick={() => choose(color)}>
                  {draft === color ? <CheckIcon /> : null}
                  <span className="visually-hidden">{t(`appearance.preset.${id}` as MessageKey)}</span>
                </button>
              ))}
            </div>
            <div className="custom-color">
              <div>
                <label htmlFor={ids.custom} className="label-small">{t('appearance.color.custom')}</label>
                <input id={ids.custom} type="color" value={draft} onChange={(event) => choose(event.target.value)} />
              </div>
              <div>
                <label htmlFor={ids.hex} className="label-small">{t('appearance.color.hex')}</label>
                <input id={ids.hex} value={hex} spellCheck={false} autoComplete="off" maxLength={7}
                  aria-invalid={!hexValid || undefined} aria-describedby={hexValid ? undefined : ids.hexError}
                  onChange={(event) => {
                    const value = event.target.value.trim();
                    setHex(value.toUpperCase());
                    if (isAccentColor(value)) choose(value);
                  }} />
              </div>
            </div>
            {!hexValid && <p className="field-error" id={ids.hexError}>{t('appearance.color.invalid')}</p>}
            <p className="contrast-ok" data-testid="contrast">
              <CheckIcon />
              {t('appearance.contrast', { ratio: formatNumber(Math.floor(ratio * 10) / 10, language) })}
            </p>
            <p className="hint">{t('appearance.contrast.auto')}</p>
            <p className="actions">
              {/* An invalid typed value blocks saving: draft still holds the last valid color, which is not what the field shows. */}
              <button type="button" disabled={!admin || busy || !hexValid || draft === savedAccent || !loaded}
                onClick={() => loaded && isAccentColor(hex) && void run(() => changeAccentColor(api, loaded.etag, draft), 'appearance.color.saved')}>
                {t('appearance.save')}
              </button>
              <button type="button" className="secondary" disabled={busy || draft === defaultAccent} onClick={() => choose(defaultAccent)}>
                {t('appearance.reset')}
              </button>
            </p>
          </section>

          <section className="card" aria-labelledby="logo-heading">
            <h2 id="logo-heading">{t('appearance.logo.title')}</h2>
            <div className="dropzone" data-testid="logo-dropzone" data-active={dropping || undefined}
              onDragOver={(event) => {
                event.preventDefault();
                setDropping(true);
              }}
              onDragLeave={() => setDropping(false)} onDrop={onDrop}>
              {shownLogo !== null
                ? <img src={shownLogo} alt={t('brand.logo')} className="dropzone-logo" />
                : <span className="dropzone-icon" aria-hidden="true"><UploadIcon /></span>}
              <p className="dropzone-title">{t('appearance.logo.drop')}</p>
              <p>
                <button type="button" className="link-button" disabled={busy} onClick={() => fileInput.current?.click()}>{t('appearance.logo.choose')}</button>
              </p>
              <p className="hint">{t('appearance.logo.hint', { size: 512 })}</p>
              <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" className="visually-hidden" tabIndex={-1}
                aria-hidden="true" data-testid="logo-file"
                onChange={(event) => {
                  takeFile(event.target.files?.[0]);
                  event.target.value = '';
                }} />
            </div>
            {admin && loaded?.appearance.logo && (
              <p className="actions">
                <button type="button" className="secondary" disabled={busy}
                  onClick={() => void run(() => removeLogo(api, loaded.etag), 'appearance.logo.removed')}>
                  {t('appearance.logo.remove')}
                </button>
              </p>
            )}
          </section>

          {admin && loaded && (
            <section className="card" aria-labelledby="reset-heading">
              <h2 id="reset-heading">{t('appearance.resetAll.title')}</h2>
              <p>{t('appearance.resetAll.hint', { name: defaultName, color: defaultAccent.toUpperCase() })}</p>
              <p className="actions">
                {!confirmReset
                  ? <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmReset(true)}>{t('appearance.resetAll.start')}</button>
                  : <>
                    <button type="button" disabled={busy} onClick={() => {
                      setConfirmReset(false);
                      void run(() => resetAppearance(api, loaded.etag), 'appearance.resetAll.done');
                    }}>{t('appearance.resetAll.confirm')}</button>
                    <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmReset(false)}>{t('appearance.resetAll.cancel')}</button>
                  </>}
              </p>
            </section>
          )}

          <div id={ids.notice} aria-live="polite">
            {notice?.kind === 'saved' && <p className="success" role="status">{t(notice.text)}</p>}
            {notice?.kind === 'invalid' && <p className="state state-problem" role="alert">{t(notice.text)}</p>}
            {notice?.kind === 'problem' && (
              notice.problem.kind === 'conflict'
                ? <ProblemView problem={notice.problem} actionLabel="states.reload" onAction={() => {
                  edited.current = false;
                  setNotice(null);
                  reload();
                }} />
                : <ProblemView problem={notice.problem} />
            )}
          </div>
        </div>

        <aside className="card preview" aria-labelledby="preview-heading">
          <h2 className="label-small" id="preview-heading">{t('appearance.preview')}</h2>
          <p className="hint">{t('appearance.preview.hint')}</p>
          <div className="preview-set" aria-hidden="true">
            <figure data-testid="preview-app">
              <figcaption className="label-small">{t('appearance.preview.app')}</figcaption>
              <div className="preview-window">
                <div className="preview-bar">
                  <PreviewMark logo={shownLogo} name={brand.name} />
                  <span className="preview-name">{brand.name}</span>
                </div>
                <div className="preview-body">
                  <div className="preview-nav">
                    <span className="preview-item active" /><span className="preview-item" /><span className="preview-item" />
                  </div>
                  <div className="preview-content">
                    <span className="preview-line w80 strong" />
                    <span className="preview-line w100" />
                    <span className="preview-line w70" />
                    <div className="preview-row">
                      <span className="preview-button">{t('appearance.preview.button')}</span>
                      <span className="preview-link">{t('appearance.preview.link')}</span>
                      <span className="preview-badge">{t('appearance.preview.badge')}</span>
                    </div>
                  </div>
                </div>
              </div>
            </figure>
            <figure data-testid="preview-login">
              <figcaption className="label-small">{t('appearance.preview.login')}</figcaption>
              <div className="preview-window preview-gate">
                <div className="preview-bar">
                  <PreviewMark logo={shownLogo} name={brand.name} />
                  <span className="preview-name">{brand.name}</span>
                </div>
                <div className="preview-form">
                  <span className="preview-title">{t('login.title')}</span>
                  <span className="preview-field" /><span className="preview-field" />
                  <span className="preview-button">{t('login.submit')}</span>
                </div>
              </div>
            </figure>
            <figure data-testid="preview-portal">
              <figcaption className="label-small">{t('appearance.preview.portal')}</figcaption>
              <div className="preview-window preview-gate">
                <div className="preview-bar">
                  <PreviewMark logo={shownLogo} name={brand.name} />
                  <span className="preview-name">{brand.name}</span>
                  <span className="preview-addition">{t('portal.brandAddition')}</span>
                </div>
                <div className="preview-content">
                  <span className="preview-title">{t('portal.title')}</span>
                  <span className="preview-line w100" />
                  <span className="preview-line w70" />
                  <div className="preview-row"><span className="preview-button">{t('appearance.preview.button')}</span><span className="preview-badge">{t('appearance.preview.badge')}</span></div>
                </div>
              </div>
            </figure>
            {(['de', 'en'] as const).map((mail) => (
              <figure key={mail} data-testid={`preview-mail-${mail}`} lang={mail}>
                <figcaption className="label-small">{t(mail === 'de' ? 'appearance.preview.mailDe' : 'appearance.preview.mailEn')}</figcaption>
                <div className="preview-window preview-mail">
                  <p className="preview-subject">{MAIL_SAMPLE[mail].subject}</p>
                  <div className="preview-mail-head">
                    <PreviewMark logo={shownLogo} name={brand.name} />
                    <span className="preview-name">{brand.name}</span>
                  </div>
                  <div className="preview-mail-body">
                    <p>{MAIL_SAMPLE[mail].greeting}</p>
                    <p>{MAIL_SAMPLE[mail].body}</p>
                    <p>{MAIL_SAMPLE[mail].closing}<br />{brand.name}</p>
                  </div>
                </div>
              </figure>
            ))}
          </div>
          <p className="hint" data-testid="preview-color">{draft.toUpperCase()}</p>
        </aside>
      </div>
    </>
  );
}
