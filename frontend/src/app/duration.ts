import { formatNumber, parseDecimal } from '../i18n/format.ts';

// Durations of time entries (TASK-069, rule 2): whole minutes from 1 to 1440. The input accepts "1:15", "75m", "1,25 h"
// (decimal in the notation of the language) and "1h 15m"; a bare number is refused, because it could mean hours or minutes.
export const MAX_MINUTES = 1440;

const UNIT_HOURS = /^(h|std\.?|stunden?|hours?)$/i;
const UNIT_MINUTES = /^(m|min\.?|minuten?|minutes?)$/i;

export function parseDuration(text: string, language: string): number | null {
  const input = text.trim();
  if (input === '') return null;
  const clock = /^(\d{1,2}):([0-5]\d)$/.exec(input);
  if (clock !== null) return valid(Number(clock[1]) * 60 + Number(clock[2]));
  // Pairs of a number and a unit, for example "1h 15m", "75 min" or "1,25 Std."
  const parts = input.match(/[0-9.,]+\s*[A-Za-zäöüÄÖÜ.]+/g);
  if (parts === null || parts.join('').replace(/\s+/g, '') !== input.replace(/\s+/g, '')) return null;
  let minutes = 0;
  const seen = new Set<string>();
  for (const part of parts) {
    const match = /^([0-9.,]+)\s*(.+)$/.exec(part.trim());
    if (match === null) return null;
    const value = parseDecimal(match[1] ?? '', language);
    const unit = (match[2] ?? '').trim();
    if (value === null || value < 0) return null;
    const kind = UNIT_HOURS.test(unit) ? 'h' : UNIT_MINUTES.test(unit) ? 'm' : null;
    if (kind === null || seen.has(kind)) return null;
    seen.add(kind);
    minutes += kind === 'h' ? value * 60 : value;
  }
  return valid(Math.round(minutes));
}

function valid(minutes: number): number | null {
  return Number.isInteger(minutes) && minutes >= 1 && minutes <= MAX_MINUTES ? minutes : null;
}

// "1 Std. 15 Min." in German, "1 h 15 min" in English; the same minutes in every language.
export function formatMinutes(minutes: number, language: string): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const [h, m] = language === 'de' ? ['Std.', 'Min.'] : ['h', 'min'];
  if (hours === 0) return `${rest} ${m}`;
  if (rest === 0) return `${formatNumber(hours, language, 0)} ${h}`;
  return `${formatNumber(hours, language, 0)} ${h} ${rest} ${m}`;
}

// Hours with two decimals, rounded half up as in the export: 75 minutes are 1,25.
export function formatHours(minutes: number, language: string): string {
  const hundredths = Math.floor((minutes * 100) / 60 + 0.5 + 1e-9);
  return new Intl.NumberFormat(language, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(hundredths / 100);
}

// The input for a known duration: "1:15".
export function durationInput(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;
}
