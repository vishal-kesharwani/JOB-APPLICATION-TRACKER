package com.jobtracker.notification.dto;

import com.jobtracker.notification.model.ReminderStatus;
import com.jobtracker.notification.model.ScheduledReminder;

import java.util.List;

public record ReminderSummaryResponse(
        long totalReminders,
        long scheduledReminders,
        long triggeredReminders,
        long cancelledReminders,
        long activeReminders) {

    public static ReminderSummaryResponse from(List<ScheduledReminder> reminders) {
        long scheduled = reminders.stream().filter(reminder -> reminder.getStatus() == ReminderStatus.SCHEDULED).count();
        long triggered = reminders.stream().filter(reminder -> reminder.getStatus() == ReminderStatus.TRIGGERED).count();
        long cancelled = reminders.stream().filter(reminder -> reminder.getStatus() == ReminderStatus.CANCELLED).count();
        long total = reminders.size();
        return new ReminderSummaryResponse(total, scheduled, triggered, cancelled, scheduled);
    }
}
