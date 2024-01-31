@file:OptIn(ExperimentalCli::class)
package cc.buessow.glumagic

import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.input.InputProvider
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import cc.buessow.glumagic.mongodb.MongoDbInputProvider
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStreamWriter
import java.time.*
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalQueries

object Main {

  internal val ArgTypeDateTime = object: ArgType<Instant>(true) {
    private val formatter = DateTimeFormatterBuilder()
        .parseLenient()
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .optionalStart()
        .appendLiteral('T')
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalEnd()
        .optionalStart()
        .appendZoneOrOffsetId()
        .optionalEnd()
        .toFormatter()
    private val zoneIdOrOffsetQueries = listOf(TemporalQueries.offset(), TemporalQueries.zone())

    override val description: kotlin.String
      get() = "Date/time in ISO format yyyy-mm-ddTHH:MM:SSZ"

    override fun convert(value: kotlin.String, name: kotlin.String): Instant {
      try {
        val ta = formatter.parse(value)
        return if (!ta.isSupported(ChronoField.HOUR_OF_DAY)) {
          val date = LocalDate.from(ta)
          Instant.ofEpochSecond(date.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC))

        } else if (zoneIdOrOffsetQueries.all { ta.query(it) == null }) {
            val date = LocalDateTime.from(ta)
            Instant.ofEpochSecond(date.toEpochSecond(ZoneOffset.UTC))

          } else {
            Instant.from(ta)
          }
      } catch (e: DateTimeParseException) {
        throw ParsingException(
            "Option $name is expected to be in yyyy-mm-ddTHH:MM:SSZ format. '$value' is provided.")
      }
    }
  }

  internal val ArgTypeInFile = object: ArgType<File>(true) {
    override val description: kotlin.String
      get() = "Input file"

    override fun convert(value: kotlin.String, name: kotlin.String): File {
      return File(value).also { f ->
        if (!f.isFile || !f.canRead()) {
          throw ParsingException("'$value' for option $name is not a readable file.")
        }
      }
    }
  }

  internal val ArgTypeOutFile = object: ArgType<File>(true) {
    override val description: kotlin.String
      get() = "Output file"

    override fun convert(value: kotlin.String, name: kotlin.String): File {
      return File(value).also { f ->
        val canCreate =
           !f.exists() && !f.isDirectory() && f.absoluteFile.parentFile.canWrite()
        val canModify = f.exists() && f.canWrite()
        if (!canCreate && !canModify) {
          throw ParsingException("Cannot write '$value' for option $name.")
        }
      }
    }
  }

  private fun dump(input: InputProvider) = runBlocking {
    for (dv in input.getGlucoseReadings(Instant.now().minusSeconds(1800L))) {
      println("glucose $dv")
    }
    for (dv in input.getCarbs(Instant.now().minusSeconds(18000L))) {
      println("carbs $dv")
    }
    val bpss = input.getBasalProfileSwitches(Instant.now().minusSeconds(1800L))
    println("profile switch $bpss")

    for (tb in input.getTemporaryBasalRates(Instant.now().minus(2L, ChronoUnit.HOURS))) {
      println("temp $tb")
    }
  }

  private fun output(
      from: Instant, upto: Instant, input: InputProvider, config: Config, outFile: File?) {
    val config1 = Config(
        trainingPeriod = Duration.between(from, upto),
        predictionPeriod = Duration.ZERO,
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes = 60, sigma = 0.5),
        hrLong = config.hrLong,
        hrHighThreshold = config.hrHighThreshold,
        freq = config.freq,
        zone = config.zoneId)
    val trainingData = DataLoader.getTrainingData(input, from, config1)
    println("Loaded ${trainingData.date.size} values")
    if (outFile == null) {
      trainingData.writeCsv(OutputStreamWriter(System.out))
    } else if (outFile.extension == "csv") {
      outFile.outputStream().use { trainingData.writeCsv(OutputStreamWriter(it)) }
    } else if (outFile.extension == "json") {
      trainingData.writeToFile(outFile)
    }
  }

  private fun getConfig(configFile: File?): Config {
    return configFile?.let { Config.fromJson(configFile) } ?: Config(
        trainingPeriod = Duration.ZERO,
        predictionPeriod = Duration.ZERO,
        carbAction = Config.LogNorm(peakInMinutes = 45, sigma = 0.5),
        insulinAction = Config.LogNorm(peakInMinutes =60, sigma = 0.5),
        hrLong = listOf(Duration.ofHours(12), Duration.ofHours(24)),
        hrHighThreshold = 120,
        freq = Duration.ofMinutes(5),
        zone = ZoneId.of("CET"))
  }

  private class QueryApi: Subcommand("api", "Query Mongo API") {
    val from by option(ArgTypeDateTime, shortName = "from", description = "from date").required()
    val upto by option(ArgTypeDateTime, shortName = "to", description = "up to date").required()
    val configFile by option(ArgTypeInFile, shortName = "c", description = "config file")
    val outFile by option(ArgTypeOutFile, shortName = "o", description = "output file")
    val database by option(ArgType.String, shortName = "db", description = "MongoDB database").required()
    val apiKey by option(ArgType.String, shortName = "k", description = "Mongo API key").required()
    val serverUrl by option(ArgType.String, shortName = "s", description = "Mongo API server URL")
        .default("https://eu-central-1.aws.data.mongodb-api.com/app/data-iiidw")

    override fun execute() {
      MongoApiInputProvider(database, serverUrl, apiKey).use { input ->
        output(from, upto, input, getConfig(configFile), outFile)
      }
    }
  }

  private class QueryDb: Subcommand("db", "Query Mongo API") {
    val userName by option(
        ArgType.String, shortName = "u", description = "MongoDB user name").required()
    val password by option(
        ArgType.String, shortName = "p", description = "MongoDB password").required()
    val database by option(
        ArgType.String, shortName = "db", description = "MongoDB database").required()
    val host by option(
        ArgType.String,
        shortName = "s", fullName = "server",
        description = "MongoDB server host, e.g. 'mongodb+srv://cluster0.10utl.gcp.mongodb.net'").required()

    override fun execute() {
      MongoDbInputProvider(host, database, userName, password).use { input -> dump(input) }
    }
  }

  private class Version: Subcommand("version", "Version information") {
    override fun execute() {
      println("version 1.0")
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val argParser = ArgParser("glumagic")
    argParser.subcommands(QueryApi(), QueryDb(), Version())
    argParser.parse(args)
  }
}
