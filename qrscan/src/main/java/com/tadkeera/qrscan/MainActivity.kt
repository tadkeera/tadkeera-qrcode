package com.tadkeera.qrscan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissionsOnStartup()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompanionScannerApp()
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
fun CompanionScannerApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var event by remember { mutableStateOf<Event?>(null) }
    val tickets = remember { mutableStateListOf<Ticket>() }

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
                    val gson = Gson()
                    val payload = gson.fromJson(jsonString, SyncPayload::class.java)
                    if (payload != null && payload.event != null) {
                        event = payload.event
                        tickets.clear()
                        tickets.addAll(payload.tickets)
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
        // Scanner View
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("كاشف المناسبة: ${event?.eventName}") },
                    navigationIcon = {
                        IconButton(onClick = { event = null; tickets.clear() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    },
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
                                                
                                                // Scan processing
                                                val ticket = tickets.find { it.qrCodeData == qrCode }
                                                if (ticket == null) {
                                                    scanResult = ScanResult.Invalid
                                                } else {
                                                    if (ticket.isScanned) {
                                                        ticket.scanCount += 1
                                                        scanResult = ScanResult.Duplicate(ticket, ticket.scannedAt ?: System.currentTimeMillis())
                                                        triggerTelegramReport(context, event!!, tickets, "محاولة تكرار مسح تذكرة ⚠️", ticket)
                                                    } else {
                                                        ticket.isScanned = true
                                                        ticket.scannedAt = System.currentTimeMillis()
                                                        ticket.scanCount = 1
                                                        scanResult = ScanResult.Success(ticket)
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

                // Overlay controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                                Toast.makeText(context, "تم ترحيل كامل التقرير وإرساله إلى قناة تليجرام!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("مزامنة وإرسال تقرير تليجرام 🔄")
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

// Function to compile audit statistics and push to Telegram dynamically
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
${targetTicket?.let { "• التذكرة الأخيرة: ${it.qrCodeData}\n• الضيف: ${it.guestName}\n• مسح مكرر: ${it.scanCount} مرات" } ?: "تقرير المزامنة الشامل للزوار"}
    """.trimIndent()

    GlobalScope.launch(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(reportText, "UTF-8")
            val urlString = "https://api.telegram.org/bot$token/sendMessage?chat_id=$channelId&text=$encodedText&parse_mode=Markdown"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
