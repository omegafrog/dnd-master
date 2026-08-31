description = "End-to-end & evaluation — real DB, real AI, multi-service workflow"

dependencies {
    testImplementation(project(":identity-access-service"))
    testImplementation(project(":adventure-service"))
    testImplementation(project(":rule-knowledge-service"))
    testImplementation(project(":ai-game-master-service"))
    testImplementation("org.springframework.ai:spring-ai-starter-model-ollama")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
}

// ── Guard: exclude heavy E2E/Evaluation/Performance tests from `test` ──
tasks.named<Test>("test") {
    exclude("**/*E2ETest*", "**/*EvaluationTest*", "**/*PerformanceTest*")
}

tasks.withType<Test>().configureEach {
    // MigrationCompatibilityTest lives under `test`, not `integrationTest`.
    // Keep the compatibility migration path available for every system-test task.
    systemProperty(
        "dnd.migration.location",
        rootProject.projectDir.resolve("infra/migrations/compatibility").absolutePath,
    )
    systemProperty(
        "dnd.observability.policy",
        rootProject.projectDir.resolve("infra/observability/telemetry-policy.properties").absolutePath,
    )
    systemProperty("dnd.reactor.root", rootProject.projectDir.absolutePath)
}

// ── Integration test task (failsafe equivalent) ──
val integrationTest by tasks.registering(Test::class) {
    description = "Runs E2E, evaluation, and performance tests"
    group = "verification"
    shouldRunAfter("test")
    include("**/*E2ETest*", "**/*EvaluationTest*", "**/*PerformanceTest*")
    // ensure project dependencies resolve to compiled classes (not bootJar)
    dependsOn(
        ":identity-access-service:classes",
        ":adventure-service:classes",
        ":rule-knowledge-service:classes",
        ":ai-game-master-service:classes",
    )
}
