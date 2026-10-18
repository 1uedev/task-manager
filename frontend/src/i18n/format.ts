// Locale dependent presentation. Stored and exchanged values stay ISO 8601 / plain numbers.

export function formatDateTime(instant: Date | string, language: string, timeZone?: string): string {
  const date = typeof instant === 'string' ? new Date(instant) : instant;
  const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
  if (timeZone !== undefined) options.timeZone = timeZone;
  return new Intl.DateTimeFormat(language, options).format(date);
}

export function formatNumber(value: number, language: string, maximumFractionDigits = 2): string {
  return new Intl.NumberFormat(language, { maximumFractionDigits }).format(value);
}

function escapeForRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Parses user input in the notation of the selected language. Ambiguous input such as "1.5" in a
// language that groups with "." is rejected instead of being read as 15.
export function parseDecimal(text: string, language: string): number | null {
  const parts = new Intl.NumberFormat(language).formatToParts(12345.6);
  const decimal = parts.find((part) => part.type === 'decimal')?.value ?? '.';
  const group = parts.find((part) => part.type === 'group')?.value ?? ',';
  const normalised = text.trim().replace(/[\s  ]/g, group.trim() === '' ? ' ' : '');
  const d = escapeForRegExp(decimal);
  const g = escapeForRegExp(group.trim() === '' ? ' ' : group);
  const pattern = new RegExp(`^[+-]?(?:\\d{1,3}(?:${g}\\d{3})+|\\d+)(?:${d}\\d+)?$`);
  if (!pattern.test(normalised)) return null;
  const canonical = normalised.split(group.trim() === '' ? ' ' : group).join('').replace(decimal, '.');
  const value = Number(canonical);
  return Number.isFinite(value) ? value : null;
}
