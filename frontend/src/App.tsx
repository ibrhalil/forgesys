import { useState, useEffect } from 'react';
import './App.css';

interface TenantResponse {
  status: string;
  message: string;
  tenantId?: string;
}

interface Project {
  id: string;
  name: string;
  progress: number;
  status: 'In Progress' | 'Completed' | 'Delayed';
  category: string;
}

interface Task {
  id: string;
  title: string;
  assignee: string;
  priority: 'High' | 'Medium' | 'Low';
  done: boolean;
}

const TENANT_DATA: Record<string, { projects: Project[]; tasks: Task[]; teamSize: number; monthlySpend: string }> = {
  'acme-corp': {
    teamSize: 14,
    monthlySpend: '$4,200',
    projects: [
      { id: '1', name: 'SaaS Platform Redesign', progress: 75, status: 'In Progress', category: 'Frontend' },
      { id: '2', name: 'PostgreSQL Migration', progress: 100, status: 'Completed', category: 'Database' },
      { id: '3', name: 'OAuth2 Authentication', progress: 90, status: 'In Progress', category: 'Security' }
    ],
    tasks: [
      { id: '101', title: 'Implement JWT validation filter', assignee: 'Jane Cooper', priority: 'High', done: true },
      { id: '102', title: 'Optimize docker multi-stage build', assignee: 'John Doe', priority: 'Medium', done: false },
      { id: '103', title: 'Design Glassmorphism Dashboard UI', assignee: 'Alex Smith', priority: 'High', done: false }
    ]
  },
  'stark-industries': {
    teamSize: 45,
    monthlySpend: '$28,500',
    projects: [
      { id: '1', name: 'Mark 85 HUD Interface', progress: 40, status: 'In Progress', category: 'Embedded' },
      { id: '2', name: 'Arc Reactor Clean Grid', progress: 95, status: 'In Progress', category: 'Infrastructure' },
      { id: '3', name: 'Jarvis Voice Recognition v4', progress: 100, status: 'Completed', category: 'AI/ML' }
    ],
    tasks: [
      { id: '101', title: 'Tune vibrational frequency mapping', assignee: 'Tony Stark', priority: 'High', done: true },
      { id: '102', title: 'Refactor thruster stability algorithm', assignee: 'Bruce Banner', priority: 'High', done: false },
      { id: '103', title: 'Add thermal resistance logging', assignee: 'Jarvis', priority: 'Low', done: true }
    ]
  },
  'wayne-enterprises': {
    teamSize: 8,
    monthlySpend: '$9,800',
    projects: [
      { id: '1', name: 'Batcave Tech Upgrade', progress: 85, status: 'In Progress', category: 'Hardware' },
      { id: '2', name: 'Satellite Tracking Array', progress: 100, status: 'Completed', category: 'Telecom' },
      { id: '3', name: 'Tumbler Engine Refurbish', progress: 15, status: 'Delayed', category: 'Mechanical' }
    ],
    tasks: [
      { id: '101', title: 'Calibrate sonar ping overlay', assignee: 'Lucius Fox', priority: 'High', done: true },
      { id: '102', title: 'Deploy night vision filter patches', assignee: 'Bruce Wayne', priority: 'Medium', done: false },
      { id: '103', title: 'Order bulletproof graphite compounds', assignee: 'Alfred Pennyworth', priority: 'High', done: true }
    ]
  }
};

