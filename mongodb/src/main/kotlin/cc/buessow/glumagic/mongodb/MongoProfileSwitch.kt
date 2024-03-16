package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.MlProfileSwitch
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.Duration
import java.time.Instant

@MongoCollection("treatments")
internal data class MongoProfileSwitch(
    @SerializedName("created_at") @BsonProperty("created_at") val createdAt: String?,
    val date: Long?,
    val originalProfileName: String?,
    val originalDuration: Long?,
    val originalPercentage: Int?,
    val profileJson: String,
    val eventType: String = "Profile Switch") {

  private class ProfileBasal(val timeAsSeconds: Long, val value: Double) {
    val duration get(): Duration = Duration.ofSeconds(timeAsSeconds)
  }
  private class Profile(val basal: List<ProfileBasal>?)

  private val profile get() = gson.fromJson(profileJson, Profile::class.java)

  private val basalRates: List<Pair<Duration, Double>> get() {
    val bs = profile.basal ?: return emptyList()
    return bs.mapIndexed { i, b ->
      val p = bs.elementAtOrNull(i + 1)?.duration ?: Duration.ofDays(1)
      p - b.duration to b.value }
  }

  private val created: Instant get() =
    date?.let { Instant.ofEpochMilli(it) } ?: Instant.parse(createdAt!!)

  fun toMlProfileSwitch() = MlProfileSwitch(
      name = originalProfileName,
      start = created,
      basalRates = basalRates,
      duration = originalDuration?.let { millis -> Duration.ofMillis(millis) },
      rate = (originalPercentage ?: 100) / 100.0)

  companion object {
    private val gson = GsonBuilder().create()
  }
}
