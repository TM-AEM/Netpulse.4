package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.ui.components.MetricCard
import com.example.ui.theme.NetPulseTheme
import com.example.ui.theme.WifiAccent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val deterministicUsage = NetworkUsage(
      downloadBytes = 1024L * 1024L * 750L,   // 750 MB download
      uploadBytes = 1024L * 1024L * 250L,     // 250 MB upload
      startTime = 1700000000000L,
      endTime = 1700086400000L,
      networkType = NetworkType.WIFI
    )

    composeTestRule.setContent {
      NetPulseTheme(dynamicColor = false) {
        MetricCard(
          title = "واي فاي (Wi-Fi)",
          usage = deterministicUsage,
          icon = Icons.Default.Wifi,
          accentColor = WifiAccent,
          modifier = Modifier.padding(16.dp),
          language = AppLanguage.AR,
          forcedUnit = AppDataUnit.MB,
          secondaryInfo = "75%",
          testTag = "wifi_metric_card"
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
