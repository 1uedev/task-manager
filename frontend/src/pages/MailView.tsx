import type { MailPreview } from '../api/product.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// A finished mail of the template "Antwort an Kunden" (TASK-082) as the server set it: head with logo and name under the
// accent line, greeting, text, signature and footer, and the plain text of the same message. The HTML of the mail itself is
// never put into the page; the parts carry the same filled texts and are shown as text only.
export function MailView({ mail, logoUrl, testId }: { readonly mail: MailPreview; readonly logoUrl: string | null; readonly testId?: string }) {
  const { t } = useI18n();
  const parts = mail.parts;
  return (
    <div className="mail-view" data-testid={testId} lang={mail.language}>
      <div className="mail-frame" style={{ borderTopColor: parts.accentColor }}>
        <div className="mail-head">
          {parts.logo && logoUrl !== null && <img src={logoUrl} alt="" className="mail-logo" />}
          <span className="mail-name">{parts.name}</span>
        </div>
        <div className="mail-main">
          {parts.greeting !== '' && <p className="prewrap" data-part="greeting">{parts.greeting}</p>}
          <p className="prewrap" data-part="body">{parts.body}</p>
          {parts.signature !== '' && <p className="prewrap" data-part="signature">{parts.signature}</p>}
        </div>
        {parts.footer !== '' && <p className="mail-foot prewrap" data-part="footer">{parts.footer}</p>}
      </div>
      <details className="mail-plain">
        <summary>{t('mailTemplate.plainText')}</summary>
        <pre data-testid={testId === undefined ? undefined : `${testId}-text`}>{mail.text}</pre>
      </details>
    </div>
  );
}
