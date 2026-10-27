import { useRef, useState, type DragEvent, type MouseEvent } from 'react';
import { attachmentUrl, checkAttachment, listAttachments, removeAttachment, uploadAttachment, type Project, type Ticket } from '../api/product.ts';
import { UploadIcon } from '../app/Icons.tsx';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { useAccountName } from '../app/session.tsx';
import { EmptyText, LoadedView, NoticeView, useProblemText, type Notice } from '../app/ui.tsx';
import { formatBytes, formatDateTime } from '../i18n/format.ts';
import { useI18n } from '../i18n/I18nProvider.tsx';

// Office documents that can carry macros. They stay allowed as common business files, but the list warns before they are
// opened (Holger's decision of 27 October 2026, addendum to DEC-082).
const MACRO_EXTENSIONS = new Set(['docm', 'dotm', 'xlsm', 'xltm', 'xlsb', 'xlam', 'pptm', 'potm', 'ppsm', 'ppam', 'sldm']);
const mayCarryMacros = (fileName: string) => fileName.includes('.') && MACRO_EXTENSIONS.has(fileName.slice(fileName.lastIndexOf('.') + 1).toLowerCase());

// The limit of a new installation, shown until the server names its own (review F8 of TASK-051); the server decides.
const DEFAULT_LIMIT_BYTES = 25_000_000;

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
  // The server names attachments among the writable fields exactly when it accepts adding and removing them (review F1 of TASK-051).
  const editable = ticket.writableFields.includes('attachments');
  const limit = attachments.state === 'ok' ? attachments.value.maxBytes ?? DEFAULT_LIMIT_BYTES : DEFAULT_LIMIT_BYTES;

  const problemText = (problem: Parameters<typeof text>[0]) => text(problem, {
    'attachment.too_large': 'attachments.error.tooLarge',
    'attachment.type_blocked': 'attachments.error.blocked',
    'attachment.empty': 'attachments.error.empty',
    'request.invalid': 'attachments.error.name',
    'auth.forbidden': 'attachments.error.forbidden',
    'attachment.storage_unavailable': 'attachments.error.storage',
    'attachment.not_found': 'attachments.error.gone',
    'attachment.incomplete': 'attachments.error.incomplete',
  });

  // Every file is tried; at the end one notice names what was attached and why each other file was not (review F3 of TASK-051).
  async function upload(files: readonly File[]) {
    setNotice(null);
    const added: string[] = [];
    const refused: string[] = [];
    for (const file of files) {
      setUploading(file.name);
      const result = await uploadAttachment(api, project.id, ticket.number, file);
      if (result.kind === 'ok') {
        added.push(result.data.value.name);
        continue;
      }
      refused.push(`${file.name}: ${problemText(result.problem)}`);
      // The rights may have changed; the list is read again and may disappear.
      if (result.problem.status === 403) reload();
    }
    setUploading(null);
    if (added.length > 0) {
      reload();
      onChanged();
    }
    if (files.length === 1) {
      return setNotice(added.length === 1 ? { kind: 'success', text: t('attachments.added', { name: added[0] ?? '' }) } : { kind: 'info', text: refused[0] ?? '' });
    }
    const summary = t('attachments.addedOf', { count: added.length, total: files.length, names: added.length > 0 ? ` (${added.join(', ')})` : '' });
    setNotice({ kind: refused.length === 0 ? 'success' : 'info', text: [summary, ...refused].join(' ') });
  }

  // The download stays a link, so the browser streams and saves the file; a short question first explains a failure.
  async function download(event: MouseEvent<HTMLAnchorElement>, id: string, fileName: string) {
    if (event.currentTarget.dataset.checked === 'true') {
      delete event.currentTarget.dataset.checked;
      return;
    }
    event.preventDefault();
    const link = event.currentTarget;
    const result = await checkAttachment(api, project.id, ticket.number, id);
    if (result.kind !== 'ok') {
      reload();
      const reason = result.problem.status === 404 ? t('attachments.error.gone')
        : result.problem.status === 403 ? t('attachments.error.noRead')
          : result.problem.status === 503 ? t('attachments.error.storage') : text(result.problem);
      return setNotice({ kind: 'info', text: `${fileName}: ${reason}` });
    }
    link.dataset.checked = 'true';
    link.click();
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
        {({ items }) => (
          <>
            {items.length === 0 ? <EmptyText text={t('attachments.none')} /> : (
              <ul className="attachments" data-testid="ticket-attachments">
                {items.map((item) => (
                  <li key={item.id}>
                    <a href={attachmentUrl(project.id, ticket.number, item.id)} download={item.name} aria-label={t('attachments.download', { name: item.name })}
                      onClick={(event) => void download(event, item.id, item.name)}>{item.name}</a>
                    <span className="hint">{t('attachments.meta', { size: formatBytes(item.size, language), by: name(item.uploadedBy), date: formatDateTime(item.uploadedAt, language) })}</span>
                    {mayCarryMacros(item.name) && (
                      <p className="attachment-warning" data-testid="attachment-macro-warning">
                        <span aria-hidden="true">⚠</span> {t('attachments.macroWarning')}
                      </p>
                    )}
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
                <p className="hint">{t('attachments.hint', { size: formatBytes(limit, language) })}</p>
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
