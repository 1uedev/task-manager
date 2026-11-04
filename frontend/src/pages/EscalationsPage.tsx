import { listSlaEscalations, type SlaEscalation } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor } from '../app/route.ts';
import { EmptyText, LoadedView, PageHead } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// The list "SLA Eskalationen" (TASK-070): the stages reached for the groups of the person, newest first. The server shows
// only tickets the person may read with the read rules of customer and priority, checked when the list is read (rule 2).
export function EscalationsPage() {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [items, reload] = useLoad(() => listSlaEscalations(api), [api]);
  const clock = (item: SlaEscalation) => t(item.kind === 'first_response' ? 'sla.firstResponse' : 'sla.resolution');

  return (
    <>
      <PageHead title={t('escalations.title')} intro={t('escalations.intro')}>
        <button type="button" className="secondary" onClick={reload}>{t('escalations.refresh')}</button>
      </PageHead>
      <LoadedView loaded={items} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('escalations.empty')} /> : (
          <ul className="item-list" data-testid="escalation-list">
            {list.map((item) => (
              <li key={`${item.projectId}/${item.number}/${item.cycleNumber}/${item.kind}/${item.percent}/${item.groupId}`} className="card"
                data-ticket={`${item.projectKey}-${item.number}`} data-percent={item.percent}>
                <a href={hrefFor({ name: 'ticket', projectId: item.projectId, number: item.number })}>{`${item.projectKey}-${item.number}`}</a>
                <p>{t('escalations.line', { percent: item.percent, clock: clock(item), group: item.groupName })}</p>
                <p className="hint">{t('escalations.when', { threshold: formatDateTime(item.thresholdAt, language), reached: formatDateTime(item.reachedAt, language) })}
                  {item.cycleOutcome ? `, ${t(item.cycleOutcome === 'met' ? 'escalations.closedMet' : 'escalations.closedCancelled')}` : ''}</p>
              </li>
            ))}
          </ul>
        )}
      </LoadedView>
    </>
  );
}
