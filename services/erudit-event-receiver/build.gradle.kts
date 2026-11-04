apply(from = rootProject.file("gradle/service-conventions.gradle"))

dependencies {
    "implementation"(project(":platform:erudit-observability"))
    "implementation"("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("org.springframework.boot:spring-boot-starter-webflux")
    "implementation"("org.springframework.boot:spring-boot-starter-validation")
    "implementation"("org.springframework.boot:spring-boot-starter-data-r2dbc")
    "implementation"("org.postgresql:r2dbc-postgresql")
    "implementation"("com.clickhouse:clickhouse-jdbc:0.9.8:all")
    "implementation"("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.1")
    "implementation"("org.springframework.boot:spring-boot-starter")
    "implementation"("org.springframework.cloud:spring-cloud-starter-config")
    "implementation"("org.springframework.retry:spring-retry")
    "implementation"("org.springframework.boot:spring-boot-starter-aspectj")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("io.projectreactor:reactor-test")
    "testImplementation"("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    "testImplementation"("org.testcontainers:testcontainers-postgresql:2.0.5")
    "testImplementation"("org.testcontainers:testcontainers-clickhouse:2.0.5")
    "testImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "compileOnly"("org.projectlombok:lombok:1.18.42")
    "annotationProcessor"("org.projectlombok:lombok:1.18.42")
}

tasks.named<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate") {
    configOptions.put("reactive", "true")
}
