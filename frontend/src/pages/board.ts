import type { MessageKey } from '../i18n/index.ts';

// Preview of drag and drop between columns with fixed sample cards. It stores nothing and talks to no server;
// the real ticket board follows with the ticket tasks (Kanban is planned after V1, DEC-041).
export const COLUMNS = ['todo', 'doing', 'review', 'done'] as const;
export type Column = (typeof COLUMNS)[number];
type Tag = 'ui' | 'api' | 'rights';
export interface SampleCard { readonly id: string; readonly title: MessageKey; readonly tag: Tag; readonly key: string; readonly initials: string }
export type Board = Readonly<Record<Column, readonly SampleCard[]>>;

export const INITIAL: Board = {
  todo: [
    { id: 'c1', title: 'board.sample.1', tag: 'ui', key: 'TM-101', initials: 'AK' },
    { id: 'c4', title: 'board.sample.4', tag: 'ui', key: 'TM-104', initials: 'JS' },
    { id: 'c7', title: 'board.sample.7', tag: 'api', key: 'TM-107', initials: 'MB' },
  ],
  doing: [
    { id: 'c2', title: 'board.sample.2', tag: 'ui', key: 'TM-102', initials: 'LW' },
    { id: 'c5', title: 'board.sample.5', tag: 'rights', key: 'TM-105', initials: 'AK' },
  ],
  review: [{ id: 'c3', title: 'board.sample.3', tag: 'api', key: 'TM-103', initials: 'JS' }],
  done: [{ id: 'c6', title: 'board.sample.6', tag: 'ui', key: 'TM-106', initials: 'MB' }],
};

// Moves a card to a column and position. Positions beyond the end place it last.
export function moveCard(board: Board, id: string, column: Column, index: number): Board {
  let card: SampleCard | undefined;
  const next: Record<Column, SampleCard[]> = { todo: [], doing: [], review: [], done: [] };
  for (const name of COLUMNS) {
    next[name] = board[name].filter((item) => {
      if (item.id !== id) return true;
      card = item;
      return false;
    });
  }
  if (!card) return board;
  next[column].splice(Math.max(0, Math.min(index, next[column].length)), 0, card);
  return next;
}

export function locate(board: Board, id: string): { column: Column; index: number } | null {
  for (const column of COLUMNS) {
    const index = board[column].findIndex((item) => item.id === id);
    if (index >= 0) return { column, index };
  }
  return null;
}
