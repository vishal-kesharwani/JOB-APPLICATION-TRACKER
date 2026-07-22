package com.jobtracker.application.dto;

import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ApplicationResponse {

    private Long id;
    private String company;
    private String role;
    private ApplicationStatus status;
    private LocalDate appliedDate;
    private String resumeVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApplicationResponse fromEntity(Application app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .company(app.getCompany())
                .role(app.getRole())
                .status(app.getStatus())
                .appliedDate(app.getAppliedDate())
                .resumeVersion(app.getResumeVersion())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
