apply(from = rootProject.file("gradle/service-conventions.gradle"))

dependencies {
    "implementation"(project(":platform:erudit-observability"))
    "implementation"("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    "implementation"("org.springframework.cloud:spring-cloud-config-server")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.springframework.cloud:spring-cloud-starter-config")
    "testImplementation"("org.springframework.retry:spring-retry")
    "testImplementation"("org.springframework.boot:spring-boot-starter-aspectj")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("config-repository"))
    systemProperty("eureka.client.enabled", "false")
    systemProperty("config.repository", rootProject.file("config-repository").absolutePath)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
