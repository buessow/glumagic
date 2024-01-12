package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant

interface InputProvider {
  suspend fun getGlucoseReadings(from: Instant, upto: Instant? = null): List<DateValue>
  suspend fun getHeartRates(from: Instant, upto: Instant? = null): List<DateValue>
  suspend fun getCarbs(from: Instant, upto: Instant? = null): List<DateValue>
  suspend fun getBoluses(from: Instant, upto: Instant? = null): List<DateValue>
  suspend fun getLongHeartRates(at: Instant, threshold: Int, durations: List<Duration>): List<Int> {
    val maxDuration = durations.maxOrNull() ?: return emptyList()
    val hrs = getHeartRates(at - maxDuration)
    val counts = MutableList(durations.size) { 0 }
    for (hr in hrs) {
      for ((i, period) in durations.withIndex()) {
        if (hr.timestamp < at - period) continue
        if (hr.value >= threshold) counts[i]++
      }
    }
    return counts
  }

  suspend fun getBasalProfileSwitches(from: Instant, upto: Instant? = null): MlProfileSwitches?

  suspend fun getTemporaryBasalRates(from: Instant, upto: Instant? = null): List<MlTemporaryBasalRate>
}
