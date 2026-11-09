// Comparison of two wiki documents by blocks (TASK-093, DEC-123): the history compares two revisions, the conflict of a save
// compares the own unsaved input with the newer draft of someone else. A block is one element at the top of the document; it
// counts as unchanged when its kind and its text are the same. A removed block directly followed by an added one at the same
// place is shown as changed. The comparison never merges anything; it only shows.

export interface DocNode {
  readonly type?: string;
  readonly text?: string;
  readonly attrs?: Readonly<Record<string, unknown>>;
  readonly content?: readonly DocNode[];
}

export interface Block {
  readonly kind: string;
  readonly text: string;
}

export type DiffEntry =
  | { readonly op: 'same'; readonly block: Block }
  | { readonly op: 'added'; readonly block: Block }
  | { readonly op: 'removed'; readonly block: Block }
  | { readonly op: 'changed'; readonly before: Block; readonly after: Block };

function plain(node: DocNode): string {
  if (node.type === 'text') return node.text ?? '';
  if (node.type === 'hardBreak') return '\n';
  if (node.type === 'image') return `[${String(node.attrs?.alt ?? '')}]`;
  if (node.type === 'horizontalRule') return '---';
  const children = node.content ?? [];
  const separator = node.type === 'tableRow' ? ' | ' : ['bulletList', 'orderedList', 'table', 'blockquote', 'listItem', 'tableCell', 'tableHeader'].includes(node.type ?? '') ? '\n' : '';
  return children.map(plain).join(separator);
}

function kind(node: DocNode): string {
  if (node.type === 'heading') return `heading${String(node.attrs?.level ?? 1)}`;
  return node.type ?? 'unknown';
}

/** The blocks of a document in their order. */
export function blocksOf(doc: DocNode | null | undefined): Block[] {
  return (doc?.content ?? []).map((node) => ({ kind: kind(node), text: plain(node) }));
}

const same = (a: Block, b: Block) => a.kind === b.kind && a.text === b.text;

/** The difference from `before` to `after` by the longest common sequence of blocks. */
export function diffBlocks(before: readonly Block[], after: readonly Block[]): DiffEntry[] {
  const n = before.length;
  const m = after.length;
  const table: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      table[i]![j] = same(before[i]!, after[j]!) ? table[i + 1]![j + 1]! + 1 : Math.max(table[i + 1]![j]!, table[i]![j + 1]!);
    }
  }
  const raw: DiffEntry[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (same(before[i]!, after[j]!)) {
      raw.push({ op: 'same', block: after[j]! });
      i++;
      j++;
    } else if (table[i + 1]![j]! >= table[i]![j + 1]!) {
      raw.push({ op: 'removed', block: before[i]! });
      i++;
    } else {
      raw.push({ op: 'added', block: after[j]! });
      j++;
    }
  }
  while (i < n) raw.push({ op: 'removed', block: before[i++]! });
  while (j < m) raw.push({ op: 'added', block: after[j++]! });
  const result: DiffEntry[] = [];
  for (let k = 0; k < raw.length; k++) {
    const entry = raw[k]!;
    const next = raw[k + 1];
    if (entry.op === 'removed' && next?.op === 'added') {
      result.push({ op: 'changed', before: entry.block, after: next.block });
      k++;
    } else {
      result.push(entry);
    }
  }
  return result;
}

/** Whether two documents differ in any block. */
export function differs(a: DocNode | null | undefined, b: DocNode | null | undefined): boolean {
  return diffBlocks(blocksOf(a), blocksOf(b)).some((entry) => entry.op !== 'same');
}
