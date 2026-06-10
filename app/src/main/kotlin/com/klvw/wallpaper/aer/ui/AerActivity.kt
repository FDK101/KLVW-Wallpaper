package com.klvw.wallpaper.aer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AerActivity : ComponentActivity() {

    private val vm: AerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KLVWTheme {
                AerScreen(vm = vm, onFinish = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkMountState()
        vm.refresh()
    }
}
