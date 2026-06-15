package com.tadkeera.eventtickets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tadkeera.eventtickets.ui.screens.*
import com.tadkeera.eventtickets.ui.theme.TadkeeraTheme
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
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
    val viewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "event_list") {
        composable("event_list") {
            EventListScreen(
                viewModel = viewModel,
                onCreateEvent = {},
                onEventClick = { eventId ->
                    navController.navigate("event_detail/$eventId")
                }
            )
        }
        composable(
            route = "event_detail/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            EventDetailScreen(
                eventId = eventId,
                navController = navController,
                viewModel = viewModel
            )
        }
        composable(
            route = "ticket_design/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            TicketDesignScreen(
                eventId = eventId,
                navController = navController,
                viewModel = viewModel
            )
        }
        composable(
            route = "ticket_issuance/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            TicketIssuanceScreen(
                eventId = eventId,
                navController = navController,
                viewModel = viewModel
            )
        }
        composable(
            route = "barcode_scanner/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            BarcodeScannerScreen(
                eventId = eventId,
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}
