package com.jobtracker.notification.service;

import com.jobtracker.notification.config.NotificationProperties;
import com.jobtracker.notification.model.ReminderStatus;
import com.jobtracker.notification.model.ReminderType;
import com.jobtracker.notification.model.ScheduledReminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private final TaskScheduler taskScheduler;
    private final NotificationProperties properties;
    private final NotificationMetricsService metrics;

    private final AtomicLong reminderSequence = new AtomicLong(1000);
    private final Map<Long, ScheduledReminder> remindersById = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> futuresByReminderId = new ConcurrentHashMap<>();

    public ScheduledReminder scheduleReminder(
            Long applicationId,
            String company,
            String role,
            ReminderType reminderType,
            String message,
            Duration delay) {

        Duration normalizedDelay = normalizeDelay(delay);
        Long reminderId = reminderSequence.incrementAndGet();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime scheduledFor = LocalDateTime.now().plus(normalizedDelay);

        ScheduledReminder reminder = ScheduledReminder.builder()
                .reminderId(reminderId)
                .applicationId(applicationId)
                .company(company)
                .role(role)
                .reminderType(reminderType)
                .message(message)
                .scheduledFor(scheduledFor)
                .status(ReminderStatus.SCHEDULED)
                .createdAt(createdAt)
                .build();

        remindersById.put(reminderId, reminder);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> triggerReminder(reminderId), Instant.now().plus(normalizedDelay));
        futuresByReminderId.put(reminderId, future);

        metrics.recordScheduled(reminderType.name());
        log.info("Scheduled {} reminder for application {} ({}) at {}", reminderType, applicationId, company, scheduledFor);
        return reminder;
    }

    public int cancelForApplication(Long applicationId, String reason) {
        List<ScheduledReminder> matchingReminders = remindersById.values().stream()
                .filter(reminder -> reminder.getApplicationId().equals(applicationId))
                .filter(reminder -> reminder.getStatus() == ReminderStatus.SCHEDULED)
                .toList();

        int cancelled = 0;
        LocalDateTime cancelledAt = LocalDateTime.now();

        for (ScheduledReminder reminder : matchingReminders) {
            ScheduledFuture<?> future = futuresByReminderId.remove(reminder.getReminderId());
            if (future != null) {
                future.cancel(false);
            }
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminder.setCancelledAt(cancelledAt);
            reminder.setCancellationReason(reason);
            metrics.recordCancelled(reminder.getReminderType().name(), reason);
            cancelled++;
        }

        if (cancelled > 0) {
            log.info("Cancelled {} reminders for application {} because {}", cancelled, applicationId, reason);
        }

        return cancelled;
    }

    public List<ScheduledReminder> listReminders() {
        return remindersById.values().stream()
                .sorted(Comparator.comparing(ScheduledReminder::getScheduledFor).reversed()
                        .thenComparing(ScheduledReminder::getReminderId))
                .collect(Collectors.toList());
    }

    public long countActiveReminders() {
        return remindersById.values().stream()
                .filter(reminder -> reminder.getStatus() == ReminderStatus.SCHEDULED)
                .count();
    }

    public long countTriggeredReminders() {
        return remindersById.values().stream()
                .filter(reminder -> reminder.getStatus() == ReminderStatus.TRIGGERED)
                .count();
    }

    public long countCancelledReminders() {
        return remindersById.values().stream()
                .filter(reminder -> reminder.getStatus() == ReminderStatus.CANCELLED)
                .count();
    }

    public NotificationProperties properties() {
        return properties;
    }

    private void triggerReminder(Long reminderId) {
        ScheduledReminder reminder = remindersById.get(reminderId);
        if (reminder == null || reminder.getStatus() != ReminderStatus.SCHEDULED) {
            return;
        }

        reminder.setStatus(ReminderStatus.TRIGGERED);
        reminder.setTriggeredAt(LocalDateTime.now());
        futuresByReminderId.remove(reminderId);
        metrics.recordTriggered(reminder.getReminderType().name());
        log.info("Triggered {} reminder for application {} ({})", reminder.getReminderType(), reminder.getApplicationId(), reminder.getCompany());
    }

    private Duration normalizeDelay(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return Duration.ofSeconds(1);
        }
        return delay;
    }

    public LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DEFAULT_ZONE);
    }
}
