import { useMemo, useState } from "react";
import { ApplicationsApi, STATUSES, prettyStatus } from "../api.js";
import { useFetch } from "../hooks/useFetch.js";
import {
  EmptyState,
  ErrorBanner,
  Modal,
  PageHeader,
  Spinner,
  StatusBadge,
  formatDate,
  isClosed,
} from "../components/ui.jsx";

const EMPTY_FORM = {
  company: "",
  role: "",
  appliedDate: new Date().toISOString().slice(0, 10),
  resumeVersion: "",
};

function NewApplicationForm({ onCreated, onCancel }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await ApplicationsApi.create(form);
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && (
        <p className="rounded-lg border border-red-900/60 bg-red-950/30 p-3 text-xs text-red-300">
          {error.message}
        </p>
      )}

      <div>
        <label className="label" htmlFor="company">Company</label>
        <input
          id="company"
          className="input"
          required
          autoFocus
          placeholder="e.g. Netflix"
          value={form.company}
          onChange={set("company")}
        />
      </div>

      <div>
        <label className="label" htmlFor="role">Role</label>
        <input
          id="role"
          className="input"
          required
          placeholder="e.g. Backend Engineer"
          value={form.role}
          onChange={set("role")}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor="appliedDate">Applied date</label>
          <input
            id="appliedDate"
            type="date"
            className="input"
            required
            value={form.appliedDate}
            onChange={set("appliedDate")}
          />
        </div>
        <div>
          <label className="label" htmlFor="resumeVersion">Résumé version</label>
          <input
            id="resumeVersion"
            className="input"
            placeholder="e.g. java-backend"
            value={form.resumeVersion}
            onChange={set("resumeVersion")}
          />
        </div>
      </div>

      <p className="text-xs text-slate-500">
        Creating an application publishes an <code>application-created</code> event
        to Kafka — the notification and analytics services react to it.
      </p>

      <div className="flex justify-end gap-2 pt-1">
        <button type="button" onClick={onCancel} className="btn-ghost">
          Cancel
        </button>
        <button type="submit" disabled={submitting} className="btn-primary">
          {submitting ? "Creating…" : "Create application"}
        </button>
      </div>
    </form>
  );
}

export default function Applications() {
  const [showNew, setShowNew] = useState(false);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState(null);

  const { data, error, loading, reload } = useFetch(() => ApplicationsApi.list(), {
    intervalMs: 15000,
  });

  const applications = useMemo(() => {
    const list = Array.isArray(data) ? data : [];
    const q = query.trim().toLowerCase();
    return list
      .filter((a) => (statusFilter ? a.status === statusFilter : true))
      .filter((a) =>
        q
          ? `${a.company} ${a.role} ${a.resumeVersion || ""}`.toLowerCase().includes(q)
          : true
      )
      .sort((a, b) => b.id - a.id);
  }, [data, query, statusFilter]);

  async function changeStatus(app, status) {
    if (status === app.status) return;
    setBusyId(app.id);
    setActionError(null);
    try {
      await ApplicationsApi.updateStatus(app.id, status);
      await reload({ quiet: true });
    } catch (err) {
      setActionError(err);
    } finally {
      setBusyId(null);
    }
  }

  async function remove(app) {
    if (!window.confirm(`Delete the ${app.company} — ${app.role} application?`)) return;
    setBusyId(app.id);
    setActionError(null);
    try {
      await ApplicationsApi.remove(app.id);
      await reload({ quiet: true });
    } catch (err) {
      setActionError(err);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Applications"
        subtitle="Every create, status change, and delete publishes a Kafka event."
        actions={
          <button onClick={() => setShowNew(true)} className="btn-primary">
            + New application
          </button>
        }
      />

      <ErrorBanner error={error || actionError} onRetry={reload} />

      <div className="mb-4 flex flex-wrap gap-3">
        <input
          className="input max-w-xs"
          placeholder="Search company, role, résumé…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          className="input max-w-[220px]"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {prettyStatus(s)}
            </option>
          ))}
        </select>
        {(query || statusFilter) && (
          <button
            className="btn-ghost"
            onClick={() => {
              setQuery("");
              setStatusFilter("");
            }}
          >
            Clear
          </button>
        )}
      </div>

      {loading && !data ? (
        <Spinner label="Loading applications…" />
      ) : applications.length === 0 ? (
        <EmptyState
          title={data?.length ? "No applications match your filters" : "No applications yet"}
          description={
            data?.length
              ? "Try clearing the search or status filter."
              : "Create your first application to see the event-driven pipeline in action."
          }
          action={
            !data?.length && (
              <button onClick={() => setShowNew(true)} className="btn-primary">
                + New application
              </button>
            )
          }
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-surface-800">
          <table className="min-w-full divide-y divide-surface-800">
            <thead className="bg-surface-900">
              <tr>
                {["Company", "Role", "Applied", "Résumé", "Status", ""].map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-400"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-800 bg-surface-950">
              {applications.map((app) => (
                <tr
                  key={app.id}
                  className={`transition-opacity hover:bg-surface-900/60 ${
                    busyId === app.id ? "opacity-50" : ""
                  }`}
                >
                  <td className="px-4 py-3 text-sm font-medium text-white">
                    {app.company}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-300">{app.role}</td>
                  <td className="px-4 py-3 text-sm text-slate-400">
                    {formatDate(app.appliedDate)}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-400">
                    {app.resumeVersion || "—"}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <StatusBadge status={app.status} />
                      {isClosed(app.status) && (
                        <span className="text-[11px] text-slate-600">closed</span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-2">
                      <select
                        aria-label={`Change status for ${app.company}`}
                        className="input max-w-[190px] py-1.5 text-xs"
                        value={app.status}
                        disabled={busyId === app.id}
                        onChange={(e) => changeStatus(app, e.target.value)}
                      >
                        {STATUSES.map((s) => (
                          <option key={s} value={s}>
                            {prettyStatus(s)}
                          </option>
                        ))}
                      </select>
                      <button
                        onClick={() => remove(app)}
                        disabled={busyId === app.id}
                        className="btn-danger px-3 py-1.5 text-xs"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={showNew}
        title="New application"
        onClose={() => setShowNew(false)}
      >
        <NewApplicationForm
          onCancel={() => setShowNew(false)}
          onCreated={() => {
            setShowNew(false);
            reload({ quiet: true });
          }}
        />
      </Modal>
    </div>
  );
}
