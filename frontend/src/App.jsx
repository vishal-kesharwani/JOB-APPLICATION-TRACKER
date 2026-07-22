import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import Dashboard from "./pages/Dashboard.jsx";
import Applications from "./pages/Applications.jsx";
import Reminders from "./pages/Reminders.jsx";

const NAV = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/applications", label: "Applications" },
  { to: "/reminders", label: "Reminders" },
];

// Grafana runs on the host, not behind the nginx proxy, so link to it absolutely.
const GRAFANA_URL = `${window.location.protocol}//${window.location.hostname}:3000`;

function Nav() {
  return (
    <header className="sticky top-0 z-40 border-b border-surface-800 bg-surface-950/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <div className="flex items-center gap-8">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-sm font-bold text-white">
              JT
            </div>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-white">JobTracker</p>
              <p className="text-[11px] text-slate-500">Application Tracking System</p>
            </div>
          </div>

          <nav className="flex items-center gap-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-surface-800 text-white"
                      : "text-slate-400 hover:bg-surface-900 hover:text-slate-200"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>

        <a
          href={GRAFANA_URL}
          target="_blank"
          rel="noreferrer"
          className="btn-ghost text-xs"
          title="Open the Grafana observability dashboard"
        >
          Grafana ↗
        </a>
      </div>
    </header>
  );
}

function Footer() {
  return (
    <footer className="border-t border-surface-800 py-6">
      <div className="mx-auto max-w-7xl px-4 text-center text-xs text-slate-600 sm:px-6">
        Event-driven microservices · Spring Boot · Kafka · PostgreSQL · Redis ·
        Kubernetes
      </div>
    </footer>
  );
}

export default function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <Nav />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/applications" element={<Applications />} />
          <Route path="/reminders" element={<Reminders />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
