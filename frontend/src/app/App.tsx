import { useEffect, useRef, useState, type ReactNode } from 'react';
import { THEMES, useBrand, type Theme } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { ACCOUNTS_RIGHT, AccountsPage } from '../pages/AccountsPage.tsx';
import { AppearancePage } from '../pages/AppearancePage.tsx';
import { BoardPage } from '../pages/BoardPage.tsx';
import { GroupPage, GroupsPage, PERMISSIONS_RIGHT } from '../pages/GroupsPage.tsx';
import { HELPDESK_RIGHT, HelpdeskPage } from '../pages/HelpdeskPage.tsx';
import { InboxPage } from '../pages/InboxPage.tsx';
import { JOBS_RIGHT, JobsPage } from '../pages/JobsPage.tsx';
import { LoginPage, PasswordPage } from '../pages/LoginPage.tsx';
import { PortalPage } from '../pages/PortalPage.tsx';
import { ProjectPage } from '../pages/ProjectPage.tsx';
import { ProjectsPage } from '../pages/ProjectsPage.tsx';
import { SearchPage } from '../pages/SearchPage.tsx';
import { QuickSearch } from './QuickSearch.tsx';
import { SLA_RIGHT, SlaPage } from '../pages/SlaPage.tsx';
import { TAGS_RIGHT, TagsPage } from '../pages/TagsPage.tsx';
import { EscalationsPage } from '../pages/EscalationsPage.tsx';
import { StartPage } from '../pages/StartPage.tsx';
import { TicketPage } from '../pages/TicketPage.tsx';
import { TimePage } from '../pages/TimePage.tsx';
import { WikiPage } from '../pages/WikiPage.tsx';
import { RequirementsPage } from '../pages/RequirementsPage.tsx';
import { LoadingView, ProblemView } from '../states/StateViews.tsx';
import {
  AlertIcon, BoardIcon, BookIcon, BrushIcon, ChecklistIcon, ClockIcon, InboxIcon, MailIcon, FolderIcon, GlobeIcon, GripIcon, HomeIcon, HourglassIcon, KeyIcon, LogoutIcon, MonitorIcon, MoonIcon, SearchIcon, ShieldIcon, SunIcon, TagIcon, TimerIcon, UsersIcon,
} from './Icons.tsx';
import { readStoredOrder, storeOrder } from './preferences.ts';
import { hrefFor, section, useRoute, type Route, type RouteName } from './route.ts';
import { useSession } from './session.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useReorder } from './useReorder.ts';

const NAV_LABEL: Record<RouteName, MessageKey> = {
  start: 'nav.start', projects: 'nav.projects', search: 'nav.search', inbox: 'nav.inbox', board: 'nav.board', escalations: 'nav.escalations', time: 'nav.time', wiki: 'nav.wiki', requirements: 'nav.requirements', accounts: 'nav.accounts', groups: 'nav.groups', helpdesk: 'nav.helpdesk', sla: 'nav.sla', jobs: 'nav.jobs', tags: 'nav.tags', appearance: 'nav.appearance',
};
const NAV_ICON: Record<RouteName, () => ReactNode> = {
  start: HomeIcon, projects: FolderIcon, search: SearchIcon, inbox: InboxIcon, board: BoardIcon, escalations: AlertIcon, time: HourglassIcon, wiki: BookIcon, requirements: ChecklistIcon, accounts: KeyIcon, groups: UsersIcon, helpdesk: MailIcon, sla: TimerIcon, jobs: ClockIcon, tags: TagIcon, appearance: BrushIcon,
};
const PAGE_TITLE: Record<RouteName, MessageKey> = {
  start: 'start.title', projects: 'projects.title', search: 'search.title', inbox: 'inbox.title', board: 'board.title', escalations: 'escalations.title', time: 'time.title', wiki: 'wiki.title', requirements: 'requirements.title', accounts: 'accounts.title', groups: 'groups.title', helpdesk: 'helpdesk.title', sla: 'sla.title', jobs: 'jobs.title', tags: 'tagsPage.title', appearance: 'appearance.title',
};
const THEME_LABEL: Record<Theme, MessageKey> = { light: 'theme.light', dark: 'theme.dark', system: 'theme.system' };
const THEME_ICON: Record<Theme, () => ReactNode> = { light: SunIcon, dark: MoonIcon, system: MonitorIcon };
// The main entries can be put in a personal order; the administration section stays fixed below them.
const MAIN_ROUTES: readonly RouteName[] = ['start', 'projects', 'search', 'inbox', 'board', 'escalations', 'time', 'wiki', 'requirements'];

function Preferences() {
  const { t, language, languages, setLanguage } = useI18n();
  const { theme, setTheme } = useBrand();
  return (
    <div className="preferences">
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
  );
}

// The portal carries the same appearance with the addition "Kundenportal" (TASK-081).
function BrandMark({ addition }: { readonly addition?: MessageKey }) {
  const { brand } = useBrand();
  const { t } = useI18n();
  return (
    <div className="brand">
      {brand.logoUrl !== null
        // The name beside it is read out, so the logo itself stays silent instead of announcing a generic label.
        ? <img src={brand.logoUrl} alt="" className="brand-logo" />
        : <span className="brand-mark" aria-hidden="true">{[...brand.name][0]}</span>}
      <span className="brand-name" data-testid="brand-name">{brand.name}</span>
      {addition !== undefined && <span className="brand-addition" data-testid="brand-addition">{t(addition)}</span>}
    </div>
  );
}

// Before a complete login only the login, the password change or a connection problem is shown.
export function App() {
  const { t, language } = useI18n();
  const { brand } = useBrand();
  const { view, refresh, handover } = useSession();
  const main = useRef<HTMLElement>(null);

  useEffect(() => {
    if (view.state === 'ready' && handover === null) return;
    const title: MessageKey = handover !== null ? 'handover.title' : view.state === 'pending' ? 'password.title' : view.state === 'anonymous' ? 'login.title' : 'app.loadingApp';
    document.title = `${t(title)} · ${brand.name}`;
  }, [view.state, handover, language, brand.name, t]);

  // The handover stands above everything else: a late answer that ends the session must not take it away (TASK-050).
  if (handover !== null) {
    return (
      <div className="gate">
        <header className="gate-head">
          <BrandMark />
          <Preferences />
        </header>
        <main id="main" ref={main} tabIndex={-1}><HandoverPage /></main>
      </div>
    );
  }
  if (view.state === 'ready' && view.session.portal) return <PortalShell />;
  if (view.state === 'ready') return <Shell />;
  return (
    <div className="gate">
      <header className="gate-head">
        <BrandMark />
        <Preferences />
      </header>
      <main id="main" ref={main} tabIndex={-1}>
        {view.state === 'loading' && <><h1 className="visually-hidden" tabIndex={-1}>{t('app.loadingApp')}</h1><LoadingView /></>}
        {view.state === 'offline' && (
          <>
            <h1 tabIndex={-1}>{t('app.offline')}</h1>
            <ProblemView problem={{ kind: 'network', status: null, code: null, correlationId: null }} onAction={() => void refresh()} actionLabel="states.retry" />
          </>
        )}
        {view.state === 'anonymous' && <LoginPage />}
        {view.state === 'pending' && <PasswordPage />}
      </main>
    </div>
  );
}

// After a person converted their own account the server has ended the session. The one time password is shown here and
// nowhere else, without any action that needs a session, until the person goes to the login.
function HandoverPage() {
  const { t, language } = useI18n();
  const { handover, release, refresh } = useSession();
  if (handover === null) return null;
  return (
    <>
      <h1 tabIndex={-1}>{t('handover.title')}</h1>
      <div role="status" className="success one-time" data-testid="one-time">
        <p>{t('accounts.issued', { name: handover.username })}</p>
        <p className="secret"><code>{handover.oneTimePassword}</code></p>
        <p className="hint">{t('accounts.issuedHint', { date: formatDateTime(handover.expiresAt, language) })}</p>
        <p className="hint">{t('accounts.selfConverted')}</p>
      </div>
      <p className="actions">
        <button type="button" onClick={() => { release(); void refresh(); }}>{t('accounts.toLogin')}</button>
      </p>
    </>
  );
}

// A portal account sees only its requests: no navigation of the staff, no administration (TASK-075, DEC-105).
function PortalShell() {
  const { t, language } = useI18n();
  const { brand } = useBrand();
  const { view, signOut } = useSession();
  const who = view.state === 'ready' ? view.session.displayName ?? view.session.username ?? '' : '';
  useEffect(() => {
    document.title = `${t('portal.title')} · ${brand.name}`;
  }, [language, brand.name, t]);
  return (
    <div className="gate portal">
      <header className="gate-head">
        <BrandMark addition="portal.brandAddition" />
        <div className="portal-session">
          <p className="who" data-testid="who">{who}</p>
          <button type="button" className="link-button logout" onClick={() => void signOut()}><LogoutIcon />{t('session.logout')}</button>
          <Preferences />
        </div>
      </header>
      <main id="main" tabIndex={-1}><PortalPage /></main>
    </div>
  );
}

function pageTitle(route: Route): MessageKey {
  if (route.name === 'project' || route.name === 'ticket') return 'projects.title';
  if (route.name === 'group') return 'groups.title';
  if (route.name === 'wikiPage') return 'wiki.title';
  if (route.name === 'requirementPage') return 'requirements.title';
  if (route.name === 'searchArea') return 'search.title';
  return PAGE_TITLE[route.name];
}

