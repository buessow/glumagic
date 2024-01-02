pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "1.9.0"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            library("gson", "com.google.code.gson:gson:2.8.9")
            library("reactivex", "io.reactivex.rxjava3:rxjava:3.0.4")
            library("mongodb", "org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
            library("kotlinx.cli", "org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
            library("kotlinx.coroutines.rx3","org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.3.9")
        }
        create("testLibs") {
            library("junit", "org.junit.jupiter:junit-jupiter:5.9.1")
            library("mockito", "org.mockito.kotlin:mockito-kotlin:5.1.0")
            library("mongodb.javaServer", "de.bwaldvogel:mongo-java-server:1.44.0")

            bundle("base", listOf("junit", "mockito"))
        }
    }
}

rootProject.name = "glumagic"
include("input")
include("run")
include("mongodb")
