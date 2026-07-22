package com.jobtracker.analytics.dto;

import java.util.Map;

/**
 * Top-level snapshot of the pipeline: how many live applications exist, how they
 * break down by current status, and the historical conversion funnel with rates.
 */
public record AnalyticsSummaryResponse(
        long totalApplications,
        Map<String, Long> byStatus,
        FunnelView funnel) {

    public record FunnelView(
            long totalCreated,
            long reachedInterview,
            long reachedOffer,
            long rejected,
            long withdrawn,
            double interviewRate,
            double offerRate) {
    }

    public static AnalyticsSummaryResponse of(long total,
                                              Map<String, Long> byStatus,
                                              Map<String, Long> funnel) {
        long created = funnel.getOrDefault("totalCreated", 0L);
        long interview = funnel.getOrDefault("reachedInterview", 0L);
        long offer = funnel.getOrDefault("reachedOffer", 0L);
        long rejected = funnel.getOrDefault("rejected", 0L);
        long withdrawn = funnel.getOrDefault("withdrawn", 0L);

        FunnelView view = new FunnelView(
                created, interview, offer, rejected, withdrawn,
                rate(interview, created),
                rate(offer, created));

        return new AnalyticsSummaryResponse(total, byStatus, view);
    }

    private static double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0) / denominator) / 100.0; // percentage, 2 dp
    }
}
