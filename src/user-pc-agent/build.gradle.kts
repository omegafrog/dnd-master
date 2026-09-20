description = "Runnable user PC agent for the relay WebSocket connection"

plugins {
    application
}

application {
    mainClass = "com.dndmaster.userpcagent.UserPcAgentApplication"
}

dependencies {
    implementation(project(":ai-game-master-local-codex"))
    implementation(project(":ai-game-master-service"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
}
