import { useState, type FormEvent } from 'react';
import { createProject, listProjects } from '../api/product.ts';
import { useBrand } from '../brand/BrandProvider.tsx';
import { useLoad } from '../app/hooks.ts';
import { hrefFor, navigate } from '../app/route.ts';
import { useSession } from '../app/session.tsx';
import { EmptyText, LoadedView, PageHead, TextField, useProblemText } from '../app/ui.tsx';
import { useI18n } from '../i18n/I18nProvider.tsx';

export const PROJECTS_RIGHT = 'projects.manage';
const KEY = /^[A-Za-z][A-Za-z0-9]{1,9}$/;

// The projects the person may see: as a member with a project right, or all of them with projects.manage.
export function ProjectsPage() {
  const { t } = useI18n();
  const { api } = useBrand();
  const { has } = useSession();
  const [projects, reload] = useLoad(() => listProjects(api), [api]);
  const mayCreate = has(PROJECTS_RIGHT);

  return (
    <>
      <PageHead title={t('projects.title')} intro={t('projects.intro')} />
      {mayCreate && <CreateProject />}
      <section aria-labelledby="project-list">
        <h2 id="project-list">{t('projects.list')}</h2>
        <LoadedView loaded={projects} retry={reload}>
          {(items) => items.length === 0
            ? <EmptyText text={mayCreate ? t('projects.emptyManager') : t('projects.empty')} />
            : (
              <ul className="item-list" data-testid="project-list">
                {items.map((project) => (
                  <li key={project.id}>
                    <a href={hrefFor({ name: 'project', id: project.id, tab: 'tickets' })}>
                      <span className="item-key">{project.key}</span>
                      <span className="item-title">{project.name}</span>
                    </a>
                    {project.description && <p className="hint">{project.description}</p>}
                  </li>
                ))}
              </ul>
            )}
        </LoadedView>
      </section>
    </>
  );
}

function CreateProject() {
  const { t } = useI18n();
  const { api } = useBrand();
  const text = useProblemText();
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<{ key?: string; name?: string; general?: string }>({});
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: typeof errors = {};
    if (!KEY.test(key.trim())) found.key = t('projects.error.key');
    if (name.trim() === '' || name.trim().length > 80) found.name = t('projects.error.name');
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    const result = await createProject(api, key.trim(), name.trim(), description.trim() === '' ? null : description.trim());
    setBusy(false);
    if (result.kind === 'ok') return navigate({ name: 'project', id: result.data.value.id, tab: 'tickets' });
    setErrors({ general: text(result.problem, { 'project.key_taken': 'projects.error.keyTaken' }) });
  }

  return (
    <details className="panel">
      <summary>{t('projects.create')}</summary>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField label={t('projects.key')} hint={t('projects.keyHint')} value={key} onChange={setKey} required maxLength={10} error={errors.key ?? null} />
        <TextField label={t('projects.name')} value={name} onChange={setName} required maxLength={80} error={errors.name ?? null} />
        <TextField label={t('projects.description')} value={description} onChange={setDescription} multiline maxLength={2000} />
        {errors.general !== undefined && <p role="alert" className="field-error">{errors.general}</p>}
        <button type="submit" disabled={busy}>{t('projects.createSubmit')}</button>
      </form>
    </details>
  );
}
