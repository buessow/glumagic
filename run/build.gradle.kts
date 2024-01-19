plugins {
    kotlin("jvm")
    java
    application
    glumagic
}

group = "cc.buessow.glumagic"
version = "1.0"
val mainProjectClass = "cc.buessow.glumagic.Main"

application {
    applicationName = "glumagic-$version"
    mainClass.set(mainProjectClass)
}

dependencies {
    implementation(project(":input"))
    implementation(project(":mongodb"))
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testLibs.junit)
}

tasks.register("fatJar", type = Jar::class) {
    dependsOn("test", ":input:test", ":mongodb:test", configurations.runtimeClasspath)
    archiveBaseName = "glumagic"
    manifest {
        attributes["Main-Class"] = mainProjectClass
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
