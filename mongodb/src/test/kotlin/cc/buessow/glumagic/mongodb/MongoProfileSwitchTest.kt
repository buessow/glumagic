package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.MlProfileSwitch
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MongoProfileSwitchTest {

  @Test
  fun toMlProfileSwitch_noBasal() {
    val mps = MongoProfileSwitch(
        "2020-01-01T00:00:00Z",
        "name",
        Duration.ofMinutes(10).toMillis(),
        110,
        """{}""")
    assertEquals(
        MlProfileSwitch(
            name = "name",
            start = Instant.parse("2020-01-01T00:00:00Z"),
            basalRates = emptyList(),
            duration = Duration.ofMinutes(10),
            rate = 1.1), mps.toMlProfileSwitch())
  }

  @Test
  fun toMlProfileSwitch() {
    val mps = MongoProfileSwitch(
        "2020-01-01T00:00:00Z",
        "name",
        Duration.ofMinutes(10).toMillis(),
        110,
        """{"dia":"5","carbratio":[{"time":"00:00","value":"10","timeAsSeconds":"0"},{"time":"16:00","value":"8","timeAsSeconds":"57600"}],"carbs_hr":"15","delay":"20","sens":[{"time":"00:00","value":"2","timeAsSeconds":"0"}],"timezone":"Europe\/Zurich","basal":[{"time":"00:00","value":"0.56","timeAsSeconds":"0"},{"time":"01:00","value":"0.64","timeAsSeconds":"3600"},{"time":"02:00","value":"0.64","timeAsSeconds":"7200"},{"time":"03:00","value":"0.68","timeAsSeconds":"10800"},{"time":"04:00","value":"0.68","timeAsSeconds":"14400"},{"time":"05:00","value":"0.72","timeAsSeconds":"18000"},{"time":"06:00","value":"0.84","timeAsSeconds":"21600"},{"time":"07:00","value":"0.88","timeAsSeconds":"25200"},{"time":"08:00","value":"0.8","timeAsSeconds":"28800"},{"time":"09:00","value":"0.96","timeAsSeconds":"32400"}],"target_low":[{"time":"00:00","value":"4.5","timeAsSeconds":"0"}],"target_high":[{"time":"00:00","value":"7.5","timeAsSeconds":"0"}],"startDate":"1970-01-01T00:00:00.000Z","units":"mmol"}""")
    assertEquals(
        MlProfileSwitch(
            name = "name",
            start = Instant.parse("2020-01-01T00:00:00Z"),
            basalRates = listOf(
                Duration.ofHours(1) to 0.56,
                Duration.ofHours(1) to 0.64,
                Duration.ofHours(1) to 0.64,
                Duration.ofHours(1) to 0.68,
                Duration.ofHours(1) to 0.68,
                Duration.ofHours(1) to 0.72,
                Duration.ofHours(1) to 0.84,
                Duration.ofHours(1) to 0.88,
                Duration.ofHours(1) to 0.8,
                Duration.ofHours(15) to 0.96),
            duration = Duration.ofMinutes(10),
            rate = 1.1),
        mps.toMlProfileSwitch())
    assertTrue(mps.toMlProfileSwitch().isValid)
  }
}