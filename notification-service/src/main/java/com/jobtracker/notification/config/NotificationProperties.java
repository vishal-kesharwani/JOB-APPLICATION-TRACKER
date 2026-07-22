package com.jobtracker.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /**
     * Dev-friendly defaults so reminders trigger quickly in local demos.
     * In real deployments, tune these to hours/days.
     */
    private Duration followUpReminderDelay = Duration.ofMinutes(10);
    private Duration interviewPrepReminderDelay = Duration.ofMinutes(5);
    private Duration offerFollowUpReminderDelay = Duration.ofMinutes(15);

    private int schedulerPoolSize = 2;
}
