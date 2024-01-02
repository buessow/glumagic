package cc.buessow.glumagic

import cc.buessow.glumagic.mongodb.MongoDbInputProvider
import kotlinx.cli.*
import java.time.Instant
import java.time.temporal.ChronoUnit

object Main {
  @JvmStatic
  fun main(args: Array<String>) {
    println("Hello world!")

    val parser = ArgParser("glumagic")
    val userName by parser.option(
        ArgType.String, shortName = "u", description = "MongoDB user name").required()
    val password by parser.option(
        ArgType.String, shortName = "p", description = "MongoDB password").required()
    val database by parser.option(
        ArgType.String, shortName = "db", description = "MongoDB database").required()
    val host by parser.option(
        ArgType.String,
        shortName = "s", fullName = "server",
        description = "MongoDB server host, e.g. 'mongodb+srv://cluster0.10utl.gcp.mongodb.net'").required()

    parser.parse(args)

    MongoDbInputProvider(host, database, userName, password).use { input ->
      for (dv in input.getGlucoseReadings(Instant.now().minusSeconds(1800L)).blockingGet()) {
        println("glucose $dv")
      }
      for (dv in input.getCarbs(Instant.now().minusSeconds(18000L)).blockingGet()) {
        println("carbs $dv")
      }

      val bpss = input.getBasalProfileSwitches(
          Instant.now().minusSeconds(1800L)).blockingGet()
      println("profile switch $bpss")

      for (tb in
      input.getTemporaryBasalRates(Instant.now().minus(2L, ChronoUnit.HOURS)).blockingGet()) {
        println("tb $tb")
      }
    }
  }
}