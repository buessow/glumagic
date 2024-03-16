package cc.buessow.glumagic.input

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId

class JsonParserTest {
  @Test
  fun deserialize() {
    val config = JsonParser.fromJson<Config>("""{
        "trainingPeriodMinutes": 1,
        "predictionPeriodMinutes": 2,
        "carbAction": { "name": "LogNorm", "mu": 1.1, "sigma": 1.2 },
        "insulinAction": { "name": "Exponential", "peak": 30, "total": 120 },
        "hrLongDurationMinutes": [ 10 ],
        "hrHighThreshold": 140,
        "freqMinutes": 2,
        "zoneId": "CET"
      }""")
    assertEquals(Duration.ofMinutes(1), config.trainingPeriod)
    assertEquals(Duration.ofMinutes(2), config.predictionPeriod)
    assertEquals(1.1, (config.carbAction as LogNormAction).mu)
    assertEquals(1.2, (config.carbAction as LogNormAction).sigma)
    assertEquals(Duration.ofMinutes(30), (config.insulinAction as ExponentialInsulinModel).timeToPeak)
    assertEquals(Duration.ofMinutes(120), (config.insulinAction as ExponentialInsulinModel).totalDuration)
    assertEquals(listOf(Duration.ofMinutes(10)), config.hrLong)
    assertEquals(140, config.hrHighThreshold)
    assertEquals(Duration.ofMinutes(2), config.freq)
    assertEquals(ZoneId.of("CET"), config.zoneId)
  }
}
