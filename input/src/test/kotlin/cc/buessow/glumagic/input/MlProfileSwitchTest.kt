package cc.buessow.glumagic.input

import cc.buessow.glumagic.input.DataLoaderTest.Companion.assertCollectionEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.*

class MlProfileSwitchTest {

  private val from = Instant.now()
  private val UTC = ZoneOffset.UTC
  private val CET = ZoneId.of("CET")

  private fun profileToBasalRates(
      start: String, zoneId: ZoneId, vararg basals: Pair<Int, Double>): List<DateValue> {
    val start = LocalDateTime.parse(start).atZone(zoneId).toInstant()
    val bs = basals.map { (m, amount) -> Duration.ofMinutes(m.toLong()) to amount }.toList()
    val coveredDuration = Duration.ofMinutes(basals.sumOf { (m, _) -> m }.toLong())
    val ps = MlProfileSwitch(
        "test",
        start,
        bs + listOf(Duration.ofDays(1) - coveredDuration to 0.0))
    return ps.toBasal(start, start + Duration.ofHours(4), zoneId).toList()
  }

  private fun dv(date: String, zoneId: ZoneId, amount: Double) = DateValue(
      LocalDateTime.parse(date).atZone(zoneId).toInstant(), amount)

  @Test
  fun profileToBasal_empty() {
    val start = Instant.parse("2013-12-13T10:00:00Z")
    val end = Instant.parse("2013-12-14T10:00:00Z")
    assertThrows<IllegalArgumentException> {
      MlProfileSwitch("test", from, emptyList()).toBasal(start, end, ZoneOffset.UTC).toList()
    }
  }

