plugins {
    java
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.openapi.generator") version "7.25.0" apply false
}
group = "com.erudit"
version = "0.0.1-SNAPSHOT"
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
// The root is a verification/aggregation project, with no application or runtime datasource.
dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.yaml:snakeyaml")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.jar { enabled = false }
tasks.test {
    useJUnitPlatform()
    dependsOn(subprojects.filter { it.name.startsWith("erudit-") }.map { "${it.path}:classes" })
    inputs.files(fileTree("services") { include("*/src/main/**", "*/build.gradle.kts", "*/build/classes/java/main/**") })
    inputs.files(fileTree("platform") { include("*/src/main/**") }, fileTree("infrastructure") { include("*/src/main/**", "*/build/classes/java/main/**") }, fileTree("config-repository"),
        fileTree("docker"), file("docker-compose.yml"), file("docs/api/public-operations.json"))
    systemProperty("test.postgres.image", providers.gradleProperty("postgresTestImage").get())
    systemProperty("architecture.root", projectDir.absolutePath)
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) = Unit
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if(suite.parent==null && result.skippedTestCount>0) throw GradleException("Skipped tests are forbidden")
        }
        override fun beforeTest(test: org.gradle.api.tasks.testing.TestDescriptor) = Unit
        override fun afterTest(test: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) = Unit
    })
    testLogging { events("failed", "skipped") }
}
// Keep the documented root commands while validating/building the owners independently.
tasks.register("openApiValidate") {
    group = "verification"
    dependsOn(subprojects.filter { it.path.startsWith(":services:") }.map { "${it.path}:openApiValidate" })
    dependsOn(tasks.test)
}
tasks.register("verifyServiceContracts") { dependsOn(tasks.test) }
tasks.register("jacocoTestCoverageVerification") {
    dependsOn(subprojects.filter { it.name.startsWith("erudit-") }.map { "${it.path}:jacocoTestCoverageVerification" })
}
tasks.build { dependsOn(subprojects.filter { it.name.startsWith("erudit-") }.map { "${it.path}:build" }) }

tasks.assemble { dependsOn(subprojects.filter { it.name.startsWith("erudit-") }.map { "${it.path}:assemble" }) }
