import type { IconType } from 'react-icons';
import {
  LuBlocks,
  LuChartLine,
  LuFileText,
  LuFolder,
  LuLayoutGrid,
  LuLogIn,
  LuMonitor,
  LuShield,
  LuShieldCheck,
  LuUsers,
  LuUsersRound,
} from 'react-icons/lu';
import type { MessageKey } from '../lib/i18n';
import { PERMISSIONS, type Permission } from '../lib/permissions';

export interface NavItem {
  labelKey: MessageKey;
  to: string;
  icon: IconType;
  /** Item renders only when the user holds this authority (backend enforces the real security). */
  authority?: Permission;
}

export interface NavGroup {
  id: string;
  labelKey: MessageKey;
  items: NavItem[];
}

/** Top-level nav entries (outside any collapsible group). */
export const NAV_ITEMS: NavItem[] = [
  { labelKey: 'nav.projects', to: '/', icon: LuFolder, authority: PERMISSIONS.PROJECT_READ },
  { labelKey: 'nav.apps', to: '/apps', icon: LuLayoutGrid, authority: PERMISSIONS.APP_READ },
];

/** Collapsible nav groups, in display order. A group whose items are all filtered
 *  out by authority is hidden entirely (its header included). */
export const NAV_GROUPS: NavGroup[] = [
  {
    id: 'identity',
    labelKey: 'nav.identity',
    items: [
      { labelKey: 'nav.users', to: '/users', icon: LuUsers, authority: PERMISSIONS.USER_READ },
      { labelKey: 'nav.roles', to: '/roles', icon: LuShield, authority: PERMISSIONS.ROLE_READ },
      { labelKey: 'nav.groups', to: '/groups', icon: LuUsersRound, authority: PERMISSIONS.GROUP_READ },
      { labelKey: 'nav.permissions', to: '/permissions', icon: LuShieldCheck, authority: PERMISSIONS.PERMISSION_READ },
    ],
  },
  {
    id: 'security',
    labelKey: 'nav.security',
    items: [
      { labelKey: 'nav.auditLogs', to: '/audit-logs', icon: LuFileText, authority: PERMISSIONS.AUDIT_READ },
      { labelKey: 'nav.loginHistory', to: '/login-history', icon: LuLogIn, authority: PERMISSIONS.AUDIT_READ },
      { labelKey: 'nav.requestLogs', to: '/request-logs', icon: LuMonitor, authority: PERMISSIONS.AUDIT_READ },
      // Self-service sessions: any authenticated user.
      { labelKey: 'nav.sessions', to: '/sessions', icon: LuMonitor },
    ],
  },
  {
    id: 'admin',
    labelKey: 'nav.admin',
    items: [
      { labelKey: 'nav.allSessions', to: '/all-sessions', icon: LuChartLine, authority: PERMISSIONS.USER_WRITE },
      { labelKey: 'nav.modules', to: '/modules', icon: LuBlocks, authority: PERMISSIONS.MODULE_READ },
    ],
  },
];
