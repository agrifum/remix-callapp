package com.example

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.rules.RuleChain

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  private val prelaunchOnboardingStateRule = PrelaunchOnboardingStateRule(onboardingCompletedBeforeLaunch = false)
  private val composeRule = createAndroidComposeRule<MainActivity>()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(prelaunchOnboardingStateRule).around(composeRule)

  @Test
  fun mainActivity_startsAndRendersOnboardingEntryPoint() {
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("onboarding_welcome").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_showsOnboardingStepIndicator() {
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("onboarding_step_label").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("onboarding_step_label").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_survivesSingleRecreation() {
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("onboarding_welcome").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }

  @Test
  fun mainActivity_survivesRotationChange() {
    var beforeInstanceId = 0
    var beforeConfigOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED
    try {
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
      }
      assertNotEquals(beforeInstanceId, afterInstanceId)
      composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithTag("onboarding_welcome").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
      assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
    } finally {
      composeRule.activityRule.scenario.onActivity { activity ->
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
    }
  }

  @Test
  fun useAppContext() {
    val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }
}
