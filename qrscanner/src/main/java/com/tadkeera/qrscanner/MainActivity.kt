package com.tadkeera.qrscanner

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val NAVY = Color(0xFF111E38)
private val ORANGE = Color(0xFFFF6D00)
private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)
private val BLACK = Color(0xFF000000)

/** Dates are always shown with English digits, e.g. 2026/10/31 20:15:03. */
private fun fmtTime(ts: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH).format(Date(ts))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = NAVY, secondary = ORANGE)) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
                    ScannerRoot()
                }
            }
        }
    }
}

private enum class Screen { LOGIN, SCANNER, QUERY, SETTINGS }

@Composable
private fun ScannerRoot() {
    val vm: ScannerViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(Screen.LOGIN) }
    when (screen) {
        Screen.LOGIN -> LoginScreen(vm) { screen = Screen.SCANNER }
        Screen.SCANNER -> ScannerScreen(
            vm = vm,
            onQuery = { screen = Screen.QUERY },
            onSettings = { screen = Screen.SETTINGS },
            onLogout = { screen = Screen.LOGIN }
        )
        Screen.QUERY -> QueryScreen(vm) { screen = Screen.SCANNER }
        Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.SCANNER }, onLogout = { screen = Screen.LOGIN })
    }
}

// ============================ Login ============================

@Composable
private fun LoginScreen(vm: ScannerViewModel, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var userCode by rememberSaveable { mutableStateOf("") }
    var activationCode by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            message = if (bytes == null) {
                "تعذر قراءة الملف."
            } else {
                val error = vm.importBundle(bytes)
                error ?: "تم استيراد البيانات للعمل أوفلاين ✅\n\n" +
                    "المناسبة: ${vm.bundle?.eventName.orEmpty()}\n" +
                    "عدد التذاكر: ${vm.bundle?.tickets?.size ?: 0}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("QR-SCANER", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NAVY)
        Text("تسجيل الدخول للماسح", fontSize = 16.sp, color = Color(0xFF546E7A))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, ORANGE)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = userCode,
                    onValueChange = { userCode = it.uppercase() },
                    label = { Text("كود المستخدم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = activationCode,
                    onValueChange = { activationCode = it.uppercase() },
                    label = { Text("كود التفعيل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("استيراد بيانات المناسبة") }

                Button(
                    enabled = !busy && vm.bundle != null && userCode.isNotBlank() && activationCode.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val error = vm.activate(userCode, activationCode)
                            busy = false
                            if (error == null) onLoggedIn() else message = error
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ORANGE)
                ) { Text(if (busy) "جاري التحقق..." else "استيراد", fontWeight = FontWeight.Bold) }
            }
        }
    }

    message?.let { text ->
        InfoDialog(
            title = if (text.startsWith("تم استيراد")) "تم الاستيراد" else "تنبيه",
            text = text,
            color = if (text.startsWith("تم استيراد")) GREEN else RED,
            onDismiss = { message = null }
        )
    }
}

// ============================ Scanner ============================

@Composable
private fun ScannerScreen(
    vm: ScannerViewModel,
    onQuery: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) cameraPermission.launch(Manifest.permission.CAMERA)
    }
    var modeMenu by remember { mutableStateOf(false) }
    val s = vm.stats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top bar: user name, then query / export / mode / settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("المستخدم: ${vm.operatorName}", fontWeight = FontWeight.Bold, color = NAVY, fontSize = 16.sp)
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = NAVY) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onQuery, modifier = Modifier.weight(1f)) { Text("استعلام", fontSize = 13.sp) }
            OutlinedButton(
                onClick = { exportReport(context, vm) },
                modifier = Modifier.weight(1f)
            ) { Text("تصدير", fontSize = 13.sp) }
            Box(modifier = Modifier.weight(1f)) {
                Button(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.scanMode == Mode.ENTRY) "وضع: دخول" else "وضع: خروج", fontSize = 13.sp)
                }
                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    DropdownMenuItem(text = { Text("دخول") }, onClick = { vm.setMode(Mode.ENTRY); modeMenu = false })
                    DropdownMenuItem(text = { Text("خروج") }, onClick = { vm.setMode(Mode.EXIT); modeMenu = false })
                }
            }
        }

        // Camera
        Card(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (hasCamera && vm.lockMessage == null) {
                CameraPreview(onCode = { vm.onScanned(it) })
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF263238)), contentAlignment = Alignment.Center) {
                    Text("الكاميرا غير متاحة", color = Color.White)
                }
            }
        }

        // Statistics: the scanned count is black, as requested
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, ORANGE)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("إحصائيات المناسبة", fontWeight = FontWeight.Bold, color = ORANGE)
                Text("عدد التذاكر الممسوحة: ${s?.everEntered ?: 0} من ${s?.total ?: 0}",
                    fontWeight = FontWeight.Bold, color = BLACK, fontSize = 18.sp)
                Text("داخل المناسبة الآن: ${s?.inside ?: 0}", color = NAVY)
                Text("المتبقي: ${s?.remaining ?: 0}", color = NAVY)
                Text("محاولات التزوير: ${s?.forgeries ?: 0}", color = NAVY)
            }
        }
    }

    vm.lastVerdict?.let { verdict ->
        VerdictDialog(verdict = verdict, onNext = { vm.dismissResult() })
    }

    vm.lockMessage?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("تنبيه أمني", fontWeight = FontWeight.Bold, color = RED) },
            text = { Text(text) },
            confirmButton = {
                Button(onClick = { vm.dismissLock(); onLogout() }) { Text("حسناً") }
            }
        )
    }
}

