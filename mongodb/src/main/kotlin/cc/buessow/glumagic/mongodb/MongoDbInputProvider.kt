package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DataProvider
import cc.buessow.glumagic.input.MlProfileSwitch
import cc.buessow.glumagic.input.MlProfileSwitches
import com.mongodb.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.rx3.rxSingle
import org.bson.conversions.Bson
import java.io.Closeable
import java.time.Instant

class MongoDbInputProvider(
    connectionString: String,
    database: String,
    credentials: MongoCredential? = null): Closeable, DataProvider {

  constructor(
      connectionString: String,
      database: String,
      userName: String,
      password: String,
      credentialsDatabase: String = "admin"):
    this(
        connectionString,
        database,
        MongoCredential.createCredential(userName, credentialsDatabase, password.toCharArray()))

  private val client: MongoClient
  private val db: MongoDatabase

  init {
    val settings = MongoClientSettings.builder().apply {
        readPreference(ReadPreference.secondary())
        applicationName("GluMagic")
        if (credentials != null) credential(credentials)
        applyConnectionString(ConnectionString(connectionString))
    }.build()
    client = MongoClient.create(settings)
    db = client.getDatabase(database)
  }

  override fun close() {
    client.close()
  }

  private inline fun <reified T: MongoDateValue> query(
      from: Instant,
      name: String,
      filter: Bson = empty()) = rxSingle {
    val coll = db.getCollection<T>(name)
    coll.find(and(gte("date", from.toEpochMilli()), filter))
        .sort(Sorts.ascending("date"))
        .map { t -> t.toDateValue() }
        .toList()
  }

  override fun getGlucoseReadings(from: Instant) = query<MongoGlucose>(from, "entries")

  override fun getHeartRates(from: Instant) = query<MongoHeartRate>(from, "heartrate")

  override fun getCarbs(from: Instant) = rxSingle {
    db.getCollection<MongoCarbs>("treatments")
        .find(and(gte("created_at", from.toString()), ne("carbs", null)))
        .sort(Sorts.ascending("created_at"))
        .map(MongoCarbs::toDateValue)
        .toList()
  }

  override fun getBoluses(from: Instant) = rxSingle {
    db.getCollection<MongoBolus>("treatments")
        .find(and(gte("created_at", from.toString()), ne("insulin", null)))
        .sort(Sorts.ascending("created_at"))
        .map(MongoBolus::toDateValue)
        .toList()
  }

  private suspend fun queryProfileSwitches(
      filter: Bson,
      sort: Bson,
      limit: Int = 0): List<MlProfileSwitch> {
    val coll = db.getCollection<MongoProfileSwitch>("treatments")
    return coll.find(
        and(filter,
            `in`("eventType", "Profile Switch", "Note"),
            ne("profileJson", null)))
        .sort(sort)
        .limit(limit)
        .map { x -> println("toX $x"); x.toMlProfileSwitch() }
        .toList()
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
    val coll = db.getCollection<MongoTemporaryBasal>("treatments")
    coll.find(
        and(gte("created_at", from.toString()),
            `in`("eventType", "Temp Basal")))
        .sort(Sorts.ascending("created_at"))
        .map(MongoTemporaryBasal::toMlTemporaryBasalRate)
        .toList()
  }
}