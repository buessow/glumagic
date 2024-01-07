plugins {
    kotlin("jvm")
    `java-library`
}

group = "cc.buessow.glumagic"
version = "unspecified"

dependencies {
    implementation(project(":input"))
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.rx3)
    implementation(libs.mongodb)
    implementation(libs.reactivex)
    implementation(libs.okHttp3)

    testImplementation(testLibs.bundles.base)
    testImplementation(testLibs.mongodb.javaServer)
    testImplementation(testLibs.okHttp3.mockWebServer)
}
tasks.test {
    useJUnitPlatform()
}
