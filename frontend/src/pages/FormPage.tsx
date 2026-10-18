import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { formatNumber } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { HOURS_MAX, HOURS_MIN, LABEL_MAX, validateSample, type FieldName, type Validation } from './validation.ts';

type Outcome = Validation | null;

export function FormPage() {
  const { t, language } = useI18n();
  const ids = { label: useId(), hours: useId() };
  const [values, setValues] = useState({ label: '', hours: '' });
  const [outcome, setOutcome] = useState<Outcome>(null);
  const summary = useRef<HTMLDivElement>(null);
  const success = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (outcome?.ok === false) summary.current?.focus();
    if (outcome?.ok === true) success.current?.focus();
  }, [outcome]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setOutcome(validateSample(values, language));
  }

  const errors = outcome?.ok === false ? outcome.errors : {};
  const errorFields = (Object.keys(errors) as FieldName[]).filter((name) => errors[name] !== undefined);
  const hintId = (field: FieldName) => `${ids[field]}-hint`;
  const errorId = (field: FieldName) => `${ids[field]}-error`;
  const describedBy = (field: FieldName) => (errors[field] ? `${hintId(field)} ${errorId(field)}` : hintId(field));

  return (
    <>
      <h1 tabIndex={-1}>{t('form.title')}</h1>
      <p>{t('form.intro')}</p>

      {errorFields.length > 0 && (
        <div ref={summary} tabIndex={-1} role="alert" className="error-summary" data-testid="error-summary">
          <h2>{t('form.error.summary', { count: errorFields.length })}</h2>
          <ul>
            {errorFields.map((field) => (
              <li key={field}>
                <a
                  href={`#${ids[field]}`}
                  onClick={(event) => {
                    event.preventDefault();
                    document.getElementById(ids[field])?.focus();
                  }}
                >
                  {t(field === 'label' ? 'form.label.label' : 'form.hours.label')}
                </a>
                {': '}
                {t(errors[field]!.key, errors[field]!.params)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {outcome?.ok === true && (
        <div ref={success} tabIndex={-1} role="status" className="success" data-testid="success">
          <h2>{t('form.success.title')}</h2>
          <p>{t('form.success.text', { label: outcome.label, hours: formatNumber(outcome.hours, language) })}</p>
        </div>
      )}

      <form noValidate onSubmit={submit}>
        <div className="field">
          <label htmlFor={ids.label}>
            {t('form.label.label')} <span className="required">({t('form.required')})</span>
          </label>
          <p id={hintId('label')} className="hint">{t('form.label.hint', { max: LABEL_MAX })}</p>
          {errors.label && (
            <p id={errorId('label')} className="field-error">
              <span aria-hidden="true">⚠ </span>
              <strong>{t('form.error.prefix')}:</strong> {t(errors.label.key, errors.label.params)}
            </p>
          )}
          <input
            id={ids.label}
            name="label"
            type="text"
            autoComplete="off"
            aria-required="true"
            aria-invalid={errors.label ? 'true' : undefined}
            aria-describedby={describedBy('label')}
            value={values.label}
            onChange={(event) => setValues({ ...values, label: event.target.value })}
          />
        </div>

        <div className="field">
          <label htmlFor={ids.hours}>
            {t('form.hours.label')} <span className="required">({t('form.required')})</span>
          </label>
          <p id={hintId('hours')} className="hint">
            {t('form.hours.hint', { min: HOURS_MIN, max: HOURS_MAX, example: formatNumber(1.5, language) })}
          </p>
          {errors.hours && (
            <p id={errorId('hours')} className="field-error">
              <span aria-hidden="true">⚠ </span>
              <strong>{t('form.error.prefix')}:</strong> {t(errors.hours.key, errors.hours.params)}
            </p>
          )}
          <input
            id={ids.hours}
            name="hours"
            type="text"
            inputMode="decimal"
            autoComplete="off"
            aria-required="true"
            aria-invalid={errors.hours ? 'true' : undefined}
            aria-describedby={describedBy('hours')}
            value={values.hours}
            onChange={(event) => setValues({ ...values, hours: event.target.value })}
          />
        </div>

        <button type="submit">{t('form.submit')}</button>
      </form>
    </>
  );
}
