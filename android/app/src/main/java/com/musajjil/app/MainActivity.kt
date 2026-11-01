package com.musajjil.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.musajjil.app.ui.RegisterApp
import com.musajjil.app.ui.RegisterViewModel
import com.musajjil.app.ui.theme.MusajjilTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: RegisterViewModel by viewModels {
        val app = application as MusajjilApplication
        RegisterViewModel.Factory(app.repository, app.backups)
    }

    override fun attachBaseContext(newBase: Context) {
        // Localizes native date pickers too; copy the system configuration to keep its night mode.
        val config = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList(Locale("ar")))
            setLayoutDirection(Locale("ar"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MusajjilTheme { RegisterApp(viewModel) } }
    }
    override fun onResume() {
        super.onResume()
        (application as MusajjilApplication).backups.schedule()
    }
}
