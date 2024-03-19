package cc.buessow.glumagic.input

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.lang.Double.isNaN
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

class DataLoaderTest {

  private val config = Config(
      trainingPeriod = ofMinutes(30),
      predictionPeriod = ofMinutes(15),
      hrHighThreshold = 120,
      carbAction = LogNormAction(timeToPeak = ofMinutes(45), sigma = 0.5),
      insulinAction = LogNormAction(timeToPeak = ofMinutes(60), sigma = 0.5),
      hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
      zoneId = ZoneId.of("UTC"))

  private val now = Instant.parse("2013-12-13T20:00:00Z")
  private val from = now - config.trainingPeriod

  @Test
  fun align_empty() {
    val values = emptyList<DateValue>()
    val aligned = DataLoader.align(from - ofMinutes(10), values, from, config.freq)
    assertCollectionEqualsF(aligned.toList(), Double.NaN, Double.NaN, eps = 1e-2)
  }

  @Test
  fun align_oneBefore() {
    val values1 = listOf(DateValue(now + ofMinutes(-2), 8.0))
    val aligned1 = DataLoader.align(now, values1, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned1.toList(), 8.0, 8.0, eps = 1e-2)
  }

  @Test
  fun align_oneWithin() {
    val values2 = listOf(DateValue(now + ofMinutes(2), 8.0))
    val aligned2 = DataLoader.align(now, values2, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned2.toList(), Double.NaN, 8.0, eps = 1e-2)
  }

  @Test
  fun align_oneAfter() {
    val values2 = listOf(DateValue(now + ofMinutes(12), 8.0))
    val aligned2 = DataLoader.align(now, values2, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned2.toList(), Double.NaN, Double.NaN, eps = 1e-2)
  }

  @Test
  fun align_more() {
    val values = listOf(
        DateValue(now - ofMinutes(4), 15.0),
        DateValue(now + ofMinutes(4), 5.0)
    )
    val aligned = DataLoader.align(now, values, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned.toList(), 10.0, 5.0, eps = 1e-2)
  }

  @Test
  fun align_more2() {
    val values = listOf(
        DateValue(now - ofMinutes(8), 25.0),
        DateValue(now - ofMinutes(4), 20.0),
        DateValue(now + ofMinutes(1), 15.0),
        DateValue(now + ofMinutes(9), 5.0)
    )
    val aligned = DataLoader.align(now, values, now + ofMinutes(15), config.freq)
    assertCollectionEqualsF(aligned.toList(), 16.0, 10.0, 5.0, eps = 1e-2)
  }

  @Test
  fun align_gap() {
    val values = listOf(
        DateValue(now - ofMinutes(8), 25.0),
        DateValue(now - ofMinutes(4), 20.0),
        DateValue(now + ofMinutes(1), 15.0),
        DateValue(now + ofMinutes(12), 5.0)
    )
    val aligned = DataLoader.align(now, values, now + ofMinutes(20), config.freq)
    assertCollectionEqualsF(aligned.toList(), 16.0, 11.36, 6.82, 5.0, eps = 1e-2)
  }

