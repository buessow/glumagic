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

  @Test
  fun savgol1() {
    val filter = SmoothingFilter("savgol", mapOf("windowSize" to 7, "polynomialOrder" to 2))
    ArrayApproxCompare.getMismatch(
        filter.filter(List(10) {0.0}),
        List(10){0.0},
        eps=1e-6)
    val l = MutableList(10) { 0.0 }
    l[0] = Double.NaN
    ArrayApproxCompare.getMismatch(filter.filter(l), l, eps=1e-6)
    l[1] = Double.NaN
    ArrayApproxCompare.getMismatch(filter.filter(l), l, eps=1e-6)
    val l1 = MutableList(10) { 0.0 }
    l1[4] = Double.NaN
    ArrayApproxCompare.getMismatch(filter.filter(l1), l1, eps=1e-6)
    val l2 = MutableList(10) { 0.0 }
    l2[4] = Double.NaN
    ArrayApproxCompare.getMismatch(filter.filter(l2), l2, eps=1e-6)
    val l3 = MutableList(10) { 0.0 }
    l3[l3.size-1] = Double.NaN
    ArrayApproxCompare.getMismatch(filter.filter(l3), l3, eps=1e-6)
    val l4 = MutableList(10) { Double.NEGATIVE_INFINITY }
    ArrayApproxCompare.getMismatch(filter.filter(l4), l4, eps=1e-6)
  }
}
