import { api } from '../../lib/api';
import type { Module } from './types';

export const modulesApi = {
  list: () => api.get<Module[]>('/api/v1/modules'),
  activate: (key: string) => api.post<Module>(`/api/v1/modules/${key}/activate`),
};
