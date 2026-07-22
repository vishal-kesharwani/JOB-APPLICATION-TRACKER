package com.jobtracker.application.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobtracker.application.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published to the "application-status-updated" topic whenever
 * an application's status transitions (e.g. APPLIED -> INTERVIEW_SCHEDULED).
 *
 * Consumed by: notification-service (fires interview/offer-deadline reminders),
 *              analytics-service (updates status-transition metrics)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdatedEvent {

    private Long applicationId;
    private String company;
    private String role;
    private ApplicationStatus previousStatus;
    private ApplicationStatus newStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime eventTimestamp;
}
