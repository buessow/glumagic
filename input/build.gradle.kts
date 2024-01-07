plugins {
    kotlin("jvm")
    `java-library`
}

group = "cc.buessow.glumagic"
version = "1.0"

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
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
    implementation(libs.gson)
    implementation(libs.reactivex)
    testImplementation(testLibs.bundles.base)
}
