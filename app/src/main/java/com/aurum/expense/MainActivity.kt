package com.aurum.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aurum.expense.ui.AurumRoot
import com.aurum.expense.ui.theme.AurumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AurumTheme {
                AurumRoot()
            }
        }
    }
}
