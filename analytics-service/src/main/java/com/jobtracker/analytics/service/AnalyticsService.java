package com.jobtracker.analytics.service;

import com.jobtracker.analytics.kafka.ApplicationCreatedEvent;
import com.jobtracker.analytics.kafka.ApplicationDeletedEvent;
import com.jobtracker.analytics.kafka.ApplicationStatus;
import com.jobtracker.analytics.kafka.ApplicationStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maintains the analytics read-model in Redis from the application lifecycle events.
 *
 * <p>Two flavours of aggregate are kept:
 * <ul>
 *   <li><b>Current state</b> (total, status:counts, company:counts, resume:counts) —
 *       incremented and decremented so they always reflect the live set of applications.</li>
 *   <li><b>Historical funnel</b> (funnel hash) — monotonic counters that record how far
 *       applications progressed over time; never decremented, so conversion rates are stable.</li>
 * </ul>
 *
 * <p>Because the whole model is derived from Kafka, it can be rebuilt at any time by
 * flushing Redis and replaying the topics (the consumer group resets to earliest).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StringRedisTemplate redis;

    // ---------------------------------------------------------------------
    // Writers (called by the Kafka listener)
    // ---------------------------------------------------------------------

    public void onApplicationCreated(ApplicationCreatedEvent event) {
        String status = ApplicationStatus.APPLIED.name();
        String company = safe(event.getCompany());
        String resume = safe(event.getResumeVersion());

        redis.opsForValue().increment(RedisKeys.TOTAL);
        redis.opsForHash().increment(RedisKeys.STATUS_COUNTS, status, 1);
        redis.opsForHash().increment(RedisKeys.COMPANY_COUNTS, company, 1);
        redis.opsForHash().increment(RedisKeys.RESUME_COUNTS, resume, 1);
        redis.opsForHash().increment(RedisKeys.FUNNEL, RedisKeys.FUNNEL_TOTAL_CREATED, 1);

        String stateKey = RedisKeys.appState(event.getApplicationId());
        redis.opsForHash().put(stateKey, RedisKeys.FIELD_STATUS, status);
        redis.opsForHash().put(stateKey, RedisKeys.FIELD_COMPANY, company);
        redis.opsForHash().put(stateKey, RedisKeys.FIELD_RESUME, resume);
    }

    public void onApplicationStatusUpdated(ApplicationStatusUpdatedEvent event) {
        String stateKey = RedisKeys.appState(event.getApplicationId());
        Object previous = redis.opsForHash().get(stateKey, RedisKeys.FIELD_STATUS);
        String newStatus = event.getNewStatus().name();

        // Move the current-state counter from the old bucket to the new one.
        if (previous != null) {
            redis.opsForHash().increment(RedisKeys.STATUS_COUNTS, previous.toString(), -1);
        }
        redis.opsForHash().increment(RedisKeys.STATUS_COUNTS, newStatus, 1);
        redis.opsForHash().put(stateKey, RedisKeys.FIELD_STATUS, newStatus);

        // Historical funnel — record the first time an application reaches a milestone.
        switch (event.getNewStatus()) {
            case INTERVIEW_SCHEDULED ->
                    redis.opsForHash().increment(RedisKeys.FUNNEL, RedisKeys.FUNNEL_REACHED_INTERVIEW, 1);
            case OFFER_RECEIVED ->
                    redis.opsForHash().increment(RedisKeys.FUNNEL, RedisKeys.FUNNEL_REACHED_OFFER, 1);
            case REJECTED ->
                    redis.opsForHash().increment(RedisKeys.FUNNEL, RedisKeys.FUNNEL_REJECTED, 1);
            case WITHDRAWN ->
                    redis.opsForHash().increment(RedisKeys.FUNNEL, RedisKeys.FUNNEL_WITHDRAWN, 1);
            default -> { /* APPLIED / OA_ROUND / INTERVIEWED carry no funnel milestone */ }
        }
    }

    public void onApplicationDeleted(ApplicationDeletedEvent event) {
        String stateKey = RedisKeys.appState(event.getApplicationId());
        Map<Object, Object> state = redis.opsForHash().entries(stateKey);
        if (state.isEmpty()) {
            log.warn("Delete for unknown application {} — nothing to decrement", event.getApplicationId());
            return;
        }

        redis.opsForValue().decrement(RedisKeys.TOTAL);
        decrement(RedisKeys.STATUS_COUNTS, state.get(RedisKeys.FIELD_STATUS));
        decrement(RedisKeys.COMPANY_COUNTS, state.get(RedisKeys.FIELD_COMPANY));
        decrement(RedisKeys.RESUME_COUNTS, state.get(RedisKeys.FIELD_RESUME));
        redis.delete(stateKey);
    }

    // ---------------------------------------------------------------------
    // Readers (called by the reporting controller)
    // ---------------------------------------------------------------------

    public long totalApplications() {
        return asLong(redis.opsForValue().get(RedisKeys.TOTAL));
    }

    public Map<String, Long> countsByStatus() {
        return sortedByValueDesc(RedisKeys.STATUS_COUNTS);
    }

    public Map<String, Long> countsByCompany() {
        return sortedByValueDesc(RedisKeys.COMPANY_COUNTS);
    }

    public Map<String, Long> countsByResumeVersion() {
        return sortedByValueDesc(RedisKeys.RESUME_COUNTS);
    }

    public Map<String, Long> funnel() {
        Map<Object, Object> raw = redis.opsForHash().entries(RedisKeys.FUNNEL);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put(RedisKeys.FUNNEL_TOTAL_CREATED, asLong(raw.get(RedisKeys.FUNNEL_TOTAL_CREATED)));
        out.put(RedisKeys.FUNNEL_REACHED_INTERVIEW, asLong(raw.get(RedisKeys.FUNNEL_REACHED_INTERVIEW)));
        out.put(RedisKeys.FUNNEL_REACHED_OFFER, asLong(raw.get(RedisKeys.FUNNEL_REACHED_OFFER)));
        out.put(RedisKeys.FUNNEL_REJECTED, asLong(raw.get(RedisKeys.FUNNEL_REJECTED)));
        out.put(RedisKeys.FUNNEL_WITHDRAWN, asLong(raw.get(RedisKeys.FUNNEL_WITHDRAWN)));
        return out;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void decrement(String hashKey, Object field) {
        if (field != null) {
            redis.opsForHash().increment(hashKey, field.toString(), -1);
        }
    }

    private Map<String, Long> sortedByValueDesc(String hashKey) {
        Map<Object, Object> raw = redis.opsForHash().entries(hashKey);

        // Normalise to <String, Long>, drop zeroed-out buckets, sort by count desc.
        Map<String, Long> normalised = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            long count = asLong(entry.getValue());
            if (count != 0L) {
                normalised.put(entry.getKey().toString(), count);
            }
        }

        Map<String, Long> sorted = new LinkedHashMap<>();
        normalised.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }
}
