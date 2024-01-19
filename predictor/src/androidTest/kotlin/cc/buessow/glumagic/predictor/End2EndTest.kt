package cc.buessow.glumagic.predictor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import cc.buessow.glumagic.BuildConfig
import cc.buessow.glumagic.input.ArrayApproxCompare
import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class End2EndTest {

  @get:Rule
  val internetRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.INTERNET)

  private fun withPredictor(a: (Predictor) -> Unit) {
    val cl = this::class.java.classLoader!!
    Predictor.create(
        cl.getResourceAsStream("glucose_model.json")!!,
        cl.getResourceAsStream("glucose_model.tflite")!!).use { p ->
      assertTrue(p.isValid)
      a(p)
    }
  }

  @Test
  fun trainingData() {
    MongoApiInputProvider(
        apiUrl = BuildConfig.MONGO_API_URL,
        database = BuildConfig.MONGO_DATABASE,
        apiKey = BuildConfig.MONGO_API_KEY).use { input ->
      val from = Instant.parse("2023-10-01T10:10:00Z")
      val upto = Instant.parse("2024-01-01T00:00:00Z")
      val config = Config(
          trainingPeriod = Duration.between(from, upto),
          hrLong = listOf(Duration.ofHours(24), Duration.ofHours(48)),
          hrHighThreshold = 120,
          zoneId = ZoneOffset.UTC)

      val td = runBlocking { DataLoader.getTrainingData(input, from, config) }

      td.glucose.forEachIndexed { i, v ->
        assertTrue("glucose $i was $v", v.isNaN() || v < 500F) }
      td.heartRate.forEachIndexed { i, v ->
        assertTrue("hr $i was $v", v.isNaN() || v in 20F .. 300F) }
      td.hrLong1.forEachIndexed { i, v ->
        if (v !in 0F .. 12 * 24F) {
          fail("hr24 $i was $v")
        }}
      td.hrLong2.forEachIndexed { i, v ->
        assertTrue("hr48 $i was $v", v in -0.1F .. 12 * 48F) }
    }
  }


  @Test
  fun run() = withPredictor { p ->
    MongoApiInputProvider(
        apiUrl = BuildConfig.MONGO_API_URL,
        database = BuildConfig.MONGO_DATABASE,
        apiKey = BuildConfig.MONGO_API_KEY).use { input ->
      val at = Instant.parse("2023-10-01T10:10:00Z")

      val gl = runBlocking {
        input.getGlucoseReadings(at-Duration.ofMinutes(6), at + Duration.ofHours(2))
      }
      assertEquals(22, gl.size)
      assertNull(
         ArrayApproxCompare.getMismatch(
            DataLoader.align(
                at, gl, at + Duration.ofHours(2), Duration.ofMinutes(5)).toList(),
            listOf(
                124.629F, 132.047F, 134.000F, 131.010F, 126.500F, 122.953F, 121.512F, 121.500F,
                125.395F, 123.024F, 118.542F, 117.430F, 118.605F, 120.953F, 119.000F, 111.349F,
                109.117F, 112.470F, 94.678F, 88.211F, 94.575F, 89.822F, 85.068F, 80.314F),
            eps = 1e-2))

      val (lastGlucose, inputVector) = DataLoader.getInputVector(input, at, p.config)
      ArrayApproxCompare.approxEquals(80.313F, lastGlucose, 1e-2)
      assertNull(ArrayApproxCompare.getMismatch(
          inputVector.toList(),
          listOf(
              // Hour and Long Heart Rate
              10.000F, 2.000F, 2.000F,

              // Glucose Slopes
              0.000F, 0.937F, -0.104F, -0.750F, -0.806F, -0.499F, -0.145F, 0.388F, 0.152F, -0.685F,
              -0.559F, 0.006F, 0.352F, 0.039F, -0.960F, -0.988F, 0.112F, -1.444F, -2.426F, -0.010F,
              0.161F, -0.951F, -0.951F, -0.475F,
              0.000F, -0.010F, -0.169F, -0.070F, 0.025F, 0.066F, 0.089F, 0.030F, -0.107F, -0.071F,
              0.069F, 0.091F, 0.003F, -0.131F, -0.103F, 0.107F, -0.046F, -0.254F, 0.143F, 0.259F,
              -0.094F, -0.111F, 0.048F, 0.095F,

              // Insulin Action
              2.747F, 2.494F, 2.253F, 2.028F, 1.825F, 1.647F, 1.498F, 1.349F, 1.259F, 1.191F,
              1.126F, 1.074F, 1.048F, 1.024F, 1.017F, 1.004F, 1.015F, 1.032F, 1.056F, 1.083F,
              1.110F, 1.131F, 1.144F, 1.143F, 1.122F, 1.086F, 1.048F, 1.007F, 0.975F, 0.961F,
              0.921F, 0.938F, 0.954F, 0.983F, 1.060F, 1.194F,

              // Carb Action
              0.525F, 0.453F, 0.392F, 0.339F, 0.294F, 0.255F, 0.222F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.146F, 1.772F, 6.233F, 12.622F, 19.008F, 23.976F, 26.988F, 28.130F, 27.782F,
              26.395F, 24.373F, 22.033F, 19.601F, 17.226F, 14.999F,

              // Heart Rate
              68.400F, 66.000F, 63.200F, 70.600F, 60.600F, 52.200F, 52.400F, 51.400F, 49.800F,
              48.400F, 49.800F, 51.000F, 52.200F, 52.400F, 52.000F, 52.000F, 52.600F, 51.200F,
              51.200F, 53.800F, 52.000F, 52.400F, 51.692F, 50.583F, 60.000F, 60.000F, 60.000F,
              60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F),
          1e-2))

      val glucosePredictions = p.predictGlucose(at, input)
      assertNull(ArrayApproxCompare.getMismatch(
          glucosePredictions,
          listOf(80.140, 81.498, 84.160, 87.067, 89.378, 91.106,
                 92.827, 94.071, 95.128, 95.033, 95.586, 96.276),
          1e-2))
    }
  }
}
