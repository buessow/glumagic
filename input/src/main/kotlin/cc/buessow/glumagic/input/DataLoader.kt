package cc.buessow.glumagic.input

import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*


class DataLoader(
    private val inputProvider: InputProvider,
    time: Instant,
    private val config: Config) {

  private val inputFrom: Instant = time.truncatedTo(ChronoUnit.MINUTES)
  private val inputAt = inputFrom + config.trainingPeriod
  private val inputUpTo = inputAt + config.predictionPeriod
  private val intervals = inputFrom ..< inputUpTo step config.freq


  companion object {
    @VisibleForTesting
    internal val preFetch = Duration.ofMinutes(6)

    @VisibleForTesting
    internal val carbAction = LogNormAction(Duration.ofMinutes(45), sigma = 0.5)

    @VisibleForTesting
    internal val insulinAction = LogNormAction(Duration.ofMinutes(60), sigma = 0.5)

    fun getInputVector(input: InputProvider, time: Instant, config: Config) = runBlocking {
      DataLoader(input, time, config).getInputVector()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTrainingData(input: InputProvider, time:Instant, config: Config) = runBlocking {
      val dl = DataLoader(input, time, config)
      val deferredGlucose = async { dl.loadGlucoseReadings() }
      val deferredHeartRate = async { dl.loadHeartRates2() }
      val deferredCarbAction = async { dl.loadCarbAction() }
      val deferredInsulinAction = async { dl.loadInsulinAction() }

      val gl = deferredGlucose.await()
      val hours = dl.intervals.map { ts -> OffsetDateTime.ofInstant(ts, config.zoneId).hour }
      val glSlope1 = dl.slope(gl)
      val glSlope2 = dl.slope(glSlope1)

      TrainingInput(date = dl.intervals.toList(),
                    hour = hours,
                    glucose = gl, glucoseSlope1 = glSlope1, glucoseSlope2 = glSlope2,
                    heartRate = deferredHeartRate.await().first(),
                    hrLong1 = deferredHeartRate.await()[1],
                    hrLong2 = deferredHeartRate.await()[2],
                    carbAction = deferredCarbAction.await(),
                    insulinAction = deferredInsulinAction.await())
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
            result.add(dv.copy(timestamp = tempEnd))
          }
        }
      }
      return result
    }

    fun align(
        from: Instant,
        values: Iterable<DateValue>,
        to: Instant,
        interval: Duration) = sequence {

      val fillIn = interval.multipliedBy(4)
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

  /** Fraction of [Config::freq] time, where [a] is true. This can be a fraction, if [a] is only
   * true for less than [Config::freq], i.e. if we have more values than [Config::freq].
   */
  private fun timeTrue(values: List<DateValue>, upto: Instant, a: (DateValue)->Boolean): Double {
    var active = Duration.ZERO
    var total = Duration.ZERO
    for ((v1, v2) in (values + listOf(DateValue(upto, 0.0))).zipWithNext()) {
      val d = Duration.between(v1.timestamp, v2.timestamp).coerceAtMost(config.freq)
      total += d
      if (a(v1)) active += d
    }
    return if (total > Duration.ZERO) {
      (total / config.freq) * active.toMillis().toDouble() / total.toMillis()
    } else {
      0.0
    }
  }

  private fun highHeartRate(dv: DateValue) = dv.value > config.hrHighThreshold

  suspend fun loadHeartRates2(): List<List<Float>> {
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
      var highCount = 0.0
      for (ts in intervals) {
        val n = hrs.drop(periodEndIdx).takeWhile { dv -> dv.timestamp <= ts }
        periodEndIdx += n.size
        highCount += timeTrue(
            n, hrs.elementAtOrNull(periodEndIdx)?.timestamp ?: inputUpTo, ::highHeartRate)

        val p = hrs.drop(periodStartIdx).takeWhile { dv -> dv.timestamp < ts - th }
        periodStartIdx += p.size
        highCount -= timeTrue(
            p, hrs.elementAtOrNull(periodStartIdx)?.timestamp ?: inputUpTo, ::highHeartRate)

        hrLong.add(highCount.toFloat())
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

  suspend fun loadCarbAction(): List<Float> {
    return inputProvider.getCarbs(inputFrom - carbAction.maxAge, inputUpTo).let { cs ->
      carbAction.valuesAt(cs, intervals).map(Double::toFloat)
    }
  }

  private suspend fun loadBolusAction(): List<Float> {
    return inputProvider.getBoluses(inputFrom - insulinAction.maxAge, inputUpTo).let { cs ->
      insulinAction.valuesAt(cs, intervals).map(Double::toFloat)
    }
  }

  suspend fun loadInsulinAction(): List<Float> = coroutineScope {
    val (bolusAction, basalAction) = awaitAll(
        async { loadBolusAction() },
        async { loadBasalActions() })
    bolusAction.zip(basalAction).map { (bolus, basal) -> bolus + basal }.toList()
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
          async { loadCarbAction() },
          async { loadInsulinAction() },
      )
    }
    val localTime = OffsetDateTime.ofInstant(inputFrom, config.zoneId)
    val glSlope = slope(listOf(gl, listOf(gl.last())).flatten())
    val glSlop2 = slope(glSlope)

    val input = mutableListOf<Float>()
    input.add(localTime.hour.toFloat())
    input.addAll(hrl)
    input.addAll(glSlope.dropLast(1))
    input.addAll(glSlop2.dropLast(1))
    input.addAll(ia)
    input.addAll(ca)
    input.addAll(hr)

    assert(input.size == config.inputSize) {
      "Input size is ${input.size} instead of ${config.inputSize}"
    }
    return gl.last() to input.toFloatArray()
  }
}
