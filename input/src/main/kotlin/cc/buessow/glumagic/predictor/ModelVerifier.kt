package cc.buessow.glumagic.predictor

import cc.buessow.glumagic.input.ArrayApproxCompare
import cc.buessow.glumagic.input.Config
import cc.buessow.glumagic.input.DataLoader
import cc.buessow.glumagic.input.InputProviderForTestInput
import java.util.logging.Logger

class ModelVerifier(private val predictor: Predictor) {
  private val log = Logger.getLogger(javaClass.name)
  private val config = predictor.config.copy(smoothingFilter = "none")

  private fun runInput(testData: Config.TestData): Boolean {
    log.info("input ${testData.name}")
    val dataProvider = InputProviderForTestInput(
        testData.at - predictor.config.trainingPeriod, testData)
    val mismatch = ArrayApproxCompare.getMismatch(
        DataLoader.getInputVector(dataProvider, testData.at, config).second.toList(),
        testData.inputVector.toList(), 1e-3
    ) ?: return true
    log.severe("Mismatch input '${testData.name}'")
    log.severe(mismatch)
    return false
  }

  private fun runGlucose(testData: Config.TestData): Boolean {
    log.info("glucose ${testData.name}")
    val inputProvider = InputProviderForTestInput(
        testData.at - predictor.config.trainingPeriod, testData)
    val glucose = predictor.predictGlucose(testData.at, inputProvider, config)
    val mismatch = ArrayApproxCompare.getMismatch(
        glucose.map(Double::toFloat),
        testData.outputGlucose, eps = 1e-3) ?: return true
    log.severe("Mismatch result '${testData.name}':\n$mismatch")
    return false
  }

  fun runAll(): Boolean {
    return predictor.config.testData.all { runInput(it) }
        && predictor.config.testData.all { runGlucose(it) }
  }
}
