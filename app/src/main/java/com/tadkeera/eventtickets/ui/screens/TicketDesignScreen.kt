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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class SelectedElement { NONE, QR_CODE, EVENT_CODE, GUEST_NAME }

data class FontOption(val arabicName: String, val fileName: String)

val DISPLAY_FONTS = listOf(
    FontOption("1. خط طفولي (Tufuli Arabic)", "elmessiri.ttf"),
    FontOption("2. خط بكرة شبه مكثف (29LT Bukra Semi Condensed)", "almarai.ttf"),
    FontOption("3. خط كوفام (Kufam)", "kufam.ttf"),
    FontOption("4. خط بكرة مكثف (29LT Bukra Condensed)", "tajawal.ttf"),
    FontOption("5. خط قاموس (TS Qamus)", "cairo.ttf"),
    FontOption("6. خط بكرة (29LT Bukra)", "almarai.ttf"),
    FontOption("7. خط أزكادينيا (Azkadinya)", "reemkufi.ttf"),
    FontOption("8. خط ليمونادة (Lemonada)", "lemonada.ttf"),
    FontOption("9. خط شريط الطيران (Air Strip Arabic)", "kufam.ttf"),
    FontOption("10. خط فلفل (Felfel)", "elmessiri.ttf"),
    FontOption("11. خط مجرة (Galaxy)", "tajawal.ttf"),
    FontOption("12. خط سحاب (DG Sahabh)", "elmessiri.ttf"),
    FontOption("13. خط عفيش (Afeesh)", "cairo.ttf"),
    FontOption("14. خط فلافل (Falafel)", "elmessiri.ttf"),
    FontOption("15. خط يوكيدج كوزمي (UKIJ Chiwer Kesme)", "reemkufi.ttf"),
    FontOption("16. خط العربية (Alarabiya)", "arial.ttf"),
    FontOption("17. خط حماة الإسلام (Ara Hamah Alislam)", "arefruqaa.ttf"),
    FontOption("18. خط غرناطة (Granada)", "arefruqaa.ttf"),
    FontOption("19. خط تونس (Hacen Tunisia)", "cairo.ttf")
)

data class ColorOption(val name: String, val hex: String, val color: Color)

