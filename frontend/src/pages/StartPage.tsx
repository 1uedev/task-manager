import { useState, type ReactElement } from 'react';
import { listProjects } from '../api/product.ts';
import { FolderIcon, GripIcon, PaletteIcon, SearchIcon } from '../app/Icons.tsx';
import { useLoad } from '../app/hooks.ts';
import { readStoredOrder, storeOrder } from '../app/preferences.ts';
import { hrefFor } from '../app/route.ts';
import { useSession } from '../app/session.tsx';
import { EmptyText, LoadedView } from '../app/ui.tsx';
import { useReorder } from '../app/useReorder.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

const CARDS = ['projects', 'search', 'brand'] as const;
type CardId = (typeof CARDS)[number];
const CARD_TITLE: Record<CardId, MessageKey> = { projects: 'start.projects.title', search: 'start.search.title', brand: 'start.brand.title' };

export function StartPage() {
  const { t } = useI18n();
  const { savedAccent, api } = useBrand();
  const { view } = useSession();
  const [projects, reload] = useLoad(() => listProjects(api), [api]);
  const [cards, setCardsState] = useState<CardId[]>(() => readStoredOrder('cards', CARDS));
  const setCards = (next: CardId[]) => {
    storeOrder('cards', next);
    setCardsState(next);
  };
  const { itemProps, announcement } = useReorder(cards, setCards, (id, position, count) =>
    t('dnd.position', { name: t(CARD_TITLE[id]), position, count }), 'horizontal');
  const who = view.state === 'ready' ? view.session.displayName ?? view.session.username ?? '' : '';

  // The handle is the keyboard stop of a card: Alt with the left or right arrow moves the card.
  const grip = (id: CardId) => (
    <button type="button" className="card-grip" aria-label={t('dnd.handle', { name: t(CARD_TITLE[id]) })}
      aria-describedby="cards-hint" title={t('dnd.hintHorizontal')}>
      <GripIcon />
    </button>
  );

  const content: Record<CardId, ReactElement> = {
    projects: (
      <>
        <div className="card-head">
          <div className="card-icon" aria-hidden="true"><FolderIcon /></div>
          {grip('projects')}
        </div>
        <h2 id="projects-heading">{t('start.projects.title')}</h2>
        <LoadedView loaded={projects} retry={reload}>
          {(items) => items.length === 0
            ? <EmptyText text={t('start.projects.empty')} /> : (
            <ul className="plain-list" data-testid="start-projects">
              {items.slice(0, 6).map((project) => (
                <li key={project.id}><a href={hrefFor({ name: 'project', id: project.id, tab: 'tickets' })}>{`${project.key} · ${project.name}`}</a></li>
              ))}
            </ul>
          )}
        </LoadedView>
        <p className="actions"><a className="button" href={hrefFor('projects')}>{t('start.projects.open')}</a></p>
      </>
    ),
    search: (
      <>
        <div className="card-head">
          <div className="card-icon" aria-hidden="true"><SearchIcon /></div>
          {grip('search')}
        </div>
        <h2 id="search-heading">{t('start.search.title')}</h2>
        <p>{t('start.search.intro')}</p>
        <p className="actions"><a className="button" href={hrefFor('search')}>{t('start.search.open')}</a></p>
      </>
    ),
    brand: (
      <>
        <div className="card-head">
          <div className="card-icon" aria-hidden="true"><PaletteIcon /></div>
          {grip('brand')}
        </div>
        <h2 id="brand-heading">{t('start.brand.title')}</h2>
        <p>{t('start.brand.intro')}</p>
        <p className="current-brand" data-testid="brand-current">
          <span className="swatch" aria-hidden="true" />
          {t('start.brand.color', { color: savedAccent.toUpperCase() })}
        </p>
        <p className="actions">
          <a className="button" href={hrefFor('appearance')}>{t('start.brand.open')}</a>
        </p>
      </>
    ),
  };

  return (
    <>
      <div className="hero">
        <h1 tabIndex={-1}>{who === '' ? t('start.title') : t('start.greeting', { name: who })}</h1>
        <p>{t('start.intro')}</p>
        <p className="hero-hint" id="cards-hint">{t('start.cards.hint')}</p>
      </div>
      <div className="card-grid">
        {cards.map((id, index) => (
          <section key={id} aria-labelledby={`${id}-heading`} className="card reorderable" data-card={id} {...itemProps(index)}>
            {content[id]}
          </section>
        ))}
      </div>
      <p className="visually-hidden" aria-live="polite">{announcement}</p>
    </>
  );
}
