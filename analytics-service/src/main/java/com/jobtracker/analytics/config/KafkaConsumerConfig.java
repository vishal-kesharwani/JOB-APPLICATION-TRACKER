package com.jobtracker.analytics.config;

import com.jobtracker.analytics.kafka.ApplicationCreatedEvent;
import com.jobtracker.analytics.kafka.ApplicationDeletedEvent;
import com.jobtracker.analytics.kafka.ApplicationStatusUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final String GROUP_ID = "analytics-service";

    private final String bootstrapServers;

    public KafkaConsumerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
    }

    @Bean
    public ConsumerFactory<String, ApplicationCreatedEvent> applicationCreatedConsumerFactory() {
        return consumerFactory(ApplicationCreatedEvent.class);
    }

    @Bean(name = "applicationCreatedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, ApplicationCreatedEvent> applicationCreatedKafkaListenerContainerFactory(
            CommonErrorHandler kafkaErrorHandler) {
        return containerFactory(applicationCreatedConsumerFactory(), kafkaErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, ApplicationStatusUpdatedEvent> applicationStatusUpdatedConsumerFactory() {
        return consumerFactory(ApplicationStatusUpdatedEvent.class);
    }

    @Bean(name = "applicationStatusUpdatedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, ApplicationStatusUpdatedEvent> applicationStatusUpdatedKafkaListenerContainerFactory(
            CommonErrorHandler kafkaErrorHandler) {
        return containerFactory(applicationStatusUpdatedConsumerFactory(), kafkaErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, ApplicationDeletedEvent> applicationDeletedConsumerFactory() {
        return consumerFactory(ApplicationDeletedEvent.class);
    }

    @Bean(name = "applicationDeletedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, ApplicationDeletedEvent> applicationDeletedKafkaListenerContainerFactory(
            CommonErrorHandler kafkaErrorHandler) {
        return containerFactory(applicationDeletedConsumerFactory(), kafkaErrorHandler);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> valueClass) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(valueClass);
        valueDeserializer.addTrustedPackages("com.jobtracker.analytics.kafka");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(
            ConsumerFactory<String, T> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
