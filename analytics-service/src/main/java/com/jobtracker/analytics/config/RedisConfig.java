package com.jobtracker.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Analytics keeps all aggregates in Redis as counters and hashes, so a
 * {@link StringRedisTemplate} is all we need. Redis is a read-model cache here:
 * it is derived entirely from Kafka events and can be rebuilt by replaying the
 * topics from the beginning (consumer group uses auto-offset-reset=earliest).
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
