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

tasks.withType<Test> {
  useJUnitPlatform()
}
