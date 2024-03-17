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

  private val kernelFilter: _KernelFilter? = when (filterName) {
    "none" -> null
    "median" -> Median(getInt(filterParams, "windowSize", 3))
    "savgol" -> Savgol(
        getInt(filterParams, "windowSize", 3),
        getInt(filterParams, "polynomialOrder", 2))
    "wiener" -> Wiener(getInt(filterParams, "windowSize", 3))
    else -> throw IllegalArgumentException("unknown smoothing filter '$filterName'")
  }

  fun filter(values: List<Double>) =
    kernelFilter?.filter(values.toDoubleArray())?.toList() ?: values
}
