import java.util.*

plugins {
    kotlin("jvm")
    `java-library`
    glumagic
    signing
    id("com.gradleup.nmcp")
    id("maven-publish")
}

group = "cc.outabout.glumagic"
version = "1.0.1"

val localProperties = Properties()
File(rootProject.projectDir, "local.properties").inputStream().use { localProperties.load(it) }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group as String
            artifactId = "input"
            version = project.version as String
            from(components.getByName("java"))

            pom {
                name.set("cc.outabout.glumagic.input")
                description.set("A description of what my library does.")
                inceptionYear.set("2024")
                url.set("https://github.com/buessow/glumagic/")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("buessow")
                        name.set("Robert Buessow")
                        url.set("https://github.com/buessow/")
                    }
                }
                scm {
                    url.set("https://github.com/username/glumagic/")
                    connection.set("scm:git:git://github.com/buessow/glumagic.git")
                    developerConnection.set("scm:git:ssh://git@github.com/buessow/glumagic.git")
                }
            }
        }
    }
}

nmcp {
    publishAllPublications {
        username = localProperties.getProperty("sonatype.username")
        password = localProperties.getProperty("sonatype.password")
        publicationType = "USER_MANAGED"
    }
}

signing {
    useInMemoryPgpKeys(
        localProperties["signing.key"] as String,
        localProperties["signing.password"] as String)
    sign(tasks["jar"])
    sign(publishing.publications["mavenJava"])
}

kotlin {
    jvmToolchain(Versions.JVM)
}

kotlinTestRegistry {
}

tasks.jar {
    dependsOn(tasks.test)
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
