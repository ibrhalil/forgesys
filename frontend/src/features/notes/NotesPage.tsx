import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { Note } from './types';
import { useDeleteNote, useNoteCategories, useNotes } from './hooks';
import { notify } from '../../lib/notify';
import { LuPin, LuSquarePen, LuTrash2 } from 'react-icons/lu';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { SearchInput } from '../../components/ui/SearchInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { SelectInput } from '../../components/ui/SelectInput';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';
import { useAuthStore } from '../../store/authStore';
import { formatDateTime } from '../../lib/format';

/**
 * Notes list (K-44). Server-side q search + categoryId/pinned first-match filters
 * (the backend AND-combines them); the create modal navigates to the editor page
 * where the markdown content is written.
 */
export function NotesPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const {
    page,
    setPage,
    pageSize,
    setPageSize,
    sort,
    toggleSort,
    search,
    setSearch,
    searchFields,
    setSearchFields,
    q,
  } = useListPageState({ defaultSort: { field: 'updatedAt', dir: 'desc' }, storageKey: 'notes' });
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [pinnedOnly, setPinnedOnly] = useState(false);

  const { data, isLoading, isFetching } = useNotes({
    page,
    size: pageSize,
    sorts: [sort],
    q: q || undefined,
    categoryId: categoryId ?? undefined,
    pinned: pinnedOnly || undefined,
  });
  const { data: categories } = useNoteCategories();
  const delNote = useDeleteNote();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.NOTE_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.NOTE_DELETE));

  const [deleting, setDeleting] = useState<Note | null>(null);

  const noteSearchFields = [
    { key: 'title', label: t('notes.titleCol'), searchable: true },
    { key: 'category', label: t('notes.categoryCol'), searchable: false },
    { key: 'project', label: t('projects.project'), searchable: false },
    { key: 'updatedAt', label: t('notes.updatedCol'), searchable: false },
  ];

  const columns: Column<Note>[] = [
    {
      key: 'title',
      header: t('notes.titleCol'),
      sortKey: 'title',
      hideable: false,
      render: (n) => (
        <span className="inline-flex items-center gap-2">
          {n.pinned && <LuPin size={14} className="shrink-0 text-accent" aria-label={t('notes.pinned')} />}
          <Link to={`/notes/${n.id}`} className="font-medium text-main transition-colors hover:text-accent">
            {n.title}
          </Link>
        </span>
      ),
    },
    {
      key: 'category',
      header: t('notes.categoryCol'),
      render: (n) => (n.categoryName ? <Badge tone="blue">{n.categoryName}</Badge> : <span className="text-muted">—</span>),
    },
    {
      key: 'project',
      header: t('projects.project'),
      render: (n) =>
        n.projectName ? <Badge tone="muted">{n.projectName}</Badge> : <span className="text-muted">—</span>,
    },
    {
      key: 'updatedAt',
      header: t('notes.updatedCol'),
      sortKey: 'updatedAt',
      render: (n) => <span className="text-muted">{formatDateTime(n.updatedAt)}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.notes') }]}
      title={t('notes.pageTitle')}
      description={t('notes.pageDesc')}
      actions={canWrite ? <Button variant="primary" onClick={() => navigate('/notes/new')}>{t('notes.new')}</Button> : undefined}
    >
      <DataTable<Note>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(n) => n.id}
        storageKey="notes"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q || categoryId || pinnedOnly ? t('notes.emptyFiltered') : t('notes.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        toolbar={
          <div className="flex flex-wrap items-center gap-2">
            <SearchInput
              value={search}
              onChange={setSearch}
              placeholder={t('notes.searchPh')}
              fields={noteSearchFields}
              selectedFields={searchFields}
              onSelectedFieldsChange={setSearchFields}
            />
            <SelectInput
              className="w-44"
              placeholder={t('notes.allCategories')}
              isClearable
              options={(categories?.items ?? []).map((c) => ({ value: c.id, label: c.name }))}
              value={categoryId ? { value: categoryId, label: categories?.items.find((c) => c.id === categoryId)?.name ?? categoryId } : null}
              onChange={(next) => setCategoryId((next as { value: string } | null)?.value ?? null)}
            />
            <Button
              variant={pinnedOnly ? 'primary' : 'ghost'}
              onClick={() => setPinnedOnly((v) => !v)}
            >
              <LuPin size={14} />
              {t('notes.pinned')}
            </Button>
          </div>
        }
        actionsHeader={t('common.actions')}
        actions={(n) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              { label: t('notes.open'), onClick: () => navigate(`/notes/${n.id}`), icon: LuSquarePen },
              ...(canDelete ? [{ label: t('common.delete'), onClick: () => setDeleting(n), icon: LuTrash2, danger: true }] : []),
            ]}
          />
        )}
      />

      <ConfirmDialog
        open={!!deleting}
        title={t('notes.deleteTitle')}
        message={t('notes.deleteMsg', { name: deleting?.title ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delNote.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delNote.mutateAsync(deleting.id);
            notify.success(t('notes.deleted'));
            setDeleting(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}
