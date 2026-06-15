package com.tadkeera.eventtickets.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tadkeera.eventtickets.R
import com.tadkeera.eventtickets.data.entities.Event
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(System.currentTimeMillis()) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_tadkeera_logo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TADKEERA (تذكرة)")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إنشاء مناسبة جديدة")
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("لا توجد مناسبات حالياً")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    EventCard(event, onEventClick)
                }
            }
        }

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
                        style = MaterialTheme.typography.titleLarge
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
                            singleLine = true
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
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                        enabled = eventName.isNotBlank()
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
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
fun EventCard(event: Event, onClick: (String) -> Unit) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(event.eventId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = event.eventName, style = MaterialTheme.typography.titleLarge)
            Text(text = sdf.format(Date(event.eventDate)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
