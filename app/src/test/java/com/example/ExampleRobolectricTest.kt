package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.SoundAlertManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SDR-LBJ", appName)
  }

  @Test
  fun `test railway speech text formatting`() {
    val speechText = SoundAlertManager.buildTrainAlertSpeechText(
      locoModel = "HXD3D-5033",
      route = "京沪高铁",
      direction = "下行",
      speedKmH = "310",
      trainNo = "G102"
    )
    assertTrue(speechText.contains("和谐电3D"))
    assertTrue(speechText.contains("五零三三"))
    assertTrue(speechText.contains("速度：310"))
    assertTrue(speechText.contains("高一零二"))
  }

  @Test
  fun `test railway speech unknown speed and 0 prefixed train and FXD3-J`() {
    // 1. Unknown speed
    val speechUnknownSpeed = SoundAlertManager.buildTrainAlertSpeechText(
      locoModel = "DF4D-1000",
      route = "京沪线",
      direction = "下行",
      speedKmH = "---",
      trainNo = "K8401"
    )
    assertTrue(speechUnknownSpeed.contains("速度未知"))

    // 2. 0-prefixed deadhead / empty coaching stock (0T151, 0K8396, 0Z15)
    val speech0T = SoundAlertManager.formatTrainNoForSpeech("0T151")
    assertEquals("零特一五一", speech0T)

    val speech0K = SoundAlertManager.formatTrainNoForSpeech("0K8396")
    assertEquals("零快八三九六", speech0K)

    val speech0Z = SoundAlertManager.formatTrainNoForSpeech("0Z15")
    assertEquals("零直一五", speech0Z)

    // 3. FXD3-J locomotive
    val speechFxd3j = SoundAlertManager.formatLocoForSpeech("FXD3-J-0001")
    assertTrue(speechFxd3j.startsWith("复兴电3集"))
    assertTrue(speechFxd3j.contains("零零零一"))

    val speechFxd1j = SoundAlertManager.formatLocoForSpeech("FXD1-J-0002")
    assertTrue(speechFxd1j.startsWith("复兴电1集"))
    assertTrue(speechFxd1j.contains("零零零二"))
  }
}
