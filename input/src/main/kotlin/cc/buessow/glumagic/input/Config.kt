package cc.buessow.glumagic.input

import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class Config(
    @SerializedName("trainingPeriodMinutes")
    val trainingPeriod: Duration,
    @SerializedName("predictionPeriodMinutes")
    val predictionPeriod: Duration,
    @SerializedName("hrLongDurationMinutes")
    val hrLong: List<Duration>, // = listOf(Duration.ofHours(24), Duration.ofHours(48)),
    val hrHighThreshold: Int,
    @SerializedName("freqMinutes")
    val freq: Duration = Duration.ofMinutes(5),
    val testData: List<TestData> = emptyList(),
    var zoneId: ZoneId = ZoneOffset.UTC,
) {

  companion object {
    fun fromJson(input: InputStream): Config = JsonParser.fromJson(input)
    fun fromJson(jsonFile: File): Config = JsonParser.fromJson(jsonFile)
  }
  val inputSize
    get() =
      // hour of day and long heart rates
      1 + hrLong.size +
          // glucose slope and slope of slope
          2 * (trainingPeriod / freq) +
          // carb, insulin and heart rate
          3 * ((trainingPeriod + predictionPeriod) / freq)

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
      val outputSlopes: List<Float>,
      val outputGlucose: List<Float>,
      val heartRates: List<Double>,
      val carbEvents: List<DateValue>,
      val insulinEvents: List<DateValue>,
      val hrLongCounts: List<Int>,
  ) {
    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
  }
}
