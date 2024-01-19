import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.util.*

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    glumagic
}

group = "cc.buessow.glumagic"
version = "1.0"

val localProperties = Properties()
File(rootProject.projectDir, "local.properties").inputStream().use { localProperties.load(it) }

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
      isIncludeAndroidResources = false
    }
    android {
    }
  }
  buildFeatures.buildConfig = true
  defaultConfig {
    multiDexEnabled = true
    minSdk = 30
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    setProperty("archivesBaseName", "cc.buessow.glumagic.predictor-1.0")

    localProperties.entries
        .map { (k, v) -> k.toString() to v.toString() }
        .filter { (k, _) -> k.startsWith("mongo.") }
        .forEach { (k, v) ->
          buildConfigField("String", k.replace('.', '_').uppercase(), v)
    }
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/COPYRIGHT"
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/LICENSE-notice.md"
      excludes += "META-INF/native-image/org.mongodb/bson/native-image.properties"
    }
  }
}

tasks.withType<Test> {
  testLogging {
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
  implementation(project(":mongodb"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.tensorflowLite)
  testImplementation(testLibs.bundles.base)
  androidTestImplementation(testLibs.bundles.androidx.test)
  androidTestImplementation(testLibs.bundles.base)
}
