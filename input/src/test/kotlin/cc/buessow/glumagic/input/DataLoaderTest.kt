package cc.buessow.glumagic.input

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.lang.Float.isNaN
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
      carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
      insulinAction = Config.LogNorm(peakInMinutes =60, sigma = 0.5),
      hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
      zone = ZoneId.of("UTC"))

  private val now = Instant.parse("2013-12-13T20:00:00Z")
  private val from = now - config.trainingPeriod

  @Test
  fun align_empty() {
    val values = emptyList<DateValue>()
    val aligned = DataLoader.align(from - ofMinutes(10), values, from, config.freq)
    assertCollectionEqualsF(aligned.toList(), Float.NaN, Float.NaN, eps = 1e-2)
  }

  @Test
  fun align_oneBefore() {
    val values1 = listOf(DateValue(now + ofMinutes(-2), 8.0))
    val aligned1 = DataLoader.align(now, values1, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned1.toList(), 8F, 8F, eps = 1e-2)
  }

  @Test
  fun align_oneWithin() {
    val values2 = listOf(DateValue(now + ofMinutes(2), 8.0))
    val aligned2 = DataLoader.align(now, values2, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned2.toList(), Float.NaN, 8F, eps = 1e-2)
  }

  @Test
  fun align_oneAfter() {
    val values2 = listOf(DateValue(now + ofMinutes(12), 8.0))
    val aligned2 = DataLoader.align(now, values2, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned2.toList(), Float.NaN, Float.NaN, eps = 1e-2)
  }

  @Test
  fun align_more() {
    val values = listOf(
        DateValue(now - ofMinutes(4), 15.0),
        DateValue(now + ofMinutes(4), 5.0)
    )
    val aligned = DataLoader.align(now, values, now + ofMinutes(10), config.freq)
    assertCollectionEqualsF(aligned.toList(), 10F, 5F, eps = 1e-2)
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
    assertCollectionEqualsF(aligned.toList(), 16F, 10F, 5F, eps = 1e-2)
  }

  @Test
  fun loadGlucoseReadings_empty() = runBlocking {
    val dp = mock<InputProvider> {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(emptyList())
    }
    val dataLoader = DataLoader(dp, now, config)
    val values = dataLoader.loadGlucoseReadings()
    assertCollectionEqualsF(values, *FloatArray(6) { Float.NaN }, eps = 1e-2)
    verify(dp).getGlucoseReadings(now - DataLoader.preFetch, now + config.trainingPeriod)
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
    assertCollectionEqualsF(values, Float.NaN, 80F, 120F, 120F, 120F, 120F, eps = 1e-2)
    verify(dp).getGlucoseReadings(
        Instant.parse("2013-12-13T19:24:00Z"),
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
    assertCollectionEqualsF(values, *FloatArray(6) { Float.NaN }, 60.0F, 60.0F, 60.0F, eps = 1e-2)
    verify(dp).getHeartRates(Instant.parse("2013-12-13T19:24:00Z"))
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
        values, Float.NaN, 80F, 120F, 120F, 120F, 120F, 60F, 60.0F, 60.0F, eps = 1e-2
    )
    verify(dp).getHeartRates(Instant.parse("2013-12-13T19:24:00Z"))
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
        80F, 80F, 121F, 121F, 121F, 121.00F, 121.00F, 121.00F, 121.00F,
        eps = 1e-2)
    assertCollectionEqualsF(
        values[1], 0F, 0F, 1F, 1F, 1F, 1F, 1F, 1F, 1F, eps = 1e-2)
    assertCollectionEqualsF(
        values[2], 2F, 2F, 3F, 2F, 1F, 1F, 1F, 1F, 1F, eps = 1e-2)
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
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes =60, sigma = 0.5),
        hrHighThreshold = 99,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zone = ZoneId.of("UTC"))
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
    assertCollectionEqualsF(values[1], *FloatArray(count) { 12F }, eps = 1e-2)
    assertCollectionEqualsF(values[2], *FloatArray(count) { 24F }, eps = 1e-2)
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
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes = 60, sigma = 0.5),
        hrHighThreshold = 99,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zone = ZoneId.of("UTC"))
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
    assertCollectionEqualsF(values[1], *FloatArray(count) { i -> 8F }, eps = 0.1)
    assertCollectionEqualsF(values[2], *FloatArray(count) { 16F }, eps = 0.1)
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
    assertCollectionEqualsF(values, 4F, 5F, eps = 1e-2)
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
    assertCollectionEqualsF(carbs, *FloatArray(9) { 0F }, eps = 1e-2)
    assertCollectionEqualsF(carbAction, *FloatArray(9) { 0F }, eps = 1e-2)
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
    assertCollectionEqualsF(carbs, 0F, 120F, 50F, 0F, 0F, 0F, 0F, 0F, 0F, eps = 1e-4)
    assertCollectionEqualsF(
        carbAction,
        28.73F, 24.59F, 20.99F, 18.50F, 22.75F, 40.90F, 71.58F, 105.85F, 134.91F,
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
    assertCollectionEqualsF(bolus, 0F, 120F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, eps = 1e-4)
    assertCollectionEqualsF(basal, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, eps = 1e-4)
    assertCollectionEqualsF(
        action,
        40.55F, 36.93F, 33.43F, 30.25F, 28.83F, 31.72F, 39.79F, 51.50F, 64.30F,
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
        dataLoader.loadBasalRates(),
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
        dataLoader.loadBasalRates(),
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
        dataLoader.loadBasalRates(),
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
        DataLoader.applyTemporaryBasals(basals, emptyList(), Instant.parse("2013-12-13T03:00:00Z")),
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
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes =60, sigma = 0.5),
        hrHighThreshold = 120,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zone = ZoneId.of("CET"))
    config.trainingPeriod / config.freq

    val input: InputProvider = mock() {
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
        glucose = List(6) { Float.NaN },
        glucoseSlope1 = List(6) { Float.NaN },
        glucoseSlope2 = List(6) { Float.NaN },
        heartRate = List(6) { Float.NaN },
        hrLong1 = List(6) { 0F },
        hrLong2 = List(6) { 0F },
        carbs = List(6) { 0F },
        carbAction = List(6) { 0F },
        bolus = List(6) { 0F },
        basal = List(6) { 0F },
        insulinAction = List(6) { 0F })
    assertEquals(expected, DataLoader.getTrainingData(input, at, config))
  }

  @Test
  fun getTrainingData() {
    val at = Instant.parse("2013-12-13T00:00:00Z")
    val config = Config(
        trainingPeriod = ofMinutes(30),
        predictionPeriod = Duration.ZERO,
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes = 60, sigma = 0.5),
        hrHighThreshold = 120,
        hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)),
        zone = ZoneId.of("CET"))
    val dates = (at..<(at + config.trainingPeriod) step config.freq).toList()

    val input: InputProvider = mock() {
      onBlocking { getGlucoseReadings(any(), anyOrNull()) }.thenReturn(
          dates.mapIndexed { i, d -> DateValue(d, 100.0 + i) })
      onBlocking { getHeartRates(any(), anyOrNull()) }.thenReturn(
          (at - Duration.ofHours(2) ..< at + config.trainingPeriod step config.freq)
              .asIterable()
              .mapIndexed { i, d -> DateValue(d, 110.0 + i) })
      onBlocking { getCarbs(any(), anyOrNull()) }.thenReturn(listOf(
          DateValue(Instant.parse("2013-12-13T00:05:00Z"), 10.0)))
      onBlocking { getBoluses(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getTemporaryBasalRates(any(), anyOrNull()) }.thenReturn(emptyList())
      onBlocking { getBasalProfileSwitches(any(), anyOrNull()) }.thenReturn(null)
    }

    val td = DataLoader.getTrainingData(input, at, config)
    val expected = TrainingInput(
        date = dates,
        hour = List(6) { 1 },
        glucose = dates.indices.map { i -> 100F + i },
        glucoseSlope1 = listOf(0F, 0.2F, 0.2F, 0.2F, 0.2F, 0F),
        glucoseSlope2 = listOf(0F, 0.02F, 0F, 0F, -0.02F, 0F),
        heartRate = dates.indices.map { i -> 134F + i },
        hrLong1 = List(6) { 12F },
        hrLong2 = List(6) { i -> 14F + i },
        carbs = listOf(0F, 10F, 0F, 0F, 0F, 0F),
        carbAction = listOf(0F, 0F, 6.0148083E-4F, 0.10177184F, 0.8399423F, 2.5200033F),
        bolus = List(6) { 0F },
        basal = List(6) { 0F },
        insulinAction = List(6) { 0F })
    assertEquals(expected, td)

    val qat = at - DataLoader.preFetch
    verifyBlocking(input) { getGlucoseReadings(qat, at + config.trainingPeriod) }
    verifyBlocking(input) { getHeartRates(at - config.hrLong.max(), at + config.trainingPeriod) }
    verifyBlocking(input) { getBasalProfileSwitches(at, at + config.trainingPeriod) }
    verifyBlocking(input) {
      getCarbs(at - LogNormAction.maxAge, at + config.trainingPeriod) }
    verifyBlocking(input) {
      getBoluses(at - LogNormAction.maxAge, at + config.trainingPeriod) }
  }


  companion object {

    private fun eqApprox(a: Float, b: Float, eps: Double) =
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

    fun assertCollectionEqualsF(actual: Collection<Float>, vararg expected: Float, eps: Double) {
      assertCollectionEquals(
          actual,
          *expected.toTypedArray(),
          toString = { f: Float -> "%.2f".format(f) + "F" }
      ) { a: Float, b: Float -> eqApprox(a, b, eps) }
    }
  }
}
