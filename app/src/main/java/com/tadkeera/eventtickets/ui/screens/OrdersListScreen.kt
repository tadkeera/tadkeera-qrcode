package com.tadkeera.eventtickets.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersListScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val tickets by viewModel.getTicketsFlow(eventId).collectAsState(initial = emptyList())

    // Group tickets by eventCode (Order)
    val orders = remember(tickets) {
        tickets.groupBy { it.eventCode }
            .map { (eventCode, ticketList) ->
                OrderInfo(
                    eventCode = eventCode,
                    ticketCount = ticketList.size,
                    scannedCount = ticketList.count { it.isScanned },
                    tickets = ticketList
                )
            }
            .filter { it.eventCode.isNotEmpty() }
    }

    var selectedOrderCode by remember { mutableStateOf<String?>(null) }

    if (selectedOrderCode == null) {
        // Shwo List of All Orders
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("الطلبات والاوردرات") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding ->
            if (orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("لا توجد طلبات إصدار تذاكر حالياً")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(orders) { order ->
                        Card(
                            onClick = { selectedOrderCode = order.eventCode },
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("رمز الطلب: ${order.eventCode}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("عدد التذاكر: ${order.ticketCount}", style = MaterialTheme.typography.bodyMedium)
                                    Text("الممسوحة: ${order.scannedCount}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Text("عرض التفاصيل ➡️", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show detail of selected Order
        val selectedOrder = orders.find { it.eventCode == selectedOrderCode }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("تفاصيل طلب: $selectedOrderCode") },
                    navigationIcon = {
                        IconButton(onClick = { selectedOrderCode = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.deleteOrder(selectedOrderCode!!)
                                Toast.makeText(context, "تم حذف الطلب وتذاكره بالكامل بنجاح", Toast.LENGTH_SHORT).show()
                                selectedOrderCode = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الطلب", tint = Color.Red)
                        }
                    }
                )
            }
        ) { padding ->
            selectedOrder?.let { order ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("تقرير فحص طلب التذاكر:", fontWeight = FontWeight.Bold)
                                Text("إجمالي تذاكر الطلب: ${order.ticketCount}")
                                val forgeryCount = order.tickets.count { it.scanCount > 1 }
                                Text("حاولوا تزوير/إعادة استخدام التذاكر: $forgeryCount زائر", color = if (forgeryCount > 0) Color.Red else Color.Green, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(order.tickets) { ticket ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("كود التذكرة: ${ticket.qrCodeData}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("تذكرة رقم: ${ticket.ticketNumber}", style = MaterialTheme.typography.bodySmall)
                                    if (ticket.guestName.isNotEmpty()) {
                                        Text("الضيف: ${ticket.guestName}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Status icons based on scan results
                                when {
                                    ticket.scanCount > 1 -> {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("🟫 مكرر", color = Color(0xFF5D4037), fontWeight = FontWeight.Bold)
                                            Text("مسح: ${ticket.scanCount} مرات", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D4037))
                                        }
                                    }
                                    ticket.scanCount == 1 -> {
                                        Text("✅ مقبول", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                    else -> {
                                        Text("⏳ لم تمسح", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class OrderInfo(
    val eventCode: String,
    val ticketCount: Int,
    val scannedCount: Int,
    val tickets: List<Ticket>
)
