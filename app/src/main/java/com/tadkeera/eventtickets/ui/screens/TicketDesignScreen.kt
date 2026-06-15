package com.tadkeera.eventtickets.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDesignScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showGuestName by remember { mutableStateOf(false) }
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var containerWidth by remember { mutableStateOf(1) }
    var containerHeight by remember { mutableStateOf(1) }

    // Overlay Elements Positions & Sizes (Normalized 0.0 to 1.0)
    var qrX by remember { mutableStateOf(0.1f) }
    var qrY by remember { mutableStateOf(0.1f) }
    var qrW by remember { mutableStateOf(0.2f) }
    var qrH by remember { mutableStateOf(0.2f) }
    var isQrActive by remember { mutableStateOf(false) }

    var codeX by remember { mutableStateOf(0.1f) }
    var codeY by remember { mutableStateOf(0.4f) }
    var codeSize by remember { mutableStateOf(1.0f) }
    var isCodeActive by remember { mutableStateOf(false) }

    var guestX by remember { mutableStateOf(0.1f) }
    var guestY by remember { mutableStateOf(0.6f) }
    var guestSize by remember { mutableStateOf(1.0f) }
    var isGuestActive by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var designName by remember { mutableStateOf("") }

    // Launcher for PDF picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                // Copy selected file to app internal storage to persist path access
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val tempFile = File(context.filesDir, "temp_ticket_template.pdf")
                    val outputStream = FileOutputStream(tempFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    selectedPdfFile = tempFile

                    // Render first page as bitmap at high quality (3x scale)
                    val fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(fileDescriptor)
                    if (pdfRenderer.pageCount > 0) {
                        val page = pdfRenderer.openPage(0)
                        val scale = 3
                        val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfBitmap = bitmap
                        page.close()
                    }
                    pdfRenderer.close()
                    Toast.makeText(context, "تم تحميل ملف التصميم بنجاح", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "فشل قراءة ملف PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher for CSV picker
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            viewModel.uploadCSV(eventId, uri)
            Toast.makeText(context, "تم رفع أسماء الضيوف وبدء المعالجة", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعداد وتصميم التذكرة") },
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Guest Name Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إظهار اسم الضيف في التذكرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("قم بتفعيل هذا الخيار لتمكين رفع وقراءة أسماء المدعوين من ملف CSV وطباعتها على التذاكر.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = showGuestName,
                        onCheckedChange = { showGuestName = it }
                    )
                }
            }

            // CSV Upload Button
            if (showGuestName) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "text/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        csvPickerLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("رفع ملف أسماء الضيوف CSV")
                }
            }

            // PDF Template Upload Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/pdf"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    pdfPickerLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (selectedPdfFile == null) "رفع ملف تصميم التذكرة PDF" else "تغيير ملف تصميم التذكرة PDF")
            }

            // PDF Editor / Workspace
            pdfBitmap?.let { bitmap ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اسحب العناصر وقم بقرصها (Pinch) لتغيير حجمها وتحديد موضعها على التذكرة:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Toolbar for Elements activation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isQrActive,
                            onClick = { isQrActive = !isQrActive },
                            label = { Text("إضافة QR CODE") }
                        )
                        FilterChip(
                            selected = isCodeActive,
                            onClick = { isCodeActive = !isCodeActive },
                            label = { Text("زر الكود والرقم") }
                        )
                        if (showGuestName) {
                            FilterChip(
                                selected = isGuestActive,
                                onClick = { isGuestActive = !isGuestActive },
                                label = { Text("زر اسم الضيف") }
                            )
                        }
                    }

                    // Interactive PDF preview box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .border(1.dp, Color.Gray)
                            .clip(RoundedCornerShape(4.dp))
                            .onGloballyPositioned { coordinates ->
                                containerWidth = coordinates.size.width
                                containerHeight = coordinates.size.height
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Ticket Template Page Preview",
                            modifier = Modifier.fillMaxSize()
                        )

                        // 1. Draggable QR Code overlay
                        if (isQrActive) {
                            var qrWidthPx by remember { mutableStateOf(containerWidth * qrW) }
                            var qrHeightPx by remember { mutableStateOf(containerHeight * qrH) }
                            
                            val qrTransformState = rememberTransformableState { zoomChange, _, _ ->
                                qrWidthPx = (qrWidthPx * zoomChange).coerceIn(40f, containerWidth.toFloat() * 0.5f)
                                qrHeightPx = qrWidthPx // Keep square aspect ratio
                                qrW = qrWidthPx / containerWidth
                                qrH = qrHeightPx / containerHeight
                            }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (qrX * containerWidth).roundToInt(),
                                            (qrY * containerHeight).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = (qrW * containerWidth / 2.62f).dp, // conversion fallback for visualization
                                        height = (qrH * containerHeight / 2.62f).dp
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                                    .transformable(state = qrTransformState)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            qrX = (qrX + dragAmount.x / containerWidth).coerceIn(0f, 1f - qrW)
                                            qrY = (qrY + dragAmount.y / containerHeight).coerceIn(0f, 1f - qrH)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "[QR CODE]\nالباركود الذكي",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 2. Draggable Event Code & Ticket Number overlay
                        if (isCodeActive) {
                            var codeTransformState = rememberTransformableState { zoomChange, _, _ ->
                                codeSize = (codeSize * zoomChange).coerceIn(0.5f, 3.0f)
                            }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (codeX * containerWidth).roundToInt(),
                                            (codeY * containerHeight).roundToInt()
                                        )
                                    }
                                    .wrapContentSize()
                                    .border(2.dp, Color.Red, RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                                    .padding(4.dp)
                                    .transformable(state = codeTransformState)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            codeX = (codeX + dragAmount.x / containerWidth).coerceIn(0f, 0.9f)
                                            codeY = (codeY + dragAmount.y / containerHeight).coerceIn(0f, 0.95f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "SD2RA - 1",
                                    fontSize = (12 * codeSize).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            }
                        }

                        // 3. Draggable Guest Name overlay
                        if (isGuestActive && showGuestName) {
                            var guestTransformState = rememberTransformableState { zoomChange, _, _ ->
                                guestSize = (guestSize * zoomChange).coerceIn(0.5f, 3.0f)
                            }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (guestX * containerWidth).roundToInt(),
                                            (guestY * containerHeight).roundToInt()
                                        )
                                    }
                                    .wrapContentSize()
                                    .border(2.dp, Color.Green, RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                                    .padding(4.dp)
                                    .transformable(state = guestTransformState)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            guestX = (guestX + dragAmount.x / containerWidth).coerceIn(0f, 0.9f)
                                            guestY = (guestY + dragAmount.y / containerHeight).coerceIn(0f, 0.95f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "اسم الضيف الكريم",
                                    fontSize = (12 * guestSize).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ تصميم التذكرة")
                    }
                }
            }
        }

        // Save Design dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("تسمية وحفظ التصميم") },
                text = {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { Text("اسم التصميم") },
                        placeholder = { Text("مثال: التصميم الرئيسي، VIP...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (designName.isNotBlank() && selectedPdfFile != null) {
                                viewModel.saveDesign(
                                    eventId = eventId,
                                    name = designName,
                                    templatePath = selectedPdfFile!!.absolutePath,
                                    qrCodeX = qrX,
                                    qrCodeY = qrY,
                                    qrCodeWidth = qrW,
                                    qrCodeHeight = qrH,
                                    eventCodeX = codeX,
                                    eventCodeY = codeY,
                                    eventCodeSize = codeSize,
                                    guestNameX = guestX,
                                    guestNameY = guestY,
                                    guestNameSize = guestSize,
                                    showGuestName = showGuestName
                                )
                                showSaveDialog = false
                                designName = ""
                                Toast.makeText(context, "تم حفظ تصميم التذكرة بنجاح", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "الرجاء إدخال اسم للتصميم أولاً", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("حفظ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}
