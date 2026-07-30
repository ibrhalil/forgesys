import type { RoleSummary, UserSummary } from '../../types';

// Full group (GroupResponse) — nested roles are summaries, members are user summaries.
// RoleSummary/UserSummary stay in the shared types module (embedded across features).
export interface Group {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  roles: RoleSummary[];
  members: UserSummary[];
  memberCount: number;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  active?: boolean;
}

export interface AssignMembersRequest {
  userIds: string[];
}
