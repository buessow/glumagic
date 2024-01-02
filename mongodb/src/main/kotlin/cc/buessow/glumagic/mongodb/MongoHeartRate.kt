package cc.buessow.glumagic.mongodb

data class MongoHeartRate(override val date: Long, val beatsPerMinute: Int): MongoDateValue {
  override val value: Double get() = beatsPerMinute.toDouble()
}
