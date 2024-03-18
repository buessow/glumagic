package cc.buessow.glumagic.predictor

import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.input.InputProvider
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Float.isNaN
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

class Predictor private constructor(
    val config: Config,
    private val interpreter: Interpreter) : Closeable {

  companion object {
    private val log = Logger.getLogger(Predictor::javaClass.name)

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    fun create(
        modelMetaInput: InputStream,
        modelBytesInput: InputStream): Predictor {

      val config = Config.fromJson(modelMetaInput)
      val modelBytes = modelBytesInput.readBytes()
      val sha1Hash = MessageDigest.getInstance("Sha-1").digest(modelBytes).hex()
      if (config.modelSha1?.equals(sha1Hash, ignoreCase = true) == false) {
        throw IllegalArgumentException("Invalid model checksum ${config.modelSha1} != $sha1Hash")
      }
      log.info("Loading model from ${config.creationDate}")

      val modelByteBuf = ByteBuffer.allocateDirect(modelBytes.size).apply {
        put(modelBytes)
        rewind()
      }
      return Predictor(config, Interpreter(modelByteBuf))
    }

    @Suppress("UNUSED")
    fun create(
        modelPath: File = File("/storage/emulated/0/Download"),
        modelName: String = "glucose_model"): Predictor? {
      val modelMetaFile = File(modelPath, "$modelName.json")
      val modelBytesFile = File(modelPath, "$modelName.tflite")

      if (!modelMetaFile.isFile) {
        log.severe("Meta file not found: $modelMetaFile")
        return null
      }
      if (!modelBytesFile.isFile) {
        log.severe("Model file not found: $modelBytesFile")
        return null
      }
      try {
        return Predictor(
            Config.fromJson(modelMetaFile),
            Interpreter(modelBytesFile))
      } catch (e: IOException) {
        log.log(Level.SEVERE, "Failed to load model: $e", e)
      }
      return null
    }
  }

  val isValid: Boolean get() = ModelVerifier(this).runAll()

  private fun predict(inputData: FloatArray): List<Double> {
    val cleanInput = inputData.map { f -> if (isNaN(f)) 0.0F else f }.toFloatArray()
    log.fine("input: ${cleanInput.joinToString { "%.2f".format(it) }}")
    val outputData = Array(1) { FloatArray(config.outputSize) }
     interpreter.run(Array (1) { cleanInput }, outputData)
    return outputData[0].map(Float::toDouble).toList()
  }

  private fun computeGlucose(lastGlucose: Double, slopes: List<Double>): List<Double> {
    var p = lastGlucose
    return slopes.map { s -> (5 * s + p).also { p = it } }
  }

  fun predictGlucose(at: Instant, dp: InputProvider): List<Double> {
    log.info("Predicting glucose at $at")
    val (lastGlucose, input) = DataLoader.getInputVector(dp, at, config)
    val p = predict(input)
    val glucose = if (config.yValues[0].startsWith("gls_")) {
      computeGlucose(lastGlucose, p)
    } else {
      p
    }
    log.info("Output glucose: ${glucose.joinToString()}")
    return glucose
  }

  override fun close() {
    interpreter.close()
  }
}
