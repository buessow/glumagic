package cc.buessow.glumagic.mongodb

data class MongoGlucose(override val date: Long, val sgv: Int): MongoDateValue {
  override val value: Double get() = sgv.toDouble()
}
