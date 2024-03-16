package cc.buessow.glumagic.input

import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.Writer
import java.time.Instant
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor

data class TrainingInput(
    val date: List<Instant>,
    val hour: List<Int>,
    val glucose: List<Double>,
    val glucoseSlope1: List<Double>,
    val glucoseSlope2: List<Double>,
    val heartRate: List<Double>,
    val hrLong1: List<Double>,
    val hrLong2: List<Double>,
    val carbs: List<Double>,
    val carbAction: List<Double>,
    val bolus: List<Double>,
    val basal: List<Double>,
    val insulinAction: List<Double>,
) {

  companion object {
    private val properties: List<KProperty<List<Any>>>

    init {
      val order = TrainingInput::class.primaryConstructor!!.parameters.associate { p -> p.name to p.index }
      @Suppress("UNCHECKED_CAST")
      properties = TrainingInput::class.members
          .filter { p -> p is KProperty<*> && p.visibility == KVisibility.PUBLIC }
          .map { p -> p as KProperty<List<Any>> }
          .sortedBy { p -> order[p.name] }
    }

    private val propertyNames get() = properties.map { it.name }
  }

  init {
    // Make sure that all properties have the same length.
    properties.forEach { p ->
      val size = (p.call(this) as List<*>).size
      assert(date.size == size) { "size mismatch for ${p.name}: ${date.size} != $size" } }
  }

  override fun toString() =
    "TrainingInput(" + properties.joinToString{p -> "${p.name}=${p.call(this)}"} + ")"

  fun writeJson(out: Writer) {
    JsonParser.toJson(this, out)
  }
  fun writeToFile(file: File) {
    JsonParser.toJson(this, file)
  }

  private fun values(): List<List<Any>> = properties.map { p -> p.call(this) }

  private fun records() = sequence {
    val values = values()
    for (i in date.indices) {
      yield (values.map { vs -> vs[i] })
    }
  }

  fun writeCsv(out: Writer, head: Int? = null) {
    CSVFormat.DEFAULT.print(out).apply {
      val propertyNames = propertyNames
      printRecord(propertyNames)
      records().take(head ?: Int.MAX_VALUE).forEach { r -> printRecord(r) }
      close()
    }
  }
}
