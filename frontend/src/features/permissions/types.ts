export interface Permission {
  id: string;
  name: string;
  description: string | null;
}

// Permission create/update share this shape (backend PermissionRequest).
export interface CreatePermissionRequest {
  name: string;
  description?: string;
}
