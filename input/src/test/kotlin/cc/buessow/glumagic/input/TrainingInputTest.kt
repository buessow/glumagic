package cc.buessow.glumagic.input

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.lang.AssertionError
import java.time.Duration
import java.time.Instant

class TrainingInputTest {

  private fun assertJson(expected: String, ti: TrainingInput) {
    val json = StringWriter().use {
      ti.writeJson(it)
      it.toString()
    }
    assertEquals(expected, json)
  }

  private fun assertCsv(expected: String, ti: TrainingInput) {
    val csv = StringWriter().use {
      ti.writeCsv(it)
      it.toString()
    }
    assertEquals(expected, csv)
  }

  @Test
  fun sizeMismatch() {
    try {
      TrainingInput(
          emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
          emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
          listOf(1.0))
      fail()
    } catch (e: AssertionError) {
      assertEquals("size mismatch for insulinAction: 0 != 1", e.message)
    }
  }

  @Test
  fun empty() {
    val ti = TrainingInput(
        emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    assertEquals(
        "TrainingInput(" +
            "date=[], hour=[], glucose=[], glucoseSlope1=[], glucoseSlope2=[], " +
            "heartRate=[], hrLong1=[], hrLong2=[], carbs=[], carbAction=[], " +
            "bolus=[], basal=[], insulinAction=[])",
        ti.toString())
    assertCsv(
        "date,hour,glucose,glucoseSlope1,glucoseSlope2,heartRate,hrLong1,hrLong2," +
            "carbs,carbAction,bolus,basal,insulinAction\r\n",
        ti)
    assertJson(
        """{"date":[],"hour":[],"glucose":[],"glucoseSlope1":[],"glucoseSlope2":[],""" +
          """"heartRate":[],"hrLong1":[],"hrLong2":[],"carbs":[],"carbAction":[],""" +
          """"bolus":[],"basal":[],"insulinAction":[]}""",
        ti)
  }

  @Test
  fun single() {
    val ti = TrainingInput(
        listOf(Instant.parse("2023-01-10T10:11:12Z")),
        listOf(10),
        listOf(2.0),
        listOf(3.0),
        listOf(4.0),
        listOf(5.0),
        listOf(6.0),
        listOf(7.0),
        listOf(8.0),
        listOf(9.0),
        listOf(10.0),
        listOf(11.0),
        listOf(12.0))
    assertEquals(
        "TrainingInput(" +
            "date=[2023-01-10T10:11:12Z], hour=[10], glucose=[2.0], glucoseSlope1=[3.0], " +
            "glucoseSlope2=[4.0], heartRate=[5.0], hrLong1=[6.0], hrLong2=[7.0], carbs=[8.0], " +
            "carbAction=[9.0], bolus=[10.0], basal=[11.0], insulinAction=[12.0])",
            ti.toString())
    assertCsv(
        "date,hour,glucose,glucoseSlope1,glucoseSlope2,heartRate,hrLong1,hrLong2,carbs,carbAction,bolus,basal,insulinAction\r\n" +
        "2023-01-10T10:11:12Z,10,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,12.0\r\n",
        ti)
    assertJson(
        """{"date":["2023-01-10T10:11:12Z"],"hour":[10],"glucose":[2.0],""" +
          """"glucoseSlope1":[3.0],"glucoseSlope2":[4.0],"heartRate":[5.0],"hrLong1":[6.0],""" +
            """"hrLong2":[7.0],"carbs":[8.0],"carbAction":[9.0],""" +
            """"bolus":[10.0],"basal":[11.0],"insulinAction":[12.0]}""",
        ti)
  }

  @Test
  fun removePeriod() {
    val n = 10
    val freq = Duration.ofMinutes(1)
    val s = Instant.now()
    val ti = TrainingInput(
        (s ..< s + freq.multipliedBy(n.toLong()) step freq).toList(),
        (0..< n).toList(),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble),
        (0..< n).toList().map(Int::toDouble))

    val ti1 = ti.removePeriod(s + freq, freq.multipliedBy(2))
    assertEquals(listOf(0, 3, 4, 5, 6, 7, 8, 9), ti1.hour)

    val ti2 = ti.removePeriod(s, freq.multipliedBy(2))
    assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9), ti2.hour)

    val ti5 = ti.removePeriod(s - freq, freq.multipliedBy(2))
    assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), ti5.hour)

    val ti3 = ti.removePeriod(s + freq.multipliedBy(n-2L), freq.multipliedBy(2))
    assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), ti3.hour)

    val ti4 = ti.removePeriod(
        s + freq.multipliedBy(n-2L),
        freq.multipliedBy(3))
    assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), ti4.hour)

    val ti6 = ti.removePeriod(
        s + Duration.ofSeconds(30),
        freq.multipliedBy(3))
    assertEquals(listOf(0, 5, 6, 7, 8, 9), ti6.hour)
  }
}
