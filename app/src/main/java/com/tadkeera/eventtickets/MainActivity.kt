package com.tadkeera.eventtickets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        
        // Request Storage and Camera Permissions on App Startup
        requestPermissionsOnStartup()

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            
            TadkeeraTheme(isDarkMode = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TadkeeraApp(viewModel)
                }
            }
        }
    }

    private fun requestPermissionsOnStartup() {
        // 1. Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        // 2. Storage / All Files access permission (for root directory and automatic database backup)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
            }
        }
    }
}

@Composable
fun TadkeeraApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "event_list") {
        composable("event_list") {
            EventListScreen(
                viewModel = viewModel,
                navController = navController,
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
        composable(
            route = "orders_list/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            OrdersListScreen(
                eventId = eventId,
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("data_sync") {
            DataSyncScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("backup_settings") {
            BackupSettingsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("telegram_link") {
            TelegramLinkScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}
