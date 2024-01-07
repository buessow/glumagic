@file:OptIn(ExperimentalCli::class)
package cc.buessow.glumagic

import cc.buessow.glumagic.input.InputProvider
import cc.buessow.glumagic.mongodb.MongoApiInputProvider
import cc.buessow.glumagic.mongodb.MongoDbInputProvider
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

object Main {

  private fun dump(input: InputProvider) = runBlocking {
    for (dv in input.getGlucoseReadings(Instant.now().minusSeconds(1800L))) {
      println("glucose $dv")
    }
    for (dv in input.getCarbs(Instant.now().minusSeconds(18000L))) {
      println("carbs $dv")
    }
    val bpss = input
        .getBasalProfileSwitches(Instant.now().minusSeconds(1800L))
    println("profile switch $bpss")

    for (tb in input.getTemporaryBasalRates(Instant.now().minus(2L, ChronoUnit.HOURS))
        ) {
      println("temp $tb")
    }
  }

  private class QueryApi(): Subcommand("api", "Query Mongo API") {
    val database by option(ArgType.String, shortName = "db", description = "MongoDB database").required()
    val apiKey by option(ArgType.String, shortName = "k", description = "Mongo API key").required()
    val serverUrl by option(ArgType.String, shortName = "s", description = "Mongo API server URL")
        .default("https://eu-central-1.aws.data.mongodb-api.com/app/data-iiidw")

    override fun execute() {
      MongoApiInputProvider(database, serverUrl, apiKey).use { input -> dump(input) }
    }
  }

  private class QueryDb(): Subcommand("db", "Query Mongo API") {
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

  private class Version(): Subcommand("version", "Version information") {
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
