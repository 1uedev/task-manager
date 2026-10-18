import { useBrand } from '../brand/BrandProvider.tsx';
import { syntheticBrand } from '../brand/brand.ts';
import { formatDateTime, formatNumber } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// Exchanged values stay fixed; only their presentation depends on the selected language.
const EXAMPLE_INSTANT = '2026-10-18T14:30:00Z';
const EXAMPLE_ZONE = 'Europe/Berlin';
const EXAMPLE_NUMBER = 1234.5;

export function StartPage() {
  const { t, language } = useI18n();
  const { brand, isDefault, apply, reset } = useBrand();
  return (
    <>
      <h1 tabIndex={-1}>{t('start.title')}</h1>
      <p>{t('start.intro')}</p>

      <section aria-labelledby="format-heading">
        <h2 id="format-heading">{t('start.format.title')}</h2>
        <p>{t('start.format.intro')}</p>
        <dl>
          <dt>{t('start.format.instant')}</dt>
          <dd data-testid="formatted-instant">{formatDateTime(EXAMPLE_INSTANT, language, EXAMPLE_ZONE)}</dd>
          <dt>{t('start.format.zone')}</dt>
          <dd>{EXAMPLE_ZONE}</dd>
          <dt>{t('start.format.instantIso')}</dt>
          <dd>
            <time dateTime={EXAMPLE_INSTANT}>{EXAMPLE_INSTANT}</time>
          </dd>
          <dt>{t('start.format.number')}</dt>
          <dd data-testid="formatted-number">{formatNumber(EXAMPLE_NUMBER, language)}</dd>
        </dl>
        <ul>
          <li>{t('start.format.items', { count: 1 })}</li>
          <li>{t('start.format.items', { count: 3 })}</li>
        </ul>
      </section>

      <section aria-labelledby="brand-heading">
        <h2 id="brand-heading">{t('start.brand.title')}</h2>
        <p>{t('start.brand.intro')}</p>
        <p data-testid="brand-current">{t('start.brand.current', { name: brand.name })}</p>
        <p className="actions">
          <button type="button" onClick={() => apply(syntheticBrand)} disabled={!isDefault}>
            {t('start.brand.apply')}
          </button>
          <button type="button" onClick={reset} disabled={isDefault}>
            {t('start.brand.reset')}
          </button>
        </p>
      </section>
    </>
  );
}
