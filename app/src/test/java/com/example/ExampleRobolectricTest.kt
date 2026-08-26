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
    assertTrue(speechText.contains("高一零二"))
  }
}
