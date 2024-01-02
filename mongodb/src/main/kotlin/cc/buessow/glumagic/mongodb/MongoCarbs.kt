package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Instant

data class MongoCarbs(
    @BsonProperty("created_at") val createdAt: String,
    val carbs: Int) {
  fun toDateValue() = DateValue(Instant.parse(createdAt), carbs.toDouble())
}
