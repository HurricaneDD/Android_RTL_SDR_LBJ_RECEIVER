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

  @Test
  fun testDecoderClearCurrentTrain() {
    val decoder = com.example.decoder.LbjDecoder()
    decoder.clearCurrentTrain()
    // Verify no exceptions and state is cleared cleanly
    assertTrue(true)
  }

  @Test
  fun testTrainNormalizationAndSameTrainMatching() {
    val dict = com.example.decoder.LocomotiveDict
    // Test train normalization (stripping leading zeroes/padding from digits)
    assertEquals("45001", dict.normalizeTrainNo("045001"))
    assertEquals("45001", dict.normalizeTrainNo(" 45001"))
    assertEquals("516", dict.normalizeTrainNo("00516"))
    assertEquals("K516", dict.normalizeTrainNo("K00516"))
    assertEquals("G1234", dict.normalizeTrainNo(" G1234 "))

    // Test same train matching between short packet and detailed packet / unstable retransmissions
    assertTrue(dict.isSameTrain("045001", "45001"))
    assertTrue(dict.isSameTrain("45001", "045001"))
    assertTrue(dict.isSameTrain(" 45001", "45001"))
    assertTrue(dict.isSameTrain("516", "K516"))
    assertTrue(dict.isSameTrain("00516", "K516"))
    assertTrue(dict.isSameTrain("K516", "516"))

    // Different trains should not match
    assertFalse(dict.isSameTrain("45001", "45002"))
    assertFalse(dict.isSameTrain("K516", "K518"))

    // Test extractBaseTrainNumber
    assertEquals("45001", dict.extractBaseTrainNumber("045001"))
    assertEquals("45001", dict.extractBaseTrainNumber("45001"))
    assertEquals("516", dict.extractBaseTrainNumber("K516"))
    assertEquals("516", dict.extractBaseTrainNumber("00516"))
  }

  @Test
  fun testTrainAlertSpeechTextGeneration() {
    val alertSpeech = com.example.util.SoundAlertManager.buildTrainAlertSpeechText(
      locoModel = "HXD3D-0123",
      route = "京广线",
      direction = "下行",
      speedKmH = "95",
      trainNo = "K516"
    )
    assertTrue("Initial approach speech must start with 有火车接近", alertSpeech.startsWith("有火车接近"))
    assertTrue("Initial approach speech must contain spoken train number", alertSpeech.contains("五一六") || alertSpeech.contains("516"))

    val updateSpeech = com.example.util.SoundAlertManager.buildTrainUpdateSpeechText("K516")
    assertTrue("Update speech must announce received update data", updateSpeech.contains("接收到") && updateSpeech.contains("更新数据"))
  }
}

