package com.klvw.wallpaper.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class WearMainActivity : ComponentActivity() {

    private lateinit var viewModel: WearViewModel

    private val requestBtPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.retry()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, WearViewModelFactory(this))[WearViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestBtPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            WearScreen(vm = viewModel)
        }
    }
}
