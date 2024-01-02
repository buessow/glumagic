plugins {
    kotlin("jvm")
    application
}

group = "cc.buessow.glumagic"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("cc.buessow.glumagic.Main")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":input"))
    implementation(project(":mongodb"))
    implementation(libs.kotlinx.cli)
    implementation(libs.reactivex)
    testImplementation(testLibs.junit)
}
