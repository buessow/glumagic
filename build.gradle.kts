plugins {
  id("org.jetbrains.kotlin.android")  version "1.9.20" apply false
  id("com.gradleup.nmcp")
}

nmcp {
  publishAggregation {
    project(":input")
  }
}
