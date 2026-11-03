CREATE TABLE platform_metadata (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    application_name VARCHAR(100) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_metadata (id, application_name, schema_version)
VALUES (1, 'project-design-demo', '1');
