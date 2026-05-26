package com.klvw.wallpaper.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KLVWPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            KLVWTheme {
                KLVWPopupScreen(onDismiss = { finish() })
            }
        }
    }
}
