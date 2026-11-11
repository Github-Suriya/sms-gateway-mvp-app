package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.MainViewModel
import com.example.ui.navigation.RelayNavHost
import com.example.ui.theme.RelayGatewayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelayGatewayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RelayNavHost(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }
}
