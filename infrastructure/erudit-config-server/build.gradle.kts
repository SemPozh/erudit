apply(from = rootProject.file("gradle/service-conventions.gradle"))

dependencies {
    "implementation"("org.springframework.cloud:spring-cloud-config-server")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.springframework.cloud:spring-cloud-starter-config")
    "testImplementation"("org.springframework.retry:spring-retry")
    "testImplementation"("org.springframework.boot:spring-boot-starter-aspectj")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    systemProperty("config.repository", rootProject.file("config-repository").absolutePath)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
