CREATE TABLE IF NOT EXISTS rulebook_catalog_revision (
    catalog_revision_id UUID PRIMARY KEY,
    edition TEXT NOT NULL CHECK (edition IN ('DND_5E_2014', 'DND_5E_2024')),
    display_name TEXT NOT NULL,
    rulebook_id UUID REFERENCES rulebook_registration(rulebook_id),
    revision_number BIGINT NOT NULL CHECK (revision_number > 0),
    status TEXT NOT NULL CHECK (status IN ('UNAVAILABLE', 'QUEUED', 'PROCESSING', 'READY', 'FAILED')),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (edition, revision_number)
);

CREATE UNIQUE INDEX IF NOT EXISTS rulebook_catalog_one_published_edition_uq
    ON rulebook_catalog_revision (edition) WHERE published;

INSERT INTO rulebook_catalog_revision
    (catalog_revision_id, edition, display_name, revision_number, status, published)
VALUES
    ('55000000-0000-0000-0000-000000000024', 'DND_5E_2024', 'D&D 5.5e (2024)', 1, 'UNAVAILABLE', FALSE)
ON CONFLICT (edition, revision_number) DO NOTHING;
