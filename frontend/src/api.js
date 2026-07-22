/**
 * API client for the three backend microservices.
 *
 * Every call uses a relative /api/v1/... path. In production nginx reverse-proxies
 * those paths to application-service / notification-service / analytics-service;
 * in dev, Vite's proxy does the same. The browser therefore only ever sees one
 * origin, which is why no CORS configuration is needed on the backend.
 */

async function request(path, options = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  if (!res.ok) {
    let detail = "";
    try {
      detail = await res.text();
    } catch {
      /* body may be empty */
    }
    throw new Error(
      `${options.method || "GET"} ${path} failed (${res.status})${detail ? `: ${detail}` : ""}`
    );
  }

  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/* ----------------------------- application-service ----------------------------- */

export const ApplicationsApi = {
  list: (status) =>
    request(
      `/api/v1/applications${status ? `?status=${encodeURIComponent(status)}` : ""}`
    ),
  get: (id) => request(`/api/v1/applications/${id}`),
  create: (payload) =>
    request("/api/v1/applications", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateStatus: (id, status) =>
    request(`/api/v1/applications/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  remove: (id) => request(`/api/v1/applications/${id}`, { method: "DELETE" }),
};

/* ---------------------------- notification-service ---------------------------- */

export const RemindersApi = {
  list: () => request("/api/v1/reminders"),
  summary: () => request("/api/v1/reminders/summary"),
};

/* ------------------------------ analytics-service ------------------------------ */

export const AnalyticsApi = {
  summary: () => request("/api/v1/analytics/summary"),
  byStatus: () => request("/api/v1/analytics/by-status"),
  byCompany: () => request("/api/v1/analytics/by-company"),
  byResume: () => request("/api/v1/analytics/by-resume"),
  funnel: () => request("/api/v1/analytics/funnel"),
};

/* ----------------------------------- shared ----------------------------------- */

export const STATUSES = [
  "APPLIED",
  "OA_ROUND",
  "INTERVIEW_SCHEDULED",
  "INTERVIEWED",
  "OFFER_RECEIVED",
  "REJECTED",
  "WITHDRAWN",
];

/** Terminal statuses — an application in one of these is no longer progressing. */
export const CLOSED_STATUSES = new Set(["REJECTED", "WITHDRAWN"]);

export function prettyStatus(status) {
  if (!status) return "";
  return status
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}
