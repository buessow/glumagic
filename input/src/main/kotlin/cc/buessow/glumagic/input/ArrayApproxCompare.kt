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

    fun getMismatch(
        actual: Collection<Number>,
        expected: Collection<Number>,
        eps: Double): String? {
      val mismatch = firstMismatch(expected.toList(), actual.toList(), eps) ?: return null
      val form = { f: Number -> "%.3f".format(f) + if(f is Float) "F" else "" }
      val a = actual.mapIndexed { i, f -> if (i == mismatch) "**${form(f)}" else form(f) }.joinToString()
      val e = expected.mapIndexed { i, f -> if (i == mismatch) "**${form(f)}" else form(f) }.joinToString()
      return "exp:${"%3d".format(mismatch)} [$e]\nbut was [$a]"
    }
  }
}
