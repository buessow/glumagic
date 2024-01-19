package cc.buessow.glumagic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class MainTest {
  @Test
  fun main() {
    Main.main(arrayOf<String>("version"))
  }

  @Test
  fun date() {
    assertEquals(
        Instant.parse("2024-01-01T10:10:11Z"),
        Main.ArgTypeDateTime.convert("2024-01-01T10:10:11Z", "test"))

    assertEquals(
        Instant.parse("2024-01-01T10:10:11Z"),
        Main.ArgTypeDateTime.convert("2024-01-01T10:10:11", "test"))

    assertEquals(
        Instant.parse("2024-01-01T09:10:11Z"),
        Main.ArgTypeDateTime.convert("2024-01-01T10:10:11+01:00", "test"))

    assertEquals(
        Instant.parse("2024-01-01T09:10:11Z"),
        Main.ArgTypeDateTime.convert("2024-01-01T10:10:11Europe/Zurich", "test"))

    assertEquals(
        Instant.parse("2024-01-01T00:00:00Z"),
        Main.ArgTypeDateTime.convert("2024-01-01", "test"))
  }
}
