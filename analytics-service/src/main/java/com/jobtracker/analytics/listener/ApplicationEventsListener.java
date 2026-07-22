package com.jobtracker.analytics.listener;

import com.jobtracker.analytics.kafka.ApplicationCreatedEvent;
import com.jobtracker.analytics.kafka.ApplicationDeletedEvent;
import com.jobtracker.analytics.kafka.ApplicationStatusUpdatedEvent;
import com.jobtracker.analytics.kafka.KafkaTopics;
import com.jobtracker.analytics.service.AnalyticsMetricsService;
import com.jobtracker.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationEventsListener {

    private final AnalyticsService analyticsService;
    private final AnalyticsMetricsService metrics;

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_CREATED,
            groupId = "analytics-service",
            containerFactory = "applicationCreatedKafkaListenerContainerFactory")
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_CREATED, "ApplicationCreatedEvent");
        log.info("Consumed application-created for application {} ({})",
                event.getApplicationId(), event.getCompany());
        try {
            analyticsService.onApplicationCreated(event);
        } catch (RuntimeException ex) {
            metrics.recordAggregationError("ApplicationCreatedEvent");
            throw ex;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_STATUS_UPDATED,
            groupId = "analytics-service",
            containerFactory = "applicationStatusUpdatedKafkaListenerContainerFactory")
    public void onApplicationStatusUpdated(ApplicationStatusUpdatedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_STATUS_UPDATED, "ApplicationStatusUpdatedEvent");
        log.info("Consumed application-status-updated for application {} => {}",
                event.getApplicationId(), event.getNewStatus());
        try {
            analyticsService.onApplicationStatusUpdated(event);
        } catch (RuntimeException ex) {
            metrics.recordAggregationError("ApplicationStatusUpdatedEvent");
            throw ex;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_DELETED,
            groupId = "analytics-service",
            containerFactory = "applicationDeletedKafkaListenerContainerFactory")
    public void onApplicationDeleted(ApplicationDeletedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_DELETED, "ApplicationDeletedEvent");
        log.info("Consumed application-deleted for application {}", event.getApplicationId());
        try {
            analyticsService.onApplicationDeleted(event);
        } catch (RuntimeException ex) {
            metrics.recordAggregationError("ApplicationDeletedEvent");
            throw ex;
        }
    }
}
