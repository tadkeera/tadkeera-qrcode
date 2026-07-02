package com.tadkeera.eventtickets.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tadkeera.eventtickets.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLinkScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("TadkeeraTelegram", Context.MODE_PRIVATE) }

    // Use user's requested new default credentials!
    val defaultBotToken = "8855448849:AAEOMwTZFNlZ2dRFwbsPdUjsMVVQDwg6_R0"
    val defaultChannelId = "-1004389676098"

    var botToken by remember { mutableStateOf(prefs.getString("bot_token", defaultBotToken) ?: "") }
    var channelId by remember { mutableStateOf(prefs.getString("channel_id", defaultChannelId) ?: "") }

    var isConnecting by remember { mutableStateOf(false) }
    var isConnectionSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "ربط بوت وقناة تليجرام", 
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111E38)) // Navy header
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)) // Crisp Light Grey Bg
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant Info Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFFFFD54F))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp), 
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "إعدادات الربط المباشر مع تليجرام ⚡",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFFF6D00),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "أدخل بيانات البوت ومعرف القناة (Channel ID) الخاص بك ليقوم التطبيق برفع النسخ الاحتياطية وتذاكر الـ PDF تلقائياً وفورياً إليها عند حدوث أي تعديل.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF546E7A),
                        textAlign = TextAlign.Right,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Input Fields with fixed complete borders (no clashing shadow/clipping)
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "توكن البوت (Bot Token)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111E38),
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { 
                        botToken = it
                        isConnectionSuccess = false
                    },
                    placeholder = { Text("8855448849:AAEOMw...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF111E38),
                        unfocusedTextColor = Color(0xFF111E38),
                        focusedBorderColor = Color(0xFFFF6D00),
                        unfocusedBorderColor = Color(0xFFB0BEC5)
                    )
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "آي دي القناة (Channel ID)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111E38),
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = channelId,
                    onValueChange = { 
                        channelId = it
                        isConnectionSuccess = false
                    },
                    placeholder = { Text("-1004389676098") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF111E38),
                        unfocusedTextColor = Color(0xFF111E38),
                        focusedBorderColor = Color(0xFFFF6D00),
                        unfocusedBorderColor = Color(0xFFB0BEC5)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Loading / connection status visual feedback
            if (isConnecting) {
                var pulse by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (true) {
                        pulse = !pulse
                        kotlinx.coroutines.delay(600)
                    }
                }
                Text(
                    text = "جاري التحقق من الاتصال بالخادم... 📡",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (pulse) Color(0xFFFF6D00) else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isConnectionSuccess) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text("تم الاتصال والربط بنجاح! ✅", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save and Check Connection button (Vibrant Orange matching screens!)
            Button(
                onClick = {
                    if (botToken.isBlank() || channelId.isBlank()) {
                        Toast.makeText(context, "الرجاء تعبئة كافة الحقول أولاً", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isConnecting = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Test connection by sending a quick greeting message
                            val textMsg = URLEncoder.encode("🔔 تم ربط نظام تذكرة للفعاليات بنجاح تام! قاعدة البيانات والمزامنة تعمل الآن في الخلفية.", "UTF-8")
                            val url = URL("https://api.telegram.org/bot$botToken/sendMessage?chat_id=$channelId&text=$textMsg&parse_mode=Markdown")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connect()
                            
                            val code = conn.responseCode
                            conn.disconnect()
                            
                            withContext(Dispatchers.Main) {
                                isConnecting = false
                                if (code == 200) {
                                    // Save credentials to SharedPreferences
                                    prefs.edit().apply {
                                        putString("bot_token", botToken.trim())
                                        putString("channel_id", channelId.trim())
                                        apply()
                                    }
                                    isConnectionSuccess = true
                                    Toast.makeText(context, "تم حفظ الإعدادات ونجاح اختبار الاتصال!", Toast.LENGTH_SHORT).show()
                                } else {
                                    isConnectionSuccess = false
                                    Toast.makeText(context, "فشل الاتصال: رمز الاستجابة $code. الرجاء التحقق من صحة التوكن وآي دي القناة.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isConnecting = false
                                isConnectionSuccess = false
                                Toast.makeText(context, "خطأ في الشبكة: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00))
            ) {
                Text("حفظ والتحقق من الاتصال ⚡", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
