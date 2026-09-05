package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityColdStartInstrumentedTest {
  private val prelaunchOnboardingStateRule = PrelaunchOnboardingStateRule(onboardingCompletedBeforeLaunch = true)
  private val composeRule = createAndroidComposeRule<MainActivity>()

  private fun hasSingleNodeWithText(text: String): Boolean =
    runCatching {
      composeRule.onAllNodesWithText(text).assertCountEquals(1)
      true
    }.getOrElse { false }

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(prelaunchOnboardingStateRule).around(composeRule)

  @Test
  fun mainActivity_coldStartSkipsOnboardingWhenCompleted() {
    composeRule.waitUntil(timeoutMillis = 5_000) {
      hasSingleNodeWithText("Połączenia")
    }
    composeRule.onNodeWithText("Połączenia").assertIsDisplayed()
    composeRule.onAllNodesWithTag("onboarding_welcome").assertCountEquals(0)
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
  }
}
