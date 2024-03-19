package cc.buessow.glumagic.input

import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.round

class DataLoader(
    private val inputProvider: InputProvider,
    trainingFrom: Instant,
    private val config: Config) {

  private val trainingFrom: Instant = trainingFrom.truncatedTo(ChronoUnit.MINUTES)
  private val trainingUpto = this.trainingFrom + config.trainingPeriod
  private val predictUpto = trainingUpto + config.predictionPeriod
  private val intervals = this.trainingFrom..< predictUpto step config.freq
  private val carbAction = config.carbAction
  private val insulinAction = config.insulinAction
  private val smoothingFilter = SmoothingFilter(
      config.smoothingFilter ?: "none", config.smoothingParams ?: emptyMap())
  private val insulinEvents = mutableSetOf<Instant>()

  companion object {

    class PatternMatcher<T>(val value: String) {
      var result: T? = null
      fun match(pattern: String, b: MatchResult.() -> T) {
        result = result ?: Regex(pattern).matchEntire(value)?.let { b(it) }
      }

      @Suppress("UNUSED")
      fun default(b: ()-> T) {
        if (result == null) b()
      }
    }
    fun <T> whenMatch(value: String, b: PatternMatcher<T>.()->Unit): T? {
      val pm = PatternMatcher<T>(value)
      b(pm)
      return pm.result
    }

    @VisibleForTesting
    internal val preFetch = Duration.ofMinutes(6)

    private fun assertZero(value: Int, text: String) {
      assert(value == 0) { "expect no $text, got $value" }
    }
    private fun assertWithin(name: String, values: List<Double>, min: Double, max: Double) {
      assertZero(values.count { !it.isNaN() && it < min}, "$name < $min")
      assertZero(values.count { !it.isNaN() && it > max}, "$name > $max")
    }

    fun getInputVector(input: InputProvider, at: Instant, config: Config) = runBlocking {
      DataLoader(input, at - config.trainingPeriod, config).getInputVector()
    }

    fun getTrainingData(input: InputProvider, time:Instant, config: Config) = runBlocking {
      val dl = DataLoader(input, time, config.copy(smoothingFilter = "none"))
      val deferredGlucose = async { dl.loadGlucoseReadings() }
      val deferredHeartRate = async { dl.loadHeartRatesWithLong() }
      val deferredCarbAction = async { dl.loadCarbEventsAndAction() }
      val deferredInsulin = async { dl.loadInsulinEventsAndAction() }

      assertWithin("glucose", deferredGlucose.await(), 20.0, 500.0)
      assertWithin("hear rate", deferredHeartRate.await()[0], 20.0, 300.0)
      assertWithin("long heart rate 1", deferredHeartRate.await()[1], 0.0, 10000.0)
      assertWithin("long heart rate 2", deferredHeartRate.await()[2], 0.0, 10000.0)

      val gl = deferredGlucose.await()
      val hours = dl.intervals.map { ts -> OffsetDateTime.ofInstant(ts, config.zoneId).hour }
      val glSlope1 = dl.slope(gl)
      val glSlope2 = dl.slope(glSlope1)

      val (carbs, carbAction) = deferredCarbAction.await()
      val insulin = deferredInsulin.await()

      assertWithin("carbs", carbs, 0.0, 200.0)
      assertWithin("carb action", carbAction, 0.0, 300.0)
      assertWithin("insulin bolus", insulin.bolus, 0.0, 100.0)
      assertWithin("insulin basal", insulin.basal, 0.0, 100.0)
      assertWithin("insulin action", insulin.action, 0.0, 100.0)

      val totalInsulin = insulin.bolus.sum() + insulin.basal.sum()
      val totalIAction = insulin.action.sum()
      assert (totalIAction in 0.99 * totalInsulin..1.01 * totalInsulin) {
        "total insulin $totalInsulin, total action $totalIAction"
      }

      val missingPeriods = dl.missingPeriods(Duration.ofDays(1))
      println("insulin events ${dl.insulinEvents.toList().sorted().take(1000).joinToString()}")
      println("missing periods: " + missingPeriods.joinToString{ (ts, d) -> "$ts..${ts+d}"})

      var ti = TrainingInput(date = dl.intervals.toList(),
                    hour = hours,
                    glucose = gl.drop(2),
                    glucoseSlope1 = glSlope1.drop(1), glucoseSlope2 = glSlope2,
                    heartRate = deferredHeartRate.await().first(),
                    hrLong1 = deferredHeartRate.await()[1],
                    hrLong2 = deferredHeartRate.await()[2],
                    carbs = carbs,
                    carbAction = carbAction,
                    bolus = insulin.bolus,
                    basal = insulin.basal,
                    insulinAction = insulin.action)
      for ((ts, d) in missingPeriods) ti = ti.removePeriod(ts, d)
      ti
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
                  (temp.basal ?: dv.value) * temp.rate))

          // Temporary basal until the end of this basal: we are done.
          if (temp.end >= nextStart) break

          // We need to revert to normal basal until end of basal or next
          // temporary basal unless there is no gap between this and next
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
        if (last != null && last.timestamp > curr.timestamp) {
          throw IllegalArgumentException("Out of order values $last > $curr")
        }
        // Skip over values before the start but remember the last value
        // for averaging.
        if (curr.timestamp < from) {
          last = curr
          continue
        }
        while (t < curr.timestamp && t < to) {
          if (last == null) {
            yield(Double.NaN)
          } else {
            val d1 = Duration.between(last.timestamp, t).seconds
            val d2 = Duration.between(t, curr.timestamp).seconds
            if (d1 > fillIn.seconds && d2 > fillIn.seconds) {
              yield(Double.NaN)
            } else if (d1 > fillIn.seconds) {
              yield(curr.value)
            } else if (d2 > fillIn.seconds) {
              yield(last.value)
            } else {
              // We weigh the value that is close to t higher.
              val avg = (curr.value * d1 + last.value * d2) / (d1 + d2)
              yield(avg)
            }
          }
          t += interval
        }
        last = curr
      }

      // Output the last value if we are missing values at the end.
      while (t < to) {
        if (last == null|| Duration.between(last.timestamp, t) > fillIn.multipliedBy(2)) {
          yield(Double.NaN)
        } else {
          yield(last.value)
        }
        t += interval
      }
    }
  }

  fun missingPeriods(duration: Duration): MutableList<Pair<Instant, Duration>> {
    val periods = mutableListOf<Pair<Instant, Duration>>()
    var ts0 = trainingFrom
    for (ts1 in insulinEvents.toList().sorted()) {
      if (ts1 < ts0) continue
      Duration.between(ts0, ts1).also { if (it > duration) periods.add(ts0 to it) }
      ts0 = ts1
    }
    Duration.between(ts0, predictUpto).also { if (it > duration) periods.add(ts0 to it) }
    return periods
  }

  private fun alignEvents(events: Iterable<DateValue>) = sequence {
    val halfFreq = config.freq.dividedBy(2L)
    val iter = events.iterator()

    var event = iter.nextOrNull()
    while (event != null && event.timestamp < trainingFrom - halfFreq) event = iter.nextOrNull()

    for (t in trainingFrom ..< predictUpto step config.freq) {
      var value = 0.0
      while (event != null && event.timestamp < t + halfFreq) {
        value += event.value
        event = iter.nextOrNull()
      }
      yield(value)
    }
  }.toList()

  suspend fun loadGlucoseReadings(): List<Double> {
    val loadFrom: Instant = trainingFrom - config.freq - config.freq
    val glucoseReadings = inputProvider.getGlucoseReadings(loadFrom - preFetch, trainingUpto)
    val aligned = align(loadFrom, glucoseReadings, trainingUpto, config.freq).toList()
    val smooth = smoothingFilter.filter(aligned)
    return smooth
  }

  suspend fun loadHeartRates(): List<Double> {
    val loadFrom: Instant = trainingFrom - preFetch
    return inputProvider.getHeartRates(loadFrom, predictUpto).let { hrs ->
      align(trainingFrom, hrs, predictUpto, config.freq).map { hr -> if (hr.isNaN()) 60.0 else hr }
    }.toList()
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

  suspend fun loadHeartRatesWithLong(): List<List<Double>> {
    val maxPeriod = config.hrLong.maxOrNull() ?: Duration.ZERO
    val hrs = inputProvider.getHeartRates(trainingFrom - maxPeriod, predictUpto)
    val result = mutableListOf(align(trainingFrom, hrs, predictUpto, config.freq).toList())
    for (th in config.hrLong) {
      val hrLong = mutableListOf<Double>()

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
            n, hrs.elementAtOrNull(periodEndIdx)?.timestamp ?: predictUpto, ::highHeartRate)

        val p = subList(hrs, periodStartIdx, ts - th)
        periodStartIdx += p.size
        highMillis -= millisTrue(
            p, hrs.elementAtOrNull(periodStartIdx)?.timestamp ?: predictUpto, ::highHeartRate)

        hrLong.add(round(highMillis.toDouble() / config.freq.toMillis()))
      }
      result.add(hrLong)
    }
    return result
  }

  /** Gets a duration of fractions of an hour. */
  private fun toHours(d: Duration) = d.seconds / 3600.0


  suspend fun loadLongHeartRates(): List<Double> {
    return inputProvider
        .getLongHeartRates(trainingUpto, config.hrHighThreshold, config.hrLong)
        .map(Int::toDouble).toList()
  }

  suspend fun loadCarbEventsAndAction(): Pair<List<Double>, List<Double>> {
    val carbs = inputProvider.getCarbs(trainingFrom - carbAction.totalDuration, predictUpto)
    return Pair(
        alignEvents(carbs), carbAction.valuesAt(carbs, trainingFrom - config.freq, intervals))
  }

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
      val nextStart = basals.elementAtOrNull(i + 1)?.timestamp ?: predictUpto
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
    val default = listOf(DateValue(trainingFrom, 0.0))
    val from = trainingFrom-insulinAction.totalDuration
    val (basals, tempBasals) = await2(
        async {
          val psws = inputProvider.getBasalProfileSwitches(from, predictUpto) ?: return@async default
          synchronized (insulinEvents) {
            insulinEvents.addAll(psws.switches.map(MlProfileSwitch::start))
          }
          psws.toBasal(from, predictUpto, config.zoneId)
              .toList()
              .sorted()
              .takeUnless(List<DateValue>::isEmpty) ?: default },
        async { inputProvider.getTemporaryBasalRates(from, predictUpto) })
    synchronized (insulinEvents) {
      insulinEvents.addAll(tempBasals.map(MlTemporaryBasalRate::timestamp))
    }
    return@coroutineScope adjustRates(applyTemporaryBasals(basals, tempBasals, predictUpto))
  }

  data class InsulinEvents(
      val bolus: List<Double>,
      val basal: List<Double>,
      val action: List<Double>)

  suspend fun loadInsulinEventsAndAction(): InsulinEvents = coroutineScope {
    val (bolus, basal) = awaitAll(
        async { inputProvider.getBoluses(trainingFrom - insulinAction.totalDuration, predictUpto) },
        async { loadBasalRates() })
    synchronized (insulinEvents) {
      insulinEvents.addAll(bolus.map(DateValue::timestamp))
    }
    val bolusAction = insulinAction.valuesAt(bolus, trainingFrom - config.freq, intervals)
    val basalAction = insulinAction.valuesAt(basal, trainingFrom - config.freq, intervals)

    InsulinEvents(
        bolus = alignEvents(bolus),
        basal = alignEvents(basal), //.map(DateValue::value),
        action = bolusAction.zip(basalAction).map { (bo, ba) -> bo + ba }.toList())
  }

  private fun slope(values: List<Double>): List<Double> {
    val minutes = config.freq.toMinutes()
    val slopes = List(values.size - 1) { i -> (values[i + 1] - values[i]) / minutes }
    return slopes.map {s -> if (s.isNaN()) 0.0 else s }
  }

  private suspend fun getInputVector(): Pair<Double, FloatArray> {
    val (gl, hrl, hr, carbs, insulin) = coroutineScope {
      await5(
          async { loadGlucoseReadings() },
          async { loadLongHeartRates() },
          async { loadHeartRates() },
          async { loadCarbEventsAndAction() },
          async { loadInsulinEventsAndAction() })
    }
    assertWithin("glucose", gl, 20.0, 500.0)
    assertWithin("long heart rate", hrl, 0.0, 10000.0)
    assertWithin("hear rate", hr, 20.0, 300.0)
    assertWithin("carb action", carbs.second, 0.0, 100.0)
    assertWithin("insulin action", insulin.action, 0.0, 100.0)

    val glSlope = slope(gl)
    val glSlop2 = slope(glSlope)

    val localTime = OffsetDateTime.ofInstant(trainingFrom, config.zoneId)
    val input = mutableListOf<Double>()
    val getIndex = { minute: String -> minute.toInt() / config.freq.toMinutes().toInt() }
    for (n in config.xValues) {
      input.add(whenMatch(n) {
          match("""\d+(.\d*)?""") { n.toDouble() }
          match("""hour_(\d+)""") {
              localTime.plusMinutes(groupValues[1].toLong()).hour.toDouble() }
          match("""gl_(\d+)""") { gl[getIndex(groupValues[1])+2] }
          match("""gls_(\d+)""") { glSlope[getIndex(groupValues[1]) + 1] }
          match("""gls2_(\d+)""") { glSlop2[getIndex(groupValues[1])] }
          match("""ins_(\d+)""") {
              getIndex(groupValues[1]).let { i -> insulin.basal[i] + insulin.bolus[i] }}
          match("""ia_(\d+)""") { insulin.action[getIndex(groupValues[1])] }
          match("""carbs_(\d+)""") { carbs.first[getIndex(groupValues[1])] }
          match("""ca_(\d+)""") { carbs.second[getIndex(groupValues[1])] }
          match("""hr_(\d+)""") { hr[getIndex(groupValues[1])] }
          match("""hr_long_(\d+)""") { hrl[0] }
          match("""hr_lon2_(\d+)""") { hrl[1] }
        }
        ?: throw IllegalArgumentException("unsupported column '$n'"))
    }

    return gl.last() to input.map(Double::toFloat).toFloatArray()
  }
}
