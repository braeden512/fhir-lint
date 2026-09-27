CREATE TABLE IF NOT EXISTS quality_check_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    target_profile VARCHAR(64) DEFAULT 'BASE_R4',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    resources_analyzed INTEGER,
    resource_type_counts JSONB,
    CONSTRAINT chk_job_status CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_resources_analyzed CHECK (resources_analyzed IS NULL OR resources_analyzed >= 0)
);

CREATE INDEX IF NOT EXISTS idx_quality_check_jobs_status_created 
ON quality_check_jobs (status, created_at);

CREATE INDEX IF NOT EXISTS idx_quality_check_jobs_started_at 
ON quality_check_jobs (started_at) 
WHERE status = 'PROCESSING';
