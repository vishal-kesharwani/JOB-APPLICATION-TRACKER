package com.jobtracker.application.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ApplicationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter eventsPublishedCounter;
    private final Counter eventsPublishFailedCounter;

    public ApplicationEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                     MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventsPublishedCounter = Counter.builder("kafka_events_published_total")
                .description("Total number of Kafka events successfully published")
                .register(meterRegistry);
        this.eventsPublishFailedCounter = Counter.builder("kafka_events_publish_failed_total")
                .description("Total number of Kafka events that failed to publish")
                .register(meterRegistry);
    }

    public void publishApplicationCreated(ApplicationCreatedEvent event) {
        publish(KafkaTopics.APPLICATION_CREATED, String.valueOf(event.getApplicationId()), event);
    }

    public void publishStatusUpdated(ApplicationStatusUpdatedEvent event) {
        publish(KafkaTopics.APPLICATION_STATUS_UPDATED, String.valueOf(event.getApplicationId()), event);
    }

    public void publishApplicationDeleted(ApplicationDeletedEvent event) {
        publish(KafkaTopics.APPLICATION_DELETED, String.valueOf(event.getApplicationId()), event);
    }

    private void publish(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("Published event to topic={} key={}", topic, key);
            } else {
                eventsPublishFailedCounter.increment();
                log.error("Failed to publish event to topic={} key={}", topic, key, ex);
            }
        });
    }
}
