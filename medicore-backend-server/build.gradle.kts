// MediCore HMS — backend build (Design v1.3 §2.3: Gradle Kotlin DSL, Java 21, Spring Boot 3.3)
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.medicore"
version = "0.1.0-M1"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")        // REST controllers
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")   // persistence (entities mirror Flyway schema)
    implementation("org.springframework.boot:spring-boot-starter-validation") // Jakarta Bean Validation (NFR-SEC-02)
    implementation("org.springframework.boot:spring-boot-starter-security")   // headers, BCrypt (FR-AUTH-02)
    implementation("org.springframework.session:spring-session-jdbc")         // DD-02: sessions in PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-mail")       // DD-08: SMTP adapter behind MailPort
    implementation("org.flywaydb:flyway-core")                                 // NFR-MNT-03: versioned migrations
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> { useJUnitPlatform() }
