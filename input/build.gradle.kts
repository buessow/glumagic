plugins {
    kotlin("jvm")
    `java-library`
    glumagic
}

group = "cc.buessow.glumagic"
version = "1.0"

kotlin {
    jvmToolchain(Versions.JVM)
}
kotlinTestRegistry {
}

tasks.jar {
    archiveBaseName = "cc.buessow.glumagic.input"
    manifest {
        attributes(mapOf(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.apache.commons.csv)
    implementation(libs.jdsp) {
        exclude(group = "org.apache.maven.surefire", module = "common-java5")
    }
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testLibs.bundles.base)
}
