apply(from = rootProject.file("gradle/service-conventions.gradle"))

dependencies {
    "implementation"("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.1")
    "implementation"("org.yaml:snakeyaml")
    "implementation"(project(":platform:erudit-observability"))
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
    inputs.files(rootProject.fileTree("services") {
        include("*/src/main/resources/static/openapi.yaml")
    })
    systemProperty("service.contracts.root", rootProject.file("services").absolutePath)
}

// Bundle the same revision of every service contract; no blocking network reads on event loops.
tasks.named<ProcessResources>("processResources") {
    rootProject.file("services").listFiles()!!.filter { it.isDirectory }.forEach { service ->
        from(service.resolve("src/main/resources/static/openapi.yaml")) {
            into("gateway-contracts")
            rename { "${service.name}.yaml" }
        }
    }
}
