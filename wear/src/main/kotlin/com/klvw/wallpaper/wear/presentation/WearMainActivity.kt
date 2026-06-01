package com.klvw.wallpaper.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider

class WearMainActivity : ComponentActivity() {

    private lateinit var viewModel: WearViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, WearViewModelFactory(this))[WearViewModel::class.java]
        setContent {
            WearScreen(vm = viewModel)
        }
    }
}
