// The only browser storage use of the application: the chosen display language.
// No session data, tokens or business data may be stored here (checked by tests/static.test.ts).

const KEY = 'taskmanager.language';

export function readStoredLanguage(): string | null {
  try {
    return globalThis.localStorage?.getItem(KEY) ?? null;
  } catch {
    return null;
  }
}

export function storeLanguage(language: string): void {
  try {
    globalThis.localStorage?.setItem(KEY, language);
  } catch {
    // Storage may be blocked; the choice then only lasts for this page view.
  }
}
