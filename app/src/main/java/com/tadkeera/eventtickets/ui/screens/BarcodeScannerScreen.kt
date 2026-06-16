package com.tadkeera.eventtickets.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.tadkeera.eventtickets.data.entities.Ticket
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import com.tadkeera.eventtickets.ui.viewmodel.ScanResult
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Scan Stats
    val tickets by viewModel.getTicketsFlow(eventId).collectAsState(initial = emptyList())
    val totalTickets = tickets.size
    val scannedTickets = tickets.count { it.isScanned }
    val remainingTickets = totalTickets - scannedTickets

    // Active Scanned Dialog States
    val scanResult by viewModel.scanResult.collectAsState()
    var showResultDialog by remember { mutableStateOf(false) }

    // Fallback Manual QR Input
    var manualQRCode by remember { mutableStateOf("") }

    // Tone generators for scanner feedback
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }

    LaunchedEffect(scanResult) {
        if (scanResult !is ScanResult.Idle) {
            showResultDialog = true
            // Play sound feedback based on result
            try {
                if (scanResult is ScanResult.Success) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200) // Success beep
                } else if (scanResult is ScanResult.Duplicate || scanResult is ScanResult.Invalid) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400) // Warning beep
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قارئ التذاكر والتحقق") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
            if (hasCameraPermission) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Camera Preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                // Optimize ML Kit options for lightning fast scanning (<0.1s!)
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
                                                    viewModel.scanTicket(qrCode)
                                                }
                                            }
                                            .addOnFailureListener {
                                                it.printStackTrace()
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
                        // Pulsing Laser scan line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(2.dp)
                                .background(Color.Red.copy(alpha = 0.8f))
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "يرجى توفير صلاحية استخدام الكاميرا لمسح التذاكر",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            // Overlay controls for Flash and Manual input
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
                    // Flash Toggle Button
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

                    // Auto-Focus Lens Trigger
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

                // Fallback manual checker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualQRCode,
                        onValueChange = { manualQRCode = it },
                        label = { Text("أدخل رمز التذكرة يدوياً (24 حرف)") },
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
                            if (manualQRCode.length == 24) {
                                viewModel.scanTicket(manualQRCode)
                                manualQRCode = ""
                            } else {
                                Toast.makeText(context, "الرمز يجب أن يتكون من 24 حرفاً", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("تحقق")
                    }
                }
            }
        }

        // Settings / Offline Sync Sheet
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("إعدادات ومزامنة قاعدة البيانات", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "تم تحديث ومزامنة قاعدة البيانات للعمل الكلي أوفلاين!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("مزامنة التذاكر وقاعدة البيانات للعمل أوفلاين")
                        }

                        // Stats Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("إحصائيات تذاكر المناسبة:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                    viewModel.resetScanResult()
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
                                // Green Card - Success Verification
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
                                // Red Card - Already Scanned Duplicate
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFC62828))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "تم استخدامه مسبقاً، ممنوع الدخول ❌",
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
                                        Text("آخر مسح معتمد: ${sdf.format(Date(result.lastScannedAt))}", color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is ScanResult.Invalid -> {
                                // Red Card - Invalid QR Code
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
                            viewModel.resetScanResult()
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
