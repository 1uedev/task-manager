import { useEffect, useRef, useState, type DragEvent, type KeyboardEvent } from 'react';
import { GripIcon } from '../app/Icons.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';
import { COLUMNS, INITIAL, locate, moveCard, type Board, type Column, type SampleCard } from './board.ts';

// Preview of drag and drop between columns with fixed sample cards (see board.ts). It stores nothing.

export function BoardPage() {
  const { t } = useI18n();
  const [board, setBoard] = useState<Board>(INITIAL);
  const [dragging, setDragging] = useState<string | null>(null);
  const [target, setTarget] = useState<{ column: Column; index: number } | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const refocus = useRef<string | null>(null);
  const root = useRef<HTMLDivElement>(null);

  // A card that moved to another column is a new element; the keyboard focus follows it.
  useEffect(() => {
    if (refocus.current === null) return;
    root.current?.querySelector<HTMLElement>(`[data-card="${refocus.current}"]`)?.focus();
    refocus.current = null;
  }, [board]);

  function place(id: string, column: Column, index: number) {
    const next = moveCard(board, id, column, index);
    const where = locate(next, id);
    const card = where ? next[where.column][where.index] : undefined;
    if (!where || !card) return;
    setBoard(next);
    setAnnouncement(t('board.moved', {
      name: t(card.title),
      column: t(`board.col.${where.column}` as MessageKey),
      position: where.index + 1,
      count: next[where.column].length,
    }));
  }

  const slotAtEnd = (column: Column) => target?.column === column && target.index >= board[column].length;

  const over = (column: Column, index: number) => (event: DragEvent) => {
    if (dragging === null) return;
    event.preventDefault();
    event.stopPropagation();
    if (target?.column !== column || target.index !== index) setTarget({ column, index });
  };
  const drop = (event: DragEvent) => {
    event.preventDefault();
    event.stopPropagation();
    if (dragging && target) {
      // Dropping below the own position within a column counts the card itself only once.
      const from = locate(board, dragging);
      const index = from && from.column === target.column && from.index < target.index ? target.index - 1 : target.index;
      place(dragging, target.column, index);
    }
    setDragging(null);
    setTarget(null);
  };
  const keys = (card: SampleCard, column: Column, index: number) => (event: KeyboardEvent) => {
    if (!event.altKey || event.ctrlKey || event.metaKey) return;
    const position = COLUMNS.indexOf(column);
    const left = COLUMNS[position - 1];
    const right = COLUMNS[position + 1];
    if (event.key === 'ArrowRight' && right) place(card.id, right, Math.min(index, board[right].length));
    else if (event.key === 'ArrowLeft' && left) place(card.id, left, Math.min(index, board[left].length));
    else if (event.key === 'ArrowUp' && index > 0) place(card.id, column, index - 1);
    else if (event.key === 'ArrowDown' && index < board[column].length - 1) place(card.id, column, index + 1);
    else return;
    event.preventDefault();
    refocus.current = card.id;
  };

  return (
    <>
      <p className="badge-sample">{t('board.sampleBadge')}</p>
      <h1 tabIndex={-1}>{t('board.title')}</h1>
      <p id="board-intro">{t('board.intro')}</p>
      <div className="board" ref={root}>
        {COLUMNS.map((column) => (
          <section key={column} className="board-column" data-column={column} aria-labelledby={`column-${column}`}
            data-target={target?.column === column || undefined}
            onDragOver={over(column, board[column].length)} onDrop={drop}>
            <header>
              <span className={`dot dot-${column}`} aria-hidden="true" />
              <h2 id={`column-${column}`}>{t(`board.col.${column}` as MessageKey)}</h2>
              <span className="count">{board[column].length}</span>
            </header>
            <ul>
              {board[column].map((card, index) => (
                <li key={card.id}>
                  {target?.column === column && target.index === index && dragging !== card.id && <div className="drop-slot" aria-hidden="true">{t('dnd.drop')}</div>}
                  <article className="ticket" draggable tabIndex={0} data-card={card.id}
                    data-dragging={dragging === card.id || undefined}
                    aria-labelledby={`title-${card.id}`} aria-describedby="board-hint"
                    onDragStart={(event) => {
                      event.dataTransfer.effectAllowed = 'move';
                      event.dataTransfer.setData('text/plain', card.id);
                      setDragging(card.id);
                    }}
                    onDragEnd={() => {
                      setDragging(null);
                      setTarget(null);
                    }}
                    onDragOver={over(column, index)} onDrop={drop}
                    onKeyDown={keys(card, column, index)}>
                    <div className="ticket-top">
                      <span className="ticket-key">{card.key}</span>
                      <span className="grip" aria-hidden="true"><GripIcon /></span>
                    </div>
                    <p className="ticket-title" id={`title-${card.id}`}>{t(card.title)}</p>
                    <div className="ticket-meta">
                      <span className={`tag tag-${card.tag}`}>{t(`board.tag.${card.tag}` as MessageKey)}</span>
                      <span className="avatar" aria-hidden="true">{card.initials}</span>
                    </div>
                  </article>
                </li>
              ))}
              {slotAtEnd(column) && <li aria-hidden="true"><div className="drop-slot">{t('dnd.drop')}</div></li>}
            </ul>
          </section>
        ))}
      </div>
      <p id="board-hint" className="visually-hidden">{t('board.keyboard')}</p>
      <p className="visually-hidden" aria-live="polite">{announcement}</p>
    </>
  );
}
