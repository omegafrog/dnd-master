INSERT INTO identity_access.players (player_id, username, password_hash, active, created_at)
VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'demo-player@example.com',
    '$2b$12$iUgvshCUzvTP6oaE3bRea.mD4ZPuKtkAOTjFeZnSMDaS.bIchBCTG',
    TRUE,
    NOW()
)
ON CONFLICT (username) DO NOTHING;
