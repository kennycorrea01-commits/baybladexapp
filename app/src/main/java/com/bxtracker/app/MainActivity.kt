package com.bxtracker.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bxtracker.app.ui.BxApp
import com.bxtracker.app.ui.theme.BxTheme

/** AppCompatActivity (not ComponentActivity) so AppCompatDelegate can switch the app language. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            BxTheme(settings.theme) {
                BxApp(vm)
            }
        }
    }
}
