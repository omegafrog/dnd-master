import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7"
}

val springBootVersion = "3.5.16"
val springAiVersion = "1.1.8"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

subprojects {
    group = "com.dndmaster"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Bootable deployment/context modules. Dice and combat-map are capabilities owned by Adventure.
    if (path in setOf(
            ":identity-access-service", ":adventure-service",
            ":rule-knowledge-service", ":character-management-service",
            ":ai-game-master-service", ":app-all",
    )) {
        apply(plugin = "org.springframework.boot")
    }

    // Contexts with one migration owner keep the existing resource layout.
    if (path in setOf(
            ":identity-access-service", ":rule-knowledge-service",
            ":character-management-service", ":ai-game-master-service",
    )) {
        tasks.named<ProcessResources>("processResources") {
            exclude("db/migration/**/*.sql")
            from(layout.projectDirectory.dir("src/main/resources/db/migration")) {
                include("**/*.sql")
                into("db/migration/${project.name}")
            }
        }
    }

    // Adventure owns Dice and Combat Map as code capabilities. Adventure migrations remain
    // under the conventional Flyway root so legacy Adventure tests can scan db/migration.
    // Capability migrations live outside that root to prevent recursive version collisions,
    // while retaining their independent Flyway history tables and version streams.
    if (path == ":adventure-service") {
        tasks.named<ProcessResources>("processResources") {
            exclude("db/migration/**/*.sql")
            from(layout.projectDirectory.dir("src/main/resources/db/migration")) {
                include("*.sql")
                into("db/migration/adventure-service")
            }
            from(layout.projectDirectory.dir("src/main/resources/db/migration/dice-roll-service")) {
                include("*.sql")
                into("db/capability-migration/dice-roll-service")
            }
            from(layout.projectDirectory.dir("src/main/resources/db/migration/combat-map-service")) {
                include("*.sql")
                into("db/capability-migration/combat-map-service")
            }
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}
