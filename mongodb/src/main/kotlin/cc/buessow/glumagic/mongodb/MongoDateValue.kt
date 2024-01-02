package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue

interface MongoDateValue {
  val date: Long
  val value: Double

  fun toDateValue() = DateValue(date, value)
}