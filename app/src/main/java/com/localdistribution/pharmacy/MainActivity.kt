package com.localdistribution.pharmacy

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.localdistribution.pharmacy.ui.AppRoot
import com.localdistribution.pharmacy.ui.AppState
import com.localdistribution.pharmacy.ui.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Holds the single AppState for the whole process, so rotation never reopens
 * the database, and startup is not cancelled if the activity is recreated.
 */
class App : Application() {
    val scope = MainScope()
    val state: AppState by lazy { AppState(this) }
}

class MainActivity : ComponentActivity() {
    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.scope.launch { app.state.startOnce() }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(app.state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { app.state.dailyBackupIfNeeded() }
    }
}
