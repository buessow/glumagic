package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue

internal interface MongoDateValue {
  fun toDateValue(): DateValue
}
