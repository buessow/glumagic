package cc.buessow.glumagic.input

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class SmoothingFilterTest {

  @Test
  fun savgol() {
    val filter = SmoothingFilter("savgol", mapOf("windowSize" to 5, "polynomialOrder" to 2))
    ArrayApproxCompare.getMismatch(
        filter.filter(listOf(1.0, 2.0, 3.0, 4.0, 5.0)),
        listOf(1.0, 2.0, 3.0, 4.0, 5.0),
        eps=1e-2)?.also { fail(it) }
    ArrayApproxCompare.getMismatch(
        filter.filter(listOf(1.0, 8.0, 6.0, 0.0, 8.0, 10.0)),
        listOf(3.114, 4.143, 4.886, 3.257, 5.429, 10.743),
        eps=1e-2)?.also { fail(it) }
  }
}
