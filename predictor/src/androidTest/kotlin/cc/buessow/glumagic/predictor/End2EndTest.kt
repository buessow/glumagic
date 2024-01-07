package cc.buessow.glumagic.predictor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import cc.buessow.glumagic.input.ArrayApproxCompare
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import cc.buessow.glumagic.test.BuildConfig
import org.junit.Assert
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
      Assert.assertTrue(p.isValid)
      a(p)
    }
  }

  @Test
  fun run() = withPredictor { p ->
    MongoApiInputProvider(
        apiUrl = BuildConfig.MONGO_API_URL,
        database = BuildConfig.MONGO_DATABASE,
        apiKey = BuildConfig.MONGO_API_KEY).use { dp ->
      val at = Instant.parse("2023-10-01T10:10:00Z")
      val glucosePredictions = p.predictGlucose(at, dp)
      assertNull(ArrayApproxCompare.getMismatch(
          glucosePredictions,
          listOf(80.121, 81.505, 84.246, 87.221, 89.586, 91.430,
                 93.274, 94.575, 95.641, 95.527, 96.096, 96.856),
          1e-2))
    }
  }
}
