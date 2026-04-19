plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "locapet-backend"

include(
    "app-api",
    "admin-api",
    "domain",
    "common"
)
