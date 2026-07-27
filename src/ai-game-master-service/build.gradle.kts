description = "AI Game Master — LLM-driven narration, rulings, NPC dialogue"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}
