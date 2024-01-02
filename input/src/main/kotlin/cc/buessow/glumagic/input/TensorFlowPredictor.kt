package cc.buessow.glumagic.input

import java.io.Closeable
import java.nio.file.Path

class TensorFlowPredictor constructor(
    val config: Config,
    modelDirectory: Path) : Closeable {
//
//  private val model: Sequential
////  private val graph: Graph
////  private val savedModel: SavedModelBundle
//
//  init {
//    model = Sequential.loadModelConfiguration(File(modelDirectory.toFile(), "model.json"))
//    model.loadWeights(File(modelDirectory.toFile(), "weights.hd5"))
////    org.jetbrains.kotlinx.dl.api.inference.savedmodel.SavedModel.
////    savedModel = SavedModelBundle.load(modelDirectory.toString())
////    val graphDef = GraphDef.parser().parseFrom(modelInput)
////    graph = savedModel.graph()
//  }
//
  override fun close() {
//    model.close()
////    graph.close()
////    savedModel.close()
  }
//
//  fun validate(): Boolean = ModelVerifier(this).runAll()
//
//  private fun performInference(input: FloatArray): FloatArray {
//    model.predict(input)
//    return model.predictSoftly(input)
////    Session(graph).use { session ->
////      TFloat32.tensorOf(NdArrays.vectorOf(*input)).use { inputTensor ->
////          session.runner()
////            .feed("input", inputTensor)
////            .fetch("output")
////            .run()[0]
////      }.use { outputTensor ->
////        val outputLen = outputTensor.shape().asArray()[0] as Int
////        if (outputLen != 12) throw AssertionError("expect 12 floats, got $outputLen")
////        val buffer = outputTensor.asRawTensor().data() as FloatDataBuffer
////        val output = FloatArray(outputLen)
////        buffer.read(output)
////        return output
////      }
////    }
////    return FloatArray(0)
//  }
//
//  fun predictGlucoseSlopes(input: FloatArray): List<Double> {
//    return performInference(input).map(Float::toDouble).toList()
//  }
//
//  private fun computeGlucose(lastGlucose: Double, slopes: List<Double>): List<Double> {
//    var p = lastGlucose
//    return slopes.map { s -> (5 * s + p).also { p = it } }
//  }
//
//  fun predictGlucose(at: Instant, dp: DataProvider): List<Double> {
//    log.info("Predicting glucose at $at")
//    val dataLoader = DataLoader(dp, at, config)
//    val (lastGlucose, input) = dataLoader.getInputVector(at).blockingGet()
//    return computeGlucose(lastGlucose.toDouble(), predictGlucoseSlopes(input)).also {
//      log.info("Output glucose: ${it.joinToString()}")
//    }
//  }
//
//  companion object {
//    private val log = Logger.getLogger(TensorFlowPredictor::class.java.name)
//
////    fun create(
////        modelMetaInput: InputStream,
////        modelBytesInput: InputStream): TensorFlowPredictor {
////        return TensorFlowPredictor(Config.fromJson(modelMetaInput), modelBytesInput)
////    }
////
////    fun create(
////        modelPath: File,
////        modelName: String = "glucose_model"
////    ): TensorFlowPredictor? {
////      val modelMetaFile = File(modelPath, "$modelName.json")
////      val modelBytesFile = File(modelPath, "$modelName.tflite")
////
////      if (!modelMetaFile.isFile) {
////        log.severe("Meta file not found: $modelMetaFile")
////        return null
////      }
////      if (!modelBytesFile.isFile) {
////        log.severe("Model file not found: $modelBytesFile")
////        return null
////      }
////      try {
////        modelBytesFile.inputStream().use { modelInput ->
////          return TensorFlowPredictor(Config.fromJson(modelMetaFile), modelInput)
////        }
////      } catch (e: IOException) {
////        log.log(Level.SEVERE, "Failed to load model: $e", e)
////      }
////      return null
////    }
//  }
}

