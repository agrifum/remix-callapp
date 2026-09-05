package com.example

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun mainActivity_startsAndRendersOnboardingEntryPoint() {
    composeRule.onNodeWithText("Witaj w CallUpp").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_launchesWithoutOptionalPermissionsGranted() {
    composeRule.onNodeWithText("Krok 1 z 6: ROLE_CALL_SCREENING").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_survivesActivityRecreation() {
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    composeRule.waitForIdle()
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Witaj w CallUpp").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun useAppContext() {
    val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }
}
