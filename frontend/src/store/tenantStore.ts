import { create } from 'zustand';

interface TenantState {
  tenantId: string | null;
  setTenantId: (id: string) => void;
  clearTenantId: () => void;
}

function resolveInitialTenant(): string | null {
  // 1. localStorage override
  const stored = localStorage.getItem('sf_tenant_id');
  if (stored) return stored;

  // 2. Subdomain detection
  const hostname = window.location.hostname;
  if (hostname.endsWith('.localhost')) {
    return hostname.replace('.localhost', '');
  }
  const parts = hostname.split('.');
  if (parts.length > 2) {
    return parts[0];
  }
  return null;
}

export const useTenantStore = create<TenantState>((set) => ({
  tenantId: resolveInitialTenant(),

  setTenantId: (id) => {
    localStorage.setItem('sf_tenant_id', id);
    set({ tenantId: id });
  },

  clearTenantId: () => {
    localStorage.removeItem('sf_tenant_id');
    set({ tenantId: null });
  },
}));
