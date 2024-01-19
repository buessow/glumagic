package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue

@MongoCollection("entries")
internal data class MongoGlucose(val date: Long, val sgv: Int): MongoDateValue {
  val value: Double get() = sgv.toDouble()

  override fun toDateValue() = DateValue(date, sgv)
}
