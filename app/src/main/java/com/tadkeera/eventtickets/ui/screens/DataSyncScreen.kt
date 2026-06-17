package com.tadkeera.eventtickets.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.data.entities.Event
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSyncScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val events by viewModel.events.collectAsState()
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        if (events.isNotEmpty() && selectedEvent == null) {
            selectedEvent = events.first()
        }
    }

    // Launcher for selecting JSON/DB file during Import
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            viewModel.importEventData(uri) { success, eventName ->
                if (success && eventName != null) {
                    Toast.makeText(context, "تم استيراد البيانات والمناسبة [$eventName] بنجاح!", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "فشل استيراد البيانات: ${eventName ?: "ملف غير صالح"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصدير واستيراد البيانات") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "استيراد وتصدير بيانات التذاكر للمزامنة أوفلاين:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            // Dropdown: Choose Event
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedEvent?.eventName ?: "الرجاء إنشاء مناسبة أولاً",
                    onValueChange = {},
                    label = { Text("اختر المناسبة المحددة") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "اختيار")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDropdownExpanded = !isDropdownExpanded },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    events.forEach { event ->
                        DropdownMenuItem(
                            text = { Text(event.eventName) },
                            onClick = {
                                selectedEvent = event
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Export Button
            Button(
                onClick = {
                    val event = selectedEvent
                    if (event == null) {
                        Toast.makeText(context, "الرجاء اختيار مناسبة للتصدير", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.exportEventData(event) { success, file ->
                        if (success && file != null) {
                            Toast.makeText(context, "تم تصدير البيانات بنجاح إلى: Tadkeera/${event.eventName}/${event.eventCode}.json", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "فشل تصدير البيانات، يرجى المحاولة لاحقاً", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تصدير البيانات (Export)")
            }

            // 2. IMPORT Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    importFileLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("استيراد وتحديث البيانات (IMPORT)")
            }
        }
    }
}
