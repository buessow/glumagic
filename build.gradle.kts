plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("io.reactivex.rxjava3:rxjava:3.0.4")
    implementation("org.tensorflow:tensorflow-core-platform:0.3.3")

//    implementation(platform("org.apache.logging.log4j:log4j-bom:2.22.0"))
    implementation("org.apache.logging.log4j:log4j-api:2.22.0")
    implementation("org.apache.logging.log4j:log4j-core:2.22.0")

    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
}