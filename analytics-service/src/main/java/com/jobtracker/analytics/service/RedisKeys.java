package com.jobtracker.analytics.service;

/**
 * Central definition of every Redis key the analytics read-model uses.
 * Keeping them in one place avoids typo-driven drift between the writer
 * (event listener) and the reader (reporting controller).
 */
public final class RedisKeys {

    /** Namespace prefix so analytics keys never collide with other tenants of the same Redis. */
    public static final String NS = "analytics:";

    /** String counter: total number of live (non-deleted) applications. */
    public static final String TOTAL = NS + "applications:total";

    /** Hash: status name -> count of applications currently in that status. */
    public static final String STATUS_COUNTS = NS + "status:counts";

    /** Hash: company -> number of applications sent to that company. */
    public static final String COMPANY_COUNTS = NS + "company:counts";

    /** Hash: resume version -> number of applications sent with it. */
    public static final String RESUME_COUNTS = NS + "resume:counts";

    /** Hash: cumulative lifecycle outcomes (offers, rejections, interviews reached...). */
    public static final String FUNNEL = NS + "funnel";

    private RedisKeys() {
        // constants only
    }

    /** Per-application state we must remember to correctly decrement aggregates later. */
    public static String appState(Long applicationId) {
        return NS + "app:" + applicationId;
    }

    // Hash fields inside appState(id)
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_COMPANY = "company";
    public static final String FIELD_RESUME = "resumeVersion";

    // Funnel hash fields
    public static final String FUNNEL_TOTAL_CREATED = "totalCreated";
    public static final String FUNNEL_REACHED_INTERVIEW = "reachedInterview";
    public static final String FUNNEL_REACHED_OFFER = "reachedOffer";
    public static final String FUNNEL_REJECTED = "rejected";
    public static final String FUNNEL_WITHDRAWN = "withdrawn";
}
