import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.meridiangoods"
version = "0.1.0"
description = "Meridian Goods — Slicewright demo: an event-sourced ordering system on Axon Framework 5"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Axon 5's own converter (org.axonframework:axon-conversion) uses Jackson 3.x
// (`tools.jackson.core:jackson-databind`), which needs a newer `jackson-annotations` than the
// Jackson 2.x line Spring Boot's own dependency-management BOM pins — left alone, Spring's BOM
// wins the version conflict and downgrades jackson-annotations below what Jackson 3 needs
// (NoClassDefFoundError: com.fasterxml.jackson.annotation.JsonSerializeAs at Axon's
// JacksonConverter startup). jackson-annotations is intentionally shared across the Jackson 2
// and 3 lines, so bumping it here is safe for the classic Jackson 2 (Spring MVC) codepath too.
dependencyManagement {
    dependencies {
        dependency("com.fasterxml.jackson.core:jackson-annotations:2.22")
    }
}

// Axon Framework 5's own BOM: "axon-framework-bom" — imported as a platform below so every
// axonframework module (main and test) resolves to one coherent version instead of being
// pinned per-module.
val axonBomCoordinates = "org.axonframework:axon-framework-bom:5.3.1"

dependencies {
    implementation(platform(axonBomCoordinates))
    implementation("org.axonframework.extensions.spring:axon-spring-boot-starter")
    // Registers KotlinReflectNullabilityResolver (via ServiceLoader) so Axon's @InjectEntity
    // resolver recognizes a Kotlin nullable parameter (`state: State?`) as RESOLVE_NULL instead
    // of throwing EntityNotFoundException on a not-yet-created entity — without this, a
    // create-or-noop command handler (like Place Order's) can't express "entity may not exist
    // yet" in Kotlin at all. See the findings file for the failure this fixes.
    implementation("org.axonframework.extensions.kotlin:axon-kotlin")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    testImplementation(platform(axonBomCoordinates))
    testImplementation("org.axonframework:axon-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
}
