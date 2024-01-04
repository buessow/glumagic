package cc.buessow.glumagic

import org.junit.jupiter.api.Test

internal class MainTest {
  @Test
  fun main() {
    Main.main(arrayOf<String>("-v", "-u", "x", "-p", "x", "-db", "x", "-s", "x"))
  }
}