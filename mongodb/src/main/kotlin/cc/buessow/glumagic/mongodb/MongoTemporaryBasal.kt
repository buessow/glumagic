package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.MlTemporaryBasalRate
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Duration
import java.time.Instant

internal data class MongoTemporaryBasal(
    @BsonProperty("created_at") val createdAt: String,
    val duration: Long?,
    val rate: Double?,
    val percent: Int?,
    val eventType: String = "Temp Basal") {

  fun toMlTemporaryBasalRate() = MlTemporaryBasalRate(
      timestamp = Instant.parse(createdAt),
      duration = duration?.let { min -> Duration.ofMinutes(min)} ?: Duration.ZERO,
      rate = (percent ?: 100) / 100.0,
      basal = rate)
}
