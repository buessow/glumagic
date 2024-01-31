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
        "carbAction": { "mu": 1.1, "sigma": 1.2 },
        "insulinAction": { "mu": 2.1, "sigma": 2.2 },
        "hrLongDurationMinutes": [ 10 ],
        "hrHighThreshold": 140,
        "freqMinutes": 2,
        "zoneId": "CET"
      }""")
    assertEquals(Duration.ofMinutes(1), config.trainingPeriod)
    assertEquals(Duration.ofMinutes(2), config.predictionPeriod)
    assertEquals(1.1, config.carbAction.mu)
    assertEquals(1.2, config.carbAction.sigma)
    assertEquals(2.1, config.insulinAction.mu)
    assertEquals(2.2, config.insulinAction.sigma)
    assertEquals(listOf(Duration.ofMinutes(10)), config.hrLong)
    assertEquals(140, config.hrHighThreshold)
    assertEquals(Duration.ofMinutes(2), config.freq)
    assertEquals(ZoneId.of("CET"), config.zoneId)
  }
}
