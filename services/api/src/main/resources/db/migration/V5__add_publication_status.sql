ALTER TABLE heritage_projects
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_heritage_projects_publication_status
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED'));

CREATE INDEX idx_heritage_projects_publication
    ON heritage_projects (publication_status, published_at DESC);
