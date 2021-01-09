import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "2.0.4"
}

repositories {
    mavenCentral()
}
val protobufVersion = "3.14.0"

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation(project(":protoMessages"))

}

// make fat jar executable
val shadowJar: ShadowJar by tasks
shadowJar.apply {
    manifest.attributes.apply {
        put("Implementation-Title", "Jvm Netbench Student Project")
        put("Author", "Kry127")
        put("Version", archiveVersion)
        put("Main-Class", "ru.itmo.kry127.App")
    }
}
