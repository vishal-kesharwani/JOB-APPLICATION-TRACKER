package com.jobtracker.analytics.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordConsumed(String topic, String eventType) {
        meterRegistry.counter("analytics_events_consumed_total",
                "topic", topic,
                "event_type", eventType).increment();
    }

    public void recordAggregationError(String eventType) {
        meterRegistry.counter("analytics_aggregation_errors_total",
                "event_type", eventType).increment();
    }
}
