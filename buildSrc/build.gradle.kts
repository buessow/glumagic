plugins {
  kotlin("jvm") version "1.9.0"
}

repositories {
  mavenCentral()
}

tasks.test {
  useJUnitPlatform()
}

dependencies {
//  implementation(kotlin("stdlib-jdk8"))
  implementation("org.apache.logging.log4j:log4j-api:2.22.0")
  implementation("org.apache.logging.log4j:log4j-core:2.22.0")
  testImplementation(platform("org.junit:junit-bom:5.9.1"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}