import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
  id("com.adarshr.test-logger")
  checkstyle
}

configure<TestLoggerExtension> {
  theme = ThemeType.MOCHA
  showPassed = false
  showSkipped = false
  showFailed = true
  showOnlySlow = false
}

configure<JavaPluginExtension> {
  toolchain {
    languageVersion = JavaLanguageVersion.of(Versions.JVM)
  }
  sourceCompatibility = Versions.sourceCompatibility
  targetCompatibility = Versions.targetCompatibility
}

tasks.withType<Test> {
  useJUnitPlatform()
}
