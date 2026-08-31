rootProject.name = "dnd-master"

include(
    ":identity-access-service",
    ":adventure-service",
    ":rule-knowledge-service",
    ":character-management-service",
    ":ai-game-master-service",
    ":gm-eval-service",
    ":app-all",
    ":architecture-tests",
    ":contract-tests",
    ":system-tests",
)
