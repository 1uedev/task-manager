import { useState, type ReactElement } from 'react';
import { ClockIcon, GripIcon, PaletteIcon } from '../app/Icons.tsx';
import { readStoredOrder, storeOrder } from '../app/preferences.ts';
import { hrefFor } from '../app/route.ts';
import { useReorder } from '../app/useReorder.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { formatDateTime, formatNumber } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// Exchanged values stay fixed; only their presentation depends on the selected language.
const EXAMPLE_INSTANT = '2026-10-18T14:30:00Z';
const EXAMPLE_ZONE = 'Europe/Berlin';
const EXAMPLE_NUMBER = 1234.5;

const CARDS = ['format', 'brand'] as const;
type CardId = (typeof CARDS)[number];
const CARD_TITLE: Record<CardId, MessageKey> = { format: 'start.format.title', brand: 'start.brand.title' };

export function StartPage() {
  const { t, language } = useI18n();
  const { savedAccent } = useBrand();
  const [cards, setCardsState] = useState<CardId[]>(() => readStoredOrder('cards', CARDS));
  const setCards = (next: CardId[]) => {
    storeOrder('cards', next);
    setCardsState(next);
  };
  const { itemProps, announcement } = useReorder(cards, setCards, (id, position, count) =>
    t('dnd.position', { name: t(CARD_TITLE[id]), position, count }), 'horizontal');

  // The handle is the keyboard stop of a card: Alt with the left or right arrow moves the card.
  const grip = (id: CardId) => (
    <button type="button" className="card-grip" aria-label={t('dnd.handle', { name: t(CARD_TITLE[id]) })}
      aria-describedby="cards-hint" title={t('dnd.hintHorizontal')}>
      <GripIcon />
    </button>
  );

  const content: Record<CardId, ReactElement> = {
    format: (
      <>
        <div className="card-head">
          <div className="card-icon" aria-hidden="true"><ClockIcon /></div>
          {grip('format')}
        </div>
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
        <ul className="chips">
          <li>{t('start.format.items', { count: 1 })}</li>
          <li>{t('start.format.items', { count: 3 })}</li>
        </ul>
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
        <h1 tabIndex={-1}>{t('start.title')}</h1>
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
