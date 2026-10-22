package com.deutschdreamers.mensis

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.deutschdreamers.mensis.ui.navigation.MensisNavDisplay
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Privacy rule 5: FLAG_SECURE on by default. Becomes a user setting in build step 3.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            MensisTheme {
                MensisNavDisplay()
            }
        }
    }
}
