package cc.buessow.glumagic.input

import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Suppress("unused")
data class Config(
    @SerializedName("trainingPeriodMinutes")
    val trainingPeriod: Duration,
    @SerializedName("predictionPeriodMinutes")
    val predictionPeriod: Duration = Duration.ZERO,
    val carbAction: ActionModel,
    val insulinAction: ActionModel,
    @SerializedName("hrLongDurationMinutes")
    val hrLong: List<Duration>,
    val hrHighThreshold: Int,
    @SerializedName("freqMinutes")
    val freq: Duration = Duration.ofMinutes(5),
    val testData: List<TestData> = emptyList(),
    val e2eTests: List<TestData> = emptyList(),
    val zoneId: ZoneId,
    val xValues: List<String> = emptyList(),
    val yValues: List<String> = emptyList(),
    val smoothingFilter: String? = null,
    val smoothingParams: Map<String, Any>? = emptyMap()
) {

  companion object {
    fun fromJson(input: InputStream): Config = JsonParser.fromJson(input)
    fun fromJson(jsonFile: File): Config = JsonParser.fromJson(jsonFile)
  }

  val outputSize get() = predictionPeriod / freq  // glucose slope prediction

  data class TestInputData(
      val minute: List<Int>,
      val glucose: List<Double>,
      @SerializedName("gl_slope")
      val glSlopes1: List<Double>,
      @SerializedName("gl_slop2")
      val glSlopes2: List<Double>,
      @SerializedName("heart_rate")
      val heartRates: List<Double>,
      val ca: List<Double>,
      val ia: List<Double>,
  )

  data class TestData(
      val name: String,
      val at: Instant,
      val inputData: TestInputData,
      val inputVector: FloatArray,
      val basal: List<DateValue>?,
      val outputSlopes: List<Float>,
      val outputGlucose: List<Float>,
      val heartRates: List<Double>,
      val carbEvents: List<DateValue>,
      val insulinEvents: List<DateValue>,
      val hrLongCounts: List<Int>) {

    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
  }
}
