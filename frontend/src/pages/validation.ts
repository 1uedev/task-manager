import { parseDecimal } from '../i18n/format.ts';
import type { MessageKey } from '../i18n/index.ts';

export const LABEL_MAX = 255;
export const HOURS_MIN = 0.25;
export const HOURS_MAX = 24;

export type FieldName = 'label' | 'hours';
export interface FieldError {
  readonly key: MessageKey;
  readonly params: Readonly<Record<string, string | number>>;
}

export type Validation =
  | { readonly ok: true; readonly label: string; readonly hours: number }
  | { readonly ok: false; readonly errors: Readonly<Partial<Record<FieldName, FieldError>>> };

// Rules are language independent; only the notation of the number depends on the selected language.
export function validateSample(input: { readonly label: string; readonly hours: string }, language: string): Validation {
  const errors: Partial<Record<FieldName, FieldError>> = {};
  const label = input.label.trim();
  if (label === '') errors.label = { key: 'form.error.required', params: {} };
  else if (label.length > LABEL_MAX) errors.label = { key: 'form.error.tooLong', params: { max: LABEL_MAX } };

  let hours = 0;
  if (input.hours.trim() === '') errors.hours = { key: 'form.error.required', params: {} };
  else {
    const parsed = parseDecimal(input.hours, language);
    if (parsed === null) errors.hours = { key: 'form.error.notANumber', params: {} };
    else if (parsed < HOURS_MIN || parsed > HOURS_MAX) {
      errors.hours = { key: 'form.error.outOfRange', params: { min: HOURS_MIN, max: HOURS_MAX } };
    } else hours = parsed;
  }
  return Object.keys(errors).length > 0 ? { ok: false, errors } : { ok: true, label, hours };
}
