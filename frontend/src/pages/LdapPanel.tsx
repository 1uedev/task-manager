import { useState, type FormEvent } from 'react';
import { readLdapSettings, saveLdapSettings, testLdapSettings, type LdapSettings, type LdapSettingsRequest } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad, useUnsavedGuard } from '../app/hooks.ts';
import { LoadedView, NoticeView, SelectField, TextField, useProblemText, type Notice } from '../app/ui.tsx';
import { de } from '../i18n/de.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

type Security = 'none' | 'starttls' | 'ldaps';
const SECURITIES: readonly Security[] = ['starttls', 'ldaps', 'none'];

// The connection to the LDAP directory and its check (TASK-046). The password of the search account is not entered here;
// the operation provides it to the server, and the page only learns whether it does.
export function LdapPanel({ onChanged }: { readonly onChanged: () => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const [settings, reload, replace] = useLoad(() => readLdapSettings(api), [api]);
  return (
    <details className="panel" data-testid="ldap-panel">
      <summary>{t('ldap.title')}</summary>
      <LoadedView loaded={settings} retry={reload}>
        {(value) => <LdapForm settings={value} onSaved={(saved) => {
          replace({ value: saved, etag: null });
          onChanged();
        }} />}
      </LoadedView>
    </details>
  );
}

const asSecurity = (value: string | null | undefined): Security => (value === 'none' || value === 'ldaps' || value === 'starttls' ? value : 'starttls');

function LdapForm({ settings, onSaved }: { readonly settings: LdapSettings; readonly onSaved: (saved: LdapSettings) => void }) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [enabled, setEnabled] = useState(settings.enabled);
  const [url, setUrl] = useState(settings.url ?? '');
  const [security, setSecurity] = useState<Security>(asSecurity(settings.security));
  const [bindDn, setBindDn] = useState(settings.bindDn ?? '');
  const [searchBase, setSearchBase] = useState(settings.searchBase ?? '');
  const [userFilter, setUserFilter] = useState(settings.userFilter ?? '(uid={0})');
  const [idAttribute, setIdAttribute] = useState(settings.idAttribute ?? 'entryUUID');
  const [nameAttribute, setNameAttribute] = useState(settings.nameAttribute ?? 'cn');
  const [sampleName, setSampleName] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [checked, setChecked] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const body = (): LdapSettingsRequest => ({
    enabled,
    url: url.trim(),
    security,
    bindDn: bindDn.trim() === '' ? null : bindDn.trim(),
    searchBase: searchBase.trim(),
    userFilter: userFilter.trim(),
    idAttribute: idAttribute.trim(),
    nameAttribute: nameAttribute.trim() === '' ? null : nameAttribute.trim(),
  });

  const dirty = enabled !== settings.enabled || url !== (settings.url ?? '') || security !== asSecurity(settings.security)
    || bindDn !== (settings.bindDn ?? '') || searchBase !== (settings.searchBase ?? '') || userFilter !== (settings.userFilter ?? '(uid={0})')
    || idAttribute !== (settings.idAttribute ?? 'entryUUID') || nameAttribute !== (settings.nameAttribute ?? 'cn');
  useUnsavedGuard(dirty);

  async function save(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setChecked(null);
    setBusy(true);
    const result = await saveLdapSettings(api, body());
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: text(result.problem, { 'request.invalid': 'ldap.error.invalid' }) });
    onSaved(result.data.value);
    setNotice({ kind: 'success', text: t('ldap.saved') });
  }

  async function test() {
    setNotice(null);
    setChecked(null);
    setBusy(true);
    const result = await testLdapSettings(api, body(), sampleName.trim() === '' ? null : sampleName.trim());
    setBusy(false);
    if (result.kind !== 'ok') return setNotice({ kind: 'info', text: text(result.problem, { 'request.invalid': 'ldap.error.invalid' }) });
    const reason = result.data.value.reason;
    if (result.data.value.ok) return setNotice({ kind: 'success', text: t('ldap.testOk') });
    const key = `ldap.reason.${(reason ?? '').replace(/^directory\./, '')}`;
    setChecked(Object.hasOwn(de, key) ? t(key as MessageKey) : t('ldap.reason.unknown'));
  }

  return (
    <form onSubmit={(event) => void save(event)} noValidate>
      <p className="hint">{t('ldap.intro')}</p>
      <div className="field">
        <label><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /> {t('ldap.enabled')}</label>
      </div>
      <TextField label={t('ldap.url')} hint={t('ldap.urlHint')} value={url} onChange={setUrl} maxLength={300} autoComplete="off" />
      <SelectField label={t('ldap.security')} value={security} onChange={(value) => setSecurity(asSecurity(value))}
        options={SECURITIES.map((value) => ({ value, label: t(`ldap.security.${value}` as MessageKey) }))}
        {...(security === 'none' ? { hint: t('ldap.securityNone') } : {})} />
      <TextField label={t('ldap.bindDn')} hint={t('ldap.bindDnHint')} value={bindDn} onChange={setBindDn} maxLength={500} autoComplete="off" />
      <p className="hint" data-testid="ldap-bind-password">{settings.bindPasswordSet ? t('ldap.bindPasswordSet') : t('ldap.bindPasswordMissing')}</p>
      <TextField label={t('ldap.searchBase')} value={searchBase} onChange={setSearchBase} maxLength={500} autoComplete="off" />
      <TextField label={t('ldap.userFilter')} hint={t('ldap.userFilterHint')} value={userFilter} onChange={setUserFilter} maxLength={500} autoComplete="off" />
      <TextField label={t('ldap.idAttribute')} hint={t('ldap.idAttributeHint')} value={idAttribute} onChange={setIdAttribute} maxLength={100} autoComplete="off" />
      <TextField label={t('ldap.nameAttribute')} value={nameAttribute} onChange={setNameAttribute} maxLength={100} autoComplete="off" />
      <TextField label={t('ldap.sampleName')} hint={t('ldap.sampleNameHint')} value={sampleName} onChange={setSampleName} maxLength={64} autoComplete="off" />
      <p className="actions">
        <button type="submit" disabled={busy}>{t('ldap.save')}</button>
        <button type="button" className="secondary" disabled={busy} onClick={() => void test()}>{t('ldap.test')}</button>
      </p>
      {checked !== null && <p role="alert" className="field-error" data-testid="ldap-test-failed">{t('ldap.testFailed', { reason: checked })}</p>}
      <NoticeView notice={notice} />
    </form>
  );
}
