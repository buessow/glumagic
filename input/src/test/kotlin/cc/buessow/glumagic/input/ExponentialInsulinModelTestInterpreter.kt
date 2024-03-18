package cc.buessow.glumagic.input

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ExponentialInsulinModelTest {

  @Test
  fun insulinOnBoard() {
    val fiasp = ExponentialInsulinModel.fiasp
    assertEquals(1.0, fiasp.insulinOnBoard(Duration.ofMinutes(-1)))
    assertEquals(1.0, fiasp.insulinOnBoard(Duration.ZERO))
    assertEquals(0.788, fiasp.insulinOnBoard(Duration.ofMinutes(45L)), 1e-3)
    assertEquals(0.717, fiasp.insulinOnBoard(Duration.ofMinutes(55L)), 1e-3)
    assertEquals(0.314, fiasp.insulinOnBoard(Duration.ofMinutes(120L)), 1e-3)
    assertEquals(0.0, fiasp.insulinOnBoard(Duration.ofMinutes(360L)))
    assertEquals(0.0, fiasp.insulinOnBoard(Duration.ofMinutes(420L)))
  }

  @Test
  fun valuesAt() {
    val fiasp = ExponentialInsulinModel.fiasp
    val t = Instant.parse("2013-12-13T20:00:00Z")

    val dates = listOf(5L, 30L, 55L, 120L, 180L, 230L, 360L).map { m -> t + Duration.ofMinutes(m) }.toList()
    val r = fiasp.valuesAt(listOf(DateValue(t, 5.0)), t, dates)

    val expected = listOf(0.021, 0.537, 0.860, 2.011, 0.999, 0.372, 0.201)
    assertNull(ArrayApproxCompare.getMismatch(r, expected, eps = 1e-2))
    assertEquals(5.0, r.sum(), 1e-2)

    val r10 = fiasp.valuesAt(listOf(DateValue(t, 10.0)), t, dates)
    assertNull(ArrayApproxCompare.getMismatch(r10, expected.map { 2 * it }, eps = 1e-2))
    assertEquals(10.0, r10.sum(), 1e-2)
  }

  @Test
  fun valuesAtLong() {
    val exm = ExponentialInsulinModel.fiasp
    val t = Instant.parse("2013-12-13T20:00:00Z")
    val duration = Duration.ofDays(120)
    val bolusTimes = t ..< (t + duration) step Duration.ofMinutes(6L)
    val bolus = bolusTimes.map { ts -> DateValue(ts, 2.0) }
    val times = t ..< t + duration + exm.totalDuration step Duration.ofMinutes(5)
    val a = exm.valuesAt(bolus, t, times)
    val totalBolus = bolus.sumOf { it.value }
    assertTrue(a.sum() in totalBolus * 0.99 .. totalBolus * 1.01) {
      "bolus: $totalBolus action: ${a.sum()}"}
  }
}
