description = "AI Game Master — LLM-driven narration, rulings, NPC dialogue"

// Real local-model measurements are opt-in and must not make the default unit
// test task depend on a running Ollama instance.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("local-ai-benchmark")
    }
}

val localAiBenchmark by tasks.registering(Test::class) {
    description = "Runs the opt-in Ollama local AI benchmark"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("local-ai-benchmark")
    }
    shouldRunAfter(tasks.named("test"))
}

sourceSets {
    main {
        java.exclude(
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexAppServerClient.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCompletionAdapter.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCharacterTagProvider.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexTurnFailedException.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexTurnTimeoutException.java",
        )
    }
    test {
        java.exclude(
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexAppServerClientTest.java",
            "com/dndmaster/aigamemaster/infrastructure/ai/CodexCliCharacterTagProviderTest.java",
        )
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}
