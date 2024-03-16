package cc.buessow.glumagic.input

import java.time.Instant

class DateValue(val timestamp: Instant, value: Number): Comparable<DateValue> {

  val value = value.toDouble()

  constructor(timestamp: Long, value: Number) :
      this(Instant.ofEpochMilli(timestamp), value)

  override fun compareTo(other: DateValue) =
    if (timestamp == other.timestamp) value.compareTo(other.value)
    else timestamp.compareTo(other.timestamp)

  override fun equals(other: Any?): Boolean {
    val that = other as? DateValue? ?: return false
    return timestamp == that.timestamp && value == that.value
  }

  override fun hashCode(): Int {
    var result = timestamp.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }

  operator fun component1() = timestamp
  operator fun component2() = value

  override fun toString() = "DV($timestamp, ${String.format("%.2f", value)})"
}
