package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MongoApiInputProviderTest {

  private lateinit var webServer: MockWebServer
  private lateinit var url: String
  private lateinit var inputProvider: MongoApiInputProvider
  private val date = Instant.parse("2023-01-01T10:11:12Z")


  @BeforeEach
  fun setup() {
    webServer = MockWebServer().apply { start() }
    url = webServer.url("").toString()
    inputProvider = MongoApiInputProvider("test", url, "secret")
  }

  @AfterEach
  fun shutdown() {
    webServer.shutdown()
  }

  @Test
  fun getGlucoseReadings() = runBlocking {
    webServer.enqueue(
        MockResponse().apply {
          setResponseCode(200)
          setBody("""{"documents": [{"date": ${date.toEpochMilli()}, "sgv": 101}]}""")
        })

    assertArrayEquals(
        arrayOf(DateValue(date, 101.0)),
        inputProvider.getGlucoseReadings(date).toTypedArray())

    assertEquals(1, webServer.requestCount)
    val req = webServer.takeRequest()
    assertEquals("secret", req.getHeader("api-key"))
    val reqBody = JsonParser.parseString(req.body.readString(Charsets.UTF_8)).asJsonObject
    assertEquals("entries", reqBody.get("collection").asString)
    assertEquals("test", reqBody.get("database").asString)
    assertEquals(
        """{"date":{"${'$'}gte":${date.toEpochMilli()}}}""",
        reqBody.get("filter").toString())
    assertEquals("""{"date":1}""", reqBody.get("sort").toString())
  }

  @Test
  fun getGlucoseReadings_http400() = runBlocking {
    webServer.enqueue(MockResponse().setResponseCode(400).setBody("error"))
    try {
      inputProvider.getGlucoseReadings(date)
      fail()
    } catch(e: Exception) {
      assertEquals("request failed: 400 'error'", e.message)
    }
  }

  @Test
  fun getGlucoseReadings_badResponse() = runBlocking {
    webServer.enqueue(MockResponse().setResponseCode(200).setBody("error"))
    try {
      inputProvider.getGlucoseReadings(date)
      fail()
    } catch(e: Exception) {
      assertEquals(
          "Expected BEGIN_OBJECT but was STRING at line 1 column 1 path \$",
           e.cause!!.message)
    }
  }
}