  @Test
  fun loadGlucoseReadings_empty() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, now, config)
    val values = dataLoader.loadGlucoseReadings()
    assertCollectionEqualsF(values, *DoubleArray(8) { Double.NaN }, eps = 1e-2)
    verify(dp).getGlucoseReadings(
        now - DataLoader.preFetch - Duration.ofMinutes(10),
        now + config.trainingPeriod)
    Unit
  }

  @Test
  fun loadGlucoseReadings() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(
              listOf(
                  DateValue(Instant.parse("2013-12-13T19:35:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:40:00Z"), 120.0),
              )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadGlucoseReadings()
    assertCollectionEqualsF(
        values, Double.NaN, Double.NaN, Double.NaN,80.0, 120.0, 120.0, 120.0, 120.0, eps = 1e-2)
    verify(dp).getGlucoseReadings(
        Instant.parse("2013-12-13T19:14:00Z"),
        Instant.parse("2013-12-13T20:00:00Z"))
    Unit
  }

  @Test
  fun loadHeartRates_empty() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadHeartRates()
    assertCollectionEqualsF(values, *DoubleArray(6) { 60.0 }, 60.0, 60.0, 60.0, eps = 1e-2)
    verify(dp).getHeartRates(
        Instant.parse("2013-12-13T19:24:00Z"),Instant.parse("2013-12-13T20:15:00Z"))
    Unit
  }

  @Test
  fun loadHeartRates() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(
              listOf(
                  DateValue(Instant.parse("2013-12-13T19:35:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:40:00Z"), 120.0),
              )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadHeartRates()
    assertCollectionEqualsF(
        values, 60.0, 80.0, 120.0, 120.0, 120.0, 120.0, 120.0, 120.0, 120.0, eps = 1e-2
    )
    verify(dp).getHeartRates(
        Instant.parse("2013-12-13T19:24:00Z"), Instant.parse("2013-12-13T20:15:00Z"))
    Unit
  }

  @Test
  fun loadHeartRates2() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getHeartRates(any(), any()) }.thenReturn(
          listOf(
              DateValue(Instant.parse("2013-12-13T17:15:00Z"), 139.0),
              DateValue(Instant.parse("2013-12-13T17:45:00Z"), 139.0),
              DateValue(Instant.parse("2013-12-13T17:50:00Z"), 139.0),
              DateValue(Instant.parse("2013-12-13T19:35:00Z"), 80.0),
              DateValue(Instant.parse("2013-12-13T19:40:00Z"), 121.0),
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadHeartRatesWithLong()
    assertEquals(3, values.size)
    assertCollectionEqualsF(
        values[0],
        80.0, 80.0, 121.0, 121.0, 121.0, 121.0, 121.0, 121.0, 121.0,
        eps = 1e-2)
    assertCollectionEqualsF(
        values[1], 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, eps = 1e-2)
    assertCollectionEqualsF(
        values[2], 2.0, 2.0, 3.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, eps = 1e-2)
    verify(dp).getHeartRates(
        Instant.parse("2013-12-13T17:30:00Z"),
        Instant.parse("2013-12-13T20:15:00Z"))
    Unit
  }

  @Test
  fun loadHeartRatesWithLong_allHigh() = runBlocking {
    val dataFrom = Instant.parse("2020-01-01T00:00:00Z")
    val from = Instant.parse("2020-01-01T02:00:00Z")
    val upto = Instant.parse("2020-06-01T00:00:00Z")
    val c = Config(
        trainingPeriod = Duration.between(from, upto),
        predictionPeriod = Duration.ZERO,
        carbAction = LogNormAction(timeToPeak = ofMinutes(45), sigma = 0.5),
        insulinAction = LogNormAction(timeToPeak = ofMinutes(60), sigma = 0.5),
        hrHighThreshold = 99,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zoneId = ZoneId.of("UTC"))
    val dp = mock<InputProvider> {
      onBlocking { getHeartRates(any(), any()) }.thenReturn(
        (dataFrom ..< upto step config.freq).mapIndexed { i, t ->
          DateValue(t + ofMinutes(1), 100 + i) })
    }
    val dataLoader = DataLoader(dp, from, c)
    val values = dataLoader.loadHeartRatesWithLong()
    assertEquals(3, values.size)
    val count = Duration.between(from, upto) / config.freq
    assertEquals(count, values[0].size)
    assertEquals(count, values[1].size)
    assertEquals(count, values[2].size)
    assertCollectionEqualsF(values[1], *DoubleArray(count) { 12.0 }, eps = 1e-2)
    assertCollectionEqualsF(values[2], *DoubleArray(count) { 24.0 }, eps = 1e-2)
    verify(dp).getHeartRates(dataFrom, upto)
    Unit
  }

  @Test
  fun loadHeartRatesWithLong_mixed() = runBlocking {
    val dataFrom = Instant.parse("2020-01-01T00:00:00Z")
    val from = Instant.parse("2020-01-01T02:00:00Z")
    val upto = Instant.parse("2020-01-01T05:00:00Z")
    val c = Config(
        trainingPeriod = Duration.between(from, upto),
        predictionPeriod = Duration.ZERO,
        carbAction = LogNormAction(timeToPeak = ofMinutes(45), sigma = 0.5),
        insulinAction = LogNormAction(timeToPeak = ofMinutes(60), sigma = 0.5),
        hrHighThreshold = 99,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zoneId = ZoneId.of("UTC"))
    val dp = mock<InputProvider> {
      onBlocking { getHeartRates(any(), any()) }.thenReturn(
          (dataFrom ..< upto step config.freq).mapIndexed { i, t ->
            DateValue(
                t + ofMinutes(1) - ofSeconds(5L - i % 10),
                when (i % 12) {
                  0 -> Double.NaN
                  1, 2, 3 -> 80 + (i / 100.0)
                  else -> 100 + i
                })})
    }
    val dataLoader = DataLoader(dp, from, c)
    val values = dataLoader.loadHeartRatesWithLong()
    assertEquals(config.hrLong.size + 1, values.size)
    val count = Duration.between(from, upto) / config.freq
    assertEquals(count, values[0].size)
    assertEquals(count, values[1].size)
    assertEquals(count, values[2].size)
    assertCollectionEqualsF(values[1], *DoubleArray(count) { _ -> 8.0 }, eps = 0.1)
    assertCollectionEqualsF(values[2], *DoubleArray(count) { 16.0 }, eps = 0.1)
    verify(dp).getHeartRates(dataFrom, upto)
    Unit
  }


  @Test
  fun loadLongHeartRates() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getLongHeartRates(any(), any(), any()) }.thenReturn(listOf(4, 5))
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadLongHeartRates()
    assertCollectionEqualsF(values, 4.0, 5.0, eps = 1e-2)
    verify(dp).getLongHeartRates(
        Instant.parse("2013-12-13T20:00:00Z"), 120, config.hrLong)
    Unit
  }

  @Test
  fun loadCarbs_empty() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val (carbs, carbAction) = dataLoader.loadCarbEventsAndAction()
    assertCollectionEqualsF(carbs, *DoubleArray(9) { 0.0 }, eps = 1e-2)
    assertCollectionEqualsF(carbAction, *DoubleArray(9) { 0.0 }, eps = 1e-2)
    verify(dp).getCarbs(
        Instant.parse("2013-12-13T15:30:00Z"),
        Instant.parse("2013-12-13T20:15:00Z"))
    Unit
  }

  @Test
  fun loadCarbs() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(
          listOf(
              DateValue(Instant.parse("2013-12-13T18:00:00Z"), 80.0),
              DateValue(Instant.parse("2013-12-13T19:36:00Z"), 120.0),
              DateValue(Instant.parse("2013-12-13T19:41:00Z"), 20.0),
              DateValue(Instant.parse("2013-12-13T19:42:00Z"), 30.0),
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val (carbs, carbAction) = dataLoader.loadCarbEventsAndAction()
    assertCollectionEqualsF(carbs, 0.0, 120.0, 50.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, eps = 1e-4)
    assertCollectionEqualsF(
        carbAction,
        28.73, 24.59, 20.99, 18.50, 22.75, 40.90, 71.58, 105.85, 134.91,
        eps = 1e-2)
    verify(dp).getCarbs(
        Instant.parse("2013-12-13T15:30:00Z"),
        Instant.parse("2013-12-13T20:15:00Z"))
    return@runBlocking
  }

  @Test
  fun loadInsulinAction() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getBoluses(any(), anyOrNull()) }.thenReturn(
              listOf(
                  DateValue(Instant.parse("2013-12-13T18:00:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:35:00Z"), 120.0),
              ))
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val (bolus, basal, action) = dataLoader.loadInsulinEventsAndAction()
    assertCollectionEqualsF(bolus, 0.0, 120.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, eps = 1e-4)
    assertCollectionEqualsF(basal, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, eps = 1e-4)
    assertCollectionEqualsF(
        action,
        40.55, 36.93, 33.43, 30.25, 28.83, 31.72, 39.79, 51.50, 64.30,
        eps = 1e-2)
    verify(dp).getBoluses(
        Instant.parse("2013-12-13T15:30:00Z"),
        Instant.parse("2013-12-13T20:15:00Z"))
    return@runBlocking
  }

  @Test
  fun loadBasalRates_noTemp() = runBlocking {
    val now = Instant.parse("2013-12-13T00:00:00Z")
    val bps = MlProfileSwitch(
        "test",
        now,
        listOf(
            ofMinutes(5) to 12.0,
            ofMinutes(25) to 24.0,
            ofMinutes(23 * 60 + 30) to 36.0
        )
    )

    val dp = mock<InputProvider> {
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(
          MlProfileSwitches(bps, bps, emptyList()))
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().drop(config.insulinAction.totalDuration / config.freq),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T00:05:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:10:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:15:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:20:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:25:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:30:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T00:35:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T00:40:00Z"), 3.0),
    )
    return@runBlocking
  }

  @Test
  fun loadBasalRates_withTemp() = runBlocking {
    val now = Instant.parse("2013-12-13T00:00:00Z")
    val bps = MlProfileSwitch(
        "test",
        now,
        listOf(
            ofMinutes(5) to 12.0,
            ofMinutes(25) to 24.0,
            ofMinutes(23 * 60 + 30) to 36.0
        )
    )

    val dp = mock<InputProvider> {
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(
          MlProfileSwitches(bps, bps, emptyList())
      )
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(
          listOf(
              MlTemporaryBasalRate(
                  Instant.parse("2013-12-13T00:10:00Z"),
                  ofMinutes(10),
                  1.1
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().drop(config.insulinAction.totalDuration /  config.freq),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T00:05:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:10:00Z"), 2.2),
        DateValue(Instant.parse("2013-12-13T00:15:00Z"), 2.2),
        DateValue(Instant.parse("2013-12-13T00:20:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:25:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T00:30:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T00:35:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T00:40:00Z"), 3.0),
    )
  }

  @Test
  fun loadBasalRates_multipleBasalPer5Minutes() = runBlocking {
    val now = Instant.parse("2013-12-13T00:00:00Z")
    val bps = MlProfileSwitch(
        "test",
        now,
        listOf(
            ofMinutes(1) to 60.0,
            ofMinutes(6) to 120.0,
            ofMinutes(23 * 60 + 53) to 180.0
        )
    )

    val dp = mock<InputProvider> {
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(
          MlProfileSwitches(bps, bps, emptyList()))
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().drop(config.insulinAction.totalDuration /  config.freq),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0 + 4 * 2.0),
        DateValue(Instant.parse("2013-12-13T00:05:00Z"), 2 * 2.0 + 3 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:10:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:15:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:20:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:25:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:30:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:35:00Z"), 5 * 3.0),
        DateValue(Instant.parse("2013-12-13T00:40:00Z"), 5 * 3.0),
    )
  }

  @Test
  fun applyTemporaryBasals_noTemp() = runBlocking {
    val basals = listOf(
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0)
    )
    assertCollectionEquals(
        DataLoader.applyTemporaryBasals(
            basals, emptyList(), Instant.parse("2013-12-13T03:00:00Z")),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0)
    )

    val temps = listOf(
        MlTemporaryBasalRate(
            Instant.parse("2013-12-12T22:00:00Z"), ofMinutes(40), 1.1
        ),
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T05:00:00Z"), ofMinutes(60), 1.2
        )
    )
    assertCollectionEquals(
        DataLoader.applyTemporaryBasals(basals, temps, Instant.parse("2013-12-13T03:00:00Z")),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0)
    )
    return@runBlocking
  }

  @Test
  fun applyTemporaryBasals_temps() = runBlocking {
    val basals = listOf(
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 0.5),
        DateValue(Instant.parse("2013-12-13T00:05:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0)
    )
    val temps = listOf(
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T00:30:00Z"), ofMinutes(40), 1.1
        ),
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T02:10:00Z"), ofMinutes(60), 1.2
        )
    )
    assertCollectionEquals(
        DataLoader.applyTemporaryBasals(basals, temps, Instant.parse("2013-12-13T03:00:00Z")),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 0.5),
        DateValue(Instant.parse("2013-12-13T00:05:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T00:30:00Z"), 1.1),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.2),
        DateValue(Instant.parse("2013-12-13T01:10:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T02:10:00Z"), 3.6)
    )
  }

  @Test
  fun applyTemporaryBasals_multipleTempsPerBasal() = runBlocking {
    val basals = listOf(
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0)
    )
    val temps = listOf(
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T00:10:00Z"), ofMinutes(10), 1.1
        ),
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T00:30:00Z"), ofMinutes(60), 1.2
        ),
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T02:10:00Z"), ofMinutes(60), 1.3
        ),
        MlTemporaryBasalRate(
            Instant.parse("2013-12-13T02:20:00Z"), ofMinutes(60), 1.0, basal = 0.1
        )
    )
    assertCollectionEquals(
        DataLoader.applyTemporaryBasals(basals, temps, Instant.parse("2013-12-13T03:00:00Z")),
        DateValue(Instant.parse("2013-12-13T00:00:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T00:10:00Z"), 1.1),
        DateValue(Instant.parse("2013-12-13T00:20:00Z"), 1.0),
        DateValue(Instant.parse("2013-12-13T00:30:00Z"), 1.2),
        DateValue(Instant.parse("2013-12-13T01:00:00Z"), 2.4),
        DateValue(Instant.parse("2013-12-13T01:30:00Z"), 2.0),
        DateValue(Instant.parse("2013-12-13T02:00:00Z"), 3.0),
        DateValue(Instant.parse("2013-12-13T02:10:00Z"), 3.9),
        DateValue(Instant.parse("2013-12-13T02:20:00Z"), 0.1)
    )
  }

  @Test
  fun getTrainingData_empty() {
    val at = Instant.parse("2013-12-13T00:00:00Z")
    val config = Config(
        trainingPeriod = ofMinutes(30),
        predictionPeriod = Duration.ZERO,
        carbAction = LogNormAction(timeToPeak = ofMinutes(45), sigma = 0.5),
        insulinAction = LogNormAction(timeToPeak = ofMinutes(60), sigma = 0.5),
        hrHighThreshold = 120,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zoneId = ZoneId.of("CET"))
    config.trainingPeriod / config.freq

    val input: InputProvider = mock {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getBoluses(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(null)
    }

    val expected = TrainingInput(
        date = (at ..< (at + config.trainingPeriod) step config.freq).toList(),
        hour = List(6) { 1 },
        glucose = List(6) { Double.NaN },
        glucoseSlope1 = List(6) { 0.0 },
        glucoseSlope2 = List(6) { 0.0 },
        heartRate = List(6) { Double.NaN },
        hrLong1 = List(6) { 0.0 },
        hrLong2 = List(6) { 0.0 },
        carbs = List(6) { 0.0 },
        carbAction = List(6) { 0.0 },
        bolus = List(6) { 0.0 },
        basal = List(6) { 0.0 },
        insulinAction = List(6) { 0.0 })
    assertEquals(expected, DataLoader.getTrainingData(input, at, config))
  }

  @Test
  fun getTrainingData() {
    val trainingFrom = Instant.parse("2013-12-13T00:00:00Z")
    val config = Config(
        trainingPeriod = ofMinutes(30),
        predictionPeriod = Duration.ZERO,
        carbAction = LogNormAction(timeToPeak = ofMinutes(45), sigma = 0.5),
        insulinAction = LogNormAction(timeToPeak = ofMinutes(60), sigma = 0.5),
        hrHighThreshold = 120,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zoneId = ZoneId.of("CET"))
    val dates = (trainingFrom..<(trainingFrom + config.trainingPeriod) step config.freq).toList()

    val input: InputProvider = mock {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(
          dates.mapIndexed { i, d -> DateValue(d, 100.0 + i) })
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(
          (trainingFrom - Duration.ofHours(2) ..< trainingFrom + config.trainingPeriod step config.freq)
              .asIterable()
              .mapIndexed { i, d -> DateValue(d, 110.0 + i) })
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(listOf(
          DateValue(Instant.parse("2013-12-13T00:05:00Z"), 10.0)))
      onBlocking { getBoluses(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(null)
    }

    val td = DataLoader.getTrainingData(input, trainingFrom, config)
    val expected = TrainingInput(
        date = dates,
        hour = List(6) { 1 },
        glucose = dates.indices.map { i -> 100.0 + i },
        glucoseSlope1 = listOf(0.0, 0.2, 0.2, 0.2, 0.2, 0.2),
        glucoseSlope2 = listOf(0.0, 0.04, 0.0, 0.0, 0.0, 0.0),
        heartRate = dates.indices.map { i -> 134.0 + i },
        hrLong1 = List(6) { 12.0 },
        hrLong2 = List(6) { i -> 14.0 + i },
        carbs = listOf(0.0, 10.0, 0.0, 0.0, 0.0, 0.0),
        carbAction = listOf(
            0.0, 0.0, 6.014808418383709E-4,
            0.10177184160514258, 0.8399422757728754, 2.520003225935824),
        bolus = List(6) { 0.0 },
        basal = List(6) { 0.0 },
        insulinAction = List(6) { 0.0 })
    assertEquals(expected, td)

    val qat = trainingFrom - DataLoader.preFetch
    verifyBlocking(input) {
      getGlucoseReadings(qat - ofMinutes(10), trainingFrom + config.trainingPeriod) }
    verifyBlocking(input) {
      getHeartRates(trainingFrom - config.hrLong.max(), trainingFrom + config.trainingPeriod) }
    verifyBlocking(input) { getBasalProfileSwitches(
        trainingFrom - config.insulinAction.totalDuration, trainingFrom + config.trainingPeriod) }
    verifyBlocking(input) {
      getCarbs(trainingFrom - config.carbAction.totalDuration, trainingFrom + config.trainingPeriod) }
    verifyBlocking(input) {
      getBoluses(trainingFrom - config.insulinAction.totalDuration, trainingFrom + config.trainingPeriod) }
  }

  @Test
  fun getInputVector() {
    val trainFrom = Instant.parse("2013-12-13T00:00:00Z")
    val trainUpto = trainFrom + config.trainingPeriod
    val dates = (trainFrom..<trainUpto step config.freq).toList()

    val input: InputProvider = mock {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(
          dates.mapIndexed { i, d -> DateValue(d, 100.0 + i) })
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(
          (trainFrom - Duration.ofHours(2) ..<trainUpto step config.freq)
              .asIterable()
              .mapIndexed { i, d -> DateValue(d, 110.0 + i) })
      onBlocking { getLongHeartRates(any(), any(), any()) }.thenReturn(listOf(2, 3))
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(listOf(
          DateValue(Instant.parse("2013-12-13T00:05:00Z"), 10.0)))
      onBlocking { getBoluses(any(), anyOrNull()) }.thenReturn(listOf(
          DateValue(Instant.parse("2013-12-13T00:10:00Z"), 12.0)))
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(null)
    }

    val c1 = config.copy(predictionPeriod = Duration.ZERO, xValues = listOf( "gl_00", "gl_05"))
    val (last1, v1) = DataLoader.getInputVector(input, trainUpto, c1)
    assertEquals(105.0, last1)
    assertNull(ArrayApproxCompare.getMismatch(v1.toList(), listOf(100F, 101F), eps = 1e-4))

    val c2 = config.copy(
        predictionPeriod = Duration.ZERO,
        xValues = listOf(
            "gl_00", "gls_05", "gls2_10",
            "hr_15", "hr_long_15", "hr_lon2_15",
            "ins_10", "ins_25", "ia_25",
            "0", "3.14",
            "carbs_05", "ca_20"))
    val (last2, v2) = DataLoader.getInputVector(input, trainUpto, c2)
    assertEquals(105.0, last2)
    assertNull(ArrayApproxCompare.getMismatch(
        v2.toList(),
        listOf(100F, 0.2F, 0.0F, 137.0F, 2.0F, 3.0F, 12.0F, 0.0F, 0.181F, 0.0F, 3.14F, 10.0F, 0.84F),
        eps = 1e-3))

    val qat = trainFrom - DataLoader.preFetch
    verifyBlocking(input, atLeastOnce()) {
      getGlucoseReadings(qat- Duration.ofMinutes(10), trainUpto) }
    verifyBlocking(input, atLeastOnce()) { getHeartRates(qat, trainUpto) }
    verifyBlocking(input, atLeastOnce()) {
      getLongHeartRates(trainUpto, config.hrHighThreshold, config.hrLong) }
    verifyBlocking(input, atLeastOnce()) {
      getBasalProfileSwitches(trainFrom - config.insulinAction.totalDuration, trainUpto) }
    verifyBlocking(input, atLeastOnce()) {
      getTemporaryBasalRates(trainFrom - config.insulinAction.totalDuration, trainUpto) }
    verifyBlocking(input, atLeastOnce()) {
      getCarbs(trainFrom - config.carbAction.totalDuration, trainUpto) }
    verifyBlocking(input, atLeastOnce()) {
      getBoluses(trainFrom -  config.insulinAction.totalDuration, trainUpto) }
    verifyNoMoreInteractions(input)
  }

  companion object {

    private fun eqApprox(a: Double, b: Double, eps: Double) =
      isNaN(a) && isNaN(b) || abs(a - b) < eps

    private fun eqApprox(a: DateValue, b: DateValue) =
      a.timestamp == b.timestamp && abs(a.value - b.value) < 1e-4

    private fun <T> firstMismatch(
        expected: Iterable<T>,
        actual: Iterable<T>,
        eq: (T, T) -> Boolean
    ): Int? {
      val eit = expected.iterator()
      val ait = actual.iterator()
      var i = 0
      while (eit.hasNext() && ait.hasNext()) {
        if (!eq(eit.next(), ait.next())) return i
        i++
      }
      return if (eit.hasNext() || ait.hasNext()) i else null
    }

    fun <T> assertCollectionEquals(
        actual: Collection<T>,
        vararg expected: T,
        toString: (T) -> String = { t: T -> t.toString() },
        eq: (T, T) -> Boolean,
    ) {
      val mis = firstMismatch(expected.toList(), actual, eq) ?: return
      val a = actual.mapIndexed { i, f ->
        (if (i == mis) "**" else "") + toString(f)
      }.joinToString()
      val e = expected.mapIndexed { i, f ->
        (if (i == mis) "**" else "") + toString(f)
      }.joinToString()
      throw AssertionError("\n expected  [$e]\n   but was [$a]")
    }

    fun assertCollectionEquals(actual: Collection<DateValue>, vararg expected: DateValue) =
      assertCollectionEquals(actual, *expected, eq = ::eqApprox)

    fun assertCollectionEqualsF(actual: Collection<Double>, vararg expected: Double, eps: Double) {
      assertCollectionEquals(
          actual,
          *expected.toTypedArray(),
          toString = { f: Double -> "%.2f".format(f) }
      ) { a: Double, b: Double -> eqApprox(a, b, eps) }
    }
  }
}
