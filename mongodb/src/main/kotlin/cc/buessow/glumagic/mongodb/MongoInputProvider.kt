package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.InputProvider
import cc.buessow.glumagic.input.MlProfileSwitch
import cc.buessow.glumagic.input.MlProfileSwitches
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.async
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.rx3.rxSingle
import org.bson.conversions.Bson
import java.io.Closeable
import java.time.Instant

abstract class MongoInputProvider internal constructor() : Closeable, InputProvider {

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

  private inline fun <reified T: MongoDateValue> query(
      from: Instant,
      filter: Bson = empty()) = rxSingle {
        val dateFilter = gte("date", from.toEpochMilli())
    query<T>(
        if (filter == empty()) dateFilter else and(dateFilter, filter),
        Sorts.ascending("date"))
        .map { t -> t.toDateValue() }
        .toList()
  }

  override fun getGlucoseReadings(from: Instant) =
    query<MongoGlucose>(from)

  override fun getHeartRates(from: Instant) =
    query<MongoHeartRate>(from)

  override fun getCarbs(from: Instant) = rxSingle {
    query<MongoCarbs>(
        and(gte("created_at", from.toString()), ne("carbs", null)),
        Sorts.ascending("created_at"))
        .map(MongoCarbs::toDateValue)
        .toList()
  }

  override fun getBoluses(from: Instant) = rxSingle {
    query<MongoBolus>(
        and(gte("created_at", from.toString()), ne("insulin", null)),
        Sorts.ascending("created_at"))
        .map(MongoBolus::toDateValue)
        .toList()
  }

  private suspend fun queryProfileSwitches(
      filter: Bson,
      sort: Bson,
      limit: Int = 0): List<MlProfileSwitch> {
    return query<MongoProfileSwitch>(
        and(filter,
            `in`("eventType", "Profile Switch", "Note"),
            ne("profileJson", null)),
        sort,
        limit).map(MongoProfileSwitch::toMlProfileSwitch).toList()
  }

  override fun getBasalProfileSwitches(from: Instant) = rxMaybe<MlProfileSwitches> {
    val pa = async {
      val active = queryProfileSwitches(
          lte("created_at", from.toString()),
          Sorts.descending("created_at"),
          limit = 1).firstOrNull() ?: return@async null

      // If the last switch is permanent, just take it.
      val duration = active.duration ?: return@async active to active

      val permanent = queryProfileSwitches(
          and(lte("created_at", from.toString()),
              `in`("originalDuration", null, 0)),
          Sorts.descending("created_at"),
          limit = 1).firstOrNull() ?: return@async null

      // If [active] is expired, just return permanent.
      permanent to (active.takeIf { a -> a.start.plus(duration) > from } ?:permanent)
    }.apply { start() }

    val switches = queryProfileSwitches(
          gte("created_at", from.toString()),
          Sorts.ascending("created_at"))
    val (permanent, active) = pa.await() ?: return@rxMaybe null
    MlProfileSwitches(permanent, active, switches)
  }

  override fun getTemporaryBasalRates(from: Instant) = rxSingle {
    query<MongoTemporaryBasal>(
        and(gte("created_at", from.toString()),
            `in`("eventType", "Temp Basal")),
        Sorts.ascending("created_at"))
        .map(MongoTemporaryBasal::toMlTemporaryBasalRate)
        .toList()
  }
}
