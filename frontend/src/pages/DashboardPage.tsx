import { useAuthStore } from '../store/authStore';
import { useTenantStore } from '../store/tenantStore';
import { Badge } from '../components/ui/Badge';

export function DashboardPage() {
  const { user } = useAuthStore();
  const { tenantId } = useTenantStore();

  if (!user) return null;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Dashboard</h1>
          <p className="mt-1 text-sm text-muted">Welcome back, {user.email}</p>
        </div>
        <Badge tone="green">Authenticated</Badge>
      </header>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <StatCard label="User ID" value={user.userId} mono />
        <StatCard label="Tenant" value={user.tenant ?? tenantId ?? 'N/A'} />
        <StatCard label="Authorities" value={String(user.authorities?.length ?? 0)} sub="Granted permissions" />
      </div>

      <div className="rounded-xl border border-glass bg-surface p-6 backdrop-blur-md">
        <h3 className="mb-4 text-lg font-semibold text-white">Granted Authorities</h3>
        {user.authorities && user.authorities.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {user.authorities.map((auth) => (
              <Badge key={auth} tone="green">
                {auth}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted">No authorities assigned</p>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, sub, mono }: { label: string; value: string; sub?: string; mono?: boolean }) {
  return (
    <div className="flex flex-col rounded-xl border border-glass bg-surface p-6 backdrop-blur-md">
      <span className="text-xs uppercase tracking-wide text-muted">{label}</span>
      <span className={`mt-2 break-all text-2xl font-bold text-white ${mono ? 'text-sm font-mono' : ''}`}>
        {value}
      </span>
      {sub && <span className="mt-1 text-xs text-muted">{sub}</span>}
    </div>
  );
}
