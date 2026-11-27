// Where a proposal of the AI review of a requirement may go into the editor (TASK-127, rule 1). Nothing here saves: the result
// only prepares the editor, and the person saves with the normal save. A proposal that cannot be placed without doubt is only
// offered for copying.

export interface Finding {
  readonly kind: string;
  readonly location: string;
  readonly reason: string;
  readonly suggestion: string | null;
  readonly sources: readonly string[];
}

export interface ReviewedContent {
  readonly title: string | null;
  readonly criteria: readonly { readonly id: number; readonly text: string }[] | null;
  readonly description: unknown;
}

export type Proposal =
  | { readonly kind: 'newCriterion'; readonly text: string }
  | { readonly kind: 'title'; readonly text: string }
  | { readonly kind: 'criterion'; readonly id: number; readonly text: string }
  | { readonly kind: 'description'; readonly document: unknown };

const TITLE_MAX = 200;
const CRITERION_MAX = 2000;
const KINDS = new Set(['unclear', 'contradiction', 'missing_criterion', 'not_measurable', 'other']);

// The findings of a suggestion, each checked for its form; anything else is left out.
export function findingsOf(content: Record<string, unknown> | null): Finding[] {
  const list = content?.findings;
  if (!Array.isArray(list)) return [];
  return list.filter((item): item is Finding => typeof item === 'object' && item !== null
    && KINDS.has((item as Finding).kind) && typeof (item as Finding).location === 'string' && typeof (item as Finding).reason === 'string'
    && ((item as Finding).suggestion === null || typeof (item as Finding).suggestion === 'string') && Array.isArray((item as Finding).sources));
}

function occurrences(text: string, part: string): number {
  let count = 0;
  for (let at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length)) count += 1;
  return count;
}

interface TextNode { text: string }

// Every text node of a stored document, in order; the copy is changed in place.
function textNodes(node: unknown, into: TextNode[]): TextNode[] {
  if (typeof node !== 'object' || node === null) return into;
  const value = node as { type?: unknown; text?: unknown; content?: unknown };
  if (value.type === 'text' && typeof value.text === 'string') into.push(value as TextNode);
  if (Array.isArray(value.content)) value.content.forEach((child) => textNodes(child, into));
  return into;
}

/**
 * The place of a proposal in the editor: a missing criterion becomes a new criterion; otherwise the proposal replaces the
 * passage when it stands exactly once in title, criteria and the text of the description together and that field is writable.
 * Null means only copying is offered.
 */
export function proposalFor(finding: Finding, content: ReviewedContent, writable: (field: string) => boolean): Proposal | null {
  const suggestion = finding.suggestion?.trim() ?? '';
  if (suggestion === '') return null;
  if (finding.kind === 'missing_criterion') {
    return writable('criteria') && content.criteria !== null && suggestion.length <= CRITERION_MAX ? { kind: 'newCriterion', text: suggestion } : null;
  }
  const passage = finding.location.trim();
  if (passage === '') return null;
  const title = content.title ?? '';
  const criteria = content.criteria ?? [];
  const description = content.description === null || content.description === undefined ? null : structuredClone(content.description);
  const nodes = description === null ? [] : textNodes(description, []);
  const counts = { title: occurrences(title, passage), criteria: criteria.map((c) => occurrences(c.text, passage)),
    description: nodes.map((n) => occurrences(n.text, passage)) };
  const total = counts.title + counts.criteria.reduce((a, b) => a + b, 0) + counts.description.reduce((a, b) => a + b, 0);
  if (total !== 1) return null;
  if (counts.title === 1) {
    const text = title.replace(passage, () => suggestion);
    return writable('title') && text.trim() !== '' && text.length <= TITLE_MAX ? { kind: 'title', text } : null;
  }
  const criterion = counts.criteria.indexOf(1);
  if (criterion >= 0) {
    const item = criteria[criterion]!;
    const text = item.text.replace(passage, () => suggestion);
    return writable('criteria') && text.trim() !== '' && text.length <= CRITERION_MAX ? { kind: 'criterion', id: item.id, text } : null;
  }
  const node = nodes[counts.description.indexOf(1)]!;
  if (!writable('description') || suggestion.includes('\n')) return null;
  node.text = node.text.replace(passage, () => suggestion);
  return { kind: 'description', document: description };
}
