package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import cc.buessow.glumagic.input.MlProfileSwitch
import cc.buessow.glumagic.input.MlProfileSwitches
import cc.buessow.glumagic.input.MlTemporaryBasalRate
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import de.bwaldvogel.mongo.MongoServer
import de.bwaldvogel.mongo.backend.memory.MemoryBackend
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MongoDbInputProviderTest {

  private lateinit var server: MongoServer
  private lateinit var client: MongoClient
  private lateinit var db: MongoDatabase
  private lateinit var ip: MongoDbInputProvider
  private val from = Instant.parse("2020-01-01T01:00:00Z")

  @BeforeEach
  fun setUp() {
    server = MongoServer(MemoryBackend())
    val connectionString = server.bindAndGetConnectionString()
    println("connectionString '$connectionString'")
    client = MongoClient.create(connectionString)
    val databaseName = "test"
    db = client.getDatabase(databaseName)
    ip = MongoDbInputProvider(connectionString, databaseName)
  }

  @AfterEach
  fun tearDown() {
    ip.close()
    client.close()
    server.shutdown()
  }

  @Test
  fun getGlucoseReadings_empty() {
    assertEquals(emptyList<DateValue>(), ip.getGlucoseReadings(from).blockingGet())
  }

  private suspend fun createGlucose(timestamp: Instant, value: Int): DateValue {
    db.getCollection<MongoGlucose>("entries").insertOne(
        MongoGlucose(timestamp.toEpochMilli(), value))
    return DateValue(timestamp, value.toDouble())
  }

  @Test
  fun getGlucoseReadings() {
    val e  = runBlocking {
      createGlucose(Instant.parse("2019-01-01T01:00:00Z"), 100)
      listOf(
          createGlucose(Instant.parse("2020-01-01T01:00:00Z"), 140),
          createGlucose(Instant.parse("2020-01-01T01:10:00Z"), 150),
          createGlucose(Instant.parse("2020-01-01T01:15:00Z"), 120))
    }
    assertEquals(e, ip.getGlucoseReadings(from).blockingGet())
  }

  private suspend fun createHeartRate(timestamp: Instant, value: Int): DateValue {
    db.getCollection<MongoHeartRate>("heartrate").insertOne(
        MongoHeartRate(timestamp.toEpochMilli(), value))
    return DateValue(timestamp, value.toDouble())
  }

  @Test
  fun getHeartRates() {
    val e = runBlocking {
      createHeartRate(Instant.parse("2019-01-01T01:00:00Z"), 100)
      listOf(
          createHeartRate(Instant.parse("2020-01-01T01:00:00Z"), 140),
          createHeartRate(Instant.parse("2020-01-01T01:10:00Z"), 150),
          createHeartRate(Instant.parse("2020-01-01T01:15:00Z"), 120))
    }
    assertEquals(e, ip.getHeartRates(from).blockingGet())
  }

  private suspend fun createCarbs(timestamp: Instant, value: Int): DateValue {
    db.getCollection<MongoCarbs>("treatments").insertOne(
        MongoCarbs(timestamp.toString(), value))
    return DateValue(timestamp, value.toDouble())
  }

  @Test
  fun getCarbs() {
    val e = runBlocking {
      createCarbs(Instant.parse("2019-01-01T01:00:00Z"), 100)
      listOf(
          createCarbs(Instant.parse("2020-01-01T01:00:00Z"), 140),
          createCarbs(Instant.parse("2020-01-01T01:10:00Z"), 150),
          createCarbs(Instant.parse("2020-01-01T01:15:00Z"), 120))
    }
    assertEquals(e, ip.getCarbs(from).blockingGet())
  }

  private suspend fun createBolus(timestamp: Instant, value: Double): DateValue {
    db.getCollection<MongoBolus>("treatments").insertOne(
        MongoBolus(timestamp.toString(), value))
    return DateValue(timestamp, value)
  }

  @Test
  fun getBoluses() {
    val e = runBlocking {
      createBolus(Instant.parse("2019-01-01T01:00:00Z"), 1.0)
      listOf(
          createBolus(Instant.parse("2020-01-01T01:00:00Z"), 1.4),
          createBolus(Instant.parse("2020-01-01T01:10:00Z"), 1.5),
          createBolus(Instant.parse("2020-01-01T01:15:00Z"), 1.2))
    }
    assertEquals(e, ip.getBoluses(from).blockingGet())
  }

  private suspend fun createProfileSwitch(timestamp: Instant, duration: Duration?, rate: Double?): MlProfileSwitch {
    db.getCollection<MongoProfileSwitch>("treatments").insertOne(
        MongoProfileSwitch(
            timestamp.toString(),
            "test",
            duration?.toMillis(),
            rate?.times(100.0)?.toInt(),
            "{}"))
    return MlProfileSwitch(
        name = "test",
        start = timestamp,
        basalRates = emptyList(),
        duration = duration,
        rate = rate ?: 1.0)
  }

  @Test
  fun getProfileSwitches_empty() {
    runBlocking {
      createProfileSwitch(Instant.parse("2020-01-01T02:00:00Z"), null, 1.1)
    }
    assertNull(ip.getBasalProfileSwitches(from).blockingGet())
  }

  @Test
  fun getProfileSwitches_onlyTemp() {
    runBlocking {
      createProfileSwitch(
          Instant.parse("2019-12-31T01:00:00Z"), Duration.ofDays(1), 1.1)
      createProfileSwitch(Instant.parse("2020-01-01T02:00:00Z"), null, 1.2)
    }
    assertNull(ip.getBasalProfileSwitches(from).blockingGet())
  }


  @Test
  fun getProfileSwitches_one() {
    val ps1: MlProfileSwitch
    runBlocking {
      ps1 = createProfileSwitch(Instant.parse("2019-12-01T01:00:00Z"), null, 1.1)
    }
    assertEquals(
        MlProfileSwitches(ps1, ps1, emptyList()),
        ip.getBasalProfileSwitches(from).blockingGet())
  }

  @Test
  fun getProfileSwitches_pastTemp() {
    val ps1: MlProfileSwitch
    runBlocking {
      ps1 = createProfileSwitch(Instant.parse("2019-12-01T01:00:00Z"), null, 1.1)
      createProfileSwitch(
          Instant.parse("2019-12-31T01:10:00Z"), Duration.ofHours(1), 1.1)
    }
    assertEquals(
        MlProfileSwitches(ps1, ps1, emptyList()),
        ip.getBasalProfileSwitches(from).blockingGet())
  }
  @Test
  fun getProfileSwitches_temp() {
    val ps1: MlProfileSwitch
    val ps2: MlProfileSwitch
    val ps3: MlProfileSwitch
    runBlocking {
      createProfileSwitch(
        Instant.parse("2019-12-01T00:00:00Z"), null, 1.0)
      ps1 = createProfileSwitch(
          Instant.parse("2019-12-01T01:00:00Z"), null, 1.1)
      ps2 = createProfileSwitch(
          Instant.parse("2019-12-31T01:10:00Z"), Duration.ofDays(10), 1.2)
      ps3 = createProfileSwitch(
          Instant.parse("2020-01-01T02:00:00Z"), null, 1.3)
    }
    assertEquals(
        MlProfileSwitches(ps1, ps2, listOf(ps3)),
        ip.getBasalProfileSwitches(from).blockingGet())
  }

  private suspend fun createTempBasal(timestamp: Instant, duration: Duration?, rate: Double?): MlTemporaryBasalRate {
    db.getCollection<MongoTemporaryBasal>("treatments").insertOne(
        MongoTemporaryBasal(
            timestamp.toString(),
            duration?.toMinutes(),
            null,
            rate?.times(100)?.toInt()))
    return MlTemporaryBasalRate(timestamp, duration ?: Duration.ZERO, rate ?: 1.0)
  }

  @Test
  fun getTempBasal() {
    val tb = runBlocking {
      createTempBasal(
          Instant.parse("2020-01-01T01:10:00Z"),
          Duration.ofMinutes(20),
          1.1)
    }
    assertEquals(listOf(tb), ip.getTemporaryBasalRates(from).blockingGet())
  }
}