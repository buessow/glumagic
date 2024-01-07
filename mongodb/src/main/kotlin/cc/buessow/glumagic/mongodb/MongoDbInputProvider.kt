package cc.buessow.glumagic.mongodb

import com.mongodb.*
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson

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
        if (credentials != null) credential(credentials)
        applyConnectionString(ConnectionString(connectionString))
    }.build()
    client = MongoClient.create(settings)
    db = client.getDatabase(database)
  }

  override fun close() {
    client.close()
    super.close()
  }

  override suspend fun <T: Any> query(
      clazz: Class<T>,
      filter: Bson,
      sort: Bson,
      limit: Int): List<T> {
    val collectionName = clazz.getAnnotation(MongoCollection::class.java).name
    return db.getCollection<T>(collectionName, clazz).find(filter).sort(sort).limit(limit).toList()
  }
}
