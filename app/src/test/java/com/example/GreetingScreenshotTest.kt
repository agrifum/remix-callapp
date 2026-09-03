package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.CallRowItem
import com.example.ui.model.CallRowDirection
import com.example.ui.model.CallRowUiModel
import com.example.ui.theme.MyApplicationTheme
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
    composeTestRule.setContent {
      MyApplicationTheme {
        CallRowItem(
          call = CallRowUiModel(
            id = "test_1",
            phoneKey = "48123456789",
            displayNumber = "+48 123 456 789",
            contactOrClientDisplayName = "Jan Kowalski",
            timestamp = System.currentTimeMillis(),
            direction = CallRowDirection.INCOMING,
            durationSeconds = 120,
            isClient = true,
            hasNotes = true,
            clientId = "c1"
          ),
          onClick = {},
          onDialClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
