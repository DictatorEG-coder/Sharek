package com.sharek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101014)
private val SurfaceDark = Color(0xFF1B1B22)
private val Crimson = Color(0xFFE53950)
private val Electric = Color(0xFF8ADFFF)
private val Muted = Color(0xFF9A9AA5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SharekApp() }
    }
}

@Composable
private fun SharekApp() {
    var screen by remember { mutableStateOf("home") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Crimson,
            secondary = Electric,
            background = Background,
            surface = SurfaceDark
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            when (screen) {
                "send" -> PlaceholderScreen("إرسال الملفات", "اختر الملفات التي تريد إرسالها.", { screen = "home" })
                "receive" -> PlaceholderScreen("استلام الملفات", "سنبحث عن الهاتف الآخر بعد تجهيز الاتصال.", { screen = "home" })
                else -> HomeScreen(
                    onSend = { screen = "send" },
                    onReceive = { screen = "receive" }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(42.dp))
        Box(
            modifier = Modifier.size(86.dp).background(SurfaceDark, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("ش", color = Crimson, fontSize = 52.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(18.dp))
        Text("شارك", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("أرسل. استقبل. ببساطة.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(58.dp))
        ActionCard("إرسال", "أرسل الصور والفيديوهات والملفات", Crimson, onSend) {
            Icon(Icons.Default.CallMade, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        ActionCard("استلام", "استقبل الملفات من هاتف قريب", Electric, onReceive) {
            Icon(Icons.Default.CallReceived, null, tint = Background, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("لا حاجة إلى اتصال بالإنترنت", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text("شارك • الإصدار التجريبي 0.1", color = Color(0xFF666671), fontSize = 11.sp)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SurfaceDark,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(54.dp).background(accent, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(color = Crimson)
        Spacer(Modifier.height(28.dp))
        Text("صلِّ على النبي ﷺ", color = Electric, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
    }
}
