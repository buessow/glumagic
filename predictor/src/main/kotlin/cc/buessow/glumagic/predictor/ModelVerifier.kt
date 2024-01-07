package cc.buessow.glumagic.predictor

import cc.buessow.glumagic.input.ArrayApproxCompare
import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.input.InputProviderForTestInput
import java.util.logging.Logger

class ModelVerifier(private val predictor: Predictor) {
  private val log = Logger.getLogger(javaClass.name)

  private fun runInput(testData: Config.TestData): Boolean {
    log.info("input ${testData.name}")
    val dataProvider = InputProviderForTestInput(testData)
    val dataLoader = DataLoader(dataProvider, testData.at, predictor.config)
    val mismatch = ArrayApproxCompare.getMismatch(
        dataLoader.getInputVector(testData.at).blockingGet().second.toList(),
        testData.inputVector.toList(), 1e-4
    ) ?: return true
    log.severe("Mismatch '${testData.name}': $mismatch")
    return false
  }

  private fun runSlopes(testData: Config.TestData): Boolean {
    log.info("inference ${testData.name}")
    val slopes = predictor.predictGlucoseSlopes(testData.inputVector)
    val mismatch = ArrayApproxCompare.getMismatch(
        slopes.toList().map(Double::toFloat),
        testData.outputSlopes, eps = 0.1) ?: return true
    log.severe("Mismatch '${testData.name}': $mismatch")
    return false
  }

  private fun runGlucose(testData: Config.TestData): Boolean {
    log.info("glucose ${testData.name}")
    val dataProvider = InputProviderForTestInput(testData)
    val glucose = predictor.predictGlucose(testData.at, dataProvider)
    val mismatch = ArrayApproxCompare.getMismatch(
        glucose.map(Double::toFloat),
        testData.outputGlucose, eps = 1e-4) ?: return true
    log.severe("Mismatch '${testData.name}': $mismatch")
    return false
  }

  fun runAll(): Boolean {
    return predictor.config.testData.all { runInput(it) }
        && predictor.config.testData.all { runSlopes(it) }
        && predictor.config.testData.all { runGlucose(it) }
  }
}
