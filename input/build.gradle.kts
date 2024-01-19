plugins {
    kotlin("jvm")
    `java-library`
    glumagic
}

group = "cc.buessow.glumagic"
version = "1.0"

kotlin {
    jvmToolchain(17)
}
kotlinTestRegistry {
}

tasks.jar {
    archiveBaseName = "cc.buessow.glumagic.input"
    manifest {
        attributes(mapOf("Implementation-Title" to project.name,
            "Implementation-Version" to project.version))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.apache.commons.csv)
    testImplementation(testLibs.bundles.base)
}
