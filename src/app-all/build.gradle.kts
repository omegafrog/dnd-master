description = "All-in-one: single bootable jar composing every backend context"

springBoot {
    mainClass = "com.dndmaster.appall.DndMasterAllInOneApplication"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(project(":identity-access-service"))
    implementation(project(":adventure-service"))
    implementation(project(":rule-knowledge-service"))
    implementation(project(":character-management-service"))
    implementation(project(":ai-game-master-service"))
}
