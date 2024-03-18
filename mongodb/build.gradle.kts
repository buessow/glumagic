plugins {
    kotlin("jvm")
    `java-library`
    glumagic
}

kotlin {
    jvmToolchain(Versions.JVM)
}

group = "cc.buessow.glumagic"
version = "1.0"

dependencies {
    implementation(project(":input"))
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mongodb)
    implementation(libs.okHttp3)

    testImplementation(testLibs.bundles.base)
    testImplementation(testLibs.mongodb.javaServer)
    testImplementation(testLibs.okHttp3.mockWebServer)
}
