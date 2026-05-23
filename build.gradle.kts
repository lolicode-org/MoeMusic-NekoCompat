import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

group = "org.lolicode.moemusic.nekocompat"
version = providers.gradleProperty("mod_version").get()

repositories {
    mavenLocal()
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
    implementation("net.fabricmc:fabric-loader:0.19.2")
    implementation("net.fabricmc.fabric-api:fabric-api:0.146.1+26.1.2")
    implementation("net.fabricmc:fabric-language-kotlin:1.13.10+kotlin.2.3.20")

    implementation("org.lolicode.moemusic:api:1.0.0")
    compileOnly("lol.bai:badpackets:fabric-0.12.2")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.3")
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
