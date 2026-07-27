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

    // bootable modules — apply Spring Boot plugin for bootJar support
    if (path in setOf(
            ":identity-access-service", ":adventure-service",
            ":rule-knowledge-service", ":character-management-service",
            ":dice-roll-service", ":combat-map-service",
            ":ai-game-master-service", ":app-all",
    )) {
        apply(plugin = "org.springframework.boot")
    }

    if (path in setOf(
            ":identity-access-service", ":adventure-service",
            ":rule-knowledge-service", ":character-management-service",
            ":dice-roll-service", ":combat-map-service",
            ":ai-game-master-service",
    )) {
        tasks.named<ProcessResources>("processResources") {
            exclude("db/migration/**/*.sql")
            from(layout.projectDirectory.dir("src/main/resources/db/migration")) {
                include("**/*.sql")
                into("db/migration/${project.name}")
            }
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}
