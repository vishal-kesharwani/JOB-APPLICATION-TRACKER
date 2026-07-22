package com.jobtracker.analytics.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit test for the funnel rate maths — no Redis/Kafka context required,
 * so it runs fast in CI without any infrastructure.
 */
class AnalyticsSummaryResponseTest {

    @Test
    void computesInterviewAndOfferRatesAsPercentages() {
        Map<String, Long> funnel = Map.of(
                "totalCreated", 20L,
                "reachedInterview", 5L,
                "reachedOffer", 2L,
                "rejected", 8L,
                "withdrawn", 1L);

        AnalyticsSummaryResponse response = AnalyticsSummaryResponse.of(
                12L, Map.of("APPLIED", 12L), funnel);

        assertEquals(12L, response.totalApplications());
        assertEquals(25.0, response.funnel().interviewRate());  // 5/20
        assertEquals(10.0, response.funnel().offerRate());      // 2/20
        assertEquals(5L, response.funnel().reachedInterview());
    }

    @Test
    void ratesAreZeroWhenNoApplicationsCreated() {
        AnalyticsSummaryResponse response = AnalyticsSummaryResponse.of(
                0L, Map.of(), Map.of());

        assertEquals(0.0, response.funnel().interviewRate());
        assertEquals(0.0, response.funnel().offerRate());
        assertEquals(0L, response.funnel().totalCreated());
    }
}
