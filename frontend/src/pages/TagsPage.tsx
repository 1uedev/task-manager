import { useState, type FormEvent } from 'react';
import { createCompanyTag } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';
import { NoticeView, PageHead, TextField, type Notice } from '../app/ui.tsx';
import { TagCatalog, useTagProblemText } from './TagParts.tsx';

export const TAGS_RIGHT = 'tags.manage';

// The company catalog for holders of tags.manage (TASK-072): create, describe, color, rename, archive and merge tags.
export function TagsPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const problemText = useTagProblemText();
  const [name, setName] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [round, setRound] = useState(0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === '') return;
    const result = await createCompanyTag(api, name.trim());
    if (result.kind === 'ok') {
      setName('');
      setNotice({ kind: 'success', text: t('tags.created', { name: result.data.value.name }) });
      setRound((value) => value + 1);
      return;
    }
    setNotice({ kind: 'info', text: result.problem.code === 'request.invalid' ? t('tags.error.name') : problemText(result.problem) });
  }

  return (
    <>
      <PageHead title={t('tagsPage.title')} intro={t('tagsPage.intro')} />
      <section aria-labelledby="tags-new" className="panel">
        <h2 id="tags-new">{t('tags.new')}</h2>
        <NoticeView notice={notice} />
        <form className="inline-form" onSubmit={(event) => void submit(event)}>
          <TextField label={t('tags.name')} value={name} onChange={setName} maxLength={50} />
          <button type="submit" disabled={name.trim() === ''}>{t('tags.createSubmit')}</button>
        </form>
      </section>
      <section aria-labelledby="tags-catalog">
        <h2 id="tags-catalog">{t('tags.company')}</h2>
        <TagCatalog scope={{ kind: 'company' }} round={round} />
      </section>
    </>
  );
}
