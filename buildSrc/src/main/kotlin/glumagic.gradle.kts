import com.adarshr.gradle.testlogger.TestLoggerExtension

plugins {
  id("com.adarshr.test-logger")
  checkstyle
}

configure<TestLoggerExtension> {
  showPassed = false
  showSkipped = false
  showFailed = true
  showOnlySlow = false
}

tasks.withType<Test> {
  useJUnitPlatform()
}
