package com.tadkeera.eventtickets.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.tadkeera.eventtickets.data.entities.ScannerUser
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ORANGE = Color(0xFFFF6D00)
private val NAVY = Color(0xFF111E38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSettingsScreen(
    eventId: String,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val event = viewModel.getEventFlow(eventId).collectAsState(initial = null).value
    val users by viewModel.getScannerUsersFlow(eventId).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH) }

    var isEditingDate by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingEndDate by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("إعدادات المناسبة", fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NAVY)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- End date & activation code ----------
            SettingsCard(title = "تاريخ انتهاء المناسبة") {
                val hasEnd = (event?.endDate ?: 0L) > 0L
                Text(
                    "التاريخ الحالي: " + if (hasEnd) dateFormat.format(Date(event!!.endDate)) else "غير محدد",
                    fontWeight = FontWeight.Bold, color = NAVY
                )
                Text(
                    "كود التفعيل: " + (event?.activationCode?.takeIf { it.isNotEmpty() } ?: "—"),
                    fontWeight = FontWeight.Bold, color = NAVY
                )

                if (!isEditingDate) {
                    Button(
                        onClick = { isEditingDate = true; pendingEndDate = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasEnd) "تعديل" else "تحديد تاريخ الانتهاء")
                    }
                } else {
                    Text(
                        "التاريخ الجديد: " + (pendingEndDate?.let { dateFormat.format(Date(it)) } ?: "لم يتم اختيار تاريخ"),
                        color = NAVY
                    )
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("اختيار التاريخ من التقويم")
                    }
                    Button(
                        enabled = pendingEndDate != null,
                        onClick = {
                            val picked = pendingEndDate ?: return@Button
                            if (picked < startOfTodayMillis()) {
                                Toast.makeText(context, "لا يمكن اختيار تاريخ سابق", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateEventEndDate(eventId, endOfDayLocal(picked)) {
                                Toast.makeText(context, "تم حفظ التعديل وإنشاء كود تفعيل جديد", Toast.LENGTH_SHORT).show()
                                isEditingDate = false
                                pendingEndDate = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ التعديل")
                    }
                    TextButton(onClick = { isEditingDate = false; pendingEndDate = null }) {
                        Text("إلغاء")
                    }
                }
            }

            // ---------- Scanner users ----------
            SettingsCard(title = "مستخدمو الماسح (البوابات)") {
                Button(onClick = { viewModel.addScannerUser(eventId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("إنشاء مستخدم")
                }
                if (users.isEmpty()) {
                    Text("لم يتم إنشاء مستخدمين بعد", color = Color(0xFF546E7A))
                }
                users.forEach { user ->
                    UserRow(user = user, onCreateCode = { viewModel.createScannerUserCode(user) })
                }
            }

            // ---------- Export ----------
            Button(
                onClick = {
                    val ready = event != null && event.endDate > 0L && users.any { it.accessCode.isNotEmpty() }
                    if (!ready) {
                        Toast.makeText(
                            context,
                            "يجب تحديد تاريخ الانتهاء وإنشاء كود لمستخدم واحد على الأقل قبل التصدير",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        viewModel.exportEventBundle(eventId) { file ->
                            if (file == null) {
                                Toast.makeText(context, "فشل تصدير بيانات المناسبة", Toast.LENGTH_SHORT).show()
                            } else {
                                shareEventBundle(context, file)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE)
            ) {
                Text("تصدير بيانات المناسبة", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingEndDate ?: startOfTodayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingEndDate = pickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("موافق") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, ORANGE)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ORANGE)
            content()
        }
    }
}

@Composable
private fun UserRow(user: ScannerUser, onCreateCode: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(user.name, fontWeight = FontWeight.Bold, color = NAVY)
            if (user.accessCode.isEmpty()) {
                Text("لم يتم إنشاء كود لهذا المستخدم", color = Color(0xFF546E7A), fontSize = 13.sp)
            } else {
                Text("كود المستخدم: ${user.accessCode}", fontWeight = FontWeight.Bold, color = NAVY)
            }
            OutlinedButton(onClick = onCreateCode, modifier = Modifier.fillMaxWidth()) {
                Text(if (user.accessCode.isEmpty()) "إنشاء كود المستخدم" else "إعادة إنشاء الكود")
            }
        }
    }
}

/** Midnight (local) of today, in millis. */
private fun startOfTodayMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * DatePicker returns UTC midnight of the chosen day. Convert it to 23:59:59 of the same
 * calendar day in local time, so the event stays valid for the whole day.
 */
private fun endOfDayLocal(pickerMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun shareEventBundle(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "تصدير بيانات المناسبة"))
}
