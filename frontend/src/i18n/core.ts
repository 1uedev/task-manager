// Framework free translation core. Texts are separated from logic: components only pass keys.
// Fallback rule: selected language, then English, then the key itself (reported through onMissing).

export type PluralMessage = Partial<Record<Intl.LDMLPluralRule, string>> & { readonly other: string };
export type Message = string | PluralMessage;
export type Catalog = Readonly<Record<string, Message>>;
export type Params = Readonly<Record<string, string | number>>;
export type Registry = Readonly<Record<string, Catalog>>;

export const FALLBACK_LANGUAGE = 'en';
export const DEFAULT_LANGUAGE = 'de';

export type MissingReporter = (language: string, key: string) => void;

function lookup(registry: Registry, language: string, key: string): Message | undefined {
  const catalog = registry[language];
  if (!catalog) return undefined;
  return Object.hasOwn(catalog, key) ? catalog[key] : undefined;
}

function interpolate(text: string, language: string, params: Params): string {
  return text.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
    const value = params[name];
    if (value === undefined) return placeholder;
    return typeof value === 'number' ? new Intl.NumberFormat(language).format(value) : value;
  });
}

function pick(message: Message, language: string, params: Params): string {
  if (typeof message === 'string') return message;
  const count = params.count;
  if (typeof count !== 'number') return message.other;
  const category = new Intl.PluralRules(language).select(count);
  return message[category] ?? message.other;
}

export function translate(
  registry: Registry,
  language: string,
  key: string,
  params: Params = {},
  onMissing?: MissingReporter,
): string {
  const own = lookup(registry, language, key);
  if (own !== undefined) return interpolate(pick(own, language, params), language, params);
  onMissing?.(language, key);
  const fallback = lookup(registry, FALLBACK_LANGUAGE, key);
  if (fallback !== undefined) return interpolate(pick(fallback, FALLBACK_LANGUAGE, params), FALLBACK_LANGUAGE, params);
  return key;
}

export function resolveLanguage(
  stored: string | null,
  browserLanguages: readonly string[],
  supported: readonly string[],
  fallback: string = DEFAULT_LANGUAGE,
): string {
  if (stored !== null && supported.includes(stored)) return stored;
  for (const candidate of browserLanguages) {
    const base = candidate.toLowerCase().split('-')[0] ?? '';
    if (supported.includes(base)) return base;
  }
  return fallback;
}

export function placeholders(message: Message): string[] {
  const texts = typeof message === 'string' ? [message] : Object.values(message);
  const found = new Set<string>();
  for (const text of texts) for (const match of text.matchAll(/\{(\w+)\}/g)) found.add(match[1] ?? '');
  return [...found].sort();
}
