rootProject.name = "dnd-master"

include(
    ":identity-access-service",
    ":adventure-service",
    ":rule-knowledge-service",
    ":character-management-service",
    ":dice-roll-service",
    ":combat-map-service",
    ":ai-game-master-service",
    ":ai-game-master-local-codex",
    ":user-pc-agent",
    ":agent-connection-relay-service",
    ":gm-eval-service",
    ":app-all",
    ":architecture-tests",
    ":contract-tests",
    ":system-tests",
)
