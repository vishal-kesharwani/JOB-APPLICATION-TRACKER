package com.jobtracker.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordConsumed(String topic, String eventType) {
        meterRegistry.counter("notification_events_consumed_total",
                "topic", topic,
                "event_type", eventType).increment();
    }

    public void recordScheduled(String reminderType) {
        meterRegistry.counter("reminders_scheduled_total",
                "reminder_type", reminderType).increment();
    }

    public void recordTriggered(String reminderType) {
        meterRegistry.counter("reminders_triggered_total",
                "reminder_type", reminderType).increment();
    }

    public void recordCancelled(String reminderType, String reason) {
        meterRegistry.counter("reminders_cancelled_total",
                "reminder_type", reminderType,
                "reason", reason).increment();
    }
}
