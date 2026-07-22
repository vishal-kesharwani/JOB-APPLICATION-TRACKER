package com.jobtracker.application.service;

import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.application.dto.UpdateStatusRequest;
import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.kafka.ApplicationCreatedEvent;
import com.jobtracker.application.kafka.ApplicationDeletedEvent;
import com.jobtracker.application.kafka.ApplicationEventProducer;
import com.jobtracker.application.kafka.ApplicationStatusUpdatedEvent;
import com.jobtracker.application.repository.ApplicationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApplicationService {

    private final ApplicationRepository repository;
    private final ApplicationEventProducer eventProducer;
    private final Counter applicationsCreatedCounter;
    private final Counter statusUpdatesCounter;
    private final Timer applicationCreationLatency;

    public ApplicationService(ApplicationRepository repository,
                               ApplicationEventProducer eventProducer,
                               MeterRegistry meterRegistry) {
        this.repository = repository;
        this.eventProducer = eventProducer;

        // Custom business metrics - these are what SRE interviewers ask about
        this.applicationsCreatedCounter = Counter.builder("job_applications_created_total")
                .description("Total number of job applications created")
                .register(meterRegistry);

        this.statusUpdatesCounter = Counter.builder("job_status_updates_total")
                .description("Total number of job application status transitions")
                .register(meterRegistry);

        this.applicationCreationLatency = Timer.builder("application_creation_latency")
                .description("Time taken to create and persist a new application")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request) {
        return applicationCreationLatency.record(() -> {
            Application application = Application.builder()
                    .company(request.getCompany())
                    .role(request.getRole())
                    .appliedDate(request.getAppliedDate())
                    .resumeVersion(request.getResumeVersion())
                    .status(ApplicationStatus.APPLIED)
                    .build();

            Application saved = repository.save(application);

            // Publish event only AFTER successful DB commit
            publishAfterCommit(() -> {
                applicationsCreatedCounter.increment();
                eventProducer.publishApplicationCreated(
                        ApplicationCreatedEvent.builder()
                                .applicationId(saved.getId())
                                .company(saved.getCompany())
                                .role(saved.getRole())
                                .resumeVersion(saved.getResumeVersion())
                                .appliedDate(saved.getAppliedDate())
                                .eventTimestamp(LocalDateTime.now())
                                .build()
                );
            });

            log.info("Created application id={} company={} role={}", saved.getId(), saved.getCompany(), saved.getRole());
            return ApplicationResponse.fromEntity(saved);
        });
    }

    public ApplicationResponse getApplication(Long id) {
        Application application = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));
        return ApplicationResponse.fromEntity(application);
    }

    public List<ApplicationResponse> getAllApplications() {
        return repository.findAll().stream()
                .map(ApplicationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ApplicationResponse> getByStatus(ApplicationStatus status) {
        return repository.findByStatus(status).stream()
                .map(ApplicationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApplicationResponse updateStatus(Long id, UpdateStatusRequest request) {
        Application application = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));

        ApplicationStatus previousStatus = application.getStatus();
        application.setStatus(request.getStatus());

        Application saved = repository.save(application);

        publishAfterCommit(() -> {
            statusUpdatesCounter.increment();
            eventProducer.publishStatusUpdated(
                    ApplicationStatusUpdatedEvent.builder()
                            .applicationId(saved.getId())
                            .company(saved.getCompany())
                            .role(saved.getRole())
                            .previousStatus(previousStatus)
                            .newStatus(saved.getStatus())
                            .eventTimestamp(LocalDateTime.now())
                            .build()
            );
        });

        log.info("Updated status for application id={} {} -> {}", id, previousStatus, saved.getStatus());
        return ApplicationResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteApplication(Long id) {
        Application application = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));

        String company = application.getCompany();
        repository.deleteById(id);

        publishAfterCommit(() -> eventProducer.publishApplicationDeleted(
                ApplicationDeletedEvent.builder()
                        .applicationId(id)
                        .company(company)
                        .eventTimestamp(LocalDateTime.now())
                        .build()
        ));

        log.info("Deleted application id={}", id);
    }

    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
