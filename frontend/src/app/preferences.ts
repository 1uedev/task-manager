// The only browser storage use of the application: personal display preferences of this browser, namely the
// language, light or dark appearance and the order of navigation entries and start page cards.
// No session data, tokens, company settings or business data may be stored here (checked by tests/static.test.ts).

const KEYS = {
  language: 'taskmanager.language',
  theme: 'taskmanager.theme',
  navigation: 'taskmanager.order.navigation',
  cards: 'taskmanager.order.cards',
} as const;

export type PreferenceName = keyof typeof KEYS;
export type OrderName = 'navigation' | 'cards';

function read(name: PreferenceName): string | null {
  try {
    return globalThis.localStorage?.getItem(KEYS[name]) ?? null;
  } catch {
    return null;
  }
}

function write(name: PreferenceName, value: string): void {
  try {
    globalThis.localStorage?.setItem(KEYS[name], value);
  } catch {
    // Storage may be blocked; the choice then only lasts for this page view.
  }
}

export function readStoredLanguage(): string | null {
  return read('language');
}

export function storeLanguage(language: string): void {
  write('language', language);
}

export function readStoredTheme(): string | null {
  return read('theme');
}

export function storeTheme(theme: string): void {
  write('theme', theme);
}

// A stored order is untrusted text: unknown entries are dropped, duplicates removed and entries added later
// appended, so a stale or edited value can never hide a page or a card.
export function restoreOrder<T extends string>(stored: string | null, known: readonly T[]): T[] {
  let parsed: unknown = null;
  try {
    parsed = stored === null ? null : JSON.parse(stored);
  } catch {
    parsed = null;
  }
  const result: T[] = [];
  if (Array.isArray(parsed)) {
    for (const item of parsed) {
      const match = known.find((candidate) => candidate === item);
      if (match !== undefined && !result.includes(match)) result.push(match);
    }
  }
  for (const item of known) if (!result.includes(item)) result.push(item);
  return result;
}

export function readStoredOrder<T extends string>(name: OrderName, known: readonly T[]): T[] {
  return restoreOrder(read(name), known);
}

export function storeOrder(name: OrderName, order: readonly string[]): void {
  write(name, JSON.stringify(order));
}
