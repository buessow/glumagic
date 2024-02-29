package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.MlTemporaryBasalRate
import com.google.gson.annotations.SerializedName
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Duration
import java.time.Instant

@MongoCollection("treatments")
internal data class MongoTemporaryBasal(
    @SerializedName("created_at")
    @BsonProperty("created_at") val createdAt: String?,
    val date: Long?,
    val duration: Long?,
    val rate: Double?,
    val percent: Int?,
    val eventType: String = "Temp Basal") {

  private val created: Instant get() =
    date?.let { Instant.ofEpochMilli(it) } ?: Instant.parse(createdAt!!)

  fun toMlTemporaryBasalRate() = MlTemporaryBasalRate(
      timestamp = created,
      duration = duration?.let { min -> Duration.ofMinutes(min)} ?: Duration.ZERO,
      rate = 1.0 + (percent ?: 100) / 100.0,
      basal = rate)
}
