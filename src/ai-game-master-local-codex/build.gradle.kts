description = "Development-only local Codex execution for AI Game Master"

sourceSets {
    main {
        java.srcDir("../ai-game-master-service/src/main/java")
        java.include(
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexAppServerClient.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCompletionAdapter.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCharacterTagProvider.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexTurnFailedException.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexTurnTimeoutException.java",
            "com/dndmaster/aigamemaster/localcodex/**",
        )
    }
    test {
        java.srcDir("../ai-game-master-service/src/test/java")
        java.include(
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexAppServerClientTest.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCharacterTagProviderTest.java",
        )
    }
}

dependencies {
    implementation(project(":ai-game-master-service"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
}
