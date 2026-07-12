rootProject.name = "taskmanager"

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include("common")
include("app")
include("auth")
