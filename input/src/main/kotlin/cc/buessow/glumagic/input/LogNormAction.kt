package cc.buessow.glumagic.input

import org.jetbrains.annotations.VisibleForTesting
import java.time.Duration
import java.time.Instant
import kotlin.math.*

internal class LogNormAction(private var mu: Double, private val sigma: Double = 1.0) {

  companion object {
    @VisibleForTesting
    internal val maxAge: Duration = Duration.ofHours(4)
  }

  constructor(config: Config.LogNorm) : this(config.mu, config.sigma)

  constructor(timeToPeak: Duration, sigma: Double = 1.0) : this(
      mu = ln(timeToPeak.toMillis() / 3600_000.0) + sigma * sigma,
      sigma = sigma
  )

  fun valuesAt(values: List<DateValue>, times: Iterable<Instant>): List<Double> {
    val results = mutableListOf<Double>()

    var winStart = 0
    for (t in times) {
      // Move the first value in the time window we're interested in.
      while (winStart < values.size && t - maxAge > values[winStart].timestamp)
        winStart++

      var total = 0.0
      var i = winStart
      while (i < values.size) {
        val (ti, vi) = values[i].let { it.timestamp to it.value }
        val td = Duration.between(ti, t)
        if (td <= Duration.ZERO) break
        val x = td.toMillis() / 3_600_000.0
        val exp = -(ln(x) - mu).pow(2) / (2 * sigma.pow(2))
        val y = 1 / (x * sigma * sqrt(2 * PI)) * exp(exp)
        if (!y.isFinite()) throw AssertionError()
        total += vi * y
        i++
      }
      results.add(total)
    }
    return results
  }
}
