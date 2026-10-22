package com.deutschdreamers.mensis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/**
 * View models a test builds by hand have no owner, so nothing cancels their scopes. Their
 * `WhileSubscribed` flows keep querying Room for a few seconds after the last collector, and a query
 * still running when the test closes the database crashes the run ("connection pool has been closed").
 * [track] each view model and call [cancelAndJoin] before closing the database.
 */
class TestViewModels {
    private val viewModels = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    /** Cancels every tracked scope and waits until its coroutines, including a running query, have finished. */
    suspend fun cancelAndJoin() {
        viewModels.forEach {
            it.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }
        viewModels.clear()
    }
}
