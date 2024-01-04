package cc.buessow.glumagic.predictor

import android.annotation.TargetApi
import android.os.Build
import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.input.DataProvider
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Float.isNaN
import java.nio.ByteBuffer
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

class Predictor private constructor(
    val config: Config,
    private val interpreter: Interpreter) : Closeable {

  companion object {
    private val log = Logger.getLogger(javaClass.name)

    private val interpreterOptions = Interpreter.Options().apply {
      numThreads = 1
      useNNAPI = false
    }

    fun create(
        modelMetaInput: InputStream,
        modelBytesInput: InputStream): Predictor {

      val modelBytes = modelBytesInput.readBytes()
      val modelByteBuf = ByteBuffer.allocateDirect(modelBytes.size).apply {
        put(modelBytes)
        rewind()
      }
      return Predictor(Config.fromJson(modelMetaInput), Interpreter(modelByteBuf))
    }

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

  val isValid: Boolean = ModelVerifier(this).runAll()

  fun predictGlucoseSlopes(inputData: FloatArray): List<Double> {
    val cleanInput = inputData.map { f -> if (isNaN(f)) 0.0F else f }.toFloatArray()
    log.fine("input: ${cleanInput.joinToString { "%.2f".format(it) }}")
//    val inputTensor = TensorBuffer.createFixedSize(
//        intArrayOf(1, inputData.size), DataType.FLOAT32)
//    inputTensor.loadArray(cleanInput)
    val outputData = Array(1) { FloatArray(config.outputSize) }
     interpreter.run(Array (1) { cleanInput }, outputData)
//    interpreter.run(inputTensor.buffer, outputData)
    return outputData[0].map(Float::toDouble).toList()
  }

  @TargetApi(Build.VERSION_CODES.O)
  fun predictGlucoseSlopes(at: Instant, dp: DataProvider): List<Double> {
    val dataLoader = DataLoader(dp, at - config.trainingPeriod, config)
    val (_, input) = dataLoader.getInputVector(at).blockingGet()
    return predictGlucoseSlopes(input)
  }
  private fun computeGlucose(lastGlucose: Double, slopes: List<Double>): List<Double> {
    var p = lastGlucose
    return slopes.map { s -> (5 * s + p).also { p = it } }
  }

  fun predictGlucose(at: Instant, dp: DataProvider): List<Double> {
    log.info("Predicting glucose at $at")
    val dataLoader = DataLoader(dp, at, config)
    val (lastGlucose, input) = dataLoader.getInputVector(at).blockingGet()
    return computeGlucose(lastGlucose.toDouble(), predictGlucoseSlopes(input)).also {
      log.info("Output glucose: ${it.joinToString()}")
    }
  }

  override fun close() {
    interpreter.close()
  }
}
