package cc.buessow.glumagic.input

import kotlinx.coroutines.Deferred
import java.time.Duration
import java.time.Instant

internal fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

internal operator fun Duration.div(d: Duration) = (seconds / d.seconds).toInt()

class TimeInstantProgression(
    override val start: Instant,
    override val endExclusive: Instant,
    private val step: Duration
) : Iterable<Instant>, OpenEndRange<Instant> {
  override fun iterator(): Iterator<Instant> =
    object : Iterator<Instant> {
      private var current = start
      override fun hasNext() = current < endExclusive
      override fun next() = current.apply { current += step }
    }

  infix fun step(step: Duration) =
    TimeInstantProgression(start, endExclusive, step)
}

operator fun Instant.rangeUntil(other: Instant) =
  TimeInstantProgression(this, other, Duration.ofMinutes(1))

data class DeferredResult2<T1, T2>(val value1: T1, val value2: T2)

suspend fun <T1, T2> await2(t1: Deferred<T1>, t2: Deferred<T2>) =
  DeferredResult2(t1.await(), t2.await())

data class DeferredResult5<T1, T2, T3, T4, T5>(
    val value1: T1, val value2: T2, val value3: T3, val value4: T4, val value5: T5)
 suspend fun <T1, T2, T3, T4, T5> await5(
    t1: Deferred<T1>, t2: Deferred<T2>, t3: Deferred<T3>, t4: Deferred<T4>, t5: Deferred<T5>) =
  DeferredResult5(t1.await(), t2.await(), t3.await(), t4.await(), t5.await())