  @Test
  fun profileToBasal_single() {
    val basals = listOf(Duration.ofHours(24) to 1.1)

    val start = Instant.parse("2013-12-13T10:00:00Z")
    val end = Instant.parse("2013-12-13T22:00:00Z")

    // zero duration
    val basalRates0 =
      MlProfileSwitch("test", from, basals).toBasal(start, start, ZoneOffset.UTC).toList()
    assertCollectionEquals(basalRates0)

    val basalRates1 =
      MlProfileSwitch("test", from, basals).toBasal(start, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(basalRates1, DateValue(start, 1.1))

    val startTz = Instant.parse("2013-12-13T09:00:00Z")
    val basalRatesTz = MlProfileSwitch("test", from, basals).toBasal(
        startTz, end, ZoneOffset.ofHours(1)).toList()
    assertCollectionEquals(
        basalRatesTz, dv("2013-12-13T09:00:00", UTC, 1.1))

    // profileToBasal will output at lest one basal rate per day
    val start2 = Instant.parse("2013-12-12T10:00:00Z")
    val basalRates2 =
      MlProfileSwitch("test", from, basals).toBasal(start2, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(
        basalRates2,
        dv("2013-12-12T10:00:00", UTC, 1.1),
        dv("2013-12-13T00:00:00", UTC, 1.1))

    val basalRates3 = MlProfileSwitch("test", from, basals, rate = 2.0).toBasal(
        start, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(basalRates3, DateValue(start, 2.2))
  }

  @Test
  fun profileToBasal_more() {
    val basals = listOf(
        Duration.ofHours(6) to 1.1, Duration.ofHours(16) to 1.2, Duration.ofHours(2) to 0.9)
    val start1 = Instant.parse("2013-12-13T10:00:00Z")
    val end = Instant.parse("2013-12-14T10:00:00Z")
    val basalRates1 = MlProfileSwitch(
        "test", from, basals).toBasal(start1, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(
        basalRates1,
        dv("2013-12-13T10:00:00", UTC, 1.2),
        dv("2013-12-13T22:00:00", UTC, 0.9),
        dv("2013-12-14T00:00:00", UTC, 1.1),
        dv("2013-12-14T06:00:00", UTC, 1.2))

    val start2 = Instant.parse("2013-12-12T23:00:00Z")
    val basalRates2 = MlProfileSwitch(
        "test", from, basals).toBasal(start2, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(
        basalRates2,
        dv("2013-12-12T23:00:00", UTC, 0.9),
        dv("2013-12-13T00:00:00", UTC, 1.1),
        dv("2013-12-13T06:00:00", UTC, 1.2),
        dv("2013-12-13T22:00:00", UTC, 0.9),
        dv("2013-12-14T00:00:00", UTC, 1.1),
        dv("2013-12-14T06:00:00", UTC, 1.2))
  }

  @Test
  fun toBasal_hourly() {
    val bps = MlProfileSwitch(
        name = "test",
        start = Instant.parse("2013-12-13T10:00:00Z"),
        basalRates = (0..<24).map { Duration.ofHours(1) to 1.0 + it / 100.0 })
    assertTrue(bps.isValid)
    val start1 = Instant.parse("2013-12-13T15:00:00Z")
    val end = Instant.parse("2013-12-14T01:00:00Z")
    val basalRates1 = bps.toBasal(start1, end, ZoneOffset.UTC).toList()
    assertCollectionEquals(
        basalRates1,
        dv("2013-12-13T15:00:00", UTC, 1.15),
        dv("2013-12-13T16:00:00", UTC, 1.16),
        dv("2013-12-13T17:00:00", UTC, 1.17),
        dv("2013-12-13T18:00:00", UTC, 1.18),
        dv("2013-12-13T19:00:00", UTC, 1.19),
        dv("2013-12-13T20:00:00", UTC, 1.20),
        dv("2013-12-13T21:00:00", UTC, 1.21),
        dv("2013-12-13T22:00:00", UTC, 1.22),
        dv("2013-12-13T23:00:00", UTC, 1.23),
        dv("2013-12-14T00:00:00", UTC, 1.00))
  }

  @Test
  fun profileToBasal_dstWinterSummer1() {
    val basalRates = profileToBasalRates(
        "2024-03-31T01:00:00", CET,
        120 to 1.1, 60 to 1.2, 240 to 1.3)
    assertCollectionEquals(
        basalRates,
        dv("2024-03-31T01:00:00", CET, 1.1),
        dv("2024-03-31T02:00:00", CET, 1.3))
  }

  @Test
  fun profileToBasal_dstWinterSummer2() {
    val basalRates = profileToBasalRates(
        "2024-03-31T01:00:00", CET,
        90 to 1.1, 90 to 1.2, 240 to 1.3)
    assertCollectionEquals(
        basalRates,
        dv("2024-03-31T01:00:00", CET, 1.1),
        dv("2024-03-31T01:30:00", CET, 1.2),
        dv("2024-03-31T03:00:00", CET, 1.3))   // also 02:00 CET
  }

  @Test
  fun profileToBasal_dstWinterSummer3() {
    val basalRates = profileToBasalRates(
        "2024-03-31T01:00:00", CET,
        90 to 1.1, 60 to 1.2, 60 to 1.3, 240 to 1.4)
    assertCollectionEquals(
        basalRates,
        dv("2024-03-31T01:00:00", CET, 1.1),
        dv("2024-03-31T01:30:00", CET, 1.2),
        dv("2024-03-31T03:00:00", CET, 1.3),  // also 02:00 CET
        dv("2024-03-31T04:00:00", CET, 1.4))
  }

  @Test
  fun profileToBasal_dstWinterSummer4() {
    val basalRates = profileToBasalRates(
        "2024-03-31T01:00", CET,
        90 to 1.1, 45 to 1.2, 15 to 1.3, 90 to 1.4, 180 to 1.5)
    assertCollectionEquals(
        basalRates,
        dv("2024-03-31T01:00", CET, 1.1),
        dv("2024-03-31T01:30", CET, 1.2),
        dv("2024-03-31T03:00", CET, 1.4),
        dv("2024-03-31T04:30", CET, 1.5))
  }

  @Test
  fun profileToBasal_dstSummerWinter1() {
    val basalRates = profileToBasalRates(
        "2024-10-27T01:00:00", CET,
        120 to 1.1, 60 to 1.2, 240 to 1.3)
    assertCollectionEquals(
        basalRates,
        dv("2024-10-27T01:00:00", CET, 1.1),
        dv("2024-10-27T02:00:00", CET, 1.2),
        dv("2024-10-27T01:00:00", UTC, 1.2),
        dv("2024-10-27T03:00:00", CET, 1.3))
  }

  @Test
  fun profileToBasal_dstSummerWinter2() {
    val basalRates = profileToBasalRates(
        "2024-10-27T01:00:00", CET,
        120 to 1.1, 65 to 1.2, 240 to 1.3)
    assertCollectionEquals(
        basalRates,
        dv("2024-10-27T01:00:00", CET, 1.1),
        dv("2024-10-27T00:00:00", UTC, 1.2),
        dv("2024-10-27T01:00:00", UTC, 1.2),
        dv("2024-10-27T02:05:00", UTC, 1.3))
  }

  @Test
  fun profileToBasal_dstSummerWinter3() {
    val basalRates = profileToBasalRates(
        "2024-10-27T01:00:00", CET,
        120 to 1.1, 30 to 1.2, 15 to 1.3, 20 to 1.4, 240 to 1.5)
    assertCollectionEquals(
        basalRates,
        dv("2024-10-27T01:00:00", CET, 1.1),
        dv("2024-10-27T00:00:00", UTC, 1.2),
        dv("2024-10-27T00:30:00", UTC, 1.3),
        dv("2024-10-27T00:45:00", UTC, 1.4),
        dv("2024-10-27T01:00:00", UTC, 1.2),
        dv("2024-10-27T01:30:00", UTC, 1.3),
        dv("2024-10-27T01:45:00", UTC, 1.4),
        dv("2024-10-27T02:05:00", UTC, 1.5))
  }
}
