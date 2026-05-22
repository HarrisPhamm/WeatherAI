package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.WeatherScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WeatherViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: WeatherViewModel = viewModel()
      val themeSetting by viewModel.themeSetting.collectAsState()
      val isDarkTheme = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = isDarkTheme) {
        WeatherScreen(viewModel = viewModel)
      }
    }
  }
}

