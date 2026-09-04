package com.example.system.sms

/**
 * Keeps the production binding centralized while allowing deterministic test
 * fixtures to replace the reader without reintroducing application casting.
 */
object SystemSmsReaderProvider {
    @Volatile
    var override: SystemSmsReader? = null
}
