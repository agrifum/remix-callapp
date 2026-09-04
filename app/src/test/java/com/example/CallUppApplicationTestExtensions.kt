package com.example

import com.example.core.di.AppContainer
import java.util.WeakHashMap

private val testContainers = WeakHashMap<CallUppApplication, AppContainer>()

/**
 * Test-only compatibility fixture. Production Application has no container property.
 */
val CallUppApplication.container: AppContainer
    get() = synchronized(testContainers) {
        testContainers.getOrPut(this) { AppContainer(this) }
    }
