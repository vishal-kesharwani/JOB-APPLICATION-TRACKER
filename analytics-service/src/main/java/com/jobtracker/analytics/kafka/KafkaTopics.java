package com.jobtracker.analytics.kafka;

public final class KafkaTopics {

    public static final String APPLICATION_CREATED = "application-created";
    public static final String APPLICATION_STATUS_UPDATED = "application-status-updated";
    public static final String APPLICATION_DELETED = "application-deleted";

    private KafkaTopics() {
        // constants only
    }
}
