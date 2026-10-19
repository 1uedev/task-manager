import { useEffect, useRef, useState, type ReactNode } from 'react';
import { THEMES, useBrand, type Theme } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { AppearancePage } from '../pages/AppearancePage.tsx';
import { BoardPage } from '../pages/BoardPage.tsx';
import { FormPage } from '../pages/FormPage.tsx';
import { StartPage } from '../pages/StartPage.tsx';
import { StatesPage } from '../pages/StatesPage.tsx';
import { BoardIcon, BrushIcon, FormIcon, GlobeIcon, GripIcon, HomeIcon, LayersIcon, MonitorIcon, MoonIcon, ShieldIcon, SunIcon } from './Icons.tsx';
import { readStoredOrder, storeOrder } from './preferences.ts';
import { hrefFor, useRoute, type Route } from './route.ts';
import { useReorder } from './useReorder.ts';

const NAV_LABEL: Record<Route, MessageKey> = { start: 'nav.start', board: 'nav.board', states: 'nav.states', form: 'nav.form', appearance: 'nav.appearance' };
const NAV_ICON: Record<Route, () => ReactNode> = { start: HomeIcon, board: BoardIcon, states: LayersIcon, form: FormIcon, appearance: BrushIcon };
const PAGE_TITLE: Record<Route, MessageKey> = { start: 'start.title', board: 'board.title', states: 'states.title', form: 'form.title', appearance: 'appearance.title' };
const THEME_LABEL: Record<Theme, MessageKey> = { light: 'theme.light', dark: 'theme.dark', system: 'theme.system' };
const THEME_ICON: Record<Theme, () => ReactNode> = { light: SunIcon, dark: MoonIcon, system: MonitorIcon };
// The main entries can be put in a personal order; the administration section stays fixed below them.
const MAIN_ROUTES: readonly Route[] = ['start', 'board', 'states', 'form'];

export function App() {
  const { t, language, languages, setLanguage } = useI18n();
  const { brand, theme, setTheme } = useBrand();
  const route = useRoute();
  const main = useRef<HTMLElement>(null);
  const first = useRef(true);
  const [order, setOrderState] = useState<Route[]>(() => readStoredOrder('navigation', MAIN_ROUTES));
  const setOrder = (next: Route[]) => {
    storeOrder('navigation', next);
    setOrderState(next);
  };
  const { itemProps, announcement } = useReorder(order, setOrder, (target, position, count) =>
    t('dnd.position', { name: t(NAV_LABEL[target]), position, count }),
  );

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

  const link = (target: Route) => {
    const Icon = NAV_ICON[target];
    return (
      <a href={hrefFor(target)} aria-current={target === route ? 'page' : undefined}>
        <Icon />
        <span>{t(NAV_LABEL[target])}</span>
      </a>
    );
  };

  return (
    <div className="shell">
      <a className="skip-link" href="#main" onClick={(event) => {
        event.preventDefault();
        main.current?.focus();
      }}>
        {t('app.skipToContent')}
      </a>
      <header className="sidebar">
        <div className="brand">
          {brand.logoUrl !== null
            ? <img src={brand.logoUrl} alt={t('brand.logo')} className="brand-logo" />
            : <span className="brand-mark" aria-hidden="true">{brand.name.slice(0, 1)}</span>}
          <span className="brand-name" data-testid="brand-name">{brand.name}</span>
        </div>
        <nav aria-label={t('nav.label')}>
          <ul className="nav-main" aria-describedby="nav-hint">
            {order.map((target, index) => (
              <li key={target} className="reorderable" data-route={target} {...itemProps(index)}>
                {link(target)}
                <span className="grip" aria-hidden="true"><GripIcon /></span>
              </li>
            ))}
          </ul>
          <p id="nav-hint" className="visually-hidden">{t('dnd.hint')}</p>
          <p className="nav-section"><ShieldIcon />{t('nav.admin')}</p>
          <ul>
            <li>{link('appearance')}</li>
          </ul>
        </nav>
        <div className="sidebar-footer">
          <div className="language">
            <GlobeIcon />
            <label htmlFor="language" className="visually-hidden">{t('language.label')}</label>
            <select id="language" value={language} onChange={(event) => setLanguage(event.target.value)}>
              {languages.map((code) => (
                <option key={code} value={code} lang={code}>
                  {t(`language.${code}` as MessageKey)}
                </option>
              ))}
            </select>
          </div>
          <div className="theme" role="group" aria-label={t('theme.label')}>
            {THEMES.map((id) => {
              const Icon = THEME_ICON[id];
              return (
                <button key={id} type="button" aria-pressed={theme === id} title={t(THEME_LABEL[id])} onClick={() => setTheme(id)}>
                  <Icon />
                  <span className="visually-hidden">{t(THEME_LABEL[id])}</span>
                </button>
              );
            })}
          </div>
        </div>
        <p className="visually-hidden" aria-live="polite">{announcement}</p>
      </header>
      <div className="workspace">
        <main id="main" ref={main} tabIndex={-1} className={`page-${route}`}>
          {route === 'start' && <StartPage />}
          {route === 'board' && <BoardPage />}
          {route === 'states' && <StatesPage />}
          {route === 'form' && <FormPage />}
          {route === 'appearance' && <AppearancePage />}
        </main>
        <footer>
          <p>{t('notice.synthetic')}</p>
        </footer>
      </div>
    </div>
  );
}
