plugins {
    `java-library`
    jacoco
    id("io.spring.dependency-management")
}

group = "com.erudit"
version = rootProject.version
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
dependencyManagement {
    imports { mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) }
}
dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-zipkin")
    api("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:context-propagation")
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform(); finalizedBy(tasks.jacocoTestReport) }
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.70".toBigDecimal() } } }
}
val verifyNoDatabaseDependencies = tasks.register("verifyNoDatabaseDependencies") {
    doLast {
        val forbidden = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.filter {
            it.moduleVersion.id.group == "org.hibernate.orm" || it.name == "hibernate-core" ||
                it.name.contains("jdbc", true) || it.name.contains("r2dbc", true) ||
                it.name.contains("data-jpa", true) || it.name.contains("persistence-api", true)
        }
        check(forbidden.isEmpty()) { "Technical starter must not depend on database libraries: $forbidden" }
    }
}
tasks.check { dependsOn(tasks.jacocoTestCoverageVerification, verifyNoDatabaseDependencies) }
