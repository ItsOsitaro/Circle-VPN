package com.example

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: VpnViewModel = viewModel()
                val context = LocalContext.current

                // Initialize state
                LaunchedEffect(Unit) {
                    viewModel.init(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    
                    when (currentScreen) {
                        is Screen.Login -> LoginScreen(viewModel = viewModel)
                        is Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var subUrl by remember { mutableStateOf("") }
    val loginLoading by viewModel.loginLoading.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Soft background ambient radial glows
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(V2Secondary.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.2f),
                        radius = size.width * 0.8f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(V2Primary.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.8f),
                        radius = size.width * 0.9f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Logo Header Section
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .shadow(20.dp, CircleShape, spotColor = V2Secondary)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vpn_logo),
                    contentDescription = "Circle VPN Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Circle VPN",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "سرویس اتصال امن و هوشمند",
                fontSize = 14.sp,
                color = V2TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Form Section Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = V2Surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ورود با لینک سابسکرایب",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    
                    Text(
                        text = "لینک اشتراک v2ray خود را جهت استخراج خودکار وارد نمایید",
                        fontSize = 12.sp,
                        color = V2TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 16.dp),
                        textAlign = TextAlign.Right
                    )

                    // URL Input Field
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_input"),
                        placeholder = { 
                            Text(
                                "https://example.com/sub.txt", 
                                color = V2TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Left
                            ) 
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = "Link Icon",
                                tint = V2Primary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = V2Secondary,
                            unfocusedBorderColor = V2TextSecondary.copy(alpha = 0.3f),
                            focusedContainerColor = V2Background,
                            unfocusedContainerColor = V2Background
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Error text display
                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = loginError ?: "",
                                color = V2Error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Error Icon",
                                tint = V2Error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Login Button (Blue)
                    Button(
                        onClick = { viewModel.loginWithSubscription(context, subUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = V2Primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loginLoading
                    ) {
                        if (loginLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "ورود و بارگذاری",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Trial Pre-fill Option for Testability
                    Text(
                        text = "می‌خواهید دمو را تست کنید؟ ضربه بزنید",
                        fontSize = 11.sp,
                        color = V2Secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                // Pre-fill with a functional fallback sub string
                                subUrl = "https://pub-c7f8a96499894bd999fe23ca10ebf15c.r2.dev/v2tunnel_test_sub.txt"
                            }
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun DashboardScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val configs by viewModel.configs.collectAsState()
    val serverPings by viewModel.serverPings.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val livePing by viewModel.livePing.collectAsState()
    val liveSpeedDownload by viewModel.liveSpeedDownload.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()
    val diagnosticLogs by VpnDiagnosticManager.logs.collectAsState()

    // ActivityResultLauncher for registering Android VpnService permissions
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpnConnection(context)
        } else {
            Toast.makeText(context, "VPN permissions declined", Toast.LENGTH_SHORT).show()
            viewModel.disconnectVpn(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = V2Background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action buttons (Logout & Refresh)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.logout(context) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(V2Surface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = V2TextSecondary
                        )
                    }

                    val refreshing by viewModel.refreshing.collectAsState()
                    IconButton(
                        onClick = {
                            if (!refreshing) {
                                viewModel.refreshSubscription(
                                    context = context,
                                    onSuccess = {
                                        Toast.makeText(context, "لیست سرورها بروزرسانی شد", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(V2Surface, CircleShape)
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = V2Secondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = V2Secondary
                            )
                        }
                    }
                }

                Text(
                    text = "Circle VPN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Info pill
                Box(
                    modifier = Modifier
                        .background(V2Surface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${configs.size} سرور",
                            fontSize = 11.sp,
                            color = V2Secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = "Server Count",
                            tint = V2Secondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Connection Core Button Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Pulsing Ring Animation
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseScale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseAlpha"
                    )

                    val coreColor by animateColorAsState(
                        targetValue = when (vpnStatus) {
                            VpnStatus.CONNECTED -> V2Success
                            VpnStatus.CONNECTING -> Color(0xFFFFB300) // Amber
                            VpnStatus.DISCONNECTED -> V2Primary
                        },
                        animationSpec = tween(500),
                        label = "coreColor"
                    )

                    // Pulse effect
                    if (vpnStatus != VpnStatus.DISCONNECTED) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = coreColor,
                                        radius = (size.minDimension / 2f) * pulseScale,
                                        alpha = pulseAlpha
                                    )
                                }
                        )
                    }

                    // Main Interaction Button
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        coreColor,
                                        coreColor.copy(alpha = 0.6f),
                                        coreColor.copy(alpha = 0.15f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .shadow(12.dp, CircleShape, spotColor = coreColor)
                            .clickable {
                                // Request VPN permissions first if connecting
                                if (vpnStatus == VpnStatus.DISCONNECTED) {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPrepareLauncher.launch(intent)
                                    } else {
                                        viewModel.toggleVpnConnection(context)
                                    }
                                } else {
                                    viewModel.toggleVpnConnection(context)
                                }
                            }
                            .testTag("connection_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (vpnStatus) {
                                    VpnStatus.CONNECTED -> Icons.Filled.Check
                                    VpnStatus.CONNECTING -> Icons.Filled.HourglassEmpty
                                    VpnStatus.DISCONNECTED -> Icons.Filled.PowerSettingsNew
                                },
                                contentDescription = "Power Trigger",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (vpnStatus) {
                                    VpnStatus.CONNECTED -> "متصل"
                                    VpnStatus.CONNECTING -> "در حال تست..."
                                    VpnStatus.DISCONNECTED -> "اتصال"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // 2. Status Label Section
            item {
                Text(
                    text = when (vpnStatus) {
                        VpnStatus.CONNECTED -> "متصل شد به: ${selectedServer?.remarks ?: "بهترین سرور"}"
                        VpnStatus.CONNECTING -> "تست تک تک سرورها و انتخاب کمترین پینگ..."
                        VpnStatus.DISCONNECTED -> "غیرفعال (برای اتصال روی دکمه ضربه بزنید)"
                    },
                    fontSize = 13.sp,
                    color = when (vpnStatus) {
                        VpnStatus.CONNECTED -> V2Success
                        VpnStatus.CONNECTING -> Color(0xFFFFB300)
                        VpnStatus.DISCONNECTED -> V2TextSecondary
                    },
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 3. Connection Live Stats Rows
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ping Display Widget
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = V2Surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("تأخیر (پینگ)", fontSize = 11.sp, color = V2TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (vpnStatus == VpnStatus.CONNECTED && livePing > 0) "$livePing ms" else "---",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (vpnStatus == VpnStatus.CONNECTED) V2Success else Color.White
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Ping Icon",
                                tint = V2Secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Real-Time Speed Display Widget
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = V2Surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("سرعت دانلود", fontSize = 11.sp, color = V2TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (vpnStatus == VpnStatus.CONNECTED) String.format("%.1f KB/s", liveSpeedDownload) else "0.0 KB/s",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = V2Secondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Speed Icon",
                                tint = V2Secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 4. Graphical Speed over Time Canvas
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = V2Surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "نمودار نوسان سرعت و پینگ لحظه‌ای",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "ترافیک ورودی زنده دستگاه",
                            fontSize = 10.sp,
                            color = V2TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Drawing Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val maxVal = speedHistory.maxOrNull()?.coerceAtLeast(10f) ?: 10f
                            val width = size.width
                            val height = size.height
                            val sizeHistory = speedHistory.size

                            if (sizeHistory > 1) {
                                val path = Path()
                                val fillPath = Path()

                                // Starting points
                                val xStep = width / (sizeHistory - 1)
                                val firstY = height - (speedHistory[0] / maxVal) * height

                                path.moveTo(0f, firstY)
                                fillPath.moveTo(0f, height)
                                fillPath.lineTo(0f, firstY)

                                for (i in 1 until sizeHistory) {
                                    val currentX = i * xStep
                                    val currentY = height - (speedHistory[i] / maxVal) * height
                                    path.lineTo(currentX, currentY)
                                    fillPath.lineTo(currentX, currentY)
                                }

                                fillPath.lineTo(width, height)
                                fillPath.close()

                                // 1. Draw glowing gradient fill under curve
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(V2Secondary.copy(alpha = 0.25f), Color.Transparent)
                                    )
                                )

                                // 2. Draw line stroke
                                drawPath(
                                    path = path,
                                    color = V2Secondary,
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                // 3. Draw a glowing point at the last value
                                val lastX = width
                                val lastY = height - (speedHistory.last() / maxVal) * height
                                drawCircle(
                                    color = V2Secondary,
                                    radius = 6.dp.toPx(),
                                    center = Offset(lastX, lastY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.dp.toPx(),
                                    center = Offset(lastX, lastY)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Diagnostic and Packet Capture Tool Card
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = V2Surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { VpnDiagnosticManager.clear() },
                                colors = ButtonDefaults.buttonColors(containerColor = V2Error.copy(alpha = 0.15f), contentColor = V2Error),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("پاک کردن لاگ‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Text(
                                text = "ابزار آنالیز و عیب‌یابی پکت‌ها (Diagnostic)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Right
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explanatory card why bypass happens
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = V2Background),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "🔍 چرا ترافیک اصلی گوشی VPN را دور می‌زند؟",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = V2Secondary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "۱. عدم وجود پشته Tun2Socks: اندروید بسته‌های لایه ۳ (IP) را به کارت شبکه مجازی TUN می‌فرستد. اما برای عبور این بسته‌ها از سرورهای V2Ray (که لایه ۷ یا اپلیکیشن هستند)، به یک پشته کاربری TCP/IP (مثل lwIP یا Tun2Socks) برای تبدیل بسته‌های خام IP به سوکت نیاز است. در حال حاضر فقط ترافیک رنج محلی 10.0.0.0/24 مسیردهی می‌شود تا از قطع شدن کلی اینترنت گوشی جلوگیری شود.\n" +
                                           "۲. وابستگی به پراکسی سیستم (HttpProxy): پراکسی سیستم اندروید فقط توسط برنامه‌هایی که تنظیمات سیستم را می‌خوانند (مانند برخی مرورگرها) اعمال می‌شود. بقیه اپلیکیشن‌ها و بازی‌ها مستقیماً با سرور خارج ارتباط برقرار کرده و به طور کامل VPN را دور می‌زند.",
                                    fontSize = 11.sp,
                                    color = V2TextSecondary,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "لاگ زنده پکت‌های دریافتی:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (diagnosticLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(V2Background, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "هیچ پکت یا اتصالی ثبت نشده است\n(برای ثبت لاگ، از برنامه‌های دیگر استفاده کنید)",
                                    fontSize = 11.sp,
                                    color = V2TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                diagnosticLogs.forEach { log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(V2Background, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left side: Status Chip
                                        val statusColor = when (log.status) {
                                            "TUNNELED" -> V2Success
                                            "BYPASSED" -> Color(0xFFFFB300)
                                            "FAILED" -> V2Error
                                            else -> V2Secondary
                                        }
                                        val statusText = when (log.status) {
                                            "TUNNELED" -> "تونل شده"
                                            "BYPASSED" -> "دور زدن"
                                            "FAILED" -> "ناموفق"
                                            else -> "اطلاعات"
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Right side: Log Information
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Text(
                                                    text = log.description,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    textAlign = TextAlign.Right
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "[${log.type}]",
                                                    fontSize = 9.sp,
                                                    color = V2Secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = log.details,
                                                fontSize = 10.sp,
                                                color = V2TextSecondary,
                                                textAlign = TextAlign.Right,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                            Text(
                                                text = timeFormat.format(java.util.Date(log.timestamp)),
                                                fontSize = 8.sp,
                                                color = V2TextSecondary.copy(alpha = 0.6f),
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
