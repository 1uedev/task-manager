import { useEffect, useRef } from 'react';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { FormPage } from '../pages/FormPage.tsx';
import { StartPage } from '../pages/StartPage.tsx';
import { StatesPage } from '../pages/StatesPage.tsx';
import { hrefFor, routes, useRoute, type Route } from './route.ts';

const NAV_LABEL: Record<Route, MessageKey> = { start: 'nav.start', states: 'nav.states', form: 'nav.form' };
const PAGE_TITLE: Record<Route, MessageKey> = { start: 'start.title', states: 'states.title', form: 'form.title' };

export function App() {
  const { t, language, languages, setLanguage } = useI18n();
  const { brand } = useBrand();
  const route = useRoute();
  const main = useRef<HTMLElement>(null);
  const first = useRef(true);

  useEffect(() => {
    document.title = `${t(PAGE_TITLE[route])} · ${brand.name}`;
  }, [route, language, brand.name, t]);

  // After a navigation the page heading takes focus so keyboard and screen reader users start at the new content.
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    main.current?.querySelector<HTMLElement>('h1')?.focus();
  }, [route]);

  return (
    <>
      <a className="skip-link" href="#main" onClick={(event) => {
        event.preventDefault();
        main.current?.focus();
      }}>
        {t('app.skipToContent')}
      </a>
      <header className="app-header">
        <div className="brand">
          {brand.logoUrl !== null && <img src={brand.logoUrl} alt="" height="32" />}
          <span className="brand-name" data-testid="brand-name">{brand.name}</span>
        </div>
        <nav aria-label={t('nav.label')}>
          <ul>
            {routes.map((target) => (
              <li key={target}>
                <a href={hrefFor(target)} aria-current={target === route ? 'page' : undefined}>
                  {t(NAV_LABEL[target])}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="language">
          <label htmlFor="language">{t('language.label')}</label>
          <select id="language" value={language} onChange={(event) => setLanguage(event.target.value)}>
            {languages.map((code) => (
              <option key={code} value={code} lang={code}>
                {t(`language.${code}` as MessageKey)}
              </option>
            ))}
          </select>
        </div>
      </header>
      <main id="main" ref={main} tabIndex={-1}>
        {route === 'start' && <StartPage />}
        {route === 'states' && <StatesPage />}
        {route === 'form' && <FormPage />}
      </main>
      <footer>
        <p>{t('notice.synthetic')}</p>
      </footer>
    </>
  );
}
