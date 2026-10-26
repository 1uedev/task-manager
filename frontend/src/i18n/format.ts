// Locale dependent presentation. Stored and exchanged values stay ISO 8601 / plain numbers.

export function formatDateTime(instant: Date | string, language: string, timeZone?: string): string {
  const date = typeof instant === 'string' ? new Date(instant) : instant;
  const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
  if (timeZone !== undefined) options.timeZone = timeZone;
  return new Intl.DateTimeFormat(language, options).format(date);
}

// A calendar date (yyyy-MM-dd) has no time zone; it is shown as the same day everywhere.
export function formatDate(date: string, language: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (match === null) return date;
  const instant = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeZone: 'UTC' }).format(instant);
}

// A size in bytes as bytes, kB or MB (decimal, as the limit of the server is counted).
export function formatBytes(bytes: number, language: string): string {
  if (bytes < 1000) return `${formatNumber(bytes, language, 0)} B`;
  if (bytes < 1_000_000) return `${formatNumber(bytes / 1000, language, 1)} kB`;
  return `${formatNumber(bytes / 1_000_000, language, 1)} MB`;
}

export function formatNumber(value: number, language: string, maximumFractionDigits = 2): string {
  return new Intl.NumberFormat(language, { maximumFractionDigits }).format(value);
}

function escapeForClass(text: string): string {
  return text.replace(/[\\\]^-]/g, '\\$&');
}

const SPACES = [' ', '\u00a0', '\u202f'];

// Parses user input in the notation of the selected language. Only the separators of that language are
// accepted, and grouping only in complete groups of three digits. Everything else is rejected instead of
// being repaired: "1 5" or "1.5" in a language that groups with "." must never turn into 15.
export function parseDecimal(text: string, language: string): number | null {
  const parts = new Intl.NumberFormat(language).formatToParts(12345.6);
  const decimal = parts.find((part) => part.type === 'decimal')?.value ?? '.';
  const group = parts.find((part) => part.type === 'group')?.value ?? ',';
  // Languages that group with a space accept any of the usual space characters between the groups.
  const groups = group.trim() === '' ? SPACES : [group];
  const g = `[${groups.map(escapeForClass).join('')}]`;
  const d = `[${escapeForClass(decimal)}]`;
  const pattern = new RegExp(`^[+-]?(?:\\d{1,3}(?:${g}\\d{3})+|\\d+)(?:${d}\\d+)?$`);
  const trimmed = text.trim();
  if (!pattern.test(trimmed)) return null;
  const value = Number(trimmed.replace(new RegExp(g, 'g'), '').replace(decimal, '.'));
  return Number.isFinite(value) ? value : null;
}