function Shell() {
  const { t, language } = useI18n();
  const { brand } = useBrand();
  const { view, has, signOut } = useSession();
  const [route, visit] = useRoute();
  const current = section(route);
  const main = useRef<HTMLElement>(null);
  const first = useRef(true);
  const [order, setOrderState] = useState<RouteName[]>(() => readStoredOrder('navigation', MAIN_ROUTES));
  const setOrder = (next: RouteName[]) => {
    storeOrder('navigation', next);
    setOrderState(next);
  };
  const { itemProps, announcement } = useReorder(order, setOrder, (target, position, count) =>
    t('dnd.position', { name: t(NAV_LABEL[target]), position, count }),
  );
  const routeKey = hrefFor(route);
  const who = view.state === 'ready' ? view.session.displayName ?? view.session.username ?? '' : '';

  useEffect(() => {
    document.title = `${t(pageTitle(route))} · ${brand.name}`;
  }, [routeKey, language, brand.name, t]);

  // After a navigation the page heading takes focus so keyboard and screen reader users start at the new content.
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const focus = () => main.current?.querySelector('h1')?.focus();
    focus();
    // A page that loads first gets its heading a moment later, unless the person already moved into the new content.
    const timer = setTimeout(() => {
      const active = document.activeElement;
      if (active === null || active === document.body || active === main.current || !main.current?.contains(active)
        || active.tagName === 'H1') focus();
    }, 400);
    return () => clearTimeout(timer);
  }, [routeKey, visit]);

  const link = (target: RouteName) => {
    const Icon = NAV_ICON[target];
    return (
      <a href={hrefFor(target)} aria-current={target === current ? 'page' : undefined}>
        <Icon />
        <span>{t(NAV_LABEL[target])}</span>
      </a>
    );
  };

  const admin: RouteName[] = [
    ...(has(ACCOUNTS_RIGHT) ? ['accounts' as const] : []),
    ...(has(PERMISSIONS_RIGHT) ? ['groups' as const] : []),
    ...(has(HELPDESK_RIGHT) ? ['helpdesk' as const] : []),
    ...(has(SLA_RIGHT) ? ['sla' as const] : []),
    ...(has(JOBS_RIGHT) ? ['jobs' as const] : []),
    ...(has(TAGS_RIGHT) ? ['tags' as const] : []),
    'appearance',
  ];
  const allowed = (name: RouteName) => name !== 'accounts' && name !== 'groups' && name !== 'jobs' && name !== 'helpdesk' && name !== 'sla' && name !== 'tags'
    ? true : admin.includes(name);

  return (
    <div className="shell">
      <a className="skip-link" href="#main" onClick={(event) => {
        event.preventDefault();
        main.current?.focus();
      }}>
        {t('app.skipToContent')}
      </a>
      <header className="sidebar">
        <BrandMark />
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
            {admin.map((target) => <li key={target}>{link(target)}</li>)}
          </ul>
        </nav>
        <div className="sidebar-footer">
          <p className="who" data-testid="who">{who}</p>
          <button type="button" className="link-button logout" onClick={() => void signOut()}><LogoutIcon />{t('session.logout')}</button>
          <Preferences />
        </div>
        <p className="visually-hidden" aria-live="polite">{announcement}</p>
      </header>
      <div className="workspace">
        <div className="topbar"><QuickSearch /></div>
        <main id="main" ref={main} tabIndex={-1} className={`page-${current}`}>
          {!allowed(current) && <><h1 tabIndex={-1}>{t(PAGE_TITLE[current])}</h1><ProblemView problem={{ kind: 'forbidden', status: 403, code: null, correlationId: null }} /></>}
          {route.name === 'start' && <StartPage />}
          {route.name === 'projects' && <ProjectsPage />}
          {route.name === 'project' && <ProjectPage key={route.id} id={route.id} tab={route.tab} />}
          {route.name === 'ticket' && <TicketPage key={`${route.projectId}/${route.number}`} projectId={route.projectId} number={route.number} />}
          {route.name === 'search' && <SearchPage key="search" />}
          {route.name === 'searchArea' && <SearchPage key={`${routeKey}/${visit}`} area={route.area} q={route.q} />}
          {route.name === 'board' && <BoardPage />}
          {route.name === 'accounts' && allowed('accounts') && <AccountsPage />}
          {route.name === 'groups' && allowed('groups') && <GroupsPage />}
          {route.name === 'group' && allowed('groups') && <GroupPage key={route.id} id={route.id} />}
          {route.name === 'inbox' && <InboxPage />}
          {route.name === 'escalations' && <EscalationsPage />}
          {route.name === 'time' && <TimePage />}
          {route.name === 'wiki' && <WikiPage key="wiki" projectId={null} pageId={null} />}
          {route.name === 'wikiPage' && <WikiPage key={route.projectId} projectId={route.projectId} pageId={route.pageId} entry={{ language: route.language, published: route.published === true }} />}
          {route.name === 'requirements' && <RequirementsPage key="requirements" projectId={null} requirementId={null} />}
          {route.name === 'requirementPage' && (
            <RequirementsPage key={route.projectId} projectId={route.projectId} requirementId={route.requirementId} />
          )}
          {route.name === 'helpdesk' && allowed('helpdesk') && <HelpdeskPage />}
          {route.name === 'sla' && allowed('sla') && <SlaPage />}
          {route.name === 'jobs' && allowed('jobs') && <JobsPage />}
          {route.name === 'tags' && allowed('tags') && <TagsPage />}
          {route.name === 'appearance' && <AppearancePage />}
        </main>
        <footer>
          <p>{t('notice.footer')}</p>
        </footer>
      </div>
    </div>
  );
}
