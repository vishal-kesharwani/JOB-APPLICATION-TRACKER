package com.jobtracker.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledReminder {

    private Long reminderId;
    private Long applicationId;
    private String company;
    private String role;
    private ReminderType reminderType;
    private String message;
    private LocalDateTime scheduledFor;
    private ReminderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime triggeredAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
}
