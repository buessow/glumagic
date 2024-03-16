package cc.buessow.glumagic.mongodb

import cc.buessow.glumagic.input.DateValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MongoInputProviderTest {
  @Test
  fun deduplicateEmpty() {
    assertEquals(
        emptyList<DateValue>(),
        MongoInputProvider.deduplicate(emptyList()))
  }

  @Test
  fun deduplicateSingle() {
    val dv = DateValue(Instant.now(), 18)
    assertEquals(
        listOf(dv),
        MongoInputProvider.deduplicate(listOf(dv)))
  }

  @Test
  fun deduplicateMore() {
    val dv0 = DateValue(Instant.now(), 18)
    val dv1 = DateValue(Instant.now(), 19)
    assertEquals(
        listOf(dv0, dv1),
        MongoInputProvider.deduplicate(listOf(dv0, dv1)))
    assertEquals(
        listOf(dv0, dv1),
        MongoInputProvider.deduplicate(listOf(dv0, dv0, dv1)))
    assertEquals(
        listOf(dv0, dv1),
        MongoInputProvider.deduplicate(listOf(dv0, dv0, dv0, dv1, dv1)))
  }
}
