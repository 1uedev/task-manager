import { optionsOf, type CustomField } from '../app/configuration.ts';
import { SelectField, TextField } from '../app/ui.tsx';
import { formatDate } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import type { MessageKey } from '../i18n/index.ts';

// One custom field of a ticket as a form control and as plain text (TASK-044). The value is always the text of the control;
// the page turns it into the JSON value of the field's kind when it sends it.

export interface Person {
  readonly id: string;
  readonly name: string;
}

export function CustomFieldInput({ field, value, onChange, people, error = null, disabled = false }: {
  readonly field: CustomField;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly people: readonly Person[];
  readonly error?: string | null;
  readonly disabled?: boolean;
}) {
  const { t } = useI18n();
  const label = field.name;
  switch (field.type) {
    case 'longtext':
      return <TextField label={label} value={value} onChange={onChange} multiline maxLength={20000} error={error} disabled={disabled} />;
    case 'integer':
      return <TextField label={label} value={value} onChange={onChange} hint={t('custom.integerHint')} error={error} disabled={disabled} />;
    case 'date':
      return <TextField label={label} value={value} onChange={onChange} type="date" error={error} disabled={disabled} />;
    case 'boolean':
      return <SelectField label={label} value={value} onChange={onChange} disabled={disabled}
        options={[{ value: '', label: t('field.empty') }, { value: 'true', label: t('custom.yes') }, { value: 'false', label: t('custom.no') }]} />;
    case 'select': {
      // An inactive option stays in the list only for the ticket that holds it, so nobody can pick it for another one.
      const options = optionsOf(field).filter((option) => option.active || option.key === value);
      return <SelectField label={label} value={value} onChange={onChange} disabled={disabled}
        options={[{ value: '', label: t('field.empty') }, ...options.map((option) => ({ value: option.key, label: option.active ? option.name : t('custom.inactiveOption', { name: option.name }) }))]} />;
    }
    case 'user':
      return <SelectField label={label} value={value} onChange={onChange} disabled={disabled}
        options={[{ value: '', label: t('field.nobody') }, ...people.map((person) => ({ value: person.id, label: person.name }))]} />;
    default:
      return <TextField label={label} value={value} onChange={onChange} maxLength={200} error={error} disabled={disabled} />;
  }
}

/** The value of a custom field as text for reading: option names, account names, dates in the notation of the language. */
export function useCustomText(): (field: CustomField, value: string | number | boolean | undefined, name: (id: string) => string) => string {
  const { t, language } = useI18n();
  return (field, value, name) => {
    if (value === undefined || value === '') return t('field.empty');
    switch (field.type) {
      case 'boolean':
        return value === true || value === 'true' ? t('custom.yes') : t('custom.no');
      case 'select':
        return optionsOf(field).find((option) => option.key === value)?.name ?? String(value);
      case 'user':
        return name(String(value));
      case 'date':
        return formatDate(String(value), language);
      default:
        return String(value);
    }
  };
}

export const CUSTOM_ERROR: Readonly<Record<string, MessageKey>> = {
  invalid: 'custom.error.invalid',
  required: 'custom.error.required',
  inactive: 'custom.error.inactive',
  option_inactive: 'custom.error.optionInactive',
  not_member: 'custom.error.notMember',
  required_for_transition: 'custom.error.requiredForTransition',
};
