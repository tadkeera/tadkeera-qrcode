package com.tadkeera.eventtickets.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
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
    var generatedFile by remember { mutableStateOf<File?>(null) }

    val designs by viewModel.getDesignsFlow(eventId).collectAsState(initial = emptyList())
    var selectedDesign by remember { mutableStateOf<TicketDesign?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

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
                .padding(24.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            if (isGenerating) {
                CircularProgressIndicator()
                Text("جاري توليد التذاكر الفريدة وإنشاء ملف PDF...", style = MaterialTheme.typography.bodyMedium)
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
                                generatedFile = file
                                if (file != null) {
                                    Toast.makeText(context, "تم إصدار التذاكر وإنشاء الملف بنجاح!", Toast.LENGTH_LONG).show()
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

            // PDF Share / Open options
            generatedFile?.let { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "تم حفظ التذاكر بنجاح في مجلد التنزيلات!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "لا يتوفر قارئ PDF على هذا الجهاز لتشغيل الملف", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text("فتح ملف PDF", color = MaterialTheme.colorScheme.primaryContainer)
                            }

                            Button(
                                onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة التذاكر"))
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("مشاركة الملف")
                            }
                        }
                    }
                }
            }
        }
    }
}
