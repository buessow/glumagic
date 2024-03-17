package cc.buessow.glumagic.input

import java.time.Instant
import java.time.ZoneId

data class MlProfileSwitches(
    val firstPermanent: MlProfileSwitch,
    val first: MlProfileSwitch,
    val switches: List<MlProfileSwitch>) {

  fun toBasal(
      from: Instant,
      to: Instant,
      zoneId: ZoneId) = sequence {

    var current: MlProfileSwitch? = first
    var currentPermanent = firstPermanent
    if (current == null || from < current.start) {
      yield(DateValue(from, 0.0))
    }

    var t = from.coerceAtLeast(current?.start ?: from)
    val iter = switches.sortedBy { mlps -> mlps.start}.iterator()
    while (current != null) {
      val next = iter.nextOrNull()
      val nextStart = next?.start ?: to
      if (!current.isPermanent) {
        val end = (t + current.duration).coerceAtMost(nextStart)
        yieldAll(current.toBasal(t, end, zoneId))
        t = end
      } else {
        currentPermanent = current
      }
      yieldAll(currentPermanent.toBasal(t, nextStart, zoneId))
      t = nextStart
      current = next
    }
  }
}
