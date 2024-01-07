package cc.buessow.glumagic.mongodb

import com.google.gson.annotations.SerializedName
import org.bson.codecs.pojo.annotations.BsonProperty

@MongoCollection("heartrate")
internal data class MongoHeartRate(override val date: Long, val beatsPerMinute: Int):
  MongoDateValue {
  override val value: Double get() = beatsPerMinute.toDouble()
}

@MongoCollection("activity")
internal data class MongoHeartRateActivity(
    @SerializedName("samplingStart")
    @BsonProperty("samplingStart")
    override val date: Long,
    val heartRate: Int,
    val eventType: String = "HeartRate"):
  MongoDateValue {
  override val value: Double get() = heartRate.toDouble()
}


@MongoCollection("treatments")
internal data class MongoHeartRateTreatment(
    @SerializedName("samplingStart")
    @BsonProperty("samplingStart")
    override val date: Long,
    val heartRate: Int,
    val eventType: String = "HeartRate"):
  MongoDateValue {
  override val value: Double get() = heartRate.toDouble()
}


/*
hr1 = read_cached_collection(
    'activity',
    [('samplingStart', 'date'), ('samplingEnd', 'date_end'), ('heartRate', 'heart_rate')],
    {'eventType': 'HeartRate', 'samplingStart': {'$gt': START, '$lt': END}},
    limit=0)
hr2 = read_cached_collection(
    'treatments',
    [('samplingStart', 'date'), ('samplingEnd', 'date_end'), ('heartRate', 'heart_rate')],
    {'eventType': 'HeartRate', 'samplingStart': {'$gt': START, '$lt': END}},
    limit=0)
hr3 = read_cached_collection(
    'heartrate',
    [('created_at', 'date_end'), ('duration', 'duration_sec'), ('beatsPerMinute', 'heart_rate')],
    {'isValid': True, 'created_at': {'$gt': START, '$lt': END}},
    limit=0)
 */
