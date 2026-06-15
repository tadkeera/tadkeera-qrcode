package com.tadkeera.eventtickets.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val eventState = viewModel.getEventFlow(eventId).collectAsState(initial = null)
    val event = eventState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.eventName ?: "لوحة تحكم المناسبة") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "الرجاء اختيار أحد الخيارات التالية لإدارة المناسبة:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Button 1: Ticket Design
            Card(
                onClick = { navController.navigate("ticket_design/$eventId") },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. تصميم التذكرة", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("قم بإعداد قالب التذكرة ووضع الـ QR كود، رقم التذكرة، وأسماء الضيوف في أماكنها المناسبة.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Button 2: Issue Tickets
            Card(
                onClick = { navController.navigate("ticket_issuance/$eventId") },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. إصدار التذاكر", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("حدد كمية التذاكر المطلوبة لإنشاء الأكواد الفريدة وملف الـ PDF المجمع للطباعة.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Button 3: Barcode Scanner
            Card(
                onClick = { navController.navigate("barcode_scanner/$eventId") },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("3. قارئ الباركود والتحقق", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("افتح الكاميرا لمسح تذاكر الضيوف عند البوابة والتحقق من صحتها بدون إنترنت.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
