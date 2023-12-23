package cc.buessow.glumagic.input

import java.time.Instant

data class DateValue(val timestamp: Instant, val value: Double) {
  constructor(timestamp: Long, value: Double) :
      this(Instant.ofEpochMilli(timestamp), value)

  override fun toString() = "DateValue($timestamp, ${String.format("%.2f", value)})"
}