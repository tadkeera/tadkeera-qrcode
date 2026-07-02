package com.tadkeera.eventtickets.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.data.entities.Ticket
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
        // Show List of All Orders
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "الطلبات والاورودوات", 
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA))
            ) {
                if (orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد طلبات إصدار تذاكر حالياً", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(orders) { index, order ->
                            // Alternate card border outline (Screenshot 8 Style)
                            val cardBorderColor = if (index % 2 == 0) Color(0xFFFF6D00) else Color(0xFF1976D2)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = cardBorderColor),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(2.dp, cardBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("رمز الطلب: ${order.eventCode}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                    // Reverted to English numbers as requested!
                                    Text("عدد التذاكر: ${order.ticketCount}", fontSize = 16.sp, color = Color(0xFF546E7A))
                                    Text("الممسوحة: ${order.scannedCount}", fontSize = 16.sp, color = Color(0xFF546E7A))

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = { selectedOrderCode = order.eventCode },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = cardBorderColor),
                                        modifier = Modifier.align(Alignment.Start)
                                    ) {
                                        Text("عرض التفاصيل ⬅️", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show detail of selected Order (Screenshot 9 Style)
        val selectedOrder = orders.find { it.eventCode == selectedOrderCode }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "تفاصيل طلب: $selectedOrderCode", 
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedOrderCode = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
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
                            Icon(Icons.Default.Delete, contentDescription = "حذف الطلب", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38))
                )
            }
        ) { padding ->
            selectedOrder?.let { order ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFFF8F9FA))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            // High level card matching Screenshot 9
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(2.dp, Color(0xFFFF6D00))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("تقرير فحص طلب التذاكر", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111E38))
                                    // Reverted to English numbers as requested!
                                    Text("إجمالي تذاكر الطلب: ${order.ticketCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF546E7A))
                                    val forgeryCount = order.tickets.count { it.scanCount > 1 }
                                    Text(
                                        "حاولوا تزوير/إعادة استخدام التذاكر: $forgeryCount زائر", 
                                        color = if (forgeryCount > 0) Color.Red else Color(0xFF2E7D32), 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        items(order.tickets) { ticket ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(2.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, Color(0xFFECEFF1))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status on the left (or right)
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Reverted to English numbers as requested!
                                        Text("تذكرة رقم: ${ticket.ticketNumber}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111E38))
                                        
                                        when {
                                            ticket.scanCount > 1 -> {
                                                Text("مكرر ، تم المسح : ${ticket.scanCount} مرات ❌", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            ticket.scanCount == 1 -> {
                                                Text("✅ مقبول", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            else -> {
                                                Text("لم تمسح ⏳", color = Color(0xFF78909C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // QR Code and Guest details
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = ticket.qrCodeData, 
                                            style = MaterialTheme.typography.bodyMedium, 
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111E38),
                                            maxLines = 4,
                                            textAlign = TextAlign.Right
                                        )
                                        if (ticket.guestName.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("الضيف: ${ticket.guestName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF546E7A))
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
}

data class OrderInfo(
    val eventCode: String,
    val ticketCount: Int,
    val scannedCount: Int,
    val tickets: List<Ticket>
)
