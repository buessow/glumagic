package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MlProfileSwitch(
    val name: String?,
    val start: Instant,
    val basalRates: List<Pair<Duration, Double>>,
    val duration: Duration? = null,
    val rate: Double = 1.0) {

  val isValid = basalRates.sumOf { (d, _) -> d.seconds } == 24 * 3600L
  val isPermanent = duration == null || duration == Duration.ZERO

  fun toBasal(from: Instant, to: Instant, zoneId: ZoneId) = sequence {
    if (!isValid) throw IllegalArgumentException("Invalid basal profile")
    if (to <= from) return@sequence

    var i = 0
    var t = from.atZone(zoneId).truncatedTo(ChronoUnit.DAYS).toInstant()
    while (t < to) {
      var (d, amount) = basalRates[i]
      if (t > from) {
        yield(DateValue(t, amount * rate))
      } else if (t + d > from) {
        yield(DateValue(from, amount * rate))
      }

      // Daylight savings adjustments
      val nextTrans = zoneId.rules.nextTransition(t)
      if (nextTrans != null && t + d >= nextTrans.instant) {
        t = nextTrans.instant
        // Beginning of according to DST change.
        var t0 = t.atZone(zoneId).truncatedTo(ChronoUnit.DAYS).toInstant() - nextTrans.duration
        i = 0
        while (true) {
          t0 += basalRates[i].first
          if (t0 > t) break
          i = (i + 1) % basalRates.size
        }

      } else {
        t += d
        i = (i + 1) % basalRates.size
      }
    }
  }
}
