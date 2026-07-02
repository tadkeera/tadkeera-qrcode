package com.tadkeera.eventtickets.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.R
import com.tadkeera.eventtickets.data.entities.Event
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Delete Event States
    var eventToDelete by remember { mutableStateOf<Event?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            eventDate = selectedCal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_tadkeera_logo),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            "تذكرة (Tadkeera)",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("backup_settings") }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات والنسخ الاحتياطي",
                            tint = Color(0xFF6366F1), // Royal purple tint!
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { navController.navigate("data_sync") }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "تصدير واستيراد البيانات",
                            tint = Color(0xFF6366F1), // Royal purple tint!
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(24.dp), // Elegant 24px radius!
                containerColor = Color(0xFF4F46E5), // Royal Purple accent!
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إنشاء مناسبة جديدة", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F172A)) // Elegant deep slate background!
        ) {
            // Premium Glassmorphism Welcome Banner (24px radius, subtle border)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp), // Elegant 24px radius!
                colors = CardDefaults.cardColors(containerColor = Color(0x1F4F46E5)), // Soft glassmorphic transparent purple!
                border = BorderStroke(1.dp, Color(0x3F6366F1)) // Subtle neon purple outline!
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "أهلاً بك في نظام تذكرة 🌟",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                    Text(
                        "قم بإنشاء وتصميم وإدارة تذاكر مناسباتك بطريقة عصرية مذهلة وآمنة بالكامل أوفلاين.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF94A3B8), // Sleek muted gray text
                        lineHeight = 20.sp
                    )
                }
            }

            Text(
                "المناسبات الحالية النشطة:",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White
            )

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "لا توجد مناسبات حالياً 📂",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            "انقر على الزر (+) بالأسفل لإضافة مناسبتك الأولى وبدء التصميم!",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(events) { event ->
                        EventCard(
                            event = event,
                            onClick = onEventClick,
                            onDelete = {
                                eventToDelete = event
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Add Event Dialog with Premium Glass style
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddDialog = false
                    eventName = ""
                    eventDate = System.currentTimeMillis()
                },
                title = {
                    Text(
                        text = "إنشاء مناسبة جديدة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text("اسم المناسبة") },
                            placeholder = { Text("مثال: حفل تخرج، مؤتمر...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )

                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() }
                        ) {
                            OutlinedTextField(
                                value = sdf.format(Date(eventDate)),
                                onValueChange = {},
                                label = { Text("تاريخ المناسبة") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledBorderColor = Color(0xFF334155),
                                    disabledLabelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (eventName.isNotBlank()) {
                                viewModel.createEvent(eventName, eventDate)
                                showAddDialog = false
                                eventName = ""
                                eventDate = System.currentTimeMillis()
                            }
                        },
                        enabled = eventName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إضافة")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            eventName = ""
                            eventDate = System.currentTimeMillis()
                        }
                    ) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog && eventToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        "تأكيد حذف المناسبة؟ ⚠️",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                },
                text = {
                    Text("هل أنت متأكد من رغبتك في حذف مناسبة [${eventToDelete!!.eventName}] وكل التذاكر الصادرة، وملفات الـ PDF، والتصاميم المعتمدة التابعة لها كلياً؟ هذا الإجراء نهائي ولا يمكن التراجع عنه.", color = Color(0xFF94A3B8))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteEvent(eventToDelete!!)
                            showDeleteDialog = false
                            eventToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("نعم، احذف كلياً")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; eventToDelete = null }) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    onClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)) // Elegant 24px radius!
            .clickable { onClick(event.eventId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Premium dark slate card
        border = BorderStroke(1.dp, Color(0x3F6366F1)) // Glass purple border!
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تاريخ البدء: ${sdf.format(Date(event.eventDate))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            }

            // Elegant Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(Color(0x1FEF4444), CircleShape) // Translucent red background
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف المناسبة كلياً",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
