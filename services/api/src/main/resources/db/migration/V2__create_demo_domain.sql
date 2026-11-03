CREATE TABLE heritage_projects (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    heritage_type VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_images (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES heritage_projects (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    label VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reconstruction_jobs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES heritage_projects (id) ON DELETE CASCADE,
    algorithm VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    image_count INTEGER NOT NULL CHECK (image_count >= 0),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_images_project_id ON project_images (project_id, created_at);
CREATE INDEX idx_reconstruction_jobs_project_id ON reconstruction_jobs (project_id, created_at);
