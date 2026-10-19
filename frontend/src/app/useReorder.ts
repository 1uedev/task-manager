import { useState, type DragEvent, type KeyboardEvent } from 'react';

// Reorders a list by drag and drop. The keyboard alternative is Alt with the arrow keys on any element inside an
// entry; every move is announced through `announcement`, which the caller puts into a polite live region.
export function move<T>(list: readonly T[], from: number, to: number): T[] {
  if (from < 0 || from >= list.length) return [...list];
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(Math.max(0, Math.min(to, next.length)), 0, item as T);
  return next;
}

export function useReorder<T>(
  items: readonly T[],
  setItems: (next: T[]) => void,
  describe: (item: T, position: number, count: number) => string,
  axis: 'vertical' | 'horizontal' = 'vertical',
) {
  const [dragging, setDragging] = useState<number | null>(null);
  const [over, setOver] = useState<number | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const [back, forward] = axis === 'vertical' ? ['ArrowUp', 'ArrowDown'] : ['ArrowLeft', 'ArrowRight'];

  function commit(from: number, to: number) {
    const next = move(items, from, to);
    setItems(next);
    const item = items[from];
    if (item !== undefined) setAnnouncement(describe(item, next.indexOf(item) + 1, next.length));
  }

  function itemProps(index: number) {
    return {
      draggable: true,
      'data-dragging': dragging === index || undefined,
      'data-over': over === index && dragging !== null && dragging !== index ? (dragging < index ? 'after' : 'before') : undefined,
      onDragStart: (event: DragEvent) => {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(index));
        setDragging(index);
      },
      onDragOver: (event: DragEvent) => {
        if (dragging === null) return;
        event.preventDefault();
        if (over !== index) setOver(index);
      },
      onDrop: (event: DragEvent) => {
        event.preventDefault();
        if (dragging !== null && dragging !== index) commit(dragging, index);
        setDragging(null);
        setOver(null);
      },
      onDragEnd: () => {
        setDragging(null);
        setOver(null);
      },
      onKeyDown: (event: KeyboardEvent) => {
        if (!event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key === back && index > 0) commit(index, index - 1);
        else if (event.key === forward && index < items.length - 1) commit(index, index + 1);
        else return;
        event.preventDefault();
        // Moving an element in the document drops its focus; give it back so the next key press continues.
        const target = event.target as HTMLElement;
        globalThis.requestAnimationFrame(() => target.focus());
      },
    };
  }
  return { itemProps, dragging, announcement };
}
