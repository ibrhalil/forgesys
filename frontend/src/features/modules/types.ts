/** Module catalog entry with per-tenant activation state (wire shape of GET /api/v1/modules). */
export interface Module {
  key: string;
  name: string;
  minPlan: string;
  active: boolean;
  allowedByPlan: boolean;
}
