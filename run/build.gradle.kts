plugins {
    kotlin("jvm")
    java
    application
}

group = "cc.buessow.glumagic"
version = "1.0"
val mainProjectClass = "cc.buessow.glumagic.Main"

kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "glumagic-$version"
    mainClass.set(mainProjectClass)
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":input"))
    implementation(project(":mongodb"))
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testLibs.junit)
}

tasks.register("fatJar", type = Jar::class) {
    dependsOn(configurations.runtimeClasspath)
    archiveBaseName = "glumagic"
    manifest {
        attributes["Main-Class"] = mainProjectClass
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(
        configurations.runtimeClasspath.get().map {
            if (!it.isDirectory) zipTree(it.path) else it.path })
}

tasks {
    getByName<Delete>("clean") {
        delete.add("build/libs/glumagic-$version.jar")
    }
}
