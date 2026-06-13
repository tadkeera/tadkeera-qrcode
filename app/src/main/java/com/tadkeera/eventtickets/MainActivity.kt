package com.tadkeera.eventtickets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tadkeera.eventtickets.ui.screens.EventListScreen
import com.tadkeera.eventtickets.ui.theme.TadkeeraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TadkeeraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TadkeeraApp()
                }
            }
        }
    }
}

@Composable
fun TadkeeraApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "event_list") {
        composable("event_list") {
            EventListScreen(
                onCreateEvent = { /* Navigate to create event */ },
                onEventClick = { eventId -> /* Navigate to event details */ }
            )
        }
        // Add other routes here
    }
}
