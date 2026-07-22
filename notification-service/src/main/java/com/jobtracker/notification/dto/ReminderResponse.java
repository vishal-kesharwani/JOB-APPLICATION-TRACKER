package com.jobtracker.notification.dto;

import com.jobtracker.notification.model.ReminderStatus;
import com.jobtracker.notification.model.ReminderType;
import com.jobtracker.notification.model.ScheduledReminder;

import java.time.LocalDateTime;

public record ReminderResponse(
        Long reminderId,
        Long applicationId,
        String company,
        String role,
        ReminderType reminderType,
        String message,
        LocalDateTime scheduledFor,
        ReminderStatus status,
        LocalDateTime createdAt,
        LocalDateTime triggeredAt,
        LocalDateTime cancelledAt,
        String cancellationReason) {

    public static ReminderResponse from(ScheduledReminder reminder) {
        return new ReminderResponse(
                reminder.getReminderId(),
                reminder.getApplicationId(),
                reminder.getCompany(),
                reminder.getRole(),
                reminder.getReminderType(),
                reminder.getMessage(),
                reminder.getScheduledFor(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getTriggeredAt(),
                reminder.getCancelledAt(),
                reminder.getCancellationReason());
    }
}
