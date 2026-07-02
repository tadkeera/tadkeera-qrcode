package com.tadkeera.eventtickets.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    var isRestoringTelegram by remember { mutableStateOf(false) }

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
                title = { 
                    Text(
                        "الإعدادات والنسخ الاحتياطي", 
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
            // "مركز حماية البيانات والنسخ الاحتياطي" Premium Banner Card (Screenshot 3 Style)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), // Light Blue background
                border = BorderStroke(1.dp, Color(0xFF90CAF9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "مركز حماية البيانات والنسخ الاحتياطي",
                            color = Color(0xFF111E38), // Navy Title
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "احمِ بيانات مناسباتك وتذاكرك بالكامل؛ قم بإنشاء واستعادة النسخ الاحتياطية بالهاتف، أو اربط تليجرام BACKUP الاحتياطية محلياً في مجلد سحابياً وتنزيلها بضغطة زر واحدة.",
                            color = Color(0xFF455A64), // Muted Text
                            fontSize = 12.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Large Lock Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFFFFD54F), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Link Telegram (Purple Button)
            BackupButton(
                text = "ربط وتأكيد اتصال تليجرام",
                icon = Icons.Default.Link,
                backgroundColor = Color(0xFF4A148C), // Deep Purple
                onClick = { navController.navigate("telegram_link") }
            )

            // 2. Create Backup Locally (Blue Button)
            BackupButton(
                text = "إنشاء نسخة احتياطية جديدة محلياً (.db)",
                icon = Icons.Default.Save,
                backgroundColor = Color(0xFF1976D2), // Royal Blue
                onClick = {
                    viewModel.triggerManualBackup { success, msg ->
                        if (success) {
                            Toast.makeText(context, msg ?: "تم النسخ الاحتياطي بنجاح!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "فشل النسخ الاحتياطي: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            // 3. Restore Backup Locally (Orange Button)
            BackupButton(
                text = "استعادة نسخة احتياطية من الهاتف",
                icon = Icons.Default.Autorenew,
                backgroundColor = Color(0xFFFF6D00), // Vibrant Orange
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    restoreFileLauncher.launch(intent)
                }
            )

            // 4. Restore Backup from Telegram (Cloud Blue Button)
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
                    color = if (pulse) Color(0xFFFF6D00) else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            } else {
                BackupButton(
                    text = "استعادة نسخة احتياطية من تليجرام",
                    icon = Icons.Default.CloudDownload,
                    backgroundColor = Color(0xFF1E88E5), // Telegram Sky Blue
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
                    }
                )
            }
        }
    }
}

@Composable
fun BackupButton(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon on the left
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            // Text on the right
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = Color.White,
                textAlign = TextAlign.Right
            )
        }
    }
}
