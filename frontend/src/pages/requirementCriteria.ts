// The criteria of a requirement in the editor and the taking over of the own input after a version conflict (TASK-096).
import type { RequirementCriterion } from '../api/product.ts';

/** One criterion in the editor: `id` is its fixed number, `null` for a new one; `key` only tells the rows apart. */
export interface Draft {
  readonly key: number;
  readonly id: number | null;
  readonly text: string;
}

/**
 * "Meine Eingaben behalten" for the criteria (F1 of the TASK-096 review): the own changes are found per fixed number against the
 * state the input started from, everything else comes from the newer state. An own text or removal wins over the other one;
 * an own order wins, the criteria the other person added follow it; own new criteria stay at their place. A criterion the person changed
 * that the other person removed meanwhile stays as a new one, so the typed text is not lost.
 */
export function mergeCriteria(base: readonly RequirementCriterion[] | null, mine: readonly Draft[],
  theirs: readonly RequirementCriterion[] | null, next: () => number): Draft[] {
  if (base === null || theirs === null) return [...mine];
  const original = new Map(base.map((criterion) => [criterion.id, criterion.text]));
  const own = new Map(mine.flatMap((draft) => (draft.id === null ? [] : [[draft.id, draft] as const])));
  const theirIds = new Set(theirs.map((criterion) => criterion.id));
  const removedByMe = (id: number) => original.has(id) && !own.has(id);
  const textOf = (id: number, text: string) => {
    const draft = own.get(id);
    return draft !== undefined && draft.text !== original.get(id) ? draft.text : text;
  };
  const merged = new Map<number, Draft>();
  for (const criterion of theirs) {
    if (!removedByMe(criterion.id)) merged.set(criterion.id, { key: next(), id: criterion.id, text: textOf(criterion.id, criterion.text) });
  }
  const orphan = (draft: Draft) => draft.id !== null && !theirIds.has(draft.id) && draft.text !== original.get(draft.id);
  const order = (ids: readonly (number | null)[]) => ids.filter((id) => id !== null && own.has(id)).join(',');
  const reordered = order(base.map((criterion) => criterion.id)) !== order(mine.map((draft) => draft.id));
  const result: Draft[] = [];
  if (reordered) {
    for (const draft of mine) {
      const taken = draft.id === null ? undefined : merged.get(draft.id);
      if (draft.id === null) result.push(draft);
      else if (taken !== undefined) {
        result.push(taken);
        merged.delete(draft.id);
      } else if (orphan(draft)) result.push({ key: next(), id: null, text: draft.text });
    }
    result.push(...merged.values());
    return result;
  }
  // The other order stays; an own new criterion keeps its place behind the numbered criterion it followed in the own list,
  // or stays in front when it came first (R1 of the follow up review of TASK-096).
  const behind = new Map<number | null, Draft[]>();
  let anchor: number | null = null;
  for (const draft of mine) {
    if (draft.id !== null && merged.has(draft.id)) anchor = draft.id;
    const added = draft.id === null ? draft : orphan(draft) ? { key: next(), id: null, text: draft.text } : null;
    if (added !== null) behind.set(anchor, [...(behind.get(anchor) ?? []), added]);
  }
  result.push(...(behind.get(null) ?? []));
  for (const [id, draft] of merged) result.push(draft, ...(behind.get(id) ?? []));
  return result;
}
