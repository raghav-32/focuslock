package com.focuslock.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.AppContainer
import com.focuslock.app.container
import kotlinx.coroutines.delay

@Composable
inline fun <reified T : ViewModel> containerViewModel(crossinline make: (AppContainer) -> T): T {
    val c = LocalContext.current.container
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = make(c) as V
        },
    )
}

/** Wall-clock "now" that re-emits every [intervalMs]; used for countdowns. */
@Composable
fun rememberNow(intervalMs: Long = 1_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), intervalMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
