description = "All-in-one: single bootable jar merging every backend service"

springBoot {
    mainClass = "com.dndmaster.appall.DndMasterAllInOneApplication"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(project(":identity-access-service"))
    implementation(project(":adventure-service"))
    implementation(project(":rule-knowledge-service"))
    implementation(project(":character-management-service"))
    implementation(project(":combat-map-service"))
    implementation(project(":ai-game-master-service"))
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    providers.gradleProperty("developmentOtelAgent").orNull?.let { agentPath ->
        jvmArgs("-javaagent:$agentPath")
    }
}

tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("developmentBootJar") {
    group = "build"
    description = "Packages the local development runtime with its Prometheus registry."
    mainClass.set("com.dndmaster.appall.DndMasterAllInOneApplication")
    targetJavaVersion.set(JavaVersion.VERSION_21)
    classpath = sourceSets.main.get().runtimeClasspath + configurations.getByName("developmentOnly")
    archiveFileName.set("app-container.jar")
}
