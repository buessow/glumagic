package cc.buessow.glumagic.input

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.lang.Float.isNaN
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Instant
import kotlin.math.abs

class DataLoaderTest {

  private val config = Config(
      trainingPeriod = ofMinutes(30),
      predictionPeriod = ofMinutes(15),
      hrHighThreshold = 120,
      hrLong = listOf(Duration.ofHours(1), Duration.ofHours(2)))

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
  fun loadGlucoseReadings_empty() {
    val dp = mock<DataProvider> {
      on { getGlucoseReadings(any()) }.thenReturn(Single.just(emptyList()))
    }
    val dataLoader = DataLoader(dp, now, config)
    val values = dataLoader.loadGlucoseReadings().blockingGet()
    assertCollectionEqualsF(values, *FloatArray(6) { Float.NaN }, eps = 1e-2)
    verify(dp).getGlucoseReadings((now - ofMinutes(6)))
  }

  @Test
  fun loadGlucoseReadings() {
    val dp = mock<DataProvider> {
      on { getGlucoseReadings(any()) }.thenReturn(
          Single.just(
              listOf(
                  DateValue(Instant.parse("2013-12-13T19:35:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:40:00Z"), 120.0),
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadGlucoseReadings().blockingGet()
    assertCollectionEqualsF(values, Float.NaN, 80F, 120F, 120F, 120F, 120F, eps = 1e-2)
    verify(dp).getGlucoseReadings(Instant.parse("2013-12-13T19:24:00Z"))
  }

  @Test
  fun loadHeartRates_empty() {
    val dp = mock<DataProvider> {
      on { getHeartRates(any()) }.thenReturn(Single.just(emptyList()))
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadHeartRates().blockingGet()
    assertCollectionEqualsF(values, *FloatArray(6) { Float.NaN }, 60.0F, 60.0F, 60.0F, eps = 1e-2)
    verify(dp).getHeartRates(Instant.parse("2013-12-13T19:24:00Z"))
  }

  @Test
  fun loadHeartRates() {
    val dp = mock<DataProvider> {
      on { getHeartRates(any()) }.thenReturn(
          Single.just(
              listOf(
                  DateValue(Instant.parse("2013-12-13T19:35:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:40:00Z"), 120.0),
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadHeartRates().blockingGet()
    assertCollectionEqualsF(
        values, Float.NaN, 80F, 120F, 120F, 120F, 120F, 60.0F, 60.0F, 60.0F, eps = 1e-2
    )
    verify(dp).getHeartRates(Instant.parse("2013-12-13T19:24:00Z"))
  }

  @Test
  fun loadLongHeartRates() {
    val dp = mock<DataProvider> {
      on { getLongHeartRates(any(), any(), any()) }.thenReturn(Single.just(listOf(4, 5)))
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadLongHeartRates().blockingGet()
    assertCollectionEqualsF(values, 4F, 5F, eps = 1e-2)
    verify(dp).getLongHeartRates(
        Instant.parse("2013-12-13T20:00:00Z"), 120, config.hrLong
    )
  }

  @Test
  fun loadCarbs_empty() {
    val dp = mock<DataProvider> {
      on { getCarbs(any()) }.thenReturn(Single.just(emptyList()))
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadCarbAction().blockingGet()
    assertCollectionEqualsF(values, *FloatArray(9) { 0F }, eps = 1e-2)
    verify(dp).getCarbs(Instant.parse("2013-12-13T15:30:00Z"))
  }

  @Test
  fun loadCarbs() {
    val dp = mock<DataProvider> {
      on { getCarbs(any()) }.thenReturn(
          Single.just(
              listOf(
                  DateValue(Instant.parse("2013-12-13T18:00:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:15:00Z"), 120.0),
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadCarbAction().blockingGet()
    assertCollectionEqualsF(
        values,
        38.811302F,
        54.82804F,
        77.4348F,
        98.96094F,
        114.48894F,
        122.4908F,
        123.627625F,
        119.49986F,
        111.85238F,
        eps = 1e-2
    )
    verify(dp).getCarbs(Instant.parse("2013-12-13T15:30:00Z"))
  }

  @Test
  fun loadInsulinAction() {
    val dp = mock<DataProvider> {
      on { getBoluses(any()) }.thenReturn(
          Single.just(
              listOf(
                  DateValue(Instant.parse("2013-12-13T18:00:00Z"), 80.0),
                  DateValue(Instant.parse("2013-12-13T19:15:00Z"), 120.0),
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, Instant.parse("2013-12-13T19:30:00Z"), config)
    val values = dataLoader.loadInsulinAction().blockingGet()
    assertCollectionEqualsF(
        values,
        42.355312F,
        44.484722F,
        51.670033F,
        62.434647F,
        74.27621F,
        84.97886F,
        93.15497F,
        98.23983F,
        100.26634F,
        eps = 1e-3
    )
    verify(dp).getBoluses(Instant.parse("2013-12-13T15:30:00Z"))
  }

  @Test
  fun loadBasalRates_noTemp() {
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

    val dp = mock<DataProvider> {
      on { getBasalProfileSwitches(any()) }.thenReturn(
          Maybe.just(MlProfileSwitches(bps, bps, emptyList()))
      )
      on { getTemporaryBasalRates(any()) }.thenReturn(Single.just(emptyList()))
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().blockingGet(),
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
  }

  @Test
  fun loadBasalRates_withTemp() {
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

    val dp = mock<DataProvider> {
      on { getBasalProfileSwitches(any()) }.thenReturn(
          Maybe.just(MlProfileSwitches(bps, bps, emptyList()))
      )
      on { getTemporaryBasalRates(any()) }.thenReturn(
          Single.just(
              listOf(
                  MlTemporaryBasalRate(
                      Instant.parse("2013-12-13T00:10:00Z"),
                      ofMinutes(10),
                      1.1
                  )
              )
          )
      )
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().blockingGet(),
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
  fun loadBasalRates_multipleBasalPer5Minutes() {
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

    val dp = mock<DataProvider> {
      on { getBasalProfileSwitches(any()) }.thenReturn(
          Maybe.just(MlProfileSwitches(bps, bps, emptyList()))
      )
      on { getTemporaryBasalRates(any()) }.thenReturn(Single.just(emptyList()))
    }
    val dataLoader = DataLoader(dp, now, config)
    assertCollectionEquals(
        dataLoader.loadBasalRates().blockingGet(),
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
  fun applyTemporaryBasals_noTemp() {
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
  }

  @Test
  fun applyTemporaryBasals_temps() {
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
  fun applyTemporaryBasals_multipleTempsPerBasal() {
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
      throw AssertionError("expected [$e] but was [$a]")
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
