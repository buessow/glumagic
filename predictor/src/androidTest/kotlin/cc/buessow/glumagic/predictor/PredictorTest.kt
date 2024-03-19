package cc.buessow.glumagic.predictor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PredictorTest {

  @Test
  fun loadAndVerifyModel() {
    val cl = this::class.java.classLoader!!
    Predictor.create(
        ::TensorflowInterpreter,
        cl.getResourceAsStream("glucose_model.json")!!,
        cl.getResourceAsStream("glucose_model.tflite")!!).use { p ->
      assertNotNull(p.config.zoneId)
      assertTrue(p.isValid)
    }
  }
}
