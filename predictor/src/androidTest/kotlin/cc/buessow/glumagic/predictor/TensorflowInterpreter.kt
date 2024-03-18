package cc.buessow.glumagic.predictor

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

internal class TensorflowInterpreter(modelBytes: ByteBuffer): ModelInterpreter {
  private val interpreter = Interpreter(modelBytes)
  override fun run(input: Array<FloatArray>, output: Array<FloatArray>) {
    interpreter.run(input, output)
  }
  override fun close() { interpreter.close() }
}
