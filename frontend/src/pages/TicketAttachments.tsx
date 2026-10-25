import { useRef, useState, type DragEvent } from 'react';
import { attachmentUrl, listAttachments, removeAttachment, uploadAttachment, type Project, type Ticket } from '../api/product.ts';
import { UploadIcon } from '../app/Icons.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { useAccountName } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, useProblemText, type Notice } from '../app/ui.tsx';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// The limit the server enforces; shown only as a hint, the server decides.
const LIMIT_MB = 25;

// The attachments of one ticket (TASK-051). Reading, adding and removing follow the rules of the field `attachments`, which
// only the server knows: without the read rule the section is not shown, and a refused change is explained, not hidden.
export function TicketAttachments({ project, ticket, round, onChanged }: {
  readonly project: Project;
  readonly ticket: Ticket;
  readonly round: number;
  // The history shows additions and removals, so it is read again after each change.
  readonly onChanged: () => void;
}) {
  const { t, language } = useI18n();
  const { api } = useBrand();
  const name = useAccountName();
  const text = useProblemText();
  const [attachments, reload] = useLoad(() => listAttachments(api, project.id, ticket.number), [api, project.id, ticket.number, round]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [uploading, setUploading] = useState<string | null>(null);
  const [dropping, setDropping] = useState(false);
  const [removing, setRemoving] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  // Without the read rule for attachments the section is not shown at all.
  if (attachments.state === 'problem' && (attachments.problem.status === 403 || attachments.problem.status === 404)) return null;
  // writableFields does not name attachments; an empty list means no ticket.edit, so nothing can be changed here.
  const editable = ticket.writableFields.length > 0;

  const problemText = (problem: Parameters<typeof text>[0]) => text(problem, {
    'attachment.too_large': 'attachments.error.tooLarge',
    'attachment.type_blocked': 'attachments.error.blocked',
    'attachment.empty': 'attachments.error.empty',
    'request.invalid': 'attachments.error.name',
    'auth.forbidden': 'attachments.error.forbidden',
    'attachment.storage_unavailable': 'attachments.error.storage',
    'attachment.not_found': 'attachments.error.gone',
  });

  async function upload(files: readonly File[]) {
    setNotice(null);
    for (const file of files) {
      setUploading(file.name);
      const result = await uploadAttachment(api, project.id, ticket.number, file);
      setUploading(null);
      if (result.kind !== 'ok') {
        // The rights may have changed; the list is read again and may disappear.
        if (result.problem.status === 403) reload();
        return setNotice({ kind: 'info', text: `${file.name}: ${problemText(result.problem)}` });
      }
      setNotice({ kind: 'success', text: t('attachments.added', { name: result.data.value.name }) });
      reload();
      onChanged();
    }
  }

  async function remove(id: string, fileName: string) {
    setNotice(null);
    setRemoving(null);
    const result = await removeAttachment(api, project.id, ticket.number, id);
    if (result.kind !== 'ok') {
      reload();
      return setNotice({ kind: 'info', text: problemText(result.problem) });
    }
    setNotice({ kind: 'success', text: t('attachments.removed', { name: fileName }) });
    reload();
    onChanged();
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDropping(false);
    if (!editable || uploading !== null) return;
    void upload([...event.dataTransfer.files]);
  }

  return (
    <section aria-labelledby="ticket-attachments" className="panel">
      <h2 id="ticket-attachments">{t('field.attachments')}</h2>
      <NoticeView notice={notice} />
      {uploading !== null && <p role="status" className="hint" data-testid="attachment-uploading">{t('attachments.uploading', { name: uploading })}</p>}
      <LoadedView loaded={attachments} retry={reload}>
        {(items) => (
          <>
            {items.length === 0 ? <EmptyText text={t('attachments.none')} /> : (
              <ul className="attachments" data-testid="ticket-attachments">
                {items.map((item) => (
                  <li key={item.id}>
                    <a href={attachmentUrl(project.id, ticket.number, item.id)} download={item.name} aria-label={t('attachments.download', { name: item.name })}>{item.name}</a>
                    <span className="hint">{t('attachments.meta', { size: formatBytes(item.size, language), by: name(item.uploadedBy), date: formatDateTime(item.uploadedAt, language) })}</span>
                    {editable && removing !== item.id && (
                      <button type="button" className="secondary" onClick={() => setRemoving(item.id)}>{t('attachments.remove', { name: item.name })}</button>
                    )}
                    {editable && removing === item.id && (
                      <span role="group" aria-label={t('attachments.removeAsk', { name: item.name })} className="inline-form">
                        <span className="hint">{t('attachments.removeHint')}</span>
                        <button type="button" onClick={() => void remove(item.id, item.name)}>{t('attachments.removeConfirm')}</button>
                        <button type="button" className="secondary" onClick={() => setRemoving(null)}>{t('attachments.cancel')}</button>
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {editable && (
              <div className="dropzone" data-testid="attachment-dropzone" data-active={dropping || undefined}
                onDragOver={(event) => {
                  event.preventDefault();
                  setDropping(true);
                }}
                onDragLeave={() => setDropping(false)} onDrop={onDrop}>
                <span className="dropzone-icon" aria-hidden="true"><UploadIcon /></span>
                <p className="dropzone-title">{t('attachments.drop')}</p>
                <p>
                  <button type="button" className="link-button" disabled={uploading !== null} onClick={() => fileInput.current?.click()}>{t('attachments.choose')}</button>
                </p>
                <p className="hint">{t('attachments.hint', { size: LIMIT_MB })}</p>
                <input ref={fileInput} type="file" multiple className="visually-hidden" tabIndex={-1} aria-hidden="true" data-testid="attachment-file"
                  onChange={(event) => {
                    const files = [...(event.target.files ?? [])];
                    event.target.value = '';
                    if (files.length > 0) void upload(files);
                  }} />
              </div>
            )}
          </>
        )}
      </LoadedView>
    </section>
  );
}
