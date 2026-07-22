import { useEffect } from "react";
import { CLOSED_STATUSES, prettyStatus } from "../api.js";

/* --------------------------------- badges --------------------------------- */

const STATUS_STYLES = {
  APPLIED: "bg-slate-700/50 text-slate-300 border-slate-600",
  OA_ROUND: "bg-sky-900/40 text-sky-300 border-sky-800",
  INTERVIEW_SCHEDULED: "bg-amber-900/40 text-amber-300 border-amber-800",
  INTERVIEWED: "bg-violet-900/40 text-violet-300 border-violet-800",
  OFFER_RECEIVED: "bg-emerald-900/40 text-emerald-300 border-emerald-800",
  REJECTED: "bg-red-950/50 text-red-400 border-red-900",
  WITHDRAWN: "bg-surface-800 text-slate-400 border-surface-700",
};

export function StatusBadge({ status }) {
  const cls = STATUS_STYLES[status] || STATUS_STYLES.APPLIED;
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${cls}`}
    >
      {prettyStatus(status)}
    </span>
  );
}

const REMINDER_STATUS_STYLES = {
  SCHEDULED: "bg-indigo-900/40 text-indigo-300 border-indigo-800",
  TRIGGERED: "bg-emerald-900/40 text-emerald-300 border-emerald-800",
  CANCELLED: "bg-surface-800 text-slate-400 border-surface-700",
};

export function ReminderBadge({ status }) {
  const cls = REMINDER_STATUS_STYLES[status] || REMINDER_STATUS_STYLES.SCHEDULED;
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${cls}`}
    >
      {prettyStatus(status)}
    </span>
  );
}

/* --------------------------------- layout --------------------------------- */

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold text-white">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, tone = "default" }) {
  const toneCls =
    {
      default: "text-white",
      good: "text-emerald-400",
      warn: "text-amber-400",
      bad: "text-red-400",
    }[tone] || "text-white";

  return (
    <div className="card">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </p>
      <p className={`mt-2 text-3xl font-semibold ${toneCls}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

/**
 * Horizontal bar list — used for by-company / by-status breakdowns.
 *
 * `labelFor` defaults to the raw key: company names like "Atidan Technologies"
 * must not be run through prettyStatus (it would lowercase the second word).
 * Status charts pass labelFor={prettyStatus} explicitly.
 */
export function BarList({ data, emptyText = "No data yet", colorFor, labelFor }) {
  const entries = Object.entries(data || {});
  if (entries.length === 0) {
    return <p className="py-6 text-center text-sm text-slate-500">{emptyText}</p>;
  }
  const max = Math.max(...entries.map(([, v]) => v), 1);
  const label = labelFor || ((k) => k);

  return (
    <ul className="space-y-3">
      {entries.map(([key, value]) => (
        <li key={key}>
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="truncate text-slate-300">{label(key)}</span>
            <span className="ml-3 shrink-0 font-medium text-slate-400">{value}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-surface-800">
            <div
              className={`h-full rounded-full ${colorFor ? colorFor(key) : "bg-indigo-500"}`}
              style={{ width: `${(value / max) * 100}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}

/* --------------------------------- states --------------------------------- */

export function Spinner({ label = "Loading…" }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-sm text-slate-400">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-600 border-t-indigo-400" />
      {label}
    </div>
  );
}

export function ErrorBanner({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="mb-4 flex items-start justify-between gap-4 rounded-lg border border-red-900/60 bg-red-950/30 p-4">
      <div>
        <p className="text-sm font-medium text-red-300">Something went wrong</p>
        <p className="mt-1 break-words text-xs text-red-400/80">{error.message}</p>
        <p className="mt-2 text-xs text-slate-500">
          If the backend is still starting up, give it a moment — the services take
          a couple of minutes on a cold start.
        </p>
      </div>
      {onRetry && (
        <button onClick={onRetry} className="btn-ghost shrink-0">
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action }) {
  return (
    <div className="card flex flex-col items-center justify-center py-14 text-center">
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {description && (
        <p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

/* --------------------------------- modal ---------------------------------- */

export function Modal({ open, title, onClose, children }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => e.key === "Escape" && onClose?.();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/70 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="relative z-10 w-full max-w-lg rounded-xl border border-surface-700 bg-surface-900 p-6 shadow-2xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-white">{title}</h2>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-slate-400 hover:bg-surface-800 hover:text-slate-200"
            aria-label="Close"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/* --------------------------------- helpers -------------------------------- */

export function formatDateTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDate(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function isClosed(status) {
  return CLOSED_STATUSES.has(status);
}
