package cc.buessow.glumagic.predictor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import cc.buessow.glumagic.BuildConfig
import cc.buessow.glumagic.input.*
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class End2EndTest {

  @get:Rule
  val internetRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.INTERNET)

  private fun withPredictor(a: (Predictor) -> Unit) {
    val cl = this::class.java.classLoader!!
    Predictor.create(
        { modelBytes -> TensorflowInterpreter(modelBytes) },
        cl.getResourceAsStream("glucose_model.json")!!,
        cl.getResourceAsStream("glucose_model.tflite")!!).use { p ->
      assertTrue(p.isValid)
      a(p)
    }
  }

  private fun runTestData(p: Predictor, testData: Config.TestData) {
    MongoApiInputProvider(
        apiUrl = BuildConfig.MONGO_API_URL,
        database = BuildConfig.MONGO_DATABASE,
        apiKey = BuildConfig.MONGO_API_KEY).use { ip ->
      val (_, inputVector) = DataLoader.getInputVector(ip, testData.at, p.config)
      ArrayApproxCompare.getMismatch(
          inputVector.asList(), testData.inputVector.asList(), eps = 0.01)?.also { fail("\n"+it) }
      val pred = p.predictGlucose(testData.at, ip)
      val m =ArrayApproxCompare.getMismatch(
          testData.outputGlucose,
          pred.map(Number::toFloat), eps = 0.01)
      if (m != null) {
        fail(m)
      }
    }
  }

  @Test
  fun testDataE2E() = withPredictor { p ->
    for (testData in p.config.e2eTests.drop(1)) {
      runTestData(p, testData)
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
          carbAction = LogNormAction(timeToPeak = Duration.ofMinutes(45), sigma = 0.5),
          insulinAction = ExponentialInsulinModel.fiasp,
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
      val time = Instant.parse("2023-10-01T10:10:00Z")
      val localTime = time.atZone(ZoneId.of("CET"))

      val gl = runBlocking {
        input.getGlucoseReadings(
            time - p.config.freq.multipliedBy(1L),
            time + Duration.ofHours(3))
      }
      assertNull(
          ArrayApproxCompare.getMismatch(
              gl.map { it.value },
              listOf(
                  119.000,
                  130.000, 134.000, 134.000, 128.000, 125.000, 121.000,
                  122.000, 121.000, 130.000, 116.000, 121.000, 114.000,
                  123.000, 119.000, 119.000, 104.000, 114.000, 111.000,
                  79.000, 97.000, 78.000, 87.000, 141.000, 151.000,
                  152.000, 163.000, 180.000, 175.000, 179.000),
              eps = 1e-2))

      assertNull(
         ArrayApproxCompare.getMismatch(
             DataLoader.align(
                 time, gl,time + Duration.ofHours(3), p.config.freq).toList(),
             listOf(
                 124.629, 132.047, 134.000, 131.010, 126.500, 122.953,
                 121.512, 121.500, 125.395, 123.024, 118.542, 117.430,
                 118.605, 120.953, 119.000, 111.349, 109.117, 112.470,
                 94.678, 88.211, 94.575, 89.822, 85.068, 80.314,
                 82.605, 87.000, 103.297, 114.090, 124.883, 141.000,
                 146.067, 151.508, 157.611, 171.670, 177.458, 177.033),
             eps = 1e-2))

      val (lastGlucose, inputVector) = DataLoader.getInputVector(
          input,
          time+p.config.trainingPeriod,
          p.config.copy(
              hrHighThreshold = 120,
              insulinAction = ExponentialInsulinModel.fiasp,
              smoothingFilter = "none"))
      ArrayApproxCompare.approxEquals(80.314F, lastGlucose, 1e-2)

      assertEquals(12, localTime.hour)
      assertEquals(localTime.hour.toFloat(), inputVector[0]) // Hour
      val period = (p.config.trainingPeriod.seconds / p.config.freq.seconds).toInt()
      var start = 1
      assertNull(ArrayApproxCompare.getMismatch( // Glucose
          inputVector.toList().drop(start).take(period),
          listOf(
              124.629F, 132.047F, 134.000F, 131.010F, 126.500F, 122.953F, 121.512F, 121.500F,
              125.395F, 123.024F, 118.542F, 117.430F, 118.605F, 120.953F, 119.000F, 111.349F,
              109.117F, 112.470F, 94.678F, 88.211F, 94.575F, 89.822F, 85.068F, 80.314F),
          eps = 1e-3))
      start += period
      assertNull(ArrayApproxCompare.getMismatch( // Glucose Slopes
          inputVector.toList().drop(start).take(period),
          listOf(
              -1.120F, 1.484F, 0.391F, -0.598F, -0.902F, -0.709F, -0.288F, -0.002F, 0.779F,
              -0.474F, -0.896F, -0.222F, 0.235F, 0.470F, -0.391F, -1.530F, -0.446F, 0.671F,
              -3.558F, -1.293F, 1.273F, -0.951F, -0.951F, -0.951F),
          eps = 1e-3))
      start += period
      assertNull(ArrayApproxCompare.getMismatch( // Glucose Slopes 2
          inputVector.toList().drop(start).take(period),
          listOf(
              0.325F, 0.521F, -0.219F, -0.198F, -0.061F, 0.039F, 0.084F, 0.057F, 0.156F,
              -0.251F, -0.084F, 0.135F, 0.092F, 0.047F, -0.172F, -0.228F, 0.217F, 0.223F,
              -0.846F, 0.453F, 0.513F, -0.445F, -0.000F, 0.000F),
          eps = 1e-3))
      start += period
      assertNull(ArrayApproxCompare.getMismatch( // Insulin
          inputVector.toList().drop(start).take(period + 12),
          listOf(0.000F, 0.070F, 0.135F, 0.273F, 0.118F, 0.090F, 0.113F, 0.131F, 0.147F,
                 0.322F, 0.212F, 0.154F, 0.119F, 0.286F, 0.266F, 0.169F, 0.064F, 0.060F,
                 0.064F, 0.000F, 0.000F, 0.000F, 0.000F, 0.111F, 0.333F, 0.342F, 0.002F,
                 0.002F, 0.002F, 0.078F, 1.477F, 0.167F, 0.427F, 0.143F, 0.000F, 0.234F),
          eps = 1e-3))
      start += period + 12
      assertNull(ArrayApproxCompare.getMismatch( // Insulin Action
          inputVector.toList().drop(start).take(period + 12),
          listOf(0.248F, 0.237F, 0.226F, 0.216F, 0.208F, 0.201F, 0.194F, 0.187F, 0.181F,
                 0.176F, 0.172F, 0.169F, 0.167F, 0.165F, 0.164F, 0.164F, 0.164F, 0.163F,
                 0.162F, 0.160F, 0.157F, 0.154F, 0.150F, 0.145F, 0.140F, 0.138F, 0.137F,
                 0.136F, 0.134F, 0.132F, 0.129F, 0.135F, 0.143F, 0.151F, 0.158F, 0.163F),
          eps = 1e-3))
      start += period + 12
      assertNull(ArrayApproxCompare.getMismatch( // Carbs
          inputVector.toList().drop(start).take(period + 12),
          listOf(
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 30.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F),
          eps = 1e-3))
      start += period + 12
      assertNull(ArrayApproxCompare.getMismatch( // Carb Action
          inputVector.toList().drop(start).take(period + 12),
          listOf(
              0.525F, 0.453F, 0.392F, 0.339F, 0.294F, 0.255F, 0.222F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F,
              0.000F, 0.000F, 0.000F, 0.146F, 1.772F, 6.233F, 12.622F, 19.008F, 23.976F,
              26.988F, 28.130F, 27.782F, 26.395F, 24.373F, 22.033F, 19.601F, 17.226F, 14.999F),
          eps = 1e-3))
      start += period + 12
      assertNull(ArrayApproxCompare.getMismatch( // Heart Rate
          inputVector.toList().drop(start).take(period + 12),
          listOf(
              68.400F, 66.000F, 63.200F, 70.600F, 60.600F, 52.200F, 52.400F, 51.400F, 49.800F,
              48.400F, 49.800F, 51.000F, 52.200F, 52.400F, 52.000F, 52.000F, 52.600F, 51.200F,
              51.200F, 53.800F, 52.000F, 52.400F, 51.692F, 50.583F, 51.000F, 51.600F, 53.200F,
              56.400F, 61.600F, 65.800F, 67.600F, 78.345F, 91.667F, 78.200F, 59.618F, 54.000F),
          eps=1e-3))
      start += period + 12
      assertNull(ArrayApproxCompare.getMismatch( // Long Heart Rate
          inputVector.toList().drop(start),  // no [List::take] to check overall length
          listOf(2F, 2F),
          eps = 1e-3))

      val glucosePredictions = p.predictGlucose(time, input)
      assertNull(ArrayApproxCompare.getMismatch(
          glucosePredictions,
          listOf(
              118.144, 112.364, 107.960, 104.213, 102.522, 100.512,
              100.180, 100.467, 101.339, 102.542, 104.232, 104.258),
          eps = 1e-2))
    }
  }
}
