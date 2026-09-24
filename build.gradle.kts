plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
}

group = "com.erudit"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Database and migrations
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    implementation("com.clickhouse:clickhouse-jdbc:0.9.8:all")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // OpenAPI and Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Lombok
    val lombokVersion = "1.18.42"

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")

    // Testcontainers
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
}

// OpenAPI code generation
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/static/openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")

    apiPackage.set("com.erudit.openapi.api")
    modelPackage.set("com.erudit.openapi.model")

    importMappings.set(
        mapOf(
            "ContentReportRequest" to "com.erudit.content.ContentReportRequest"
        )
    )

    schemaMappings.set(
        mapOf(
            "ContentReportRequest" to "com.erudit.content.ContentReportRequest"
        )
    )

    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "openApiNullable" to "false",
            "useTags" to "true",
            "dateLibrary" to "java8"
        )
    )
}

openApiValidate {
    inputSpec.set(
        layout.projectDirectory.file("src/main/resources/static/openapi.yaml")
    )
}

sourceSets {
    main {
        java.srcDir(
            layout.buildDirectory.dir("generated/openapi/src/main/java")
        )
    }
}

configurations {
    compileClasspath {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
