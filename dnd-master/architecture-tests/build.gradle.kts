description = "Architecture constraints — package layering, naming, dependency rules"

dependencies {
    testImplementation("com.tngtech.archunit:archunit:1.4.2")
}

tasks.withType<Test> {
    systemProperty("reactorRoot", rootProject.projectDir.absolutePath)
}
