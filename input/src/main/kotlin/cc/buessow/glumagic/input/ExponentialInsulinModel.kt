package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant
import kotlin.math.*

/** Exponential insulin model for FIASP etc. taken from
 * https://seemycgm.com/2017/10/21/exponential-insulin-curves-fiasp/
 */
class ExponentialInsulinModel(timeToPeak: Duration, private val totalDuration: Duration) {

  companion object {
    val fiasp = ExponentialInsulinModel(
        Duration.ofMinutes(55), Duration.ofMinutes(360))
  }

  private val tp = toMinutes(timeToPeak)
  private val td = toMinutes(totalDuration)

  /** Time constant of exp decay. */
  private val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
  /** Rise time factor. */
  private val a = 2 * tau / td
  /** Auxiliary scale factor. */
  private val S = 1 / (1 - a + (1 + a) * exp(-td / tau))

  private fun toMinutes(t: Duration) = t.toMillis() / 60_000.0

  private fun insulinAction(t: Double) =
      (S / (tau * tau)) * t * (1 - t / td) * exp(-t / tau)
  fun insulinAction(t: Duration) = insulinAction(toMinutes(t))

  private fun insulinOnBoard(t: Double) =
      1 - S * (1 - a) * ((t * t) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1

  @Suppress("Unused")
  fun insulinOnBoard(t: Duration) = insulinOnBoard(toMinutes(t))

  fun valuesAt(values: List<DateValue>, times: Iterable<Instant>): List<Double> {
    val results = mutableListOf<Double>()

    var winStart = 0
    for (t in times) {
      // Move the first value in the time window we're interested in.
      while (winStart < values.size && t - totalDuration > values[winStart].timestamp)
        winStart++

      var total = 0.0
      var i = winStart
      while (i < values.size) {
        val (ti, vi) = values[i].let { it.timestamp to it.value }
        val td = Duration.between(ti, t)
        if (td <= Duration.ZERO) break
        val ia = insulinAction(td)
        total += vi * ia
        i++
      }
      results.add(total)
    }
    return results
  }
}
