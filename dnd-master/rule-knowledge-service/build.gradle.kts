description = "D&D rulebooks — RAG indexing, embedding, semantic retrieval"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.testcontainers:postgresql")
}
