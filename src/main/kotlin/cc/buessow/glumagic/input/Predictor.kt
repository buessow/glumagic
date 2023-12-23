package cc.buessow.glumagic.input

class Predictor private constructor(
    val config: Config
) {
  /*

      companion object {
          private val log = LoggerFactory.getLogger(javaClass)
          private val interpreterOptions = Interpreter.Options().apply {
              numThreads = 1
              useNNAPI = false
          }

          fun create(
              modelMetaInput: InputStream,
              modelBytesInput: InputStream): Predictor? {

              val modelBytes = modelBytesInput.readBytes()
              val modelByteBuf = ByteBuffer.allocateDirect(modelBytes.size).apply {
                  put(modelBytes)
                  rewind()
              }
              return Predictor(
                  Config.fromJson(modelMetaInput), Interpreter(modelByteBuf))
          }

          fun create(
              modelPath: File = File("/storage/emulated/0/Download"),
              modelName: String = "glucose_model"
          ): Predictor? {
              val modelMetaFile = File(modelPath, "$modelName.json")
              val modelBytesFile = File(modelPath, "$modelName.tflite")

              if (!modelMetaFile.isFile) {
                  log.error("Meta file not found: $modelMetaFile")
                  return null
              }
              if (!modelBytesFile.isFile) {
                  log.error("Model file not found: $modelBytesFile")
                  return null
              }
              try {
                  return Predictor(
                      Config.fromJson(modelMetaFile),
                      Interpreter(modelBytesFile))
              } catch (e: IOException) {
                  log.error("Failed to load model: $e", e)
              }
              return null
          }
      }

      val isValid: Boolean = ModelVerifier(aapsLogger, this).runAll()

      fun predictGlucoseSlopes(inputData: FloatArray): List<Double> {
          val cleanInput = inputData.map { f -> if (isNaN(f)) 0.0F else f }.toFloatArray()
          log.debug("input: ${cleanInput.joinToString { "%.2f".format(it) }}")
          val inputTensor = TensorBuffer.createFixedSize(
              intArrayOf(1, inputData.size), DataType.FLOAT32)
          inputTensor.loadArray(cleanInput)
          val outputData = Array(1) { FloatArray(config.outputSize) }
          // interpreter.run(Array (1) { cleanInput }, outputData)
          interpreter.run(inputTensor.buffer, outputData)
          return outputData[0].map(Float::toDouble).toList()
      }

      fun predictGlucoseSlopes(at: Instant, dp: DataProvider): List<Double> {
          val dataLoader = DataLoader(aapsLogger, dp, at - config.trainingPeriod, config)
          val (_, input) = dataLoader.getInputVector(at).blockingGet()
          return predictGlucoseSlopes(input)
      }
      private fun computeGlucose(lastGlucose: Double, slopes: List<Double>): List<Double> {
          var p = lastGlucose
          return slopes.map { s -> (5 * s + p).also { p = it } }
      }

      fun predictGlucose(at: Instant, dp: DataProvider): List<Double> {
          aapsLogger.info(LTag.ML_PRED, "Predicting glucose at $at")
          val dataLoader = DataLoader(aapsLogger, dp, at, config)
          val (lastGlucose, input) = dataLoader.getInputVector(at).blockingGet()
          return computeGlucose(lastGlucose.toDouble(), predictGlucoseSlopes(input)).also {
              aapsLogger.info(LTag.ML_PRED, "Output glucose: ${it.joinToString()}")
          }
      }

      override fun close() {
          interpreter.close()
      }
  */
}

