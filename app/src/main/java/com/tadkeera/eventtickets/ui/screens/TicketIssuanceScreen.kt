package com.tadkeera.eventtickets.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.tadkeera.eventtickets.data.entities.TicketDesign
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketIssuanceScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var ticketCountText by remember { mutableStateOf("10") }
    var isGenerating by remember { mutableStateOf(false) }

    val eventState = viewModel.getEventFlow(eventId).collectAsState(initial = null)
    val event = eventState.value

    val designs by viewModel.getDesignsFlow(eventId).collectAsState(initial = emptyList())
    var selectedDesign by remember { mutableStateOf<TicketDesign?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Fetch already generated batches/codes by grouping tickets
    val tickets by viewModel.getTicketsFlow(eventId).collectAsState(initial = emptyList())
    val generatedBatches = remember(tickets) {
        tickets.groupBy { it.eventCode }
            .map { (eventCode, ticketList) ->
                Pair(eventCode, ticketList.size)
            }
            .filter { it.first.isNotEmpty() }
    }

    LaunchedEffect(designs) {
        if (designs.isNotEmpty() && selectedDesign == null) {
            selectedDesign = designs.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إصدار تذاكر المناسبة") },
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
                "يرجى تحديد تفاصيل إصدار التذاكر للمناسبة:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            // Input: Ticket count
            OutlinedTextField(
                value = ticketCountText,
                onValueChange = { ticketCountText = it },
                label = { Text("عدد التذاكر المراد إصدارها") },
                placeholder = { Text("مثال: 50، 100، 500...") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Dropdown: Choose Design
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedDesign?.designName ?: "توليد تلقائي بدون قالب PDF",
                    onValueChange = {},
                    label = { Text("تصميم التذكرة المستخدم") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تصميم")
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
                    DropdownMenuItem(
                        text = { Text("توليد تلقائي بدون قالب PDF") },
                        onClick = {
                            selectedDesign = null
                            isDropdownExpanded = false
                        }
                    )
                    designs.forEach { design ->
                        DropdownMenuItem(
                            text = { Text(design.designName) },
                            onClick = {
                                selectedDesign = design
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Action Button
            if (isGenerating) {
                var dotCount by remember { mutableStateOf(1) }
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(500)
                        dotCount = (dotCount % 3) + 1
                    }
                }
                val dots = ".".repeat(dotCount)
                Text(
                    text = "جاري توليد التذاكر الفريدة وإنشاء ملف PDF$dots ⏳",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Button(
                    onClick = {
                        val count = ticketCountText.toIntOrNull()
                        if (count == null || count <= 0) {
                            Toast.makeText(context, "الرجاء إدخال عدد تذاكر صحيح أكبر من صفر", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isGenerating = true
                        coroutineScope.launch {
                            viewModel.issueTickets(eventId, count, selectedDesign) { file ->
                                isGenerating = false
                                if (file != null) {
                                    Toast.makeText(context, "تم حفظ التذاكر بملف PDF داخلي بنجاح! حملها للذاكرة من الأسفل.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "فشل إصدار التذاكر، يرجى المحاولة لاحقاً", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("إصدار وتأكيد التذاكر")
                }
            }

            Divider()

            // Header for List of Generated Files inside the app
            Text(
                "ملفات التذاكر الصادرة والمحفوظة داخل التطبيق:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            // List of generated PDF files
            if (generatedBatches.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("لا توجد ملفات تذاكر صادرة حالياً", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(generatedBatches) { batch ->
                        val eventCode = batch.first
                        val ticketCount = batch.second
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("كود الملف: $eventCode.pdf", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text("العدد: $ticketCount تذكرة مدمجة", style = MaterialTheme.typography.bodySmall)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 1. Share Button
                                    Button(
                                        onClick = {
                                            val internalFile = viewModel.getInternalPdfFile(eventCode)
                                            if (internalFile.exists()) {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    internalFile
                                                )
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
                                            } else {
                                                Toast.makeText(context, "الملف الداخلي غير موجود", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        Text("مشاركة")
                                    }

                                    // 2. Download Button
                                    Button(
                                        onClick = {
                                            viewModel.downloadPdfToSharedStorage(eventCode, event?.eventName ?: "مناسبة") { success, file ->
                                                if (success && file != null) {
                                                    Toast.makeText(context, "تم تحميل الملف بنجاح إلى: Tadkeera/${event?.eventName}/${eventCode}.pdf", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "فشل تحميل الملف، يرجى التحقق من صلاحيات التخزين", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("تحميل")
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
