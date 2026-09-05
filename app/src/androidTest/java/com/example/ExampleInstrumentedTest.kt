package com.example

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun resetOnboardingFlag() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    runBlocking {
      AppPreferences(context).setOnboardingCompleted(false)
    }
  }

  @Test
  fun mainActivity_startsAndRendersOnboardingEntryPoint() {
    composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_showsOnboardingStepIndicator() {
    composeRule.onNodeWithTag("onboarding_step_label").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_survivesSingleRecreation() {
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_survivesRotationChange() {
    var beforeInstanceId = 0
    var beforeConfigOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED
    composeRule.activityRule.scenario.onActivity { activity ->
      beforeInstanceId = System.identityHashCode(activity)
      beforeConfigOrientation = activity.resources.configuration.orientation
      activity.requestedOrientation = if (beforeConfigOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      var currentInstanceId = beforeInstanceId
      composeRule.activityRule.scenario.onActivity { activity ->
        currentInstanceId = System.identityHashCode(activity)
      }
      currentInstanceId != beforeInstanceId
    }
    var afterInstanceId = 0
    composeRule.activityRule.scenario.onActivity { activity ->
      afterInstanceId = System.identityHashCode(activity)
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    assertNotEquals(beforeInstanceId, afterInstanceId)
    composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun useAppContext() {
    val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }
}
