description = "D&D rulebooks — RAG indexing, embedding, semantic retrieval"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
