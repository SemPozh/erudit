apply(from = rootProject.file("gradle/service-conventions.gradle"))

dependencies {
    "implementation"("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    "implementation"("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    "implementation"("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("org.springframework.boot:spring-boot-starter-webflux")
    "implementation"("org.springframework.boot:spring-boot-starter")
    "implementation"("org.springframework.cloud:spring-cloud-starter-config")
    "implementation"("org.springframework.retry:spring-retry")
    "implementation"("org.springframework.boot:spring-boot-starter-aspectj")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("src/main/resources/static/openapi.yaml"))
    systemProperty("legacy.openapi", rootProject.file("src/main/resources/static/openapi.yaml").absolutePath)
}
