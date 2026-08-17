import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

group = "org.lolicode.moemusic.nekocompat"
version = providers.gradleProperty("mod_version").get()

repositories {
    mavenLocal()
    maven("https://codeberg.org/api/packages/lolicode/maven") {
        content { includeGroupByRegex("org\\.lolicode.*") }
    }
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven {
        url = uri("https://maven2.bai.lol")
        content { includeGroup("lol.bai") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2")
    implementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")

    implementation("org.lolicode.moemusic:api:2.2.0")
    compileOnly("lol.bai:badpackets:fabric-0.12.2")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
