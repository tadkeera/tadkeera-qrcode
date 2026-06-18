package com.tadkeera.qrscan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Ticket Entity mapping
data class Ticket(
    val ticketId: String,
    val eventId: String,
    val eventCode: String,
    val ticketNumber: Int,
    val qrCodeData: String,
    val guestName: String = "",
    val issuedAt: Long = System.currentTimeMillis(),
    var isScanned: Boolean = false,
    var scannedAt: Long? = null,
    var scanCount: Int = 0
)

data class Event(
    val eventId: String,
    val eventName: String,
    val eventDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val eventCode: String = ""
)

data class SyncPayload(
    val event: Event,
    val tickets: List<Ticket>
)

sealed class ScanResult {
    object Idle : ScanResult()
    data class Success(val ticket: Ticket) : ScanResult()
    data class Duplicate(val ticket: Ticket, val lastScannedAt: Long) : ScanResult()
    object Invalid : ScanResult()
}

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("TadkeeraQRScan", Context.MODE_PRIVATE)
        
        requestPermissionsOnStartup()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompanionScannerApp(sharedPreferences)
                }
            }
        }
    }

    private fun requestPermissionsOnStartup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScannerApp(sharedPreferences: SharedPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gson = Gson()

    // Persistent state variables
    var event by remember {
        mutableStateOf<Event?>(
            sharedPreferences.getString("saved_event", null)?.let {
                try { gson.fromJson(it, Event::class.java) } catch (e: Exception) { null }
            }
        )
    }

    val tickets = remember {
        mutableStateListOf<Ticket>().apply {
            sharedPreferences.getString("saved_tickets", null)?.let {
                try {
                    val listType = object : TypeToken<List<Ticket>>() {}.type
                    val list = gson.fromJson<List<Ticket>>(it, listType)
                    addAll(list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Manual Entry Fields
    var manualEventCode by remember { mutableStateOf("") }
    var manualTicketNo by remember { mutableStateOf("") }

    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Stats
    val totalTickets = tickets.size
    val scannedTickets = tickets.count { it.isScanned }
    val remainingTickets = totalTickets - scannedTickets

    // Scan Results
    var scanResult by remember { mutableStateOf<ScanResult>(ScanResult.Idle) }
    var showResultDialog by remember { mutableStateOf(false) }

    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }

    // Save state to Shared Preferences whenever it is modified
    fun saveState(currentEvent: Event?, currentTickets: List<Ticket>) {
        sharedPreferences.edit().apply {
            if (currentEvent != null) {
                putString("saved_event", gson.toJson(currentEvent))
                putString("saved_tickets", gson.toJson(currentTickets))
            } else {
                remove("saved_event")
                remove("saved_tickets")
            }
            apply()
        }
    }

    // Launcher for JSON importer
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val payload = gson.fromJson(jsonString, SyncPayload::class.java)
                    if (payload != null && payload.event != null) {
                        event = payload.event
                        tickets.clear()
                        tickets.addAll(payload.tickets)
                        saveState(payload.event, payload.tickets)
                        Toast.makeText(context, "تم استيراد ${tickets.size} تذكرة لمناسبة [${payload.event.eventName}] بنجاح!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "ملف الاستيراد غير صالح", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "فشل استيراد الملف: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (event == null) {
        // Welcome and Import Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tadkeera_logo),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "QR SCAN",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "تذكرة - القارئ المصاحب المساعد",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    importFileLauncher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("استيراد بيانات المناسبة والعمل أوفلاين (IMPORT)", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Scanner View - Back Button is deleted as requested!
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("كاشف المناسبة: ${event?.eventName}") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Camera View
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val barcodeScanner = BarcodeScanning.getClient(
                                BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                    .build()
                            )

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                @OptIn(ExperimentalGetImage::class)
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            if (barcodes.isNotEmpty() && !showResultDialog) {
                                                val qrCode = barcodes.first().rawValue ?: ""
                                                
                                                // Scan processing - Strictly Isolated by Event ID
                                                val ticket = tickets.find { it.qrCodeData == qrCode && it.eventId == event!!.eventId }
                                                if (ticket == null) {
                                                    scanResult = ScanResult.Invalid
                                                } else {
                                                    if (ticket.isScanned) {
                                                        ticket.scanCount += 1
                                                        scanResult = ScanResult.Duplicate(ticket, ticket.scannedAt ?: System.currentTimeMillis())
                                                        saveState(event, tickets)
                                                        triggerTelegramReport(context, event!!, tickets, "محاولة تكرار مسح تذكرة ⚠️", ticket)
                                                    } else {
                                                        ticket.isScanned = true
                                                        ticket.scannedAt = System.currentTimeMillis()
                                                        ticket.scanCount = 1
                                                        scanResult = ScanResult.Success(ticket)
                                                        saveState(event, tickets)
                                                        triggerTelegramReport(context, event!!, tickets, "مسح تذكرة معتمد جديد ✅", ticket)
                                                    }
                                                }
                                                showResultDialog = true
                                                
                                                // Play sound feedback based on result
                                                try {
                                                    if (scanResult is ScanResult.Success) {
                                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                                                    } else {
                                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Central Scanning Target Box (المربع في الوسط)
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(2.dp)
                            .background(Color.Red.copy(alpha = 0.8f))
                    )
                }

                // Overlay controls with manual checking input fields at the bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isFlashOn = !isFlashOn
                                cameraControl?.enableTorch(isFlashOn)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isFlashOn) MaterialTheme.colorScheme.primary else Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isFlashOn) "إيقاف الفلاش 🔦" else "تشغيل الفلاش 💡")
                        }

                        Button(
                            onClick = {
                                try {
                                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                                    val point = factory.createPoint(0.5f, 0.5f)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    cameraControl?.startFocusAndMetering(action)
                                    Toast.makeText(context, "تم ضبط عدسة التركيز", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تركيز تلقائي 🔍")
                        }
                    }

                    Divider(color = Color.DarkGray, thickness = 1.dp)

                    // Manual Verification Inputs (حقول التحقق اليدوي)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualEventCode,
                            onValueChange = { manualEventCode = it },
                            label = { Text("كود المناسبة (5 أحرف)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.LightGray
                            ),
                            modifier = Modifier.weight(1.2f)
                        )

                        OutlinedTextField(
                            value = manualTicketNo,
                            onValueChange = { manualTicketNo = it },
                            label = { Text("رقم التذكرة") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.LightGray
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                if (manualEventCode.length == 5 && manualTicketNo.isNotEmpty()) {
                                    val ticketNoInt = manualTicketNo.toIntOrNull()
                                    if (ticketNoInt != null) {
                                        // Verify ticket manually
                                        val ticket = tickets.find {
                                            it.eventCode.equals(manualEventCode, ignoreCase = true) &&
                                            it.ticketNumber == ticketNoInt &&
                                            it.eventId == event!!.eventId
                                        }
                                        if (ticket == null) {
                                            scanResult = ScanResult.Invalid
                                        } else {
                                            if (ticket.isScanned) {
                                                ticket.scanCount += 1
                                                scanResult = ScanResult.Duplicate(ticket, ticket.scannedAt ?: System.currentTimeMillis())
                                                saveState(event, tickets)
                                                triggerTelegramReport(context, event!!, tickets, "محاولة تكرار مسح يدوي ⚠️", ticket)
                                            } else {
                                                ticket.isScanned = true
                                                ticket.scannedAt = System.currentTimeMillis()
                                                ticket.scanCount = 1
                                                scanResult = ScanResult.Success(ticket)
                                                saveState(event, tickets)
                                                triggerTelegramReport(context, event!!, tickets, "مسح يدوي معتمد جديد ✅", ticket)
                                            }
                                        }
                                        showResultDialog = true
                                        manualEventCode = ""
                                        manualTicketNo = ""
                                        
                                        // Play sound feedback
                                        try {
                                            if (scanResult is ScanResult.Success) {
                                                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                                            } else {
                                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        Toast.makeText(context, "الرجاء إدخال رقم تذكرة صحيح", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "الكود يجب أن يتكون من 5 أحرف مع رقم التذكرة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("تحقق", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Settings / Telegram Sync Dialog
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("لوحة التحكم وتليجرام أوفلاين", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                triggerTelegramReport(context, event!!, tickets, "مزامنة وتقرير يدوي فوري 🔄")
                                Toast.makeText(context, "تم ترحيل كامل التقرير والملفات المحدثة إلى تليجرام!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("مزامنة وإرسال تقرير تليجرام 🔄")
                        }

                        // Danger Reset option to clear the event if explicitly requested
                        Button(
                            onClick = {
                                event = null
                                tickets.clear()
                                saveState(null, emptyList())
                                showSettings = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("الخروج وإلغاء استيراد المناسبة الحالية ⚠️")
                        }

                        // Stats Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("إحصائيات تذاكر المناسبة الحالية:", fontWeight = FontWeight.Bold)
                                Divider()
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("إجمالي التذاكر:")
                                    Text("$totalTickets", fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("التذاكر الممسوحة:")
                                    Text("$scannedTickets", fontWeight = FontWeight.Bold, color = Color.Green)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("التذاكر المتبقية:")
                                    Text("$remainingTickets", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSettings = false }) {
                        Text("الرجوع لمسح التذاكر")
                    }
                }
            )
        }

        // Result Dialog (Popup) for Checked Tickets
        if (showResultDialog) {
            AlertDialog(
                onDismissRequest = {
                    showResultDialog = false
                    scanResult = ScanResult.Idle
                },
                title = null,
                text = {
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("ar"))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (val result = scanResult) {
                            is ScanResult.Success -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2E7D32))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "تم التحقق، مسموح الدخول ✅",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("كود المناسبة: ${result.ticket.eventCode}", fontWeight = FontWeight.Bold)
                                        Text("رقم التذكرة: ${result.ticket.ticketNumber}", fontWeight = FontWeight.Bold)
                                        if (result.ticket.guestName.isNotEmpty()) {
                                            Text("اسم الضيف: ${result.ticket.guestName}", fontWeight = FontWeight.Bold)
                                        }
                                        Text("وقت الدخول: ${sdf.format(Date(result.ticket.scannedAt ?: System.currentTimeMillis()))}")
                                    }
                                }
                            }
                            is ScanResult.Duplicate -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFC62828))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "مكرر ، تم المسح : ${result.ticket.scanCount} مرات ❌",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("كود المناسبة: ${result.ticket.eventCode}", fontWeight = FontWeight.Bold, color = Color.Red)
                                        Text("رقم التذكرة: ${result.ticket.ticketNumber}", fontWeight = FontWeight.Bold, color = Color.Red)
                                        if (result.ticket.guestName.isNotEmpty()) {
                                            Text("اسم الضيف: ${result.ticket.guestName}", fontWeight = FontWeight.Bold, color = Color.Red)
                                        }
                                        Text("آخر مسح معتمد: ${sdf.format(Date(result.lastScannedAt))}", color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is ScanResult.Invalid -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFC62828))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "تذكرة غير صالحة ❌\nممنوع الدخول",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Text(
                                    "هذا الكود غير مسجل في قاعدة البيانات لهذه المناسبة.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            else -> {}
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResultDialog = false
                            scanResult = ScanResult.Idle
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("مسح التذكرة التالية")
                    }
                }
            )
        }
    }
}

// Function to generate multi-page premium PDF audit report with three custom tables using native Android PdfDocument
fun generatePdfReport(context: Context, event: Event, tickets: List<Ticket>): File {
    val pdfFile = File(context.cacheDir, "Tadkeera_Report_${event.eventCode}.pdf")
    val document = android.graphics.pdf.PdfDocument()
    
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("ar"))
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.BLACK
    }
    
    val boldPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }
    
    val headerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = android.graphics.Color.parseColor("#1565C0") // Royal Blue
    }
    
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
        color = android.graphics.Color.LTGRAY
    }

    var pageNum = 1
    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
    var currentPage = document.startPage(pageInfo)
    var canvas = currentPage.canvas
    
    var currentY = 50f
    
    fun checkPagination(neededHeight: Float) {
        if (currentY + neededHeight > 780f) {
            document.finishPage(currentPage)
            pageNum++
            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage.canvas
            
            // Draw page footer
            canvas.drawText("صفحة $pageNum", 297f, 820f, textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })
            
            currentY = 50f
        }
    }
    
    // Draw initial footer
    canvas.drawText("صفحة $pageNum", 297f, 820f, textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })

    // Draw Document Title and Info
    canvas.drawText("تقرير حضور فعاليات ومناسبات - تذكرة", 297f, currentY, headerPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })
    currentY += 25f
    canvas.drawText("المناسبة: ${event.eventName}", 540f, currentY, boldPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
    currentY += 18f
    canvas.drawText("كود المناسبة: ${event.eventCode} | تاريخ التقرير: ${sdf.format(Date())}", 540f, currentY, textPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
    currentY += 12f
    canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
    currentY += 25f
    
    // Helper to draw section header
    fun drawSectionHeader(title: String) {
        checkPagination(35f)
        canvas.drawText(title, 540f, currentY, boldPaint.apply { 
            textSize = 12f
            color = android.graphics.Color.parseColor("#1565C0")
            textAlign = android.graphics.Paint.Align.RIGHT 
        })
        currentY += 15f
    }
    
    // Helper to draw table headers
    fun drawTableHeader(cols: List<String>, widths: List<Float>) {
        checkPagination(25f)
        var startX = 40f
        canvas.drawRect(40f, currentY - 12f, 555f, currentY + 12f, android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F5F5F5") })
        for (i in cols.indices) {
            val text = cols[i]
            val w = widths[i]
            canvas.drawText(text, startX + w/2f, currentY + 4f, boldPaint.apply { 
                textSize = 9f
                color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER 
            })
            startX += w
        }
        canvas.drawRect(40f, currentY - 12f, 555f, currentY + 12f, borderPaint)
        currentY += 24f
    }
    
    // Helper to draw row
    fun drawTableRow(cols: List<String>, widths: List<Float>) {
        checkPagination(20f)
        var startX = 40f
        for (i in cols.indices) {
            val text = cols[i]
            val w = widths[i]
            canvas.drawText(text, startX + w/2f, currentY + 4f, textPaint.apply { 
                textSize = 8.5f
                textAlign = android.graphics.Paint.Align.CENTER 
            })
            startX += w
        }
        canvas.drawLine(40f, currentY + 10f, 555f, currentY + 10f, borderPaint)
        currentY += 20f
    }
    
    // Helper to draw summary text
    fun drawTableSummary(text: String) {
        checkPagination(20f)
        canvas.drawText(text, 540f, currentY, boldPaint.apply { 
            textSize = 9.5f
            color = android.graphics.Color.parseColor("#2E7D32")
            textAlign = android.graphics.Paint.Align.RIGHT 
        })
        currentY += 25f
    }

    // --- TABLE 1: Attended Guests ---
    val attended = tickets.filter { it.isScanned }
    drawSectionHeader("الجدول الأول: الضيوف الذين حضروا")
    drawTableHeader(listOf("كود المناسبة والرقم", "اسم الضيف", "وقت وتاريخ الحضور"), listOf(150f, 200f, 165f))
    if (attended.isEmpty()) {
        drawTableRow(listOf("-", "لا يوجد حضور بعد", "-"), listOf(150f, 200f, 165f))
    } else {
        attended.forEach { t ->
            val timeStr = t.scannedAt?.let { sdf.format(Date(it)) } ?: "-"
            drawTableRow(listOf("${t.eventCode} - ${t.ticketNumber}", t.guestName.ifEmpty { "ضيف غير مسجل" }, timeStr), listOf(150f, 200f, 165f))
        }
    }
    drawTableSummary("إجمالي الحضور: ${attended.size} ضيف")

    // --- TABLE 2: Absent Guests ---
    val absent = tickets.filter { !it.isScanned }
    drawSectionHeader("الجدول الثاني: الضيوف الذين لم يحضروا")
    drawTableHeader(listOf("كود المناسبة والرقم", "اسم الضيف", "وقت وتاريخ الحضور"), listOf(150f, 200f, 165f))
    if (absent.isEmpty()) {
        drawTableRow(listOf("-", "حضر الجميع", "-"), listOf(150f, 200f, 165f))
    } else {
        absent.forEach { t ->
            drawTableRow(listOf("${t.eventCode} - ${t.ticketNumber}", t.guestName.ifEmpty { "ضيف غير مسجل" }, "لم يحضر"), listOf(150f, 200f, 165f))
        }
    }
    drawTableSummary("إجمالي عدم الحضور: ${absent.size} ضيف")

    // --- TABLE 3: Duplicate/Forgery attempts ---
    val duplicates = tickets.filter { it.scanCount > 1 }
    drawSectionHeader("الجدول الثالث: الضيوف الذين حاولوا التزوير")
    drawTableHeader(listOf("كود المناسبة والرقم", "اسم الضيف", "وقت وتاريخ الحضور عند التكرار", "عدد مرات المسح"), listOf(120f, 160f, 165f, 70f))
    if (duplicates.isEmpty()) {
        drawTableRow(listOf("-", "لا توجد محاولات تكرار أو تزوير", "-", "0"), listOf(120f, 160f, 165f, 70f))
    } else {
        duplicates.forEach { t ->
            val timeStr = t.scannedAt?.let { sdf.format(Date(it)) } ?: "-"
            drawTableRow(listOf("${t.eventCode} - ${t.ticketNumber}", t.guestName.ifEmpty { "ضيف غير مسجل" }, timeStr, t.scanCount.toString()), listOf(120f, 160f, 165f, 70f))
        }
    }
    drawTableSummary("إجمالي التذاكر المزورة والمكررة: ${duplicates.size} تذكرة")

    // Finish last page
    document.finishPage(currentPage)
    
    // Save to file
    FileOutputStream(pdfFile).use { out ->
        document.writeTo(out)
    }
    document.close()
    
    return pdfFile
}

// Function to compile audit statistics and push to Telegram dynamically (Sends Text Summary, DB JSON file, AND PDF report!)
fun triggerTelegramReport(
    context: Context,
    event: Event,
    tickets: List<Ticket>,
    title: String,
    targetTicket: Ticket? = null
) {
    val token = "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y"
    val channelId = "-1004357014151"

    val totalCount = tickets.size
    val scannedList = tickets.filter { it.isScanned }
    val scannedCount = scannedList.size
    val remainingCount = totalCount - scannedCount
    val duplicateAttempts = tickets.sumOf { if (it.scanCount > 1) it.scanCount - 1 else 0 }

    val reportText = """
*📣 [$title]*
*المناسبة:* ${event.eventName}
*كود المناسبة:* ${event.eventCode}
-------------------------------
*📊 الإحصائيات الحالية:*
• إجمالي التذاكر: $totalCount
• المقبولة والممسوحة: $scannedCount
• التذاكر المتبقية: $remainingCount
• محاولات التزوير والتكرار: $duplicateAttempts
-------------------------------
${targetTicket?.let { "• التذكرة الأخيرة: ${it.eventCode} - ${it.ticketNumber}\n• الضيف: ${it.guestName}\n• مسح مكرر: ${it.scanCount} مرات" } ?: "تقرير المزامنة الشامل للمناسبة"}
    """.trimIndent()

    GlobalScope.launch(Dispatchers.IO) {
        try {
            // 1. Send Text Message Report
            val encodedText = URLEncoder.encode(reportText, "UTF-8")
            val urlString = "https://api.telegram.org/bot$token/sendMessage?chat_id=$channelId&text=$encodedText&parse_mode=Markdown"
            var url = URL(urlString)
            var connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            connection.responseCode
            connection.disconnect()

            // 2. Save tickets as JSON file and upload via sendDocument API
            val tempFile = File(context.cacheDir, "Tadkeera_Report_${event.eventCode}.json")
            val gson = Gson()
            val jsonString = gson.toJson(tickets)
            tempFile.writeText(jsonString)

            val boundary = "Boundary-" + UUID.randomUUID().toString()
            val docUrl = URL("https://api.telegram.org/bot$token/sendDocument")
            val docConn = docUrl.openConnection() as HttpURLConnection
            docConn.doOutput = true
            docConn.requestMethod = "POST"
            docConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            docConn.outputStream.use { out ->
                // Write chat_id
                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").toByteArray())
                out.write(("$channelId\r\n").toByteArray())

                // Write caption
                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").toByteArray())
                out.write(("ملف بيانات تذاكر مناسبة: ${event.eventName}\r\n").toByteArray())

                // Write document file
                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"${tempFile.name}\"\r\n").toByteArray())
                out.write(("Content-Type: application/json\r\n\r\n").toByteArray())
                out.write(tempFile.readBytes())
                out.write(("\r\n").toByteArray())

                // End boundary
                out.write(("--$boundary--\r\n").toByteArray())
            }
            docConn.responseCode
            docConn.disconnect()
            tempFile.delete()

            // 3. Generate the gorgeous PDF report with 3 tables and upload to Telegram!
            val pdfReportFile = generatePdfReport(context, event, tickets)
            // Create beautifully renamed file for Telegram upload: "تقرير (اسم المناسبة).pdf"
            val renamedPdfFile = File(context.cacheDir, "تقرير (${event.eventName}).pdf")
            pdfReportFile.copyTo(renamedPdfFile, overwrite = true)
            
            val pdfBoundary = "Boundary-" + UUID.randomUUID().toString()
            val pdfUrl = URL("https://api.telegram.org/bot$token/sendDocument")
            val pdfConn = pdfUrl.openConnection() as HttpURLConnection
            pdfConn.doOutput = true
            pdfConn.requestMethod = "POST"
            pdfConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$pdfBoundary")

            pdfConn.outputStream.use { out ->
                // Write chat_id
                out.write(("--$pdfBoundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").toByteArray())
                out.write(("$channelId\r\n").toByteArray())

                // Write caption
                out.write(("--$pdfBoundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").toByteArray())
                out.write(("تقرير حضور ومبيعات تذاكر المناسبة: ${event.eventName}\r\n").toByteArray())

                // Write document file
                out.write(("--$pdfBoundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"${renamedPdfFile.name}\"\r\n").toByteArray())
                out.write(("Content-Type: application/pdf\r\n\r\n").toByteArray())
                out.write(renamedPdfFile.readBytes())
                out.write(("\r\n").toByteArray())

                // End boundary
                out.write(("--$pdfBoundary--\r\n").toByteArray())
            }
            pdfConn.responseCode
            pdfConn.disconnect()
            
            // Cleanup temp files
            pdfReportFile.delete()
            renamedPdfFile.delete()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
