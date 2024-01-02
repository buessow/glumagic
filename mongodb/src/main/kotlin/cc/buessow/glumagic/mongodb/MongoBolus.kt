package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Instant

internal data class MongoBolus(
    @BsonProperty("created_at") val createdAt:
    String, val insulin: Double) {
  fun toDateValue() = DateValue(Instant.parse(createdAt), insulin)
}
