package com.deutschdreamers.mensis.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** The few operations screens need on the back stack. */
class Navigator(
    private val backStack: NavBackStack<NavKey>,
) {
    fun push(key: MensisNavKey) {
        backStack.add(key)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    /** Onboarding → Main: the first-run flow must not be reachable with back. */
    fun replaceAll(key: MensisNavKey) {
        backStack.clear()
        backStack.add(key)
    }
}
