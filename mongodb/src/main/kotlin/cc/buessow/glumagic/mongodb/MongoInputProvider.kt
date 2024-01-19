package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bson.conversions.Bson
import java.io.Closeable
import java.time.Instant

abstract class MongoInputProvider internal constructor() : Closeable, InputProvider {

  companion object {
    private fun and(vararg filters: Bson?): Bson =
      filters.filterNotNull().let {
        when (it.size) {
          0 -> empty()
          1 -> it.first()
          else -> and(it)
        }
      }
  }

  override fun close() {
  }

  internal abstract suspend fun <T: Any> query(
      clazz: Class<T>,
      filter: Bson,
      sort: Bson,
      limit: Int = 0): List<T>

  private suspend inline fun <reified T: Any> query(
      filter: Bson,
      sort: Bson,
      limit: Int = 0) = query(T::class.java, filter, sort, limit)

  /** Query with date as long (millis) columns. */
  private suspend inline fun <reified T: MongoDateValue> queryMillis(
      from: Instant,
      upto: Instant?,
      filter: Bson? = null,
      dateColumn: String = "date"): List<DateValue> {
    return query<T>(from.toEpochMilli(), upto?.toEpochMilli(), filter, dateColumn)
  }

  /** Query with date as string column. */
  private suspend inline fun <reified T: MongoDateValue> queryString(
      from: Instant,
      upto: Instant?,
      filter: Bson? = null,
      dateColumn: String = "date"): List<DateValue> {
    return query<T>(from.toString(), upto?.toString(), filter, dateColumn)
  }

  private suspend inline fun <reified T: MongoDateValue> query(
        from: Any,
        upto: Any?,
        filter: Bson? = null,
        dateColumn: String = "date"): List<DateValue> {
      return query<T>(
          and(gte(dateColumn, from),
              upto?.let { lt(dateColumn, it) },
              filter),
          Sorts.ascending(dateColumn))
          .map { t -> t.toDateValue() }
          .toList()
  }

  override suspend fun getGlucoseReadings(from: Instant, upto: Instant?): List<DateValue> {
    return queryMillis<MongoGlucose>(from, upto)
  }

  override suspend fun getHeartRates(from: Instant, upto: Instant?): List<DateValue> = coroutineScope {
    val eventFilter = eq("eventType", "HeartRate")
    val hr1 = async { queryMillis<MongoHeartRate>(from, upto) }
    val hr2 = async { queryString<MongoHeartRateActivity>(from, upto, eventFilter, "samplingStart") }
    val hr3 = async { queryMillis<MongoHeartRateTreatment>(from, upto, eventFilter, "samplingStart") }

    awaitAll(hr1, hr2, hr3).flatten().sortedBy { it.timestamp }
  }

  override suspend fun getCarbs(from: Instant, upto: Instant?): List<DateValue> {
    return queryString<MongoCarbs>(from, upto, ne("carbs", null), "created_at")
  }

  override suspend fun getBoluses(from: Instant, upto: Instant?): List<DateValue> {
    return queryString<MongoBolus>(from, upto, ne("insulin", null), "created_at")
  }

  private suspend fun queryProfileSwitches(
      after: Instant?,
      until: Instant?,
      sort: Bson,
      onlyPermanent: Boolean = false,
      limit: Int = 0): List<MlProfileSwitch> {
    return query<MongoProfileSwitch>(
        and(after?.let { gt("created_at", it.toString()) },
            until?.let { lte("created_at", it.toString()) },
            if (onlyPermanent) `in`("originalDuration", null, 0) else null,
            `in`("eventType", "Profile Switch", "Note"),
            ne("profileJson", null)),
        sort,
        limit).map(MongoProfileSwitch::toMlProfileSwitch).toList()
  }

  override suspend fun getBasalProfileSwitches(from: Instant, upto: Instant?): MlProfileSwitches? = coroutineScope {
    val pa = async {
      val active = queryProfileSwitches(
          after = null,
          until = from,
          sort = Sorts.descending("created_at"),
          limit = 1).firstOrNull() ?: return@async null

      // If the last switch is permanent, just take it.
      val duration = active.duration ?: return@async active to active

      val permanent = queryProfileSwitches(
          after = null,
          until = from,
          sort = Sorts.descending("created_at"),
          onlyPermanent = true,
          limit = 1).firstOrNull() ?: return@async null

      // If [active] is expired, just return permanent.
      permanent to (active.takeIf { a -> a.start.plus(duration) > from } ?:permanent)
    }.apply { start() }

    val switches = async { queryProfileSwitches(
        after = from,
        until = upto,
        sort = Sorts.ascending("created_at")) }.apply { start() }
    val (permanent, active) = pa.await() ?: return@coroutineScope null
    MlProfileSwitches(permanent, active, switches.await())
  }

  override suspend fun getTemporaryBasalRates(from: Instant, upto: Instant?): List<MlTemporaryBasalRate> {
    return query<MongoTemporaryBasal>(
        and(gte("created_at", from.toString()),
            upto?.let { lt("created_at", it.toString()) },
            `in`("eventType", "Temp Basal")),
        Sorts.ascending("created_at"))
        .map(MongoTemporaryBasal::toMlTemporaryBasalRate)
        .toList()
  }
}
