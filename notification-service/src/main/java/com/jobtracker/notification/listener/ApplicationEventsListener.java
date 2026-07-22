package com.jobtracker.notification.listener;

import com.jobtracker.notification.config.NotificationProperties;
import com.jobtracker.notification.kafka.ApplicationCreatedEvent;
import com.jobtracker.notification.kafka.ApplicationDeletedEvent;
import com.jobtracker.notification.kafka.ApplicationStatus;
import com.jobtracker.notification.kafka.ApplicationStatusUpdatedEvent;
import com.jobtracker.notification.kafka.KafkaTopics;
import com.jobtracker.notification.model.ReminderType;
import com.jobtracker.notification.service.NotificationMetricsService;
import com.jobtracker.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationEventsListener {

    private final ReminderService reminderService;
    private final NotificationMetricsService metrics;
    private final NotificationProperties properties;

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_CREATED,
            groupId = "notification-service",
            containerFactory = "applicationCreatedKafkaListenerContainerFactory")
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_CREATED, "ApplicationCreatedEvent");
        log.info("Consumed application-created event for application {} at {}",
                event.getApplicationId(), event.getEventTimestamp());

        reminderService.scheduleReminder(
                event.getApplicationId(),
                event.getCompany(),
                event.getRole(),
                ReminderType.FOLLOW_UP,
                "Follow up on " + event.getCompany() + " application for " + event.getRole(),
                properties.getFollowUpReminderDelay());
    }

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_STATUS_UPDATED,
            groupId = "notification-service",
            containerFactory = "applicationStatusUpdatedKafkaListenerContainerFactory")
    public void onApplicationStatusUpdated(ApplicationStatusUpdatedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_STATUS_UPDATED, "ApplicationStatusUpdatedEvent");
        log.info("Consumed application-status-updated event for application {} => {}",
                event.getApplicationId(), event.getNewStatus());

        ApplicationStatus newStatus = event.getNewStatus();
        if (newStatus == ApplicationStatus.INTERVIEW_SCHEDULED) {
            reminderService.scheduleReminder(
                    event.getApplicationId(),
                    event.getCompany(),
                    event.getRole(),
                    ReminderType.INTERVIEW_PREP,
                    "Prepare for interview at " + event.getCompany() + " for " + event.getRole(),
                    properties.getInterviewPrepReminderDelay());
            return;
        }

        if (newStatus == ApplicationStatus.OFFER_RECEIVED) {
            reminderService.scheduleReminder(
                    event.getApplicationId(),
                    event.getCompany(),
                    event.getRole(),
                    ReminderType.OFFER_FOLLOW_UP,
                    "Follow up on offer discussion with " + event.getCompany(),
                    properties.getOfferFollowUpReminderDelay());
            return;
        }

        if (newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.WITHDRAWN) {
            reminderService.cancelForApplication(
                    event.getApplicationId(),
                    "application_closed_status_" + newStatus.name().toLowerCase());
        }
    }

    @KafkaListener(
            topics = KafkaTopics.APPLICATION_DELETED,
            groupId = "notification-service",
            containerFactory = "applicationDeletedKafkaListenerContainerFactory")
    public void onApplicationDeleted(ApplicationDeletedEvent event) {
        metrics.recordConsumed(KafkaTopics.APPLICATION_DELETED, "ApplicationDeletedEvent");
        log.info("Consumed application-deleted event for application {}", event.getApplicationId());

        reminderService.cancelForApplication(event.getApplicationId(), "application_deleted");
    }
}
