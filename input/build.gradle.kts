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

dependencies {
//    implementation("org.tensorflow:tensorflow-core-api:0.3.3")
//    implementation("org.tensorflow:tensorflow-core-platform:0.3.3")
////    implementation("org.bytedeco:javacpp:1.5.8")
////    implementation("org.bytedeco:tensorflow-platform:1.15.5-1.5.8")
////    implementation("org.bytedeco:cuda-platform-redist:11.8-8.6-1.5.8")
////    implementation("org.bytedeco:tensorflow-platform-gpu:1.15.5-1.5.8")
//    implementation("com.johnsnowlabs.nlp:tensorflow-cpu_2.13:0.4.1")
//    implementation("com.johnsnowlabs.nlp:tensorflow-gpu_2.13:0.4.1")
//    implementation("com.johnsnowlabs.nlp:tensorflow-m1_2.13:0.4.1")
//    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-api:0.5.2")
//    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-impl:0.5.2")
//    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-dataset:0.5.2")
//    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-onnx:0.5.2")
//    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-tensorflow:0.5.2")


}
