package cc.buessow.glumagic.input

import kotlin.math.abs

class ArrayApproxCompare {
  companion object {

    fun approxEquals(n1: Number, n2: Number, eps: Double): Boolean {
      return if (n1 is Double && n2 is Double) {
        n1.isNaN() == n2.isNaN() && abs(n1 - n2) < eps
      } else if (n1 is Float && n2 is Float) {
        n1.isNaN() == n2.isNaN() && abs(n1 - n2) < eps
      } else {
        false
      }
    }

    private fun firstMismatch(expected: List<Number>, actual: List<Number>, e: Double): Int? {
      val size = expected.size.coerceAtMost(actual.size)
      for (i in 0 ..< size) {
        if (!approxEquals(expected[i], actual[i], e))
          return i
      }
      return if (expected.size == actual.size) null else size
    }

    private fun formatMismatch(values: Collection<Number>, pos: Int): String {
      val limit = 30
      val pre = if (pos > limit) ".., " else ""
      val form = { f: Number -> "%.3f".format(f) + if(f is Float) "F" else "" }
      return values
          .mapIndexed { i, f -> if (i == pos) "**${form(f)}" else form(f) }
          .drop(if (pos > limit) pos - 2 else 0)
          .joinToString(prefix = pre)
    }

    fun getMismatch(
        actual: Collection<Number>,
        expected: Collection<Number>,
        eps: Double): String? {
      val mismatch = firstMismatch(expected.toList(), actual.toList(), eps) ?: return null
      val a = formatMismatch(actual, mismatch)
      val e = formatMismatch(expected, mismatch)
      return "exp:${"%3d".format(mismatch)} [$e]\nbut was [$a]"
    }
  }
}
