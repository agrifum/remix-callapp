package com.example

import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PrelaunchOnboardingStateRule(
  private val onboardingCompletedBeforeLaunch: Boolean
) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
          AppPreferences(context).setOnboardingCompleted(onboardingCompletedBeforeLaunch)
        }
        try {
          base.evaluate()
        } finally {
          runBlocking {
            AppPreferences(context).setOnboardingCompleted(false)
          }
        }
      }
    }
  }
}
