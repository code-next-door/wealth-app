package io.github.codenextdoor.wealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.codenextdoor.wealth.ui.WealthApp
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WealthTheme {
                WealthApp()
            }
        }
    }
}
