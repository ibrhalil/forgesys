import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { noteCategoriesApi, notesApi, type NoteListParams } from './api';
import type { NoteCategoryRequest, NoteRequest } from './types';

// ─── Notes ───
export function useNotes(params: NoteListParams = {}) {
  return useQuery({ queryKey: ['notes', params], queryFn: () => notesApi.list(params) });
}

export function useNote(id: string | undefined) {
  return useQuery({
    queryKey: ['notes', id],
    queryFn: () => notesApi.get(id as string),
    enabled: !!id,
  });
}

export function useCreateNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: NoteRequest) => notesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notes'] }),
  });
}

export function useUpdateNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: NoteRequest }) => notesApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notes'] }),
  });
}

export function useDeleteNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => notesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notes'] }),
  });
}

// ─── Note categories ───
export function useNoteCategories() {
  return useQuery({ queryKey: ['note-categories'], queryFn: () => noteCategoriesApi.list() });
}

export function useCreateNoteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: NoteCategoryRequest) => noteCategoriesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['note-categories'] }),
  });
}

export function useUpdateNoteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: NoteCategoryRequest }) =>
      noteCategoriesApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['note-categories'] }),
  });
}

export function useDeleteNoteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => noteCategoriesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['note-categories'] });
      // Notes keep living (uncategorized) — refresh their rows too.
      qc.invalidateQueries({ queryKey: ['notes'] });
    },
  });
}
