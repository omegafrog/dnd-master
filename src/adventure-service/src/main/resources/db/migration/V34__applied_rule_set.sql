CREATE TABLE IF NOT EXISTS applied_rule_set (
    rule_set_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    edition TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS applied_rule_set_rulebook (
    rule_set_id UUID NOT NULL REFERENCES applied_rule_set(rule_set_id) ON DELETE CASCADE,
    rulebook_id UUID NOT NULL,
    PRIMARY KEY (rule_set_id, rulebook_id)
);
CREATE INDEX IF NOT EXISTS applied_rule_set_adventure_idx ON applied_rule_set(adventure_id);
