ALTER TABLE heritage_projects
    ADD COLUMN collection_code VARCHAR(120),
    ADD COLUMN era VARCHAR(160),
    ADD COLUMN material VARCHAR(240),
    ADD COLUMN dimensions VARCHAR(240),
    ADD COLUMN provenance VARCHAR(500),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uq_heritage_projects_collection_code
    ON heritage_projects (collection_code)
    WHERE collection_code IS NOT NULL;

CREATE TABLE reconstruction_artifacts (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES heritage_projects (id) ON DELETE CASCADE,
    job_id UUID NOT NULL UNIQUE REFERENCES reconstruction_jobs (id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    format VARCHAR(20) NOT NULL,
    point_count BIGINT NOT NULL DEFAULT 0 CHECK (point_count >= 0),
    face_count BIGINT NOT NULL DEFAULT 0 CHECK (face_count >= 0),
    size_bytes BIGINT NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    result_path VARCHAR(500) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, version_number)
);

CREATE UNIQUE INDEX uq_reconstruction_artifacts_current
    ON reconstruction_artifacts (project_id)
    WHERE is_current;

CREATE INDEX idx_reconstruction_artifacts_project
    ON reconstruction_artifacts (project_id, version_number DESC);

WITH successful AS (
    SELECT id, project_id, result_path, created_at,
           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at, id) AS version_number,
           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at DESC, id DESC) AS current_rank
    FROM reconstruction_jobs
    WHERE status = 'SUCCEEDED' AND result_path IS NOT NULL
)
INSERT INTO reconstruction_artifacts
    (id, project_id, job_id, name, version_number, format, result_path, is_current, created_at, updated_at)
SELECT gen_random_uuid(), project_id, id,
       '重建成果 v' || version_number,
       version_number,
       UPPER(SUBSTRING(result_path FROM '\.([^.]+)$')),
       result_path,
       current_rank = 1,
       created_at,
       created_at
FROM successful;
