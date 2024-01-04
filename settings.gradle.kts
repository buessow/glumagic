pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "1.9.20"
        id("kotlin-android") version "8.2.0"
        id("com.android.library") version "8.2.0"  apply false

    }
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.4")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            library("gson", "com.google.code.gson:gson:2.8.9")
            library("reactivex", "io.reactivex.rxjava3:rxjava:3.0.4")
            library("mongodb", "org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
            library("kotlinx.cli", "org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
            library("kotlinx.coroutines.rx3","org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.3.9")
            library("tensorflowLite", "org.tensorflow:tensorflow-lite:2.14.0")
            library("tensorflowLite.support", "org.tensorflow:tensorflow-lite-support:0.4.4")
        }
        create("testLibs") {
            library("junit", "org.junit.jupiter:junit-jupiter:5.9.1")
            library("mockito", "org.mockito.kotlin:mockito-kotlin:5.1.0")
            library("mongodb.javaServer", "de.bwaldvogel:mongo-java-server:1.44.0")
            library("androidx.test.junit","androidx.test.ext:junit-ktx:1.1.5")
            library("androidx.test.espresso","androidx.test.espresso:espresso-core:3.5.1")
            library("androidx.test.runner","androidx.test:runner:1.1.5")

            bundle("androidx.test",
                   listOf("androidx.test.junit", "androidx.test.espresso", "androidx.test.runner"))
            bundle("base", listOf("junit", "mockito"))
        }
    }
}

rootProject.name = "glumagic"
include("input")
include("run")
include("mongodb")
include("predictor")