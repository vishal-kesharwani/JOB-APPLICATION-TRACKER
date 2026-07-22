package com.jobtracker.application.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event published to the "application-created" topic after a new
 * application row is successfully committed to PostgreSQL.
 *
 * Consumed by: notification-service (schedules follow-up reminder),
 *              analytics-service (updates aggregate counters)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreatedEvent {

    private Long applicationId;
    private String company;
    private String role;
    private String resumeVersion;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appliedDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime eventTimestamp;
}