val COLOR_OPTIONS = listOf(
    ColorOption("أسود ملكي", "#000000", Color(0xFF000000)),
    ColorOption("أحمر كلاسيكي", "#C62828", Color(0xFFC62828)),
    ColorOption("أخضر زمردي", "#2E7D32", Color(0xFF2E7D32)),
    ColorOption("أزرق ملكي", "#1565C0", Color(0xFF1565C0)),
    ColorOption("ذهبي فاخر", "#D4AF37", Color(0xFFD4AF37)),
    ColorOption("برونزي دافئ", "#CD7F32", Color(0xFFCD7F32)),
    ColorOption("وردي روز جولد", "#B76E79", Color(0xFFB76E79)),
    ColorOption("بنفسجي مخملي", "#4A148C", Color(0xFF4A148C)),
    ColorOption("بني غامق", "#3E2723", Color(0xFF3E2723)),
    ColorOption("رمادي فولاذي", "#37474F", Color(0xFF37474F))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDesignScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showGuestName by remember { mutableStateOf(true) } // Guest Name box is ALWAYS active!
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var containerWidth by remember { mutableStateOf(1) }
    var containerHeight by remember { mutableStateOf(1) }

    // Overlay Elements Positions & Sizes (Normalized 0.0 to 1.0)
    var qrX by remember { mutableStateOf(0.1f) }
    var qrY by remember { mutableStateOf(0.1f) }
    var qrW by remember { mutableStateOf(0.2f) }
    var qrH by remember { mutableStateOf(0.2f) }
    var qrCodeRotation by remember { mutableStateOf(0f) }
    var isQrActive by remember { mutableStateOf(true) }

    // Event Code Settings
    var codeX by remember { mutableStateOf(0.1f) }
    var codeY by remember { mutableStateOf(0.4f) }
    var codeW by remember { mutableStateOf(0.3f) }
    var codeH by remember { mutableStateOf(0.08f) }
    var codeSize by remember { mutableStateOf(1.0f) }
    var codeColorHex by remember { mutableStateOf("#C62828") } // Default Classic Red
    var codeWeight by remember { mutableStateOf("bold") } // normal, bold, extrabold
    var isCodeActive by remember { mutableStateOf(true) }

    // Guest Name Settings
    var guestX by remember { mutableStateOf(0.1f) }
    var guestY by remember { mutableStateOf(0.6f) }
    var guestW by remember { mutableStateOf(0.4f) }
    var guestH by remember { mutableStateOf(0.08f) }
    var guestSize by remember { mutableStateOf(1.0f) }
    var guestColorHex by remember { mutableStateOf("#2E7D32") } // Default Emerald Green
    var guestFontOption by remember { mutableStateOf(DISPLAY_FONTS[15]) } // Default Alarabiya
    var guestWeight by remember { mutableStateOf("bold") } // normal, bold, extrabold
    var isGuestActive by remember { mutableStateOf(true) }

    var fontDropdownExpanded by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "إعداد وتصميم التذكرة", 
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38)) // Premium deep navy
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)) // Crisp Light Grey background
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PDF Template Upload Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/pdf"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    pdfPickerLauncher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)) // Nice blue button
            ) {
                Text(
                    text = if (selectedPdfFile == null) "رفع ملف تصميم التذكرة PDF" else "تغيير ملف تصميم التذكرة PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // PDF Editor / Workspace
            pdfBitmap?.let { bitmap ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val containerWidthDp = with(density) { containerWidth.toDp() }
                val containerHeightDp = with(density) { containerHeight.toDp() }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اسحب المربعات لتحديد موضعها وحرك الحواف لتغيير حجمها. استخدم لوحات التحكم بالأسفل لتغيير الخطوط والأوزان والألوان بالكامل:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 1. Permanent Styling Card for Event Code & Ticket Number (دائم الظهور!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFFF6D00)) // Gorgeous orange outline matching Screenshot 6!
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("إعدادات: [كود المناسبة ورقم التذكرة]", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF6D00))
                            
                            // Font Size
                            Column {
                                Text("حجم خط الكود: ${String.format("%.1f", codeSize)}", fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                Slider(
                                    value = codeSize,
                                    onValueChange = { codeSize = it },
                                    valueRange = 0.5f..3.0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFFFF6D00),
                                        thumbColor = Color(0xFFFF6D00)
                                    )
                                )
                            }

                            // Font Weight Bold and Extra Bold Selector (Premium Segmented UI)
                            Column {
                                Text("سماكة الخط", fontWeight = FontWeight.Bold, color = Color(0xFF111E38), style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F3F5), RoundedCornerShape(12.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf(
                                        "normal" to "عادي",
                                        "bold" to "عريض (Bold)",
                                        "extrabold" to "عريض جداً (Extra)"
                                    ).forEach { (weightKey, label) ->
                                        val isSelected = codeWeight == weightKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFFF6D00) else Color.Transparent)
                                                .clickable { codeWeight = weightKey }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else Color(0xFF455A64),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Color Selector
                            Column {
                                Text("اللون", fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(COLOR_OPTIONS) { opt ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(opt.color)
                                                .border(
                                                    width = if (codeColorHex.uppercase() == opt.hex.uppercase()) 3.dp else 0.dp,
                                                    color = Color(0xFFFF6D00),
                                                    shape = CircleShape
                                                )
                                                .clickable { codeColorHex = opt.hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (codeColorHex.uppercase() == opt.hex.uppercase()) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = codeColorHex,
                                    onValueChange = { codeColorHex = it },
                                    placeholder = { Text("رمز لون الكود يدوياً (Hex)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF111E38),
                                        unfocusedTextColor = Color(0xFF111E38),
                                        focusedBorderColor = Color(0xFFFF6D00),
                                        unfocusedBorderColor = Color(0xFFCFD8DC)
                                    )
                                )
                                Text(
                                    "رمز لون الكود يدوياً (Hex)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF546E7A),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Left
                                )
                            }
                        }
                    }

                    // 2. Permanent Styling Card for Guest Name (دائم الظهور!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFFF6D00)) // Orange outline matching Screenshot 6!
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("إعدادات: [اسم الضيف الكريم]", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF6D00))
                            
                            // Font Size
                            Column {
                                Text("حجم خط اسم الضيف: ${String.format("%.1f", guestSize)}", fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                Slider(
                                    value = guestSize,
                                    onValueChange = { guestSize = it },
                                    valueRange = 0.5f..3.0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFFFF6D00),
                                        thumbColor = Color(0xFFFF6D00)
                                    )
                                )
                            }

                            // Font Weight Bold and Extra Bold Selector (Premium Segmented UI)
                            Column {
                                Text("سماكة الخط", fontWeight = FontWeight.Bold, color = Color(0xFF111E38), style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F3F5), RoundedCornerShape(12.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf(
                                        "normal" to "عادي",
                                        "bold" to "عريض (Bold)",
                                        "extrabold" to "عريض جداً (Extra)"
                                    ).forEach { (weightKey, label) ->
                                        val isSelected = guestWeight == weightKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFFF6D00) else Color.Transparent)
                                                .clickable { guestWeight = weightKey }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else Color(0xFF455A64),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Font Family Dropdown Selector
                            Column {
                                Text("نوع الخط العربي لاسم الضيف:", fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { fontDropdownExpanded = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                                        border = BorderStroke(1.5.dp, Color(0xFFCFD8DC))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF546E7A))
                                            Text(guestFontOption.arabicName, fontWeight = FontWeight.SemiBold, color = Color(0xFF111E38))
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = fontDropdownExpanded,
                                        onDismissRequest = { fontDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 280.dp).background(Color.White)
                                    ) {
                                        DISPLAY_FONTS.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        opt.arabicName, 
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF111E38),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Right
                                                    ) 
                                                },
                                                onClick = {
                                                    guestFontOption = opt
                                                    fontDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Color Selector
                            Column {
                                Text("اللون", fontWeight = FontWeight.Bold, color = Color(0xFF111E38))
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(COLOR_OPTIONS) { opt ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(opt.color)
                                                .border(
                                                    width = if (guestColorHex.uppercase() == opt.hex.uppercase()) 3.dp else 0.dp,
                                                    color = Color(0xFFFF6D00),
                                                    shape = CircleShape
                                                )
                                                .clickable { guestColorHex = opt.hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (guestColorHex.uppercase() == opt.hex.uppercase()) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = guestColorHex,
                                    onValueChange = { guestColorHex = it },
                                    placeholder = { Text("رمز لون اسم الضيف يدوياً (Hex)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF111E38),
                                        unfocusedTextColor = Color(0xFF111E38),
                                        focusedBorderColor = Color(0xFFFF6D00),
                                        unfocusedBorderColor = Color(0xFFCFD8DC)
                                    )
                                )
                                Text(
                                    "رمز لون الكود يدوياً (Hex)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF546E7A),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Left
                                )
                            }
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
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (qrX * containerWidth).roundToInt(),
                                            (qrY * containerHeight).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = containerWidthDp * qrW,
                                        height = containerHeightDp * qrH
                                    )
                                    .rotate(qrCodeRotation)
                                    .border(
                                        width = 2.dp,
                                        color = Color.Black,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            qrX = (qrX + dragAmount.x / containerWidth).coerceIn(0f, 1f - qrW)
                                            qrY = (qrY + dragAmount.y / containerHeight).coerceIn(0f, 1f - qrH)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                                Text(
                                    "[QR CODE]\nالباركود الذكي",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = Color.Black
                                )

                                // Left Edge Resize
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(6.dp)
                                        .fillMaxHeight(0.6f)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaX = dragAmount.x / containerWidth
                                                qrX = (qrX + deltaX).coerceIn(0f, qrX + qrW - 0.05f)
                                                qrW = (qrW - deltaX).coerceIn(0.05f, 1f - qrX)
                                            }
                                        }
                                )

                                // Right Edge Resize
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(6.dp)
                                        .fillMaxHeight(0.6f)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                qrW = (qrW + dragAmount.x / containerWidth).coerceIn(0.05f, 1f - qrX)
                                            }
                                        }
                                )

                                // Top Edge Resize
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .height(6.dp)
                                        .fillMaxWidth(0.6f)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaY = dragAmount.y / containerHeight
                                                qrY = (qrY + deltaY).coerceIn(0f, qrY + qrH - 0.05f)
                                                qrH = (qrH - deltaY).coerceIn(0.05f, 1f - qrY)
                                            }
                                        }
                                )

                                // Bottom Edge Resize
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(6.dp)
                                        .fillMaxWidth(0.6f)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                qrH = (qrH + dragAmount.y / containerHeight).coerceIn(0.05f, 1f - qrY)
                                            }
                                        }
                                )

                                // Rotation Handle
                                var previousAngle by remember { mutableStateOf(0f) }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 26.dp)
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val centerX = (qrW * containerWidth) / 2f
                                                    val centerY = (qrH * containerHeight) / 2f
                                                    val dx = offset.x - centerX
                                                    val dy = offset.y - centerY
                                                    previousAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val centerX = (qrW * containerWidth) / 2f
                                                    val centerY = (qrH * containerHeight) / 2f
                                                    val currentX = change.position.x - centerX
                                                    val currentY = change.position.y - centerY
                                                    val currentAngle = Math.toDegrees(Math.atan2(currentY.toDouble(), currentX.toDouble())).toFloat()
                                                    val angleDelta = currentAngle - previousAngle
                                                    qrCodeRotation = (qrCodeRotation + angleDelta + 360f) % 360f
                                                    previousAngle = currentAngle
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🔄", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 2. Draggable Event Code overlay - Live WYSIWYG Styling Preview!
                        if (isCodeActive) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (codeX * containerWidth).roundToInt(),
                                            (codeY * containerHeight).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = containerWidthDp * codeW,
                                        height = containerHeightDp * codeH
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = Color.Red,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(Color.White.copy(alpha = 0.5f))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            codeX = (codeX + dragAmount.x / containerWidth).coerceIn(0f, 1f - codeW)
                                            codeY = (codeY + dragAmount.y / containerHeight).coerceIn(0f, 1f - codeH)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val parsedColor = try { Color(android.graphics.Color.parseColor(codeColorHex)) } catch (e: Exception) { Color.Red }
                                val styleWeight = when (codeWeight) {
                                    "extrabold" -> FontWeight.Black
                                    "bold" -> FontWeight.Bold
                                    else -> FontWeight.Normal
                                }
                                Text(
                                    "HZ9ZS - 2",
                                    fontSize = (12 * codeSize).sp,
                                    fontWeight = styleWeight,
                                    color = parsedColor
                                )

                                // Drag & Resize Handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .background(Color.Red, RoundedCornerShape(topStart = 4.dp))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newWidthPx = (codeW * containerWidth) + dragAmount.x
                                                val newHeightPx = (codeH * containerHeight) + dragAmount.y
                                                codeW = (newWidthPx / containerWidth).coerceIn(0.1f, 0.8f)
                                                codeH = (newHeightPx / containerHeight).coerceIn(0.04f, 0.3f)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↗️", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. Draggable Guest Name overlay - Live WYSIWYG Font & Styling Preview!
                        if (isGuestActive && showGuestName) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (guestX * containerWidth).roundToInt(),
                                            (guestY * containerHeight).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = containerWidthDp * guestW,
                                        height = containerHeightDp * guestH
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = Color.Green,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(Color.White.copy(alpha = 0.5f))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            guestX = (guestX + dragAmount.x / containerWidth).coerceIn(0f, 1f - guestW)
                                            guestY = (guestY + dragAmount.y / containerHeight).coerceIn(0f, 1f - guestH)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val parsedColor = try { Color(android.graphics.Color.parseColor(guestColorHex)) } catch (e: Exception) { Color.Green }
                                val styleWeight = when (guestWeight) {
                                    "extrabold" -> FontWeight.Black
                                    "bold" -> FontWeight.Bold
                                    else -> FontWeight.Normal
                                }
                                val customFontFamily = remember(guestFontOption) {
                                    try {
                                        androidx.compose.ui.text.font.FontFamily(
                                            androidx.compose.ui.text.font.Font(
                                                path = "fonts/${guestFontOption.fileName}",
                                                assetManager = context.assets
                                            )
                                        )
                                    } catch (e: Exception) {
                                        androidx.compose.ui.text.font.FontFamily.Default
                                    }
                                }
                                Text(
                                    "ياسر ربيع طيب",
                                    fontSize = (12 * guestSize).sp,
                                    fontWeight = styleWeight,
                                    fontFamily = customFontFamily,
                                    color = parsedColor
                                )

                                // Drag & Resize Handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .background(Color.Green, RoundedCornerShape(topStart = 4.dp))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newWidthPx = (guestW * containerWidth) + dragAmount.x
                                                val newHeightPx = (guestH * containerHeight) + dragAmount.y
                                                guestW = (newWidthPx / containerWidth).coerceIn(0.1f, 0.8f)
                                                guestH = (newHeightPx / containerHeight).coerceIn(0.04f, 0.3f)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↗️", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPreviewDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("معاينة التذكرة 👁️")
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("حفظ التصميم 💾")
                        }
                    }
                }
            }
        }

        // 1. Preview Design dialog
        if (showPreviewDialog && pdfBitmap != null) {
            Dialog(
                onDismissRequest = { showPreviewDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "معاينة شكل التذكرة النهائي المطبوع:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .aspectRatio(pdfBitmap!!.width.toFloat() / pdfBitmap!!.height.toFloat())
                                .border(1.dp, Color.Gray)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = pdfBitmap!!.asImageBitmap(),
                                contentDescription = "Ticket Template",
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isQrActive) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (qrX * containerWidth).roundToInt(),
                                                (qrY * containerHeight).roundToInt()
                                            )
                                        }
                                        .size(
                                            width = (qrW * containerWidth / 2.62f).dp,
                                            height = (qrH * containerHeight / 2.62f).dp
                                        )
                                        .rotate(qrCodeRotation)
                                        .border(1.dp, Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("[QR CODE]", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (isCodeActive) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (codeX * containerWidth).roundToInt(),
                                                (codeY * containerHeight).roundToInt()
                                            )
                                        }
                                        .size(
                                            width = (codeW * containerWidth / 2.62f).dp,
                                            height = (codeH * containerHeight / 2.62f).dp
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val parsedColor = try { Color(android.graphics.Color.parseColor(codeColorHex)) } catch (e: Exception) { Color.Black }
                                    val styleWeight = when (codeWeight) {
                                        "extrabold" -> FontWeight.Black
                                        "bold" -> FontWeight.Bold
                                        else -> FontWeight.Normal
                                    }
                                    Text(
                                        "HZ9ZS - 2",
                                        fontSize = (12 * codeSize).sp,
                                        fontWeight = styleWeight,
                                        color = parsedColor
                                    )
                                }
                            }

                            if (isGuestActive && showGuestName) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (guestX * containerWidth).roundToInt(),
                                                (guestY * containerHeight).roundToInt()
                                            )
                                        }
                                        .size(
                                            width = (guestW * containerWidth / 2.62f).dp,
                                            height = (guestH * containerHeight / 2.62f).dp
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val parsedColor = try { Color(android.graphics.Color.parseColor(guestColorHex)) } catch (e: Exception) { Color.Black }
                                    val styleWeight = when (guestWeight) {
                                        "extrabold" -> FontWeight.Black
                                        "bold" -> FontWeight.Bold
                                        else -> FontWeight.Normal
                                    }
                                    val customFontFamily = remember(guestFontOption) {
                                        try {
                                            androidx.compose.ui.text.font.FontFamily(
                                                androidx.compose.ui.text.font.Font(
                                                    path = "fonts/${guestFontOption.fileName}",
                                                    assetManager = context.assets
                                                )
                                            )
                                        } catch (e: Exception) {
                                            androidx.compose.ui.text.font.FontFamily.Default
                                        }
                                    }
                                    Text(
                                        "ياسر ربيع طيب",
                                        fontSize = (12 * guestSize).sp,
                                        fontWeight = styleWeight,
                                        fontFamily = customFontFamily,
                                        color = parsedColor
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showPreviewDialog = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text("رجوع للتعديل")
                        }
                    }
                }
            }
        }

        // 2. Save Design dialog
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
                                    qrCodeRotation = qrCodeRotation,
                                    eventCodeX = codeX,
                                    eventCodeY = codeY,
                                    eventCodeSize = codeSize,
                                    guestNameX = guestX,
                                    guestNameY = guestY,
                                    guestNameSize = guestSize,
                                    showGuestName = showGuestName,
                                    isDefault = true,
                                    eventCodeColor = codeColorHex,
                                    guestNameColor = guestColorHex,
                                    guestNameFont = guestFontOption.fileName,
                                    eventCodeWidth = codeW,
                                    eventCodeHeight = codeH,
                                    guestNameWidth = guestW,
                                    guestNameHeight = guestH,
                                    eventCodeWeight = codeWeight,
                                    guestNameWeight = guestWeight
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
