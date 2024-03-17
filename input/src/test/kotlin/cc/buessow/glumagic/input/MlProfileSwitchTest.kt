package cc.buessow.glumagic.input

import cc.buessow.glumagic.input.DataLoaderTest.Companion.assertCollectionEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MlProfileSwitchTest {

    private val from = Instant.now()

    @Test
    fun profileToBasal_empty() {
        val start = Instant.parse("2013-12-13T10:00:00Z")
        val end = Instant.parse("2013-12-14T10:00:00Z")
        assertThrows<IllegalArgumentException> {
            MlProfileSwitch("test", from, emptyList()).toBasal(start, end, ZoneOffset.UTC).toList() }
    }

    @Test
    fun profileToBasal_single() {
        val basals = listOf(Duration.ofHours(24) to 1.1)

        val start = Instant.parse("2013-12-13T10:00:00Z")
        val end = Instant.parse("2013-12-13T22:00:00Z")

        // zero duration
        val basalRates0 = MlProfileSwitch("test", from, basals)
            .toBasal(start, start, ZoneOffset.UTC).toList()
        assertCollectionEquals(basalRates0)

        val basalRates1 = MlProfileSwitch("test", from, basals).toBasal(start, end, ZoneOffset.UTC).toList()
        assertCollectionEquals(basalRates1, DateValue(start, 1.1))

        val startTz = Instant.parse("2013-12-13T09:00:00Z")
        val basalRatesTz = MlProfileSwitch("test", from, basals).toBasal(
            startTz,
            end,
            ZoneOffset.ofHours(1)).toList()
        assertCollectionEquals(
            basalRatesTz,
            DateValue(Instant.parse("2013-12-13T09:00:00Z"), 1.1))

        // profileToBasal will output at lest one basal rate per day
        val start2 = Instant.parse("2013-12-12T10:00:00Z")
        val basalRates2 = MlProfileSwitch("test", from, basals).toBasal(start2, end, ZoneOffset.UTC).toList()
        assertCollectionEquals(
            basalRates2,
            DateValue(Instant.parse("2013-12-12T10:00:00Z"), 1.1),
            DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.1))

        val basalRates3 = MlProfileSwitch("test", from, basals, rate = 2.0).toBasal(
            start,
            end,
            ZoneOffset.UTC).toList()
        assertCollectionEquals(basalRates3, DateValue(start, 2.2))
    }

    @Test
    fun profileToBasal_more() {
        val basals = listOf(
            Duration.ofHours(6) to 1.1,
            Duration.ofHours(16) to 1.2,
            Duration.ofHours(2) to 0.9)
        val start1 = Instant.parse("2013-12-13T10:00:00Z")
        val end = Instant.parse("2013-12-14T10:00:00Z")
        val basalRates1 = MlProfileSwitch(
            "test", from, basals).toBasal(start1, end, ZoneOffset.UTC).toList()
        assertCollectionEquals(
            basalRates1,
            DateValue(Instant.parse("2013-12-13T10:00:00Z"), 1.2),
            DateValue(Instant.parse("2013-12-13T22:00:00Z"), 0.9),
            DateValue(Instant.parse("2013-12-14T00:00:00Z"), 1.1),
            DateValue(Instant.parse("2013-12-14T06:00:00Z"), 1.2))

        val start2 = Instant.parse("2013-12-12T23:00:00Z")
        val basalRates2 = MlProfileSwitch(
            "test", from, basals).toBasal(start2, end, ZoneOffset.UTC).toList()
        assertCollectionEquals(
            basalRates2,
            DateValue(Instant.parse("2013-12-12T23:00:00Z"), 0.9),
            DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.1),
            DateValue(Instant.parse("2013-12-13T06:00:00Z"), 1.2),
            DateValue(Instant.parse("2013-12-13T22:00:00Z"), 0.9),
            DateValue(Instant.parse("2013-12-14T00:00:00Z"), 1.1),
            DateValue(Instant.parse("2013-12-14T06:00:00Z"), 1.2))
    }

    @Test
    fun toBasal_hourly() {
        val bps = MlProfileSwitch(
            name = "test",
            start = Instant.parse("2013-12-13T10:00:00Z"),
            basalRates = (0 ..< 24).map { Duration.ofHours(1) to 1.0 + it / 100.0 })
        assertTrue(bps.isValid)
        val start1 = Instant.parse("2013-12-13T15:00:00Z")
        val end = Instant.parse("2013-12-14T01:00:00Z")
        val basalRates1 = bps.toBasal(start1, end, ZoneOffset.UTC).toList()
        assertCollectionEquals(
            basalRates1,
            DateValue(Instant.parse("2013-12-13T15:00:00Z"), 1.15),
            DateValue(Instant.parse("2013-12-13T16:00:00Z"), 1.16),
            DateValue(Instant.parse("2013-12-13T17:00:00Z"), 1.17),
            DateValue(Instant.parse("2013-12-13T18:00:00Z"), 1.18),
            DateValue(Instant.parse("2013-12-13T19:00:00Z"), 1.19),
            DateValue(Instant.parse("2013-12-13T20:00:00Z"), 1.20),
            DateValue(Instant.parse("2013-12-13T21:00:00Z"), 1.21),
            DateValue(Instant.parse("2013-12-13T22:00:00Z"), 1.22),
            DateValue(Instant.parse("2013-12-13T23:00:00Z"), 1.23),
            DateValue(Instant.parse("2013-12-14T00:00:00Z"), 1.00))
    }

}
