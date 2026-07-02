package com.tadkeera.eventtickets.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.tadkeera.eventtickets.data.entities.TicketDesign
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader

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

    // Guest Name Injection Settings
    var showGuestName by remember { mutableStateOf(false) }
    val guestNamesList = remember { mutableStateListOf<String>() }

    // Launcher for CSV guest names importer
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val reader = com.opencsv.CSVReader(InputStreamReader(inputStream))
                    val list = mutableListOf<String>()
                    var nextLine: Array<String>?
                    while (reader.readNext().also { nextLine = it } != null) {
                        val name = nextLine?.firstOrNull()?.trim() ?: continue
                        if (name.isNotEmpty()) {
                            list.add(name)
                        }
                    }
                    reader.close()
                    guestNamesList.clear()
                    guestNamesList.addAll(list)
                    Toast.makeText(context, "تم قراءة ${list.size} اسم من ملف CSV بنجاح!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "فشل قراءة ملف CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                title = { 
                    Text(
                        "إصدار تذاكر المناسبة", 
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38)) // Navy Header
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)) // Crisp Light Grey Bg
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "يرجى تحديد تفاصيل إصدار التذاكر للمناسبة:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111E38),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // Input: Ticket count (Screenshot 7 Custom Style)
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(
                    "عدد التذاكر المراد إصدارها",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6D00),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = ticketCountText,
                    onValueChange = { ticketCountText = it },
                    placeholder = { Text("مثال: 10، 300...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFFFF6D00)),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF111E38),
                        unfocusedTextColor = Color(0xFF111E38),
                        focusedBorderColor = Color(0xFFFF6D00),
                        unfocusedBorderColor = Color(0xFFFFD54F)
                    )
                )
            }

            // Dropdown: Choose Design (Screenshot 7 Custom Style)
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(
                    "تصميم التذكرة المستخدم",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6D00),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDesign?.designName ?: "توليد تلقائي بدون قالب PDF",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تصميم", tint = Color(0xFFFF6D00))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDropdownExpanded = !isDropdownExpanded }
                            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFFFF6D00)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color(0xFF111E38),
                            disabledBorderColor = Color(0xFFFFD54F),
                            disabledLabelColor = Color(0xFFFF6D00)
                        )
                    )

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f).background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "توليد تلقائي بدون قالب PDF", 
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111E38),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                ) 
                            },
                            onClick = {
                                selectedDesign = null
                                isDropdownExpanded = false
                            }
                        )
                        designs.forEach { design ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        design.designName, 
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111E38),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right
                                    ) 
                                },
                                onClick = {
                                    selectedDesign = design
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Guest Name Switch with beautiful soft colors and custom layout (Screenshot 7)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFECEFF1))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = showGuestName,
                        onCheckedChange = { showGuestName = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF6D00) // Beautiful orange switch matching Screenshot 7!
                        )
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "إظهار أسماء الضيوف وطباعتها", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111E38),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            "قم بتفعيل هذا الخيار لرفع قائمة بالأسماء ودمجها تلقائياً مع التذاكر الصادرة.", 
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF546E7A),
                            textAlign = TextAlign.Right,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // CSV Upload Button
            if (showGuestName) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (guestNamesList.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2E7D32), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "مستورد: ${guestNamesList.size} اسم",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "text/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            csvPickerLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .shadow(2.dp, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Text("رفع ملف أسماء الضيوف CSV", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Large Orange Issuance Action Button (Screenshot 7 Style)
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
                    color = Color(0xFFFF6D00),
                    modifier = Modifier.padding(14.dp),
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
                            val finalNames = if (showGuestName) guestNamesList else null
                            viewModel.issueTickets(eventId, count, selectedDesign, finalNames) { file ->
                                isGenerating = false
                                if (file != null) {
                                    Toast.makeText(context, "تم حفظ التذاكر بملف PDF داخلي بنجاح! حملها للذاكرة من الأسفل.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "فشل إصدار التذاكر، يرجى المحاولة لاحقاً", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFFFF6D00)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)) // Bold Orange button
                ) {
                    Text("إصدار وتأكيد التذاكر", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Divider and section header
            Text(
                "ملفات التذاكر الصادرة والمحفوظة داخل التطبيق",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111E38),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // List of generated PDF files with custom designs (Screenshot 7 style)
            if (generatedBatches.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("لا توجد ملفات تذاكر صادرة حالياً", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(generatedBatches) { batch ->
                        val eventCode = batch.first
                        val ticketCount = batch.second
                        
                        val internalFile = viewModel.getInternalPdfFile(eventCode)
                        if (internalFile.exists()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, Color(0xFFFFD54F)) // Orange-yellow outline matching Screenshot 7
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // 1. Share Button (Orange Outline/Border Style)
                                        Button(
                                            onClick = {
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00), contentColor = Color.White),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("مشاركة", fontWeight = FontWeight.Bold)
                                        }

                                        // 2. Download Button (Navy Style)
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111E38), contentColor = Color.White),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("تحميل", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("كود الملف: $eventCode.pdf", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111E38))
                                        Text("العدد: $ticketCount تذكرة مدمجة", style = MaterialTheme.typography.bodySmall, color = Color(0xFF546E7A))
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
