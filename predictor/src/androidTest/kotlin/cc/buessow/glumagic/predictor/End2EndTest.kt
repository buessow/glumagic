package cc.buessow.glumagic.predictor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import cc.buessow.glumagic.input.ArrayApproxCompare
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import cc.buessow.glumagic.test.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant


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
              10.000F, 5.000F, 5.000F,
              // Glucose Slopes
              0.000F, 0.937F, -0.104F, -0.750F, -0.806F, -0.499F, -0.145F, 0.388F, 0.152F, -0.685F,
              -0.559F, 0.006F, 0.352F, 0.039F, -0.960F, -0.988F, 0.112F, -1.444F, -2.426F, -0.010F,
              0.161F, -0.951F, -0.951F, -0.475F,
              0.000F, -0.010F, -0.169F, -0.070F, 0.025F, 0.066F, 0.089F, 0.030F, -0.107F, -0.071F,
              0.069F, 0.091F, 0.003F, -0.131F, -0.103F, 0.107F, -0.046F, -0.254F, 0.143F, 0.259F,
              -0.094F, -0.111F, 0.048F, 0.095F,
              // Insulin Action
              0.000F, 0.000F, 0.000F, 0.002F, 0.008F, 0.023F, 0.048F, 0.082F, 0.122F, 0.169F,
              0.219F, 0.270F, 0.321F, 0.369F, 0.413F, 0.454F, 0.493F, 0.528F, 0.560F, 0.588F,
              0.611F, 0.630F, 0.644F, 0.652F, 0.653F, 0.645F, 0.630F, 0.609F, 0.586F, 0.563F,
              0.538F, 0.510F, 0.479F, 0.448F, 0.422F, 0.404F,
              // Carb Action
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              // Heart Rate
              68.400F, 66.000F, 63.200F, 70.600F, 60.600F, 52.200F, 52.400F, 51.400F, 49.800F,
              48.400F, 49.800F, 51.000F, 52.200F, 52.400F, 52.000F, 52.000F, 52.600F, 51.200F,
              51.200F, 53.800F, 52.000F, 52.400F, 51.692F, 50.583F, 60.000F, 60.000F, 60.000F,
              60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F, 60.000F),
          1e-2))

      val glucosePredictions = p.predictGlucose(at, input)
      assertNull(ArrayApproxCompare.getMismatch(
          glucosePredictions,
          listOf(80.323, 81.221, 82.799, 84.005, 84.449, 84.493,
                 85.037, 85.885, 87.658, 88.976, 91.357, 93.860),
          1e-2))
    }
  }
}
