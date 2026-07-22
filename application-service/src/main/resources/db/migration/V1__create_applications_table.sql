CREATE TABLE applications (
    id              BIGSERIAL PRIMARY KEY,
    company         VARCHAR(255)    NOT NULL,
    role            VARCHAR(255)    NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'APPLIED',
    applied_date    DATE            NOT NULL,
    resume_version  VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_company ON applications(company);
CREATE INDEX idx_applications_resume_version ON applications(resume_version);
CREATE INDEX idx_applications_applied_date ON applications(applied_date);
