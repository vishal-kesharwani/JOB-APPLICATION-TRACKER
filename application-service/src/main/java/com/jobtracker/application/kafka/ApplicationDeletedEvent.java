package com.jobtracker.application.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published to the "application-deleted" topic when an application
 * is removed. Optional topic - consumers may ignore if not needed.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDeletedEvent {

    private Long applicationId;
    private String company;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime eventTimestamp;
}
