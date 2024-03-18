package cc.buessow.glumagic.input

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.*
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

@Suppress("SameParameterValue")
internal object JsonParser {

  private val gson: Gson

  @Suppress("UNUSED")
  internal inline fun <reified T> fromJson(json: String): T = gson.fromJson(json, T::class.java)

  @Suppress("UNUSED")
  internal inline fun <reified T> fromJson(jsonFile: File): T =
      jsonFile.inputStream().use { fromJson(it) }

  internal inline fun <reified T> fromJson(jsonInput: InputStream): T =
      jsonInput.bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, T::class.java) }

  @Suppress("UNUSED")
  internal fun <T> toJson(t: T): String = gson.toJson(t)

  private fun outputStream(file: File): OutputStream {
    return if (file.extension == "gz") {
      GZIPOutputStream(file.outputStream())
    } else {
      file.outputStream()
    }
  }

  internal fun <T> toJson(t: T, file: File) {
    outputStream(file).use { toJson(t, it) }
  }

  fun <T> toJson(t: T, out: Writer) {
    gson.toJson(t, out)
  }

  private fun <T> toJson(t: T, outputStream: OutputStream) {
    OutputStreamWriter(outputStream, Charsets.UTF_8).use { toJson(t, it) }
  }

  init {
    @Suppress("SpellCheckingInspection")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxxxx")
    val builder = GsonBuilder()
    builder.registerTypeAdapter(
        ZoneId::class.java,
        object: TypeAdapter<ZoneId>() {
          override fun write(out: JsonWriter, value: ZoneId?) {
            if (value == null) {
              out.nullValue()
            } else {
              out.value(value.id)
            }
          }
          override fun read(jr: JsonReader): ZoneId {
            val id = jr.nextString()
            return ZoneId.of(id)
          }
        })
    builder.registerTypeAdapter(
        LocalDateTime::class.java,
        object : TypeAdapter<LocalDateTime>() {
          override fun write(out: JsonWriter, value: LocalDateTime?) {
            if (value != null) out.value(formatter.format(value))
            else out.nullValue()
          }

          override fun read(jr: JsonReader) =
            LocalDateTime.parse(jr.nextString(), formatter)
        })
    builder.registerTypeAdapter(
        Instant::class.java,
        object : TypeAdapter<Instant>() {
          override fun write(out: JsonWriter, value: Instant?) {
            if (value != null) out.value(value.toString())
            else out.nullValue()
          }

          override fun read(jr: JsonReader) = jr.nextString().let {
            if (it.last() == 'Z') Instant.parse(it) else LocalDateTime.parse(it, formatter).toInstant(UTC)
          }
        })
    builder.registerTypeAdapter(
        Duration::class.java,
        object : TypeAdapter<Duration>() {
          override fun write(out: JsonWriter, value: Duration?) {
            if (value != null) out.value(value.toMinutes())
            else out.nullValue()
          }

          override fun read(jr: JsonReader) =
            Duration.ofMinutes(jr.nextLong())
        })
    builder.registerTypeAdapter(
        ActionModel::class.java,
        object: TypeAdapter<ActionModel>() {
          override fun write(out: JsonWriter, value: ActionModel?) {
            if (value == null) {
              out.nullValue()
            } else {
              out.beginObject()
              for ((n, v) in value.getArgs()) {
                out.name(n)
                when (v) {
                  is String -> out.value(v)
                  is Number -> out.value(v)
                  is Boolean -> out.value(v)
                  else -> throw IllegalArgumentException("unsupported type ${v.javaClass}")
                }
              }
              out.endObject()
            }
          }

          override fun read(jr: JsonReader): ActionModel {
            jr.beginObject()
            val args = mutableMapOf<String, Any>()
            while (jr.peek() != JsonToken.END_OBJECT) {
              val name = jr.nextName()
              when (jr.peek()) {
                JsonToken.STRING -> args[name] = jr.nextString()
                JsonToken.NUMBER -> args[name] = jr.nextDouble()
                JsonToken.BOOLEAN -> args[name] = jr.nextBoolean()
                JsonToken.NULL -> {}
                else -> throw IllegalArgumentException("unknown token ${jr.peek()}")
              }
            }
            jr.endObject()
            return ActionModel.create(args)
          }
        })
    gson = builder.create()
  }
}
