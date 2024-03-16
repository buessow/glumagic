package cc.buessow.glumagic.mongodb

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ReadPreference
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import java.util.concurrent.TimeUnit

class MongoDbInputProvider(
    connectionString: String,
    database: String,
    credentials: MongoCredential? = null): MongoInputProvider() {

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
      credentials?.let { credential(it) }
      applyConnectionString(ConnectionString(connectionString))
    }.build()
    client = MongoClient.create(settings)
    db = client.getDatabase(database)
  }

  override fun close() {
    client.close()
    super.close()
  }

  private suspend fun <T: Any> queryOnce(
      clazz: Class<T>,
      filter: Bson,
      sort: Bson,
      skip: Int,
      limit: Int): List<T> {
    val collectionName = clazz.getAnnotation(MongoCollection::class.java).name
    return db.getCollection(collectionName, clazz)
        .find(filter)
        .sort(sort)
        .skip(skip)
        .limit(limit)
        .maxTime(2, TimeUnit.HOURS)
        .batchSize(1000)
        .toList()
  }

  override suspend fun <T: Any> query(
      clazz: Class<T>,
      filter: Bson,
      sort: Bson,
      limit: Int): List<T> {
    val batchSize = 10_000
    val theLimit = if (limit == 0) Int.MAX_VALUE else limit
    val result = mutableListOf<T>()

    while (result.size < theLimit) {
      val batch = queryOnce(
          clazz, filter, sort, result.size,
          batchSize.coerceAtMost(theLimit - result.size))
      result.addAll(batch)
      if (batch.size < batchSize) break
    }
    return result
  }
}
