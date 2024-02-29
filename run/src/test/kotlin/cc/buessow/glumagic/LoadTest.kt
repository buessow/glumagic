package cc.buessow.glumagic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.fileSize

class LoadTest {
  companion object {
    private lateinit var mongoDatabase: String
    private lateinit var mongoApiKey: String
    private lateinit var mongoApiUrl: String
    private lateinit var configFile: File

    private fun getResource(name: String): InputStream =
      LoadTest::class.java.classLoader.getResourceAsStream(name)!!

    @JvmStatic
    @BeforeAll
    fun loadProperties() {
      val properties = Properties()
      properties.load(getResource("local.properties"))
      mongoDatabase = properties.getProperty("mongo.database")
      mongoApiKey = properties.getProperty("mongo.api_key")
      mongoApiUrl = properties.getProperty("mongo.api_url")
    }

    @JvmStatic
    @BeforeAll
    fun loadConfig() {
      configFile = Files.createTempFile("glucose_model", ".json").toFile()
      val input = getResource("glucose_model.json")
      configFile.outputStream().use { out -> input.copyTo(out) }
    }
  }

  @Test
  fun load() {
    val outFile = Files.createTempFile("data", ".csv")
    Main.main(arrayOf(
        "api", "--from", "2023-01-01", "--upto", "2023-02-01",
        "--configFile", configFile.toString(),
        "--database", mongoDatabase,
        "--apiKey", mongoApiKey,
        "--serverUrl", mongoApiUrl,
        "--outFile", outFile.toString()))
    assertTrue(outFile.exists(), "no output")
    assertTrue(outFile.fileSize() > 0L, "output $outFile empty")
  }
}
