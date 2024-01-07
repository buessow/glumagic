package cc.buessow.glumagic.mongodb

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.conversions.Bson
import java.io.IOException
import java.util.logging.Logger
import kotlin.coroutines.suspendCoroutine

class MongoApiInputProvider(
    private val database: String,
    private val apiUrl: String,
    private val apiKey: String): MongoInputProvider() {
  private val log = Logger.getLogger(javaClass.name)
  private val client = OkHttpClient()
  private val gson = GsonBuilder().create()

  private data class Response<T>(val documents: List<T>)

  override fun close() {
    client.connectionPool.evictAll()
    client.dispatcher.executorService.shutdown()
    super.close()
  }

  override suspend fun <T : Any> query(
      clazz: Class<T>,
      filter: Bson,
      sort: Bson,
      limit: Int): List<T> {
    val collectionName = clazz.getAnnotation(MongoCollection::class.java).name
    val body = BsonDocument().apply {
      put("collection", BsonString(collectionName))
      put("database", BsonString(database))
      put("dataSource", BsonString("Cluster0"))
      put("filter", filter.toBsonDocument())
      put("sort", sort.toBsonDocument())
      if (limit > 0) put("limit", BsonInt32(limit))
    }
    val req = Request.Builder()
        .url("$apiUrl/endpoint/data/v1/action/find")
        .header("Content-Type", "application/json")
        .header("Access-Control-Request-Headers", "*")
        .header("api-key", apiKey)
        .post(body.toJson().toRequestBody("application/json".toMediaType()))
        .build()
    log.fine {"mongo request: ${body.toJson()}" }

    return suspendCoroutine { cont ->
      client.newCall(req).enqueue(object: Callback {

        override fun onFailure(call: Call, e: IOException) {
          cont.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
          val respJson = response.body?.string()
          if (respJson == null || !response.isSuccessful) {
            val message = "request failed: ${response.code} '$respJson'"
            log.severe(message)
            cont.resumeWith(Result.failure(IOException(message)))
            return
          }
          val tt = TypeToken.getParameterized(Response::class.java, clazz)
          try {
            log.finer {"mongo response: $respJson" }
            val respObj = gson.fromJson<Response<T>>(respJson, tt.type)
            cont.resumeWith(Result.success(respObj.documents))
          } catch (e: JsonParseException) {
            cont.resumeWith(Result.failure(e))
          }
          response.close()
        }
      })
    }
  }
}
