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
    val mismatch = ArrayApproxCompare.getMismatch(
        DataLoader.getInputVector(dataProvider, testData.at, predictor.config).second.toList(),
        testData.inputVector.toList(), 1e-3
    ) ?: return true
    log.severe("Mismatch '${testData.name}'")
    log.severe(mismatch)
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
    val inputProvider = InputProviderForTestInput(testData)
    val glucose = predictor.predictGlucose(testData.at, inputProvider)
    val mismatch = ArrayApproxCompare.getMismatch(
        glucose.map(Double::toFloat),
        testData.outputGlucose, eps = 1e-4) ?: return true
    log.severe("Mismatch '${testData.name}':\n$mismatch")
    return false
  }

  fun runAll(): Boolean {
    return predictor.config.testData.all { runInput(it) }
        && predictor.config.testData.all { runSlopes(it) }
        && predictor.config.testData.all { runGlucose(it) }
  }
}
