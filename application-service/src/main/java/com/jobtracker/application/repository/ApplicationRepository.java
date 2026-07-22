package com.jobtracker.application.repository;

import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByStatus(ApplicationStatus status);

    List<Application> findByCompanyIgnoreCase(String company);

    List<Application> findByResumeVersion(String resumeVersion);

    List<Application> findByAppliedDateBetween(LocalDate start, LocalDate end);

    long countByStatus(ApplicationStatus status);
}
