plugins {
    // Lets Gradle auto-provision the Java 21 toolchain when it isn't installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "medicore-backend-server"
