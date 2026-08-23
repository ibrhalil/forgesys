// Notes module (notes:* — standalone tenant-shared notes, K-44 / Epic 3.2)
export interface Note {
  id: string;
  title: string;
  content: string;
  categoryId: string | null;
  categoryName: string | null;
  pinned: boolean;
  updatedAt: string;
}

/** Create + update share this shape (backend NoteRequest). */
export interface NoteRequest {
  title: string;
  content?: string;
  categoryId?: string | null;
  pinned?: boolean;
}

export interface NoteCategory {
  id: string;
  name: string;
  color: string | null;
}

export interface NoteCategoryRequest {
  name: string;
  color?: string;
}
