import { Fragment, useState } from 'react';
import { listPortalProjects, listPortalTickets, readPortalTicket, type PortalProject, type Ticket } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { EmptyText, LoadedView, PageHead } from '../app/ui.tsx';
import { formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// The reading view of a portal account (TASK-075, DEC-105): its projects and the requests of its organization there, with the
// fields its groups may read. Creating and answering requests follow with TASK-076.
export function PortalPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const [projects, reload] = useLoad(() => listPortalProjects(api), [api]);
  const [open, setOpen] = useState<{ readonly project: PortalProject; readonly number: number } | null>(null);

  if (open !== null) {
    return <PortalTicket project={open.project} number={open.number} onBack={() => setOpen(null)} />;
  }
  return (
    <>
      <PageHead title={t('portal.title')} intro={t('portal.intro')} />
      <LoadedView loaded={projects} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('portal.noProjects')} /> : (
          <>
            {list.map((project) => (
              <PortalProjectTickets key={project.id} project={project} onOpen={(number) => setOpen({ project, number })} />
            ))}
          </>
        )}
      </LoadedView>
    </>
  );
}

function PortalProjectTickets({ project, onOpen }: { readonly project: PortalProject; readonly onOpen: (number: number) => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [tickets, reload] = useLoad(() => listPortalTickets(api, project.id), [api, project.id]);
  const headingId = `portal-project-${project.id}`;
  return (
    <section aria-labelledby={headingId} className="panel" data-testid="portal-project">
      <h2 id={headingId}>{`${project.key} ${project.name}`}</h2>
      <LoadedView loaded={tickets} retry={reload}>
        {(list) => list.length === 0
          ? <EmptyText text={t('portal.noTickets')} /> : (
          <div className="table-scroll">
            <table data-testid="portal-tickets">
              <thead>
                <tr>
                  <th scope="col">{t('portal.number')}</th>
                  <th scope="col">{t('portal.ticketTitle')}</th>
                  <th scope="col">{t('portal.status')}</th>
                  <th scope="col">{t('portal.updated')}</th>
                </tr>
              </thead>
              <tbody>
                {list.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>{`${ticket.key}-${ticket.number}`}</td>
                    <td><button type="button" className="link-button" onClick={() => onOpen(ticket.number)}>{ticket.fields.title ?? ''}</button></td>
                    <td>{ticket.fields.status ?? ''}</td>
                    <td>{formatDateTime(ticket.updatedAt, language)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </LoadedView>
    </section>
  );
}

const SHOWN = ['type', 'status', 'priority'] as const;

function PortalTicket({ project, number, onBack }: { readonly project: PortalProject; readonly number: number; readonly onBack: () => void }) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const [ticket, reload] = useLoad(() => readPortalTicket(api, project.id, number), [api, project.id, number]);
  return (
    <>
      <p><button type="button" className="link-button" onClick={onBack}>{t('portal.back')}</button></p>
      <LoadedView loaded={ticket} retry={reload}>
        {(value: Ticket) => {
          const fields = value.fields as Readonly<Record<string, string | null | undefined>>;
          return (
          <article data-testid="portal-ticket">
            <PageHead title={`${value.key}-${value.number} ${value.fields.title ?? ''}`} />
            <dl className="facts">
              {SHOWN.filter((field) => fields[field] !== undefined).map((field) => (
                <Fragment key={field}><dt>{t(`portal.field.${field}`)}</dt><dd>{fields[field] ?? ''}</dd></Fragment>
              ))}
              <dt>{t('portal.updated')}</dt><dd>{formatDateTime(value.updatedAt, language)}</dd>
            </dl>
            {value.fields.description !== undefined && value.fields.description !== null && (
              <section aria-label={t('portal.field.description')}><p className="prewrap">{value.fields.description}</p></section>
            )}
          </article>
          );
        }}
      </LoadedView>
    </>
  );
}
