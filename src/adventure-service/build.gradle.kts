description = "Game session (adventure) management, including dice and tactical combat capabilities"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework:spring-tx")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
}

tasks.withType<Test>().configureEach {
    // Adventure now packages three independent migration owners. Spring Boot tests that
    // rely on auto-configured Flyway must only resolve Adventure's own version stream.
    systemProperty("spring.flyway.locations", "classpath:db/migration/adventure-service")
}
