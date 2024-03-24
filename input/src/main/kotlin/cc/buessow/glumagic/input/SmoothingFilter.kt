package cc.buessow.glumagic.input

import com.github.psambit9791.jdsp.filter.Median
import com.github.psambit9791.jdsp.filter.Savgol
import com.github.psambit9791.jdsp.filter.Wiener
import com.github.psambit9791.jdsp.filter._KernelFilter

class SmoothingFilter(filterName: String, filterParams: Map<String, Any>) {

  private fun getInt(params: Map<String, Any>, name: String, default: Int) =
    params.getOrDefault(name, default).let { v ->
      when (v) {
        is Number -> v.toInt()
        is String -> v.toInt()
        else -> v.toString().toInt()
      }
    }

  private val windowSize = getInt(filterParams, "windowSize", 3)

  private val kernelFilter: _KernelFilter? = when (filterName) {
    "none" -> null
    "median" -> Median(windowSize)
    "savgol" -> Savgol(
        getInt(filterParams, "windowSize", 3),
        getInt(filterParams, "polynomialOrder", 2))
    "wiener" -> Wiener(getInt(filterParams, "windowSize", 3))
    else -> throw IllegalArgumentException("unknown smoothing filter '$filterName'")
  }

  fun filter(values: List<Double>): List<Double> {
    if (kernelFilter == null) return values

    // Make sure we don't pass nan or infinity to the filter, since it won't terminate.
    // We split the input into sub-lists of finite values and pass them individually to the
    // filter if they are long enough. Other values are simply copied over.
    val result = mutableListOf<Double>()
    var pos = 0
    while (result.size < values.size) {
      val start = pos
      while (pos < values.size && values[pos].isFinite()) pos++
      if (pos - start >= windowSize) {
        result.addAll(kernelFilter.filter(values.subList(start, pos).toDoubleArray()).asList())
      } else {
        result.addAll(values.subList(start, pos))
      }
      while (pos < values.size && !values[pos].isFinite()) result.add(values[pos++])
    }
    return result
  }
}
