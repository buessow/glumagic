package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant

class InputProviderForTestInput(private val from: Instant, private val testData: Config.TestData) : InputProvider {
  private val input = testData.inputData

  private fun toDateValue(m: Int, v: Double) =
    DateValue(from + Duration.ofMinutes(m.toLong()), v)

  override suspend fun getGlucoseReadings(from: Instant, upto: Instant?) =
      input.minute.zip(input.glucose).map { (m, v) -> toDateValue(m, v) }

  override suspend fun getHeartRates(from: Instant, upto: Instant?) =
      input.minute.zip(testData.heartRates).map { (m, v) -> toDateValue(m, v) }

  override suspend fun getLongHeartRates(at: Instant, threshold: Int, durations: List<Duration>) =
    testData.hrLongCounts

  override suspend fun getBasalProfileSwitches(from: Instant, upto: Instant?): MlProfileSwitches? {
    val basals = testData.basal?.takeUnless (List<DateValue>::isEmpty) ?: return null
    val pss = basals.map { dv ->
      MlProfileSwitch(
          name = "test",
          start = dv.timestamp,
          basalRates = listOf(Duration.ofDays(1) to dv.value))
    }
    return MlProfileSwitches(pss[0], pss[0], pss.drop(1))
  }

  override suspend fun getTemporaryBasalRates(from: Instant, upto: Instant?): List<MlTemporaryBasalRate> =
    emptyList()

  override suspend fun getCarbs(from: Instant, upto: Instant?) = testData.carbEvents

  override suspend fun getBoluses(from: Instant, upto: Instant?) = testData.insulinEvents
}
