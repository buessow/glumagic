package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import com.google.gson.annotations.SerializedName
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Instant

@MongoCollection("treatments")
internal data class MongoBolus(
    @SerializedName("created_at") @BsonProperty("created_at") val createdAt: String?,
    val date: Long? = null,
    val insulin: Double): MongoDateValue {

  private val created: Instant get() =
    date?.let { Instant.ofEpochMilli(it) } ?: Instant.parse(createdAt!!)

  override fun toDateValue() = DateValue(created, insulin)
}
