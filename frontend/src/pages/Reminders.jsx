import { useMemo, useState } from "react";
import { RemindersApi, prettyStatus } from "../api.js";
import { useFetch } from "../hooks/useFetch.js";
import {
  EmptyState,
  ErrorBanner,
  PageHeader,
  ReminderBadge,
  Spinner,
  StatCard,
  formatDateTime,
} from "../components/ui.jsx";

const TYPE_LABELS = {
  FOLLOW_UP: "Follow up",
  INTERVIEW_PREP: "Interview prep",
  OFFER_FOLLOW_UP: "Offer follow-up",
};

const FILTERS = ["ALL", "SCHEDULED", "TRIGGERED", "CANCELLED"];

export default function Reminders() {
  const [filter, setFilter] = useState("ALL");

  // Poll frequently — reminders are scheduled asynchronously by the
  // notification-service after it consumes an event, so they appear on a delay.
  const list = useFetch(() => RemindersApi.list(), { intervalMs: 5000 });
  const summary = useFetch(() => RemindersApi.summary(), { intervalMs: 5000 });

  const reminders = useMemo(() => {
    const items = Array.isArray(list.data) ? list.data : [];
    return items
      .filter((r) => (filter === "ALL" ? true : r.status === filter))
      .sort((a, b) => new Date(b.scheduledFor) - new Date(a.scheduledFor));
  }, [list.data, filter]);

  const s = summary.data;

  return (
    <div>
      <PageHeader
        title="Reminders"
        subtitle="Scheduled by notification-service in response to Kafka events. This view refreshes every 5s."
      />

      <ErrorBanner error={list.error || summary.error} onRetry={list.reload} />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total" value={s?.totalReminders ?? 0} />
        <StatCard label="Scheduled" value={s?.scheduledReminders ?? 0} hint="Waiting to fire" />
        <StatCard
          label="Triggered"
          value={s?.triggeredReminders ?? 0}
          hint="Already fired"
          tone="good"
        />
        <StatCard label="Cancelled" value={s?.cancelledReminders ?? 0} hint="Closed or deleted" />
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={
              filter === f
                ? "btn bg-surface-800 text-white"
                : "btn-ghost"
            }
          >
            {f === "ALL" ? "All" : prettyStatus(f)}
          </button>
        ))}
      </div>

      {list.loading && !list.data ? (
        <Spinner label="Loading reminders…" />
      ) : reminders.length === 0 ? (
        <EmptyState
          title={
            list.data?.length
              ? `No ${prettyStatus(filter).toLowerCase()} reminders`
              : "No reminders yet"
          }
          description={
            list.data?.length
              ? "Try a different filter."
              : "Create an application, or move one to Interview Scheduled or Offer Received — notification-service will schedule a reminder within a few seconds."
          }
        />
      ) : (
        <ul className="space-y-3">
          {reminders.map((r) => (
            <li key={r.reminderId} className="card">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-white">
                      {r.company}
                    </span>
                    {r.role && (
                      <span className="text-sm text-slate-400">· {r.role}</span>
                    )}
                    <ReminderBadge status={r.status} />
                    <span className="rounded border border-surface-700 px-2 py-0.5 text-[11px] text-slate-400">
                      {TYPE_LABELS[r.reminderType] || prettyStatus(r.reminderType)}
                    </span>
                  </div>

                  <p className="mt-2 text-sm text-slate-300">{r.message}</p>

                  <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-slate-500">
                    <span>Scheduled for {formatDateTime(r.scheduledFor)}</span>
                    {r.triggeredAt && (
                      <span className="text-emerald-500/80">
                        Triggered {formatDateTime(r.triggeredAt)}
                      </span>
                    )}
                    {r.cancelledAt && (
                      <span>
                        Cancelled {formatDateTime(r.cancelledAt)}
                        {r.cancellationReason ? ` · ${r.cancellationReason}` : ""}
                      </span>
                    )}
                  </div>
                </div>

                <span className="shrink-0 text-xs text-slate-600">
                  app #{r.applicationId}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
