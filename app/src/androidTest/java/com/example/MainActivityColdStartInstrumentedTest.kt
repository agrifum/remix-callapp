package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityColdStartInstrumentedTest {
  @get:Rule
  val composeRule = createEmptyComposeRule()

  @Test
  fun mainActivity_coldStartSkipsOnboardingWhenCompleted() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    runBlocking {
      AppPreferences(context).setOnboardingCompleted(true)
    }

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      composeRule.onNodeWithText("Połączenia").assertIsDisplayed()
      composeRule.onNodeWithTag("onboarding_welcome").assertDoesNotExist()
      assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }
  }
}
