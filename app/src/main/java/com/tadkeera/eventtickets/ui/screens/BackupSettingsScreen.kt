package com.tadkeera.eventtickets.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRestoringTelegram by remember { mutableStateOf(false) }

    // Launcher for DB backup importer
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            viewModel.restoreDatabaseBackup(uri) { success, msg ->
                if (success) {
                    Toast.makeText(context, "تم استعادة البيانات بنجاح! يرجى إعادة تشغيل التطبيق لتحديث المناسبات.", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "فشل الاستعادة: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات والنسخ الاحتياطي", fontWeight = FontWeight.Bold) },
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
            // Modern Elegant Card with subtle gradient for branding
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مركز حماية البيانات والنسخ الاحتياطي 🔒",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "احمِ بيانات مناسباتك وتذاكرك بالكامل؛ قم بإنشاء واستعادة النسخ الاحتياطية محلياً في مجلد BACKUP بالهاتف، أو اربط تليجرام لمزامنتها سحابياً وتنزيلها بضغطة زر واحدة.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Link Telegram Button (NEW!)
            Button(
                onClick = { navController.navigate("telegram_link") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Text(
                    text = "ربط وتأكيد اتصال تليجرام 🔗",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // 2. Create Backup Button
            Button(
                onClick = {
                    viewModel.triggerManualBackup { success, msg ->
                        if (success) {
                            Toast.makeText(context, msg ?: "تم النسخ الاحتياطي بنجاح!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "فشل النسخ الاحتياطي: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "إنشاء نسخة احتياطية جديدة محلياً (.db) 💾",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // 3. Restore Backup Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    restoreFileLauncher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = "استعادة نسخة احتياطية من الهاتف 🔄",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // 4. Restore Backup from Telegram Button (NEW!)
            if (isRestoringTelegram) {
                var pulse by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (true) {
                        pulse = !pulse
                        kotlinx.coroutines.delay(600)
                    }
                }
                Text(
                    text = "جاري استعادة البيانات من سحابة تليجرام... 📥",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (pulse) MaterialTheme.colorScheme.primary else Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else {
                Button(
                    onClick = {
                        isRestoringTelegram = true
                        viewModel.restoreBackupFromTelegram { success, msg ->
                            isRestoringTelegram = false
                            if (success) {
                                Toast.makeText(context, msg ?: "تم استعادة البيانات بنجاح!", Toast.LENGTH_LONG).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "فشل استعادة البيانات من تليجرام: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0), contentColor = Color.White)
                ) {
                    Text(
                        text = "استعادة نسخة احتياطية من تليجرام ☁️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
