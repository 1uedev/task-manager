import type { Configuration } from '../api/product.ts';

// The rules of a project configuration the interface needs (TASK-044). They only read the configuration the server sent;
// whether a change is allowed stays with the server, which checks everything again.

export type CustomField = NonNullable<Configuration['customFields']>[number];
export type CustomOption = NonNullable<CustomField['options']>[number];
export type Transition = Configuration['transitions'][number];
export type Workflow = NonNullable<Configuration['workflows']>[number];
export type FieldKind = CustomField['type'];

export const CUSTOM_PREFIX = 'custom.';
export const FIELD_KINDS: readonly FieldKind[] = ['text', 'longtext', 'integer', 'date', 'boolean', 'select', 'user'];

export const customFieldsOf = (config: Configuration): readonly CustomField[] => config.customFields ?? [];
export const workflowsOf = (config: Configuration): readonly Workflow[] => config.workflows ?? [];
export const optionsOf = (field: CustomField): readonly CustomOption[] => field.options ?? [];

/** The workflow a ticket type follows: its own entry, or the one all other types share. */
export function workflowOf(config: Configuration, type: string): { readonly initialStatus: string; readonly transitions: readonly Transition[]; readonly own: boolean } {
  const own = workflowsOf(config).find((workflow) => workflow.type === type);
  return own === undefined
    ? { initialStatus: config.initialStatus, transitions: config.transitions, own: false }
    : { initialStatus: own.initialStatus, transitions: own.transitions, own: true };
}

/** The statuses a ticket of the type can have: all of them, or for a type with its own workflow those it names. */
export function statusKeysOf(config: Configuration, type: string): readonly string[] {
  const workflow = workflowOf(config, type);
  if (!workflow.own) return config.statuses.map((status) => status.key);
  const keys = new Set<string>([workflow.initialStatus]);
  for (const transition of workflow.transitions) {
    keys.add(transition.from);
    keys.add(transition.to);
  }
  return config.statuses.map((status) => status.key).filter((key) => keys.has(key));
}

/** The statuses a ticket may be moved to from its status: the status itself and the targets of its transitions. */
export function nextStatuses(config: Configuration, type: string, status: string): readonly string[] {
  const targets = workflowOf(config, type).transitions.filter((transition) => transition.from === status).map((transition) => transition.to);
  return [status, ...targets.filter((target) => target !== status)];
}

/** The fields a step requires, as the identifiers the server uses (`description`, `assignee`, `custom.<key>`). */
export function requiredFor(config: Configuration, type: string, from: string, to: string): readonly string[] {
  return workflowOf(config, type).transitions.find((transition) => transition.from === from && transition.to === to)?.requiredFields ?? [];
}

const KEY = /^[a-z][a-z0-9_]{0,39}$/;
export const isKey = (value: string): boolean => KEY.test(value);

/** A key for a new type, status, field or option from its name: lower case letters, digits and underscores, unique among {@code taken}. */
export function keyFromName(name: string, taken: readonly string[]): string {
  let base = name.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/ß/g, 'ss').replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  if (base === '' || !/^[a-z]/.test(base)) base = `f_${base}`.replace(/_$/, '');
  base = base.slice(0, 36).replace(/_$/, '');
  if (!taken.includes(base)) return base;
  for (let n = 2; ; n++) {
    const candidate = `${base}_${n}`;
    if (!taken.includes(candidate)) return candidate;
  }
}

// ---------------------------------------------------------------- values of custom fields in forms

export type CustomValue = string | number | boolean;

/** The text a form control shows for the value the server sent. */
export function formText(value: CustomValue | undefined): string {
  return value === undefined ? '' : String(value);
}

/**
 * The JSON value to send for the text of a form control: {@code null} when the control is empty, which clears the field.
 * {@code undefined} says the text is not a valid value of the kind, so the form shows an error and sends nothing.
 */
export function sendValue(kind: FieldKind, text: string): CustomValue | null | undefined {
  const trimmed = kind === 'longtext' ? text : text.trim();
  if (trimmed.trim() === '') return null;
  switch (kind) {
    case 'integer':
      return /^-?(0|[1-9][0-9]{0,15})$/.test(trimmed) && Number.isSafeInteger(Number(trimmed)) ? Number(trimmed) : undefined;
    case 'boolean':
      return trimmed === 'true' ? true : trimmed === 'false' ? false : undefined;
    case 'date':
      return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) && !Number.isNaN(Date.parse(`${trimmed}T00:00:00Z`)) && new Date(`${trimmed}T00:00:00Z`).toISOString().startsWith(trimmed)
        ? trimmed : undefined;
    default:
      return trimmed;
  }
}

/** The reason of a violation the server names: the field it concerns and what is wrong, or null for a code of another shape. */
export function parseViolation(code: string): { readonly field: string; readonly reason: string; readonly subject: string | null } | null {
  const [head, subject] = code.split(':');
  const match = /^(custom\.[a-z][a-z0-9_]*|[a-z][A-Za-z]*)\.([a-z_]+)$/.exec(head ?? '');
  return match === null ? null : { field: match[1] ?? '', reason: match[2] ?? '', subject: subject ?? null };
}

/** One choice of the mapping: a status for all its tickets, or for the tickets of one type as `<type>:<status>`. */
export interface MappingRow {
  readonly key: string;
  readonly status: string;
  readonly type: string | null;
  readonly targets: readonly string[];
}

/**
 * The choices for the statuses a publication named (review F4 of TASK-044). A status that tickets of several types still
 * use and that their new workflows no longer know gets one choice per type, since one target may not exist for every type.
 */
export function mappingRows(statuses: readonly string[], usage: readonly { readonly type: string; readonly status: string }[], config: Configuration): MappingRow[] {
  const all = config.statuses.map((item) => item.key);
  return statuses.flatMap((status): MappingRow[] => {
    const types = [...new Set(usage.filter((use) => use.status === status && config.ticketTypes.some((item) => item.key === use.type)
      && !statusKeysOf(config, use.type).includes(status)).map((use) => use.type))];
    if (types.length <= 1) {
      return [{ key: status, status, type: null, targets: types.length === 1 ? statusKeysOf(config, types[0]!) : all }];
    }
    return types.map((type) => ({ key: `${type}:${status}`, status, type, targets: statusKeysOf(config, type) }));
  });
}
