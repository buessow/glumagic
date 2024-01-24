plugins {
  `kotlin-dsl`
  `java-library`
}

tasks.compileJava {

}

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
  google()
  gradlePluginPortal()
}

dependencies {
  implementation("com.adarshr.test-logger:com.adarshr.test-logger.gradle.plugin:4.0.0")
}
