import { api } from '../lib/api';
import type { Company, CompanyStatusUpdateRequest } from '../types';

export const platformApi = {
  listCompanies: () => api.get<Company[]>('/api/v1/platform/companies'),
  getCompany: (id: string) => api.get<Company>(`/api/v1/platform/companies/${id}`),
  updateCompanyStatus: (id: string, data: CompanyStatusUpdateRequest) =>
    api.patch<Company>(`/api/v1/platform/companies/${id}/status`, data),
};