function App() {
  const [selectedTenant, setSelectedTenant] = useState<string>('acme-corp');
  const [apiResponse, setApiResponse] = useState<TenantResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'projects' | 'tasks'>('overview');

  useEffect(() => {
    fetchTenantTest();
  }, [selectedTenant]);

  const fetchTenantTest = async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/v1/tenant-test', {
        headers: {
          'X-Tenant-ID': selectedTenant
        }
      });
      if (response.ok) {
        const data = await response.json();
        setApiResponse(data);
      } else {
        setApiResponse({
          status: 'error',
          message: `Server returned error status ${response.status}`
        });
      }
    } catch (err: any) {
      setApiResponse({
        status: 'offline',
        message: 'Could not connect to Spring Boot API. Showing simulation data.'
      });
    } finally {
      setIsLoading(false);
    }
  };

  const activeTenantData = TENANT_DATA[selectedTenant] || TENANT_DATA['acme-corp'];

  return (
    <div className="dashboard-container">
      {/* Sidebar Navigation */}
      <aside className="sidebar">
        <div className="logo-section">
          <div className="logo-icon">SF</div>
          <h2>SystemForge</h2>
        </div>
        <nav className="nav-menu">
          <button 
            className={`nav-item ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="icon"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>
            Overview
          </button>
          <button 
            className={`nav-item ${activeTab === 'projects' ? 'active' : ''}`}
            onClick={() => setActiveTab('projects')}
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="icon"><path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/><path d="M12 6v6l4 2"/></svg>
            Projects
          </button>
          <button 
            className={`nav-item ${activeTab === 'tasks' ? 'active' : ''}`}
            onClick={() => setActiveTab('tasks')}
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="icon"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
            Tasks
          </button>
        </nav>
        <div className="tenant-selector-box">
          <label>Active Tenant Workspace</label>
          <select 
            value={selectedTenant} 
            onChange={(e) => setSelectedTenant(e.target.value)}
            className="tenant-select"
          >
            <option value="acme-corp">🏢 Acme Corporation</option>
            <option value="stark-industries">🚀 Stark Industries</option>
            <option value="wayne-enterprises">🦇 Wayne Enterprises</option>
          </select>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <header className="top-bar">
          <div className="header-title">
            <h1>Workspace Management</h1>
            <p className="subtitle">SaaS Multi-tenant Control Panel</p>
          </div>
          <div className="connection-status">
            {isLoading ? (
              <span className="badge loading">Connecting...</span>
            ) : apiResponse?.status === 'success' ? (
              <span className="badge live">🟢 Spring Boot Connected</span>
            ) : (
              <span className="badge offline">🔴 Offline Mode</span>
            )}
          </div>
        </header>

        {/* API Multi-tenancy status display */}
        <section className="api-info-panel">
          <div className="panel-header">
            <h3>Multitenancy Context (Headers & ThreadLocal)</h3>
            <button className="btn-refresh" onClick={fetchTenantTest}>
              🔄 Refresh Sync
            </button>
          </div>
          <div className="api-log">
            <div className="log-row">
              <span className="log-label">HTTP Header sent:</span>
              <code>X-Tenant-ID: {selectedTenant}</code>
            </div>
            <div className="log-row">
              <span className="log-label">Backend Response:</span>
              <span className="log-value">
                {apiResponse ? (
                  apiResponse.status === 'success' ? (
                    <span className="text-success">
                      Authenticated! Tenant <strong>{apiResponse.tenantId}</strong> resolved by filter ThreadLocal context.
                    </span>
                  ) : (
                    <span className="text-warning">
                      {apiResponse.message}
                    </span>
                  )
                ) : (
                  'Querying backend...'
                )}
              </span>
            </div>
          </div>
        </section>

        {/* Dynamic Tab Contents */}
        {activeTab === 'overview' && (
          <div className="tab-pane fade-in">
            {/* Stat Cards */}
            <div className="stat-grid">
              <div className="stat-card">
                <span className="stat-label">Active Team Size</span>
                <span className="stat-value">{activeTenantData.teamSize}</span>
                <span className="stat-desc">Developers & Managers</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Monthly Infrastructure Spend</span>
                <span className="stat-value">{activeTenantData.monthlySpend}</span>
                <span className="stat-desc">Billed automatically</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Project Accomplishment</span>
                <span className="stat-value">
                  {Math.round(
                    activeTenantData.projects.reduce((acc, curr) => acc + curr.progress, 0) /
                      activeTenantData.projects.length
                  )}%
                </span>
                <span className="stat-desc">Average project progress</span>
              </div>
            </div>

            {/* Quick overview of projects and tasks */}
            <div className="overview-split">
              <div className="data-panel">
                <h3>Projects</h3>
                <div className="project-list">
                  {activeTenantData.projects.map((p) => (
                    <div key={p.id} className="project-row">
                      <div className="project-info">
                        <h4>{p.name}</h4>
                        <span className="proj-tag">{p.category}</span>
                      </div>
                      <div className="progress-bar-container">
                        <div className="progress-bar" style={{ width: `${p.progress}%` }}></div>
                        <span className="progress-text">{p.progress}%</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="data-panel">
                <h3>Pending Tasks</h3>
                <div className="task-list">
                  {activeTenantData.tasks.map((t) => (
                    <div key={t.id} className="task-row">
                      <input type="checkbox" checked={t.done} readOnly className="task-check" />
                      <div className="task-details">
                        <p className={t.done ? 'task-done' : ''}>{t.title}</p>
                        <span className="task-assignee">Assignee: {t.assignee}</span>
                      </div>
                      <span className={`priority-badge ${t.priority.toLowerCase()}`}>
                        {t.priority}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'projects' && (
          <div className="tab-pane fade-in">
            <div className="data-panel full-width">
              <h3>All Projects for {selectedTenant.replace('-', ' ').toUpperCase()}</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Project Name</th>
                    <th>Category</th>
                    <th>Progress</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {activeTenantData.projects.map((p) => (
                    <tr key={p.id}>
                      <td>#{p.id}</td>
                      <td><strong>{p.name}</strong></td>
                      <td><span className="table-tag">{p.category}</span></td>
                      <td>
                        <div className="progress-bar-container">
                          <div className="progress-bar" style={{ width: `${p.progress}%` }}></div>
                          <span className="progress-text">{p.progress}%</span>
                        </div>
                      </td>
                      <td>
                        <span className={`status-pill ${p.status.toLowerCase().replace(' ', '-')}`}>
                          {p.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'tasks' && (
          <div className="tab-pane fade-in">
            <div className="data-panel full-width">
              <h3>All Backlog Tasks for {selectedTenant.replace('-', ' ').toUpperCase()}</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Task Title</th>
                    <th>Assignee</th>
                    <th>Priority</th>
                    <th>Task ID</th>
                  </tr>
                </thead>
                <tbody>
                  {activeTenantData.tasks.map((t) => (
                    <tr key={t.id}>
                      <td>
                        <span className={`status-indicator ${t.done ? 'done' : 'pending'}`}>
                          {t.done ? '✓ Completed' : '⚙ Pending'}
                        </span>
                      </td>
                      <td><strong>{t.title}</strong></td>
                      <td>{t.assignee}</td>
                      <td>
                        <span className={`priority-pill ${t.priority.toLowerCase()}`}>
                          {t.priority}
                        </span>
                      </td>
                      <td>#{t.id}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
