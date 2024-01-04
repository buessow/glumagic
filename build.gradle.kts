//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
//
//buildscript {
//  repositories {
//    mavenCentral()
//    google()
//    gradlePluginPortal()
//  }
//  dependencies {
//    classpath("com.android.tools.build:gradle:8.2.1")
//    classpath("com.android.library:com.android.library.gradle.plugin:8.1.4")
//    classpath(kotlin("gradle-plugin", version = "1.9.20"))
//    classpath("com.google.gms:google-services:4.4.0")
//
////    implementation("com.android.tools.build:gradle:8.2.1")
//  }
//}

dependencies {
//  implementation("com.android.library:com.android.library.gradle.plugin:8.1.4")

}

//subprojects{
//  afterEvaluate {
//    android {
//      namespace = "com.x.x"
//    }
//  }
//}
//android {
//  namespace = "com.example.myapp"
//  compileOptions {
//    sourceCompatibility = VERSION_11
//    targetCompatibility = VERSION_11
//  }
//  compileSdk = 34
//  buildToolsVersion = "34.0.0"
//}

plugins {
  id("org.jetbrains.kotlin.android")  version "2.0.0-Beta2" apply false
}