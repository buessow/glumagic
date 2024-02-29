package cc.buessow.glumagic.input

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ExponentialInsulinModelTest {

  @Test
  fun valuesAt() {
    val fiasp = ExponentialInsulinModel.fiasp
    val t = Instant.parse("2013-12-13T20:00:00Z")

    val r = fiasp.valuesAt(
        listOf(DateValue(t, 5.0)), listOf(30L, 120L, 180L, 230L).map { m -> t + Duration.ofMinutes(m) }.toList()
    )

    val e = doubleArrayOf(
        0.030820730965801808, 0.023447092680969967, 0.010787000631536287, 0.004725084000523727)
    Assertions.assertArrayEquals(e, r.toDoubleArray(), 1e-6)
  }
}
