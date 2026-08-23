// Notes module (notes:* — notes anchored to NOTES-type project containers, K-44 + K-45)
export interface Note {
  id: string;
  title: string;
  content: string;
  projectId: string;
  projectName: string | null;
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
  /** Absent on create → the default NOTES container; null on update → unchanged. */
  projectId?: string | null;
}

export interface NoteCategory {
  id: string;
  name: string;
  color: string | null;
  projectId: string;
}

export interface NoteCategoryRequest {
  name: string;
  color?: string;
  /** Absent → the default NOTES container (create only; moves are rejected). */
  projectId?: string;
}
