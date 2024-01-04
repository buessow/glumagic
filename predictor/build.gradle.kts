import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "cc.buessow.glumagic"
version = "1.0"

android {
  namespace = "cc.buessow.glumagic"
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  compileSdk = 30
  buildToolsVersion = "34.0.0"


  testOptions {
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
    android {

    }
  }
  defaultConfig {
    multiDexEnabled = true
    minSdk = 30
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    setProperty("archivesBaseName", "cc.buessow.glumagic.predictor-1.0")
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/COPYRIGHT"
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/LICENSE-notice.md"
    }
  }
}

tasks.withType<Test> {
  // use to display stdout in travis
  testLogging {
    // set options for log level LIFECYCLE
    events = setOf(
        TestLogEvent.FAILED,
        TestLogEvent.STARTED,
        TestLogEvent.SKIPPED,
        TestLogEvent.STANDARD_OUT
    )
    exceptionFormat = TestExceptionFormat.FULL
  }
  useJUnitPlatform()
}

dependencies {
  implementation(project(":input"))
  implementation(libs.reactivex)
  implementation(libs.tensorflowLite)
  testImplementation(testLibs.bundles.base)
  androidTestImplementation(testLibs.bundles.androidx.test)
  androidTestImplementation(testLibs.bundles.base)
}