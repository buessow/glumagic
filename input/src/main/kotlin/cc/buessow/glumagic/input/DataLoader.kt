package cc.buessow.glumagic.input

import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.round

class DataLoader(
    private val inputProvider: InputProvider,
    time: Instant,
    private val config: Config) {

  private val inputFrom: Instant = time.truncatedTo(ChronoUnit.MINUTES)
  private val inputAt = inputFrom + config.trainingPeriod
  private val inputUpTo = inputAt + config.predictionPeriod
  private val intervals = inputFrom ..< inputUpTo step config.freq
  private val carbAction = LogNormAction(config.carbAction)
  private val insulinAction = LogNormAction(config.insulinAction)

  companion object {
    @VisibleForTesting
    internal val preFetch = Duration.ofMinutes(6)

    private fun assertZero(value: Int, text: String) {
      assert(value == 0) { "expect no $text, got $value" }
    }
    private fun assertWithin(name: String, values: List<Float>, min: Float, max: Float) {
      assertZero(values.count { !it.isNaN() && it < min}, "$name < $min")
      assertZero(values.count { !it.isNaN() && it > max}, "$name > $max")
    }

    fun getInputVector(input: InputProvider, time: Instant, config: Config) = runBlocking {
      DataLoader(input, time, config).getInputVector()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTrainingData(input: InputProvider, time:Instant, config: Config) = runBlocking {
      val dl = DataLoader(input, time, config)
      val deferredGlucose = async { dl.loadGlucoseReadings() }
      val deferredHeartRate = async { dl.loadHeartRatesWithLong() }
      val deferredCarbAction = async { dl.loadCarbEventsAndAction() }
      val deferredInsulin = async { dl.loadInsulinEventsAndAction() }

      assertWithin("glucose", deferredGlucose.await(), 20F, 500F)
      assertWithin("hear rate", deferredHeartRate.await()[0], 20F, 300F)
      assertWithin("long heart rate 1", deferredHeartRate.await()[1], 0F, 10000F)
      assertWithin("long heart rate 2", deferredHeartRate.await()[2], 0F, 10000F)

      val gl = deferredGlucose.await()
      val hours = dl.intervals.map { ts -> OffsetDateTime.ofInstant(ts, config.zoneId).hour }
      val glSlope1 = dl.slope(gl)
      val glSlope2 = dl.slope(glSlope1)

      val (carbs, carbAction) = deferredCarbAction.await()
      val insulin = deferredInsulin.await()

      assertWithin("carbs", carbs, 0F, 200F)
      assertWithin("carb action", carbAction, 0F, 200F)
      assertWithin("insulin bolus", insulin.bolus, 0F, 100F)
      assertWithin("insulin basal", insulin.basal, 0F, 100F)
      assertWithin("insulin action", insulin.action, 0F, 100F)

      TrainingInput(date = dl.intervals.toList(),
                    hour = hours,
                    glucose = gl, glucoseSlope1 = glSlope1, glucoseSlope2 = glSlope2,
                    heartRate = deferredHeartRate.await().first(),
                    hrLong1 = deferredHeartRate.await()[1],
                    hrLong2 = deferredHeartRate.await()[2],
                    carbs = carbs,
                    carbAction = carbAction,
                    bolus = insulin.bolus,
                    basal = insulin.basal,
                    insulinAction = insulin.action)
    }

    @VisibleForTesting
    fun applyTemporaryBasals(
        basals: List<DateValue>,
        tempBasals: List<MlTemporaryBasalRate>,
        to: Instant): List<DateValue> {
      val result = mutableListOf<DateValue>()

      val itTemp = MlTemporaryBasalRate.adjustDuration(tempBasals).iterator()
      var temp = itTemp.nextOrNull()
      for (i in basals.indices) {
        val dv = basals[i]
        val nextStart = basals.elementAtOrNull(i + 1)?.timestamp ?: to
        // Search for first temporary basal that can have an impact on this
        // basal.
        while (temp != null && temp.end < dv.timestamp) {
          temp = itTemp.nextOrNull()
        }
        // No temporary basal already active when this basal starts, so keep
        // basal at start time.
        if (temp == null || temp.timestamp > dv.timestamp) {
          // Temporary adjustment starts later.
          result.add(dv)
        }
        // Iterate over all temporary basal that start before this basal
        // ends (i.e. the next starts).
        while (temp != null && temp.timestamp < nextStart) {
          result.add(
              DateValue(
                  // Temporary basal might have started earlier than this basal,
                  // don't move start time.
                  temp.timestamp.coerceAtLeast(dv.timestamp),
                  // Apply temporary adjustment.
                  (temp.basal ?: dv.value) * temp.rate
              )
          )

          // Temporary basal until the end of this basal: we are done.
          if (temp.end >= nextStart) break

          // We need to revert to normal basal until end of basal or next
          // temporary basal unless their is no gap between this and next
          // temporary basal.
          val tempEnd = temp.end
          temp = itTemp.nextOrNull()
          if (temp == null || tempEnd < temp.timestamp) {
            result.add(DateValue(timestamp = tempEnd, value = dv.value))
          }
        }
      }
      return result
    }

    fun align(
        from: Instant,
        values: Iterable<DateValue>,
        to: Instant,
        interval: Duration,
        fillIn: Duration = interval.multipliedBy(4)) = sequence {

      var t = from
      var last: DateValue? = null
      for (curr in values) {
        // Skip over values before the start but remember the last value
        // for averaging.
        if (curr.timestamp < from) {
          last = curr
          continue
        }
        while (t < curr.timestamp && t < to) {
          if (last == null) {
            yield(Float.NaN)
          } else {
            val d1 = Duration.between(last.timestamp, t).seconds
            val d2 = Duration.between(t, curr.timestamp).seconds
            if (d1 > fillIn.seconds && d2 > fillIn.seconds) {
              yield(Float.NaN)
            } else if (d1 > fillIn.seconds) {
              yield(curr.value.toFloat())
            } else if (d2 > fillIn.seconds) {
              yield(last.value.toFloat())
            } else {
              // We weigh the value that is close to t higher.
              val avg = (curr.value * d1 + last.value * d2) / (d1 + d2)
              yield(avg.toFloat())
            }
          }

          t += interval
        }
        last = curr
      }

      // Output the last value if we are missing values at the end.
      while (t < to) {
        if (last == null|| Duration.between(last.timestamp, t) > fillIn.multipliedBy(2)) {
          yield(Float.NaN)
        } else {
          yield(last.value.toFloat())
        }
        t += interval
      }
    }
  }

  private fun alignEvents(events: Iterable<DateValue>) = sequence {
    val halfFreq = config.freq.dividedBy(2L)
    val iter = events.iterator()

    var event = iter.nextOrNull()
    while (event != null && event.timestamp < inputFrom - halfFreq) event = iter.nextOrNull()

    for (t in inputFrom ..< inputUpTo step config.freq) {
      var carbs = 0.0
      while (event != null && event.timestamp < t + halfFreq) {
        carbs += event.value
        event = iter.nextOrNull()
      }
      yield(carbs.toFloat())
    }
  }.toList()

  suspend fun loadGlucoseReadings(): List<Float> {
    val loadFrom: Instant = inputFrom - preFetch
    val glucoseReadings = inputProvider.getGlucoseReadings(loadFrom, inputAt)
    return align(inputFrom, glucoseReadings, inputAt, config.freq).toList()
  }

  suspend fun loadHeartRates(): List<Float> {
    val loadFrom: Instant = inputFrom - preFetch
    return inputProvider.getHeartRates(loadFrom).let { hrs ->
      val futureHeartRates = FloatArray(config.predictionPeriod / config.freq) { 60F }
      listOf(
          align(inputFrom, hrs, inputAt, config.freq).toList(),
          futureHeartRates.toList()
      ).flatten()
    }
  }

  /** Number milliseconds, where [a] is true.*/
  private fun millisTrue(values: List<DateValue>, upto: Instant, a: (DateValue)->Boolean): Long {
    var activeMillis = 0L
    for ((v1, v2) in (values + listOf(DateValue(upto, 0.0))).zipWithNext()) {
      val d = Duration.between(v1.timestamp, v2.timestamp).coerceAtMost(config.freq)
      if (a(v1)) activeMillis += d.toMillis()
    }
    return activeMillis
  }

  private fun highHeartRate(dv: DateValue) = dv.value > config.hrHighThreshold

  private fun subList(values: List<DateValue>, start: Int, upto: Instant): List<DateValue> {
    var end = start
    while (end < values.size && values[end].timestamp <= upto) end++
    return values.subList(start, end)
  }

  suspend fun loadHeartRatesWithLong(): List<List<Float>> {
    val maxPeriod = config.hrLong.maxOrNull() ?: Duration.ZERO
    val hrs = inputProvider.getHeartRates(inputFrom - maxPeriod, inputUpTo)
    val result = mutableListOf(align(inputFrom, hrs, inputUpTo, config.freq).toList())
    for (th in config.hrLong) {
      val hrLong = mutableListOf<Float>()

      // highCount is the number of high heart rates in the window periodStartIdx ..< periodEndIdx
      // We first move periodEndIdx to the current ts and increase highCount by all high values
      // that we find and then do the same with periodStartIdx but decrease highCount.
      var periodStartIdx = 0
      var periodEndIdx = 0
      var highMillis = 0L
      for (ts in intervals) {
        val n = subList(hrs, periodEndIdx, ts)
        periodEndIdx += n.size
        highMillis += millisTrue(
            n, hrs.elementAtOrNull(periodEndIdx)?.timestamp ?: inputUpTo, ::highHeartRate)

        val p = subList(hrs, periodStartIdx, ts - th)
        periodStartIdx += p.size
        highMillis -= millisTrue(
            p, hrs.elementAtOrNull(periodStartIdx)?.timestamp ?: inputUpTo, ::highHeartRate)

        hrLong.add(round(highMillis.toDouble() / config.freq.toMillis()).toFloat())
      }
      result.add(hrLong)
    }
    return result
  }

  /** Gets a duration of fractions of an hour. */
  private fun toHours(d: Duration) = d.seconds / 3600.0

  /** Adjusts basal rates to insulin injection in [Config.freq] frequency. Note
   * that the pump likely injects insulin more often but higher accuracy
   * unlikely matters for the model.*/
  private fun adjustRates(basals: List<DateValue>): List<DateValue> {
    val result = mutableListOf<DateValue>()
    var ts = basals.firstOrNull()?.timestamp ?: return result
    // In case a basal rate ends in the middle of an interval, we need to add
    // up values from adjacent basal rates. We keep the time a rate reaches
    // into the next interval and the remaining insulin and add it to the
    // next output in [restTs] and [restInsulin].
    var restTs = Duration.ZERO
    var restInsulin = 0.0
    for (i in basals.indices) {
      val nextStart = basals.elementAtOrNull(i + 1)?.timestamp ?: inputUpTo
      val basalRate = basals[i].value
      while (ts + config.freq <= nextStart) {
        result.add(
            DateValue(
                ts, restInsulin + (toHours(config.freq - restTs) * basalRate)
            )
        )
        ts += config.freq
        restTs = Duration.ZERO
        restInsulin = 0.0
      }
      restTs = Duration.between(ts, nextStart)
      restInsulin = toHours(restTs) * basalRate
    }
    if (restInsulin > 0.0) {
      result.add(DateValue(ts, restInsulin))
    }
    return result
  }

  suspend fun loadBasalRates(): List<DateValue> = coroutineScope {
    val default = listOf(DateValue(inputFrom, 0.0))
    val basals = async {
      inputProvider.getBasalProfileSwitches(inputFrom, inputUpTo)
          ?.toBasal(inputFrom.atZone(config.zoneId), inputUpTo.atZone(config.zoneId))
          ?.toList()
          ?.takeUnless(List<DateValue>::isEmpty) ?: default
    }.apply { start() }
    val tempBasals = async { inputProvider.getTemporaryBasalRates(inputFrom, inputUpTo) }.apply { start() }
    adjustRates(applyTemporaryBasals(basals.await(), tempBasals.await(), inputUpTo))
  }

  @Suppress("Unused")
  private suspend fun loadBasalActions(): List<Float> {
    return loadBasalRates().let { basals ->
      insulinAction.valuesAt(basals, intervals).map(Double::toFloat)
    }
  }

  suspend fun loadLongHeartRates(): List<Float> {
    return inputProvider
        .getLongHeartRates(inputAt, config.hrHighThreshold, config.hrLong)
        .map(Int::toFloat).toList()
  }

  suspend fun loadCarbEventsAndAction(): Pair<List<Float>, List<Float>> {
    val carbs = inputProvider.getCarbs(inputFrom - LogNormAction.maxAge, inputUpTo)
    return Pair(
        alignEvents(carbs), carbAction.valuesAt(carbs, intervals).map(Double::toFloat))
  }

  @Suppress("Unused")
  private suspend fun loadBolusAction(): List<Float> {
    return inputProvider.getBoluses(inputFrom - LogNormAction.maxAge, inputUpTo).let { bs ->
      insulinAction.valuesAt(bs, intervals).map(Double::toFloat)
    }
  }

  data class InsulinEvents(
      val bolus: List<Float>,
      val basal: List<Float>,
      val action: List<Float>)

  suspend fun loadInsulinEventsAndAction(): InsulinEvents = coroutineScope {
    val (bolus, basal) = awaitAll(
        async { inputProvider.getBoluses(inputFrom - LogNormAction.maxAge, inputUpTo) },
        async { loadBasalRates() })
    val bolusAction = insulinAction.valuesAt(bolus, intervals).map(Double::toFloat)
    val basalAction = insulinAction.valuesAt(basal, intervals).map(Double::toFloat)

    InsulinEvents(
        bolus =  alignEvents(bolus),
        basal =  alignEvents(basal),
        action = bolusAction.zip(basalAction).map { (bo, ba) -> bo + ba }.toList())
  }

  private fun slope(values: List<Float>): List<Float> {
    val minutes = 2F * config.freq.toMinutes()
    return List(values.size) { i ->
      when (i) {
        0 -> if (values[0].isFinite()) 0F else Float.NaN
        values.size - 1 -> if (values.last().isFinite()) 0F else Float.NaN
        else -> (values[i + 1] - values[i - 1]) / minutes
      }
    }
  }

  private suspend fun getInputVector(): Pair<Float, FloatArray> {
    val (gl, hrl, hr, ca, ia) = coroutineScope {
      awaitAll(
          async { loadGlucoseReadings() },
          async { loadLongHeartRates() },
          async { loadHeartRates() },
          async { loadCarbEventsAndAction().second },
          async { loadInsulinEventsAndAction().action },
      )
    }
    assertWithin("glucose", gl, 20F, 500F)
    assertWithin("long heart rate", hrl, 0F, 10000F)
    assertWithin("hear rate", hr, 20F, 300F)
    assertWithin("carb action", ca, 0F, 100F)
    assertWithin("insulin action", ia, 0F, 100F)

    val localTime = OffsetDateTime.ofInstant(inputAt, config.zoneId)
    val glSlope = slope(listOf(gl, listOf(gl.last())).flatten())
    val glSlop2 = slope(glSlope)

    val input = mutableListOf<Float>()
    input.add(localTime.hour.toFloat())
    input.addAll(glSlope.dropLast(1))
    input.addAll(Array(config.predictionPeriod / config.freq) { 0F })
    input.addAll(glSlop2.dropLast(1))
    input.addAll(Array(config.predictionPeriod / config.freq) { 0F })
    input.addAll(ia)
    input.addAll(ca)
    input.addAll(hr)
    input.addAll(hrl)

    assert(input.size == config.inputSize) {
      "Input size is ${input.size} instead of ${config.inputSize}"
    }
    return gl.last() to input.toFloatArray()
  }
}
