package cc.buessow.glumagic.input

import java.time.Duration
import java.time.Instant

interface ActionModel {
  companion object {
    fun create(args: Map<String, Any>): ActionModel {
      return when (args.getOrDefault("name", "LogNorm")) {
        "LogNorm" -> LogNormAction(args["mu"] as Double, args["sigma"] as Double)
        "Exponential" -> ExponentialInsulinModel(
            Duration.ofMinutes((args["peak"] as Number).toLong()),
            Duration.ofMinutes((args["total"] as Number).toLong()))
        else -> throw IllegalArgumentException("unknown ActionModel ${args["name"]}")
      }
    }
  }

  fun getArgs(): Map<String, Any>

  fun valuesAt(values: List<DateValue>, start: Instant, times: Iterable<Instant>): List<Double>
}
