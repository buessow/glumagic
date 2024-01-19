package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import com.google.gson.annotations.SerializedName
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Instant

@MongoCollection("heartrate")
internal data class MongoHeartRate(val date: Long, val beatsPerMinute: Int): MongoDateValue {
  override fun toDateValue() = DateValue(date, beatsPerMinute)
}

@MongoCollection("activity")
internal data class MongoHeartRateActivity(
    @SerializedName("samplingStart")
    @BsonProperty("samplingStart")
    val start: String,
    val heartRate: Int,
    val eventType: String = "HeartRate"): MongoDateValue {

  override fun toDateValue() = DateValue(Instant.parse(start), heartRate)
}


@MongoCollection("treatments")
internal data class MongoHeartRateTreatment(
    @SerializedName("samplingStart")
    @BsonProperty("samplingStart")
    val date: Long,
    val heartRate: Int,
    val eventType: String = "HeartRate"):
  MongoDateValue {
  override fun toDateValue() = DateValue(date, heartRate)
}
