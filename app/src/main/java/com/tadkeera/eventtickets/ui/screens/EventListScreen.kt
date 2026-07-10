package com.tadkeera.eventtickets.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                containerColor = Color(0xFFFF6D00), // Vibrant orange matching Screenshots!
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, CircleShape, spotColor = Color(0xFFFF6D00))
            ) {
                Icon(Icons.Default.Add, contentDescription = "إنشاء مناسبة جديدة", modifier = Modifier.size(36.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)) // Crisp Light grey background as in images
        ) {
            // Rounded curved orange status bar at top (Matching Screenshot 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Color(0xFFFFA565)) // Beautiful pastel orange header
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Spread elements beautifully!
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Settings Icon
                        IconButton(
                            onClick = { navController.navigate("backup_settings") },
                            modifier = Modifier
                                .background(Color(0xFF2E3D52), CircleShape)
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات والنسخ الاحتياطي",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Share Icon
                        IconButton(
                            onClick = { navController.navigate("data_sync") },
                            modifier = Modifier
                                .background(Color(0xFF2E3D52), CircleShape)
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "تصدير واستيراد البيانات",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Theme Toggle Button (صباحي / مسائي - Sun / Moon Icon)
                    val isDark by viewModel.isDarkMode.collectAsState()
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier
                            .background(Color(0xFF2E3D52), CircleShape)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = "تغيير الثيم",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Central Logo and Welcome Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circle Logo
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tadkeera_logo),
                        contentDescription = "Tadkeera Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(130.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "مرحباً بك في تذكرة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                    color = Color(0xFF111E38), // Elegant Navy text
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "إدارة فعالياتك بكل سهولة واحترافية.",
                    fontSize = 15.sp,
                    color = Color(0xFF475569), // Muted dark blue-gray
                    textAlign = TextAlign.Center
                )
            }

            // Upcoming events title
            Text(
                "المناسبات القادمة",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Color(0xFF111E38),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                textAlign = TextAlign.Right
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(
                            "لا توجد مناسبات حالياً 📂",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            "انقر على الزر (+) بالأسفل لإضافة مناسبتك الأولى وبدء التصميم!",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 90.dp, start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(events) { index, event ->
                        // Alternate borders between Orange and Blue like Screenshot 1
                        val cardColor = if (index % 2 == 0) Color(0xFFFF6D00) else Color(0xFF1976D2)
                        
                        EventCard(
                            event = event,
                            cardBorderColor = cardColor,
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

        // Add Event Dialog with Orange/Blue accents (Screenshot 2 style)
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
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111E38),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Event Name
                        Text(
                            "اسم المناسبة",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111E38),
                            fontSize = 14.sp
                        )
                        // Fixed rounded outline box problem by removing nested shadows/clipping from text fields
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            placeholder = { Text("أدخل اسم المناسبة", color = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF111E38),
                                unfocusedTextColor = Color(0xFF111E38),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFFFF6D00),
                                unfocusedBorderColor = Color(0xFFB0BEC5)
                            )
                        )

                        // Event Date
                        Text(
                            "تاريخ المناسبة",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111E38),
                            fontSize = 14.sp
                        )
                        val sdf = SimpleDateFormat("d/M/yyyy", Locale.US) // Reverted to standard English numerals as requested!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() }
                        ) {
                            OutlinedTextField(
                                value = sdf.format(Date(eventDate)),
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                trailingIcon = {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_menu_my_calendar),
                                        contentDescription = "Calendar",
                                        tint = Color(0xFFFF6D00)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color(0xFF111E38),
                                    disabledContainerColor = Color.White,
                                    disabledBorderColor = Color(0xFF1976D2),
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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00))
                    ) {
                        Text("إضافة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showAddDialog = false
                            eventName = ""
                            eventDate = System.currentTimeMillis()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color(0xFF424242))
                    ) {
                        Text("إلغاء", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
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
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Text(
                        "هل أنت متأكد من رغبتك في حذف مناسبة [${eventToDelete!!.eventName}] وكل التذاكر الصادرة، وملفات الـ PDF، والتصاميم المعتمدة التابعة لها كلياً؟ هذا الإجراء نهائي ولا يمكن التراجع عنه.", 
                        color = Color(0xFF455A64),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteEvent(eventToDelete!!)
                            showDeleteDialog = false
                            eventToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("نعم، احذف كلياً", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; eventToDelete = null }) {
                        Text("إلغاء", color = Color(0xFF78909C), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    cardBorderColor: Color,
    onClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("d/M/yyyy", Locale.US) // English numbers!
    val dateString = sdf.format(Date(event.eventDate))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick(event.eventId) }
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, cardBorderColor) // Gorgeous custom outline border matching Screenshots!
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Elegant Delete Button inside card so user can delete events easily
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(Color(0xFFFDE8E8), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف المناسبة كلياً",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111E38),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF546E7A),
                    textAlign = TextAlign.Right
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Ticket Icon on the left
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(cardBorderColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = cardBorderColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