@Composable
private fun VerdictDialog(verdict: Verdict, onNext: () -> Unit) {
    val (color, title, details) = when (verdict) {
        is Verdict.Entered -> Triple(GREEN, "مسموح الدخول ✅", ticketLines(verdict.ticket))
        is Verdict.Exited -> Triple(GREEN, "مسموح الخروج ✅", ticketLines(verdict.ticket))
        is Verdict.Duplicate -> Triple(RED, "مكرر ❌ التذكرة دخلت مسبقاً", ticketLines(verdict.ticket) +
            "\nآخر دخول: ${fmtTime(verdict.lastEntry)}")
        is Verdict.NotInside -> Triple(RED, "لم يتم تسجيل دخول لهذه التذكرة ❌", ticketLines(verdict.ticket))
        is Verdict.Forged -> Triple(RED, "تذكرة غير صالحة أو مزورة ❌",
            "هذا الكود غير مسجل في بيانات المناسبة.\nالكود: ${verdict.preview}")
    }
    AlertDialog(
        onDismissRequest = onNext,
        title = null,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(8.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                }
                Text(details, textAlign = TextAlign.Center, color = NAVY)
            }
        },
        confirmButton = {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("مسح التذكرة التالية") }
        }
    )
}

private fun ticketLines(t: BundleTicket): String =
    "رقم التذكرة: ${t.ticketNumber}" +
        (if (t.guestName.isNotEmpty()) "\nالاسم: ${t.guestName}" else "") +
        "\nكود المناسبة: ${t.eventCode}"

// ============================ Query ============================

@Composable
private fun QueryScreen(vm: ScannerViewModel, onBack: () -> Unit) {
    var eventCode by rememberSaveable { mutableStateOf("") }
    var ticketNo by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("استعلام عن تذكرة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NAVY)
        OutlinedTextField(
            value = eventCode, onValueChange = { eventCode = it.uppercase() },
            label = { Text("كود المناسبة") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ticketNo, onValueChange = { ticketNo = it.filter(Char::isDigit) },
            label = { Text("رقم المناسبة (رقم التذكرة)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val number = ticketNo.toIntOrNull()
                val ticket = if (number == null) null else vm.findTicket(eventCode, number)
                if (ticket == null) {
                    result = "لا توجد تذكرة بهذا الكود والرقم."
                    lines = emptyList()
                } else {
                    val timeline = vm.timelineOf(ticket)
                    result = "رقم التذكرة: ${ticket.ticketNumber}" +
                        (if (ticket.guestName.isNotEmpty()) " - ${ticket.guestName}" else "") +
                        "\nعدد الحركات: ${timeline.size}"
                    lines = timeline.map { (if (it.type == "IN") "دخول" else "خروج") + " — " + fmtTime(it.ts) + " — " + it.operator }
                        .ifEmpty { listOf("لم تُسجَّل أي حركة لهذه التذكرة.") }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("استعلام") }

        result?.let { Text(it, fontWeight = FontWeight.Bold, color = NAVY) }
        lines.forEach { Text(it, color = if (it.startsWith("دخول")) GREEN else if (it.startsWith("خروج")) RED else NAVY) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("رجوع لمسح التذاكر") }
    }
}

// ============================ Settings ============================

@Composable
private fun SettingsScreen(vm: ScannerViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val s = vm.stats
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = NAVY) }
            Text("لوحة التحكم", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NAVY)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, ORANGE)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الربط والشبكات المحلية المتعددة (P2P) أوفلاين", fontWeight = FontWeight.Bold, color = ORANGE)
                listOf("STANDALONE" to "مستقل", "ORGANIZER" to "منظم", "HOST" to "مستضيف").forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.p2pMode == key, onClick = { vm.setP2P(key) })
                        Text(label, color = NAVY)
                    }
                }
                Text(
                    "اختيار الوضع يُحفظ على الجهاز. المزامنة بين عدة أجهزة ستُفعَّل في تحديث لاحق.",
                    fontSize = 12.sp, color = Color(0xFF546E7A)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, ORANGE)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إحصائيات تذاكر المناسبة الحالية", fontWeight = FontWeight.Bold, color = ORANGE)
                Text("عدد التذاكر الممسوحة: ${s?.everEntered ?: 0}", color = BLACK, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("إجمالي التذاكر: ${s?.total ?: 0}", color = NAVY)
                Text("داخل المناسبة الآن: ${s?.inside ?: 0}", color = NAVY)
                Text("المتبقي: ${s?.remaining ?: 0}", color = NAVY)
                Text("محاولات التزوير: ${s?.forgeries ?: 0}", color = NAVY)
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RED)
        ) { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) }
    }
}

// ============================ Shared pieces ============================

@Composable
private fun InfoDialog(title: String, text: String, color: Color, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = color) },
        text = { Text(text, textAlign = TextAlign.Center) },
        confirmButton = { Button(onClick = onDismiss) { Text("حسناً") } }
    )
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                )
                val executor = Executors.newSingleThreadExecutor()
                analysis.setAnalyzer(executor) { proxy ->
                    scanFrame(proxy, scanner) { code -> currentOnCode(code) }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@OptIn(ExperimentalGetImage::class)
private fun scanFrame(
    proxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCode: (String) -> Unit
) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstOrNull()?.rawValue?.let { if (it.isNotEmpty()) onCode(it) }
        }
        .addOnCompleteListener { proxy.close() }
}

/** Shares the encrypted report. WhatsApp is preferred; the system chooser is the fallback. */
private fun exportReport(context: Context, vm: ScannerViewModel) {
    val file: File? = vm.buildReportFile()
    if (file == null) {
        Toast.makeText(context, "لا توجد بيانات مناسبة للتصدير", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent(send).setPackage("com.whatsapp"))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(send, "إرسال تقرير المناسبة"))
    }
}
