CREATE SCHEMA IF NOT EXISTS character_management;

CREATE TABLE character_management.character_sheet (
    character_sheet_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL,
    edition TEXT NOT NULL CHECK (edition IN ('DND_5E_2014', 'DND_5E_2024')),
    character_name TEXT NOT NULL,
    character_level INTEGER NOT NULL CHECK (character_level BETWEEN 1 AND 20),
    inspiration BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE INDEX character_sheet_adventure_idx
    ON character_management.character_sheet(adventure_id);
