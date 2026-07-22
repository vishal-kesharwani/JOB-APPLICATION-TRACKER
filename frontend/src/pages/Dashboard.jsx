import { AnalyticsApi, prettyStatus } from "../api.js";
import { useFetch } from "../hooks/useFetch.js";
import {
  BarList,
  ErrorBanner,
  PageHeader,
  Spinner,
  StatCard,
} from "../components/ui.jsx";

const STATUS_BAR_COLORS = {
  APPLIED: "bg-slate-500",
  OA_ROUND: "bg-sky-500",
  INTERVIEW_SCHEDULED: "bg-amber-500",
  INTERVIEWED: "bg-violet-500",
  OFFER_RECEIVED: "bg-emerald-500",
  REJECTED: "bg-red-600",
  WITHDRAWN: "bg-surface-700",
};

/** Visual funnel: created → interview → offer, each stage as a share of created. */
function Funnel({ funnel }) {
  const created = funnel?.totalCreated || 0;
  const stages = [
    { key: "Applied", value: created, color: "bg-indigo-500" },
    { key: "Reached interview", value: funnel?.reachedInterview || 0, color: "bg-amber-500" },
    { key: "Reached offer", value: funnel?.reachedOffer || 0, color: "bg-emerald-500" },
  ];

  return (
    <div className="space-y-4">
      {stages.map((stage) => {
        const pct = created ? (stage.value / created) * 100 : 0;
        return (
          <div key={stage.key}>
            <div className="mb-1.5 flex items-baseline justify-between text-sm">
              <span className="text-slate-300">{stage.key}</span>
              <span className="text-slate-400">
                <span className="font-semibold text-white">{stage.value}</span>
                {created > 0 && (
                  <span className="ml-2 text-xs text-slate-500">
                    {pct.toFixed(1)}%
                  </span>
                )}
              </span>
            </div>
            <div className="h-3 overflow-hidden rounded-full bg-surface-800">
              <div
                className={`h-full rounded-full ${stage.color} transition-all duration-500`}
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>
        );
      })}

      <div className="grid grid-cols-2 gap-3 border-t border-surface-800 pt-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Rejected</p>
          <p className="mt-1 text-xl font-semibold text-red-400">
            {funnel?.rejected || 0}
          </p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Withdrawn</p>
          <p className="mt-1 text-xl font-semibold text-slate-400">
            {funnel?.withdrawn || 0}
          </p>
        </div>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const summary = useFetch(() => AnalyticsApi.summary(), { intervalMs: 10000 });
  const byCompany = useFetch(() => AnalyticsApi.byCompany(), { intervalMs: 15000 });
  const byResume = useFetch(() => AnalyticsApi.byResume(), { intervalMs: 15000 });

  const s = summary.data;
  const funnel = s?.funnel;

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle="Live aggregates from analytics-service — derived entirely from Kafka events, cached in Redis."
      />

      <ErrorBanner error={summary.error} onRetry={summary.reload} />

      {summary.loading && !s ? (
        <Spinner label="Loading analytics…" />
      ) : (
        <>
          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Active applications"
              value={s?.totalApplications ?? 0}
              hint="Currently tracked"
            />
            <StatCard
              label="Total submitted"
              value={funnel?.totalCreated ?? 0}
              hint="All time, including deleted"
            />
            <StatCard
              label="Interview rate"
              value={`${funnel?.interviewRate ?? 0}%`}
              hint={`${funnel?.reachedInterview ?? 0} reached interview`}
              tone="warn"
            />
            <StatCard
              label="Offer rate"
              value={`${funnel?.offerRate ?? 0}%`}
              hint={`${funnel?.reachedOffer ?? 0} offers received`}
              tone="good"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <section className="card">
              <h2 className="mb-4 text-sm font-semibold text-white">
                Conversion funnel
              </h2>
              <Funnel funnel={funnel} />
            </section>

            <section className="card">
              <h2 className="mb-4 text-sm font-semibold text-white">
                Pipeline by status
              </h2>
              <BarList
                data={s?.byStatus}
                emptyText="No applications yet — create one to populate this."
                labelFor={prettyStatus}
                colorFor={(key) => STATUS_BAR_COLORS[key] || "bg-indigo-500"}
              />
            </section>

            <section className="card">
              <h2 className="mb-4 text-sm font-semibold text-white">By company</h2>
              <BarList data={byCompany.data} emptyText="No applications yet." />
            </section>

            <section className="card">
              <h2 className="mb-4 text-sm font-semibold text-white">
                By résumé version
              </h2>
              <p className="mb-4 -mt-2 text-xs text-slate-500">
                Which résumé variant is generating the most applications.
              </p>
              <BarList data={byResume.data} emptyText="No applications yet." />
            </section>
          </div>
        </>
      )}
    </div>
  );
}
