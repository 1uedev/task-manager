import { useState } from 'react';
import { setGroupAction, setGroupField, type ProjectRights, type Result, type TicketField } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { EmptyText, NoticeView, SelectField, useProblemText, type Notice } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { ACTION_LABEL, FIELD_LABEL } from './GroupsPage.tsx';

type Answer = Promise<Result<unknown>>;

// The rights of one project for its administration (TASK-043): every group, but only its rules in this project, and only
// what the person may hand out here. The server checks every change again.
export function ProjectRightsTab({ projectId, rights, reload }: {
  readonly projectId: string;
  readonly rights: ProjectRights;
  readonly reload: () => void;
}) {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [groupId, setGroupId] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const group = rights.groups.find((item) => item.id === groupId) ?? null;
  const fields = rights.fields.find((item) => item.resourceType === 'ticket')?.fields ?? [];

  async function apply(call: () => Answer) {
    setBusy(true);
    setNotice(null);
    const result = await call();
    setBusy(false);
    reload();
    setNotice(result.kind === 'ok' ? { kind: 'success', text: t('rights.saved') } : { kind: 'info', text: text(result.problem) });
  }

  const rule = (field: string, operation: 'read' | 'write') =>
    group?.fields.find((item) => item.field === field && item.operation === operation)?.effect ?? '';

  return (
    <section aria-labelledby="project-rights" className="panel">
      <h2 id="project-rights" className="visually-hidden">{t('project.tab.rights')}</h2>
      <p className="hint">{t('rights.intro')}</p>
      <NoticeView notice={notice} />
      {rights.actions.length === 0 && fields.length === 0 ? <EmptyText text={t('rights.nothing')} /> : (
        <>
          <SelectField label={t('rights.group')} value={groupId} onChange={setGroupId}
            options={[{ value: '', label: t('rights.chooseGroup') }, ...rights.groups.map((item) => ({ value: item.id, label: item.name }))]} />
          {group !== null && (
            <>
              <fieldset data-testid="project-rights-actions">
                <legend>{t('group.actions')}</legend>
                {rights.actions.map((action) => (
                  <label key={action} className="check">
                    <input type="checkbox" checked={group.actions.some((item) => item.action === action)} disabled={busy}
                      onChange={(event) => {
                        const enabled = event.target.checked;
                        void apply(() => setGroupAction(api, group.id, action, projectId, enabled));
                      }} />
                    <span>{t(ACTION_LABEL[action] ?? 'right.unknown')}</span>
                    <code className="hint">{action}</code>
                  </label>
                ))}
              </fieldset>
              {fields.length === 0 ? <EmptyText text={t('rights.noFields')} /> : (
                <div className="table-scroll">
                  <table data-testid="project-rights-fields">
                    <caption>{t('group.fields')}</caption>
                    <thead><tr><th scope="col">{t('group.field')}</th><th scope="col">{t('group.read')}</th><th scope="col">{t('group.write')}</th></tr></thead>
                    <tbody>
                      {fields.map((field) => (
                        <tr key={field}>
                          <th scope="row">{t(FIELD_LABEL[field] ?? 'field.title')}</th>
                          {(['read', 'write'] as const).map((operation) => (
                            <td key={operation}>
                              <select aria-label={t(operation === 'read' ? 'group.readOf' : 'group.writeOf', { field: t(FIELD_LABEL[field] ?? 'field.title') })}
                                value={rule(field, operation)} disabled={busy}
                                onChange={(event) => {
                                  const effect = event.target.value === '' ? 'remove' : event.target.value as 'allow' | 'deny';
                                  void apply(() => setGroupField(api, group.id, field as TicketField, projectId, operation, effect));
                                }}>
                                <option value="">{t('group.effect.none')}</option>
                                <option value="allow">{t('group.effect.allow')}</option>
                                <option value="deny">{t('group.effect.deny')}</option>
                              </select>
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="hint">{t('group.fieldsHint')}</p>
            </>
          )}
        </>
      )}
    </section>
  );
}
