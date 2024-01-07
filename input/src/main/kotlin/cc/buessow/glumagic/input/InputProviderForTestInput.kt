package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant

class InputProviderForTestInput(private val testData: Config.TestData) : InputProvider {
  private val input = testData.inputData

  private fun toDateValue(m: Int, v: Double) =
    DateValue(testData.at + Duration.ofMinutes(m.toLong()), v)

  override suspend fun getGlucoseReadings(from: Instant) = 
      input.minute.zip(input.glucose).map { (m, v) -> toDateValue(m, v) }

  override suspend fun getHeartRates(from: Instant) =
      input.minute.zip(testData.heartRates).map { (m, v) -> toDateValue(m, v) }

  override suspend fun getLongHeartRates(at: Instant, threshold: Int, durations: List<Duration>) =
    testData.hrLongCounts

  override suspend fun getBasalProfileSwitches(from: Instant): MlProfileSwitches? = null

  override suspend fun getTemporaryBasalRates(from: Instant): List<MlTemporaryBasalRate> =
    emptyList()

  override suspend fun getCarbs(from: Instant) = testData.carbEvents

  override suspend fun getBoluses(from: Instant) = testData.insulinEvents
}
