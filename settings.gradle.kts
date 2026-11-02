pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "erudit"

include(
    "infrastructure:erudit-config-server",
    "infrastructure:erudit-discovery-server",
    "infrastructure:erudit-gateway",
    "services:erudit-admin",
    "services:erudit-user",
    "services:erudit-content",
    "services:erudit-quiz",
    "services:erudit-event-receiver",
    "services:erudit-ai-platform",
    "services:erudit-payment",
    "services:erudit-notificator"
)
