package com.tadkeera.eventtickets.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "إدارة الفعالية", 
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38)) // Deep navy top bar!
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)) // Crisp Light grey background
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Subtitle instructions
            Text(
                "قم بإدارة جميع جوانب فعالياتك بسهولة",
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                color = Color(0xFF455A64),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2x2 Grid of customized cards matching Screenshot 5!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1: Design and Issue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardCard(
                            title = "تصميم التذكرة",
                            subtitle = "أنشئ تذاكر مخصصة وأكواد QR لضيوفك.",
                            icon = Icons.Default.Brush,
                            accentColor = Color(0xFFFF6D00), // Orange
                            onClick = { navController.navigate("ticket_design/$eventId") }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        DashboardCard(
                            title = "إصدار التذاكر",
                            subtitle = "قم بتوليد وتصدير ملفات PDF للطباعة.",
                            icon = Icons.Default.Print,
                            accentColor = Color(0xFF1976D2), // Blue
                            onClick = { navController.navigate("ticket_issuance/$eventId") }
                        )
                    }
                }

                // Row 2: Scanner and Orders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardCard(
                            title = "قارئ الباركود",
                            subtitle = "امسح التذاكر للتحقق من الحضور عند البوابة.",
                            icon = Icons.Default.QrCodeScanner,
                            accentColor = Color(0xFF7B1FA2), // Purple
                            onClick = { navController.navigate("barcode_scanner/$eventId") }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        DashboardCard(
                            title = "الطلبات والتقارير",
                            subtitle = "عرض تقارير التذاكر المصدرة والزوار.",
                            icon = Icons.Default.BarChart,
                            accentColor = Color(0xFF009688), // Teal
                            onClick = { navController.navigate("orders_list/$eventId") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = accentColor),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, accentColor.copy(alpha = 0.8f)) // Matching beautiful colored borders
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(accentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Text section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF111E38),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = Color(0xFF546E7A),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
