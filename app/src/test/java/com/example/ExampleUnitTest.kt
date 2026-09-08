package com.example

import com.example.data.RouteStationKmEntity
import com.example.ui.components.formatRoutesToExportText
import com.example.ui.components.parseRoutesFromImportText
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testExportRoutesFormatting() {
    val routes = listOf(
      RouteStationKmEntity(routeName = "京沪线", stationKm = 301.2, nickname = "南京站"),
      RouteStationKmEntity(routeName = "沪宁城际", stationKm = 298.55, nickname = "南京南站"),
      RouteStationKmEntity(routeName = "陇海线", stationKm = 100.0, nickname = "")
    )
    val exported = formatRoutesToExportText(routes)
    val lines = exported.lines()
    assertEquals(3, lines.size)
    assertEquals("南京站:京沪线:301.2", lines[0])
    assertEquals("南京南站:沪宁城际:298.55", lines[1])
    assertEquals("陇海线:陇海线:100.0", lines[2])
  }

  @Test
  fun testImportRoutesParsing() {
    val input = """
      南京站:京沪线:301.200
      常州站：京沪线：165.8km
      无锡站 : 沪宁城际 : 126.4 公里
      
      # 这是一个注释行
      苏州站:京沪线:84.3
    """.trimIndent()

    val parsed = parseRoutesFromImportText(input)
    assertEquals(4, parsed.size)

    assertEquals("南京站", parsed[0].nickname)
    assertEquals("京沪线", parsed[0].routeName)
    assertEquals(301.2, parsed[0].stationKm, 0.001)

    assertEquals("常州站", parsed[1].nickname)
    assertEquals("京沪线", parsed[1].routeName)
    assertEquals(165.8, parsed[1].stationKm, 0.001)

    assertEquals("无锡站", parsed[2].nickname)
    assertEquals("沪宁城际", parsed[2].routeName)
    assertEquals(126.4, parsed[2].stationKm, 0.001)

    assertEquals("苏州站", parsed[3].nickname)
    assertEquals("京沪线", parsed[3].routeName)
    assertEquals(84.3, parsed[3].stationKm, 0.001)
  }
}

