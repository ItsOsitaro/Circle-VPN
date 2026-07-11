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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
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
                        text = "کد تایید",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    
                    Text(
                        text = "کد تاییدی که دریافت کردید را وارد کنید",
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
                                "Enter Code", 
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
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getDaysRemaining(expireTimestampSec: Long): String {
    if (expireTimestampSec <= 0) return "نامحدود"
    val currentSec = System.currentTimeMillis() / 1000L
    val diffSec = expireTimestampSec - currentSec
    if (diffSec <= 0) return "منقضی شده"
    val days = diffSec / (24L * 3600L)
    return if (days == 0L) {
        val hours = diffSec / 3600L
        "$hours ساعت"
    } else {
        "$days روز"
    }
}

fun getCountryForServer(server: V2RayConfig?): String {
    if (server == null) return "نامعلوم"
    val name = server.remarks.lowercase()
    
    return when {
        name.contains("🇺🇸") || name.contains("us") || name.contains("usa") || name.contains("united states") || name.contains("america") -> "ایالات متحده آمریکا 🇺🇸"
        name.contains("🇩🇪") || name.contains("de") || name.contains("germany") || name.contains("deutschland") || name.contains("germ") -> "آلمان 🇩🇪"
        name.contains("🇬🇧") || name.contains("gb") || name.contains("uk") || name.contains("united kingdom") || name.contains("london") || name.contains("england") -> "انگلستان 🇬🇧"
        name.contains("🇫🇷") || name.contains("fr") || name.contains("france") || name.contains("paris") -> "فرانسه 🇫🇷"
        name.contains("🇳🇱") || name.contains("nl") || name.contains("netherlands") || name.contains("amsterdam") || name.contains("holland") -> "هلند 🇳🇱"
        name.contains("🇨🇦") || name.contains("ca") || name.contains("canada") || name.contains("toronto") -> "کانادا 🇨🇦"
        name.contains("🇹🇷") || name.contains("tr") || name.contains("turkey") || name.contains("istanbul") || name.contains("turk") -> "ترکیه 🇹🇷"
        name.contains("🇫🇮") || name.contains("fi") || name.contains("finland") -> "فنلاند 🇫🇮"
        name.contains("🇸🇬") || name.contains("sg") || name.contains("singapore") -> "سنگاپور 🇸🇬"
        name.contains("🇸🇪") || name.contains("se") || name.contains("sweden") -> "سوئد 🇸🇪"
        name.contains("🇳🇴") || name.contains("no") || name.contains("norway") -> "نروژ 🇳🇴"
        name.contains("🇵🇱") || name.contains("pl") || name.contains("poland") -> "لهستان 🇵🇱"
        name.contains("🇮🇷") || name.contains("ir") || name.contains("iran") -> "ایران 🇮🇷"
        name.contains("🇯🇵") || name.contains("jp") || name.contains("japan") || name.contains("tokyo") -> "ژاپن 🇯🇵"
        name.contains("🇷🇺") || name.contains("ru") || name.contains("russia") || name.contains("moscow") -> "روسیه 🇷🇺"
        name.contains("🇦🇪") || name.contains("ae") || name.contains("uae") || name.contains("dubai") -> "امارات متحده عربی 🇦🇪"
        name.contains("🇨🇭") || name.contains("ch") || name.contains("switzerland") || name.contains("zurich") -> "سوئیس 🇨🇭"
        name.contains("🇮🇹") || name.contains("it") || name.contains("italy") || name.contains("rome") || name.contains("milan") -> "ایتالیا 🇮🇹"
        name.contains("🇪🇸") || name.contains("es") || name.contains("spain") || name.contains("madrid") || name.contains("barcelona") -> "اسپانیا 🇪🇸"
        name.contains("🇦🇺") || name.contains("au") || name.contains("australia") || name.contains("sydney") -> "استرالیا 🇦🇺"
        name.contains("🇮🇳") || name.contains("in") || name.contains("india") || name.contains("mumbai") -> "هند 🇮🇳"
        name.contains("🇭🇰") || name.contains("hk") || name.contains("hong kong") -> "هنگ کنگ 🇭🇰"
        name.contains("🇰🇷") || name.contains("kr") || name.contains("korea") || name.contains("seoul") -> "کره جنوبی 🇰🇷"
        name.contains("🇨🇳") || name.contains("cn") || name.contains("china") -> "چین 🇨🇳"
        name.contains("🇮🇪") || name.contains("ie") || name.contains("ireland") || name.contains("dublin") -> "ایرلند 🇮🇪"
        name.contains("🇦🇹") || name.contains("at") || name.contains("austria") || name.contains("vienna") -> "اتریش 🇦🇹"
        name.contains("🇧🇪") || name.contains("be") || name.contains("belgium") || name.contains("brussels") -> "بلژیک 🇧🇪"
        name.contains("🇩🇰") || name.contains("dk") || name.contains("denmark") -> "دانمارک 🇩🇰"
        else -> {
            val hash = Math.abs(server.address.hashCode())
            val countries = listOf(
                "آلمان 🇩🇪", "ایالات متحده آمریکا 🇺🇸", "انگلستان 🇬🇧", "فرانسه 🇫🇷", "هلند 🇳🇱", "کانادا 🇨🇦", "ترکیه 🇹🇷", "سنگاپور 🇸🇬", "سوئد 🇸🇪", "سوئیس 🇨🇭"
            )
            countries[hash % countries.size]
        }
    }
}

@Composable
fun PlugIcon(
    status: VpnStatus,
    rotationAngle: Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val S = size.width
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Draw rotating progress arc along the border if in CONNECTING state
        if (status == VpnStatus.CONNECTING) {
            val strokeW = S * 0.05f
            inset(strokeW / 2f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round
                    )
                )
                drawArc(
                    color = Color.White,
                    startAngle = rotationAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        // Draw the plug shape inside, rotated 45 degrees
        rotate(45f, pivot = Offset(cx, cy)) {
            // 1. Prongs / Pins (drawn first so they appear behind the body if needed)
            // Left prong
            drawLine(
                color = Color.White,
                start = Offset(cx - S * 0.075f, cy - S * 0.05f),
                end = Offset(cx - S * 0.075f, cy - S * 0.22f),
                strokeWidth = S * 0.045f,
                cap = StrokeCap.Round
            )
            // Right prong
            drawLine(
                color = Color.White,
                start = Offset(cx + S * 0.075f, cy - S * 0.05f),
                end = Offset(cx + S * 0.075f, cy - S * 0.22f),
                strokeWidth = S * 0.045f,
                cap = StrokeCap.Round
            )

            // 2. Relief sleeve at bottom of body
            val sleevePath = Path().apply {
                moveTo(cx - S * 0.05f, cy + S * 0.10f)
                lineTo(cx + S * 0.05f, cy + S * 0.10f)
                lineTo(cx + S * 0.05f, cy + S * 0.17f)
                lineTo(cx - S * 0.05f, cy + S * 0.17f)
                close()
            }
            drawPath(sleevePath, color = Color.White)

            // 3. Cable / Cord extending from sleeve
            drawLine(
                color = Color.White,
                start = Offset(cx, cy + S * 0.15f),
                end = Offset(cx, cy + S * 0.28f),
                strokeWidth = S * 0.05f,
                cap = StrokeCap.Round
            )

            // 4. Main body
            val bodyPath = Path().apply {
                // Top flat surface of the plug body
                moveTo(cx - S * 0.16f, cy - S * 0.06f)
                lineTo(cx + S * 0.16f, cy - S * 0.06f)
                // Straight right side
                lineTo(cx + S * 0.16f, cy + S * 0.04f)
                // Smooth rounded bottom
                cubicTo(
                    x1 = cx + S * 0.16f, y1 = cy + S * 0.15f,
                    x2 = cx - S * 0.16f, y2 = cy + S * 0.15f,
                    x3 = cx - S * 0.16f, y3 = cy + S * 0.04f
                )
                close()
            }
            drawPath(bodyPath, color = Color.White)

            // 5. If DISCONNECTED, overlay the gap and diagonal slash
            if (status == VpnStatus.DISCONNECTED) {
                // Draw gap with the exact background color
                drawLine(
                    color = backgroundColor,
                    start = Offset(cx - S * 0.32f, cy),
                    end = Offset(cx + S * 0.32f, cy),
                    strokeWidth = S * 0.07f,
                    cap = StrokeCap.Round
                )
                // Draw white slash line
                drawLine(
                    color = Color.White,
                    start = Offset(cx - S * 0.32f, cy),
                    end = Offset(cx + S * 0.32f, cy),
                    strokeWidth = S * 0.04f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun StylizedWorldMap(
    status: VpnStatus,
    selectedServer: V2RayConfig?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mapPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAlpha"
    )
    
    val arcPulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcPulse"
    )

    val targetZoom = if (status != VpnStatus.DISCONNECTED) 2.4f else 1.0f
    val countryStr = getCountryForServer(selectedServer)
    val coords = getCoordinatesForCountry(countryStr)
    val targetCenterX = if (status != VpnStatus.DISCONNECTED) coords.first else 0.5f
    val targetCenterY = if (status != VpnStatus.DISCONNECTED) coords.second else 0.5f

    val zoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mapZoom"
    )
    val centerX by animateFloatAsState(
        targetValue = targetCenterX,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mapCenterX"
    )
    val centerY by animateFloatAsState(
        targetValue = targetCenterY,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mapCenterY"
    )

    val connectionActive = status != VpnStatus.DISCONNECTED
    val lineProgressTarget = if (connectionActive) 1.0f else 0.0f
    val lineProgress by animateFloatAsState(
        targetValue = lineProgressTarget,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
        label = "lineProgress"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val px = centerX * w
        val py = centerY * h

        withTransform({
            scale(zoom, zoom, pivot = Offset(px, py))
        }) {
            // 1. Draw grid of background dots (tech radar scan feel)
            val rows = 18
            val cols = 30
            val dotColor = Color.White.copy(alpha = 0.04f)
            val dotRadius = (1.5f / zoom).coerceAtLeast(0.7f)
            for (r in 0..rows) {
                val y = (h / rows) * r
                for (c in 0..cols) {
                    val x = (w / cols) * c
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                }
            }

            // 2. Define highly detailed continent and landmass paths
            val naPath = Path().apply {
                moveTo(w * 0.05f, h * 0.18f)
                quadraticTo(w * 0.12f, h * 0.10f, w * 0.28f, h * 0.11f)
                lineTo(w * 0.32f, h * 0.15f)
                lineTo(w * 0.32f, h * 0.28f)
                lineTo(w * 0.28f, h * 0.36f)
                lineTo(w * 0.22f, h * 0.45f)
                lineTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.16f, h * 0.44f)
                lineTo(w * 0.12f, h * 0.32f)
                lineTo(w * 0.05f, h * 0.25f)
                close()
            }

            val greenlandPath = Path().apply {
                moveTo(w * 0.33f, h * 0.10f)
                lineTo(w * 0.39f, h * 0.10f)
                quadraticTo(w * 0.38f, h * 0.15f, w * 0.36f, h * 0.18f)
                quadraticTo(w * 0.34f, h * 0.14f, w * 0.33f, h * 0.10f)
                close()
            }

            val saPath = Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                quadraticTo(w * 0.28f, h * 0.55f, w * 0.33f, h * 0.58f)
                lineTo(w * 0.30f, h * 0.70f)
                lineTo(w * 0.22f, h * 0.88f)
                lineTo(w * 0.19f, h * 0.88f)
                quadraticTo(w * 0.16f, h * 0.72f, w * 0.17f, h * 0.65f)
                quadraticTo(w * 0.18f, h * 0.58f, w * 0.18f, h * 0.52f)
                close()
            }

            val eurasiaPath = Path().apply {
                moveTo(w * 0.41f, h * 0.34f) // Spain
                lineTo(w * 0.44f, h * 0.31f) // France
                lineTo(w * 0.48f, h * 0.27f) // Germany
                lineTo(w * 0.48f, h * 0.16f) // Norway/Sweden base
                quadraticTo(w * 0.50f, h * 0.12f, w * 0.52f, h * 0.12f) // Scandinavian top
                lineTo(w * 0.53f, h * 0.15f) // Finland
                quadraticTo(w * 0.70f, h * 0.11f, w * 0.88f, h * 0.12f) // Siberia
                lineTo(w * 0.90f, h * 0.18f) // Kamchatka
                lineTo(w * 0.88f, h * 0.24f) 
                lineTo(w * 0.83f, h * 0.30f) // Korea/East China
                lineTo(w * 0.80f, h * 0.38f) // South China
                lineTo(w * 0.74f, h * 0.46f) // Indochina
                lineTo(w * 0.70f, h * 0.41f) // East India
                lineTo(w * 0.67f, h * 0.48f) // South India tip
                lineTo(w * 0.65f, h * 0.41f) // West India
                lineTo(w * 0.59f, h * 0.40f) // Oman
                lineTo(w * 0.58f, h * 0.45f) // UAE/Yemen
                lineTo(w * 0.54f, h * 0.42f) // Saudi Arabia
                lineTo(w * 0.53f, h * 0.34f) // Turkey
                lineTo(w * 0.50f, h * 0.32f) // Greece
                lineTo(w * 0.47f, h * 0.33f) // Italy
                close()
            }

            val ukPath = Path().apply {
                moveTo(w * 0.42f, h * 0.23f)
                lineTo(w * 0.44f, h * 0.24f)
                lineTo(w * 0.43f, h * 0.28f)
                lineTo(w * 0.41f, h * 0.26f)
                close()
            }

            val icelandPath = Path().apply {
                moveTo(w * 0.40f, h * 0.15f)
                lineTo(w * 0.42f, h * 0.15f)
                lineTo(w * 0.41f, h * 0.17f)
                close()
            }

            val japanPath = Path().apply {
                moveTo(w * 0.86f, h * 0.26f)
                lineTo(w * 0.88f, h * 0.29f)
                lineTo(w * 0.87f, h * 0.33f)
                lineTo(w * 0.85f, h * 0.30f)
                close()
            }

            val africaPath = Path().apply {
                moveTo(w * 0.40f, h * 0.38f) // Morocco
                lineTo(w * 0.52f, h * 0.40f) // Egypt
                lineTo(w * 0.58f, h * 0.50f) // Somalia
                lineTo(w * 0.49f, h * 0.78f) // South Africa
                lineTo(w * 0.38f, h * 0.54f) // Gulf of Guinea
                close()
            }

            val madagascarPath = Path().apply {
                moveTo(w * 0.55f, h * 0.70f)
                lineTo(w * 0.56f, h * 0.72f)
                lineTo(w * 0.54f, h * 0.75f)
                close()
            }

            val ausPath = Path().apply {
                moveTo(w * 0.75f, h * 0.68f)
                lineTo(w * 0.86f, h * 0.67f)
                lineTo(w * 0.85f, h * 0.78f)
                lineTo(w * 0.74f, h * 0.76f)
                close()
            }

            val nzPath = Path().apply {
                moveTo(w * 0.89f, h * 0.82f)
                lineTo(w * 0.91f, h * 0.86f)
                lineTo(w * 0.90f, h * 0.87f)
                close()
            }

            val seAsiaIslandsPath = Path().apply {
                moveTo(w * 0.72f, h * 0.56f)
                lineTo(w * 0.78f, h * 0.58f)
                lineTo(w * 0.81f, h * 0.48f)
                lineTo(w * 0.83f, h * 0.52f)
                close()
            }

            // Draw Continent Fills
            val landFillColor = Color(0xFF1E293B).copy(alpha = 0.25f)
            drawPath(naPath, color = landFillColor)
            drawPath(greenlandPath, color = landFillColor)
            drawPath(saPath, color = landFillColor)
            drawPath(eurasiaPath, color = landFillColor)
            drawPath(ukPath, color = landFillColor)
            drawPath(icelandPath, color = landFillColor)
            drawPath(japanPath, color = landFillColor)
            drawPath(africaPath, color = landFillColor)
            drawPath(madagascarPath, color = landFillColor)
            drawPath(ausPath, color = landFillColor)
            drawPath(nzPath, color = landFillColor)
            drawPath(seAsiaIslandsPath, color = landFillColor)

            // Draw Continent Outlines
            val outlineColor = Color(0xFF334155).copy(alpha = 0.35f)
            val outlineStroke = Stroke(width = 1.2f / zoom)
            drawPath(naPath, color = outlineColor, style = outlineStroke)
            drawPath(greenlandPath, color = outlineColor, style = outlineStroke)
            drawPath(saPath, color = outlineColor, style = outlineStroke)
            drawPath(eurasiaPath, color = outlineColor, style = outlineStroke)
            drawPath(ukPath, color = outlineColor, style = outlineStroke)
            drawPath(icelandPath, color = outlineColor, style = outlineStroke)
            drawPath(japanPath, color = outlineColor, style = outlineStroke)
            drawPath(africaPath, color = outlineColor, style = outlineStroke)
            drawPath(madagascarPath, color = outlineColor, style = outlineStroke)
            drawPath(ausPath, color = outlineColor, style = outlineStroke)
            drawPath(nzPath, color = outlineColor, style = outlineStroke)
            drawPath(seAsiaIslandsPath, color = outlineColor, style = outlineStroke)

            // 2.2 Country borders / internal boundaries
            val bordersPath = Path().apply {
                // --- North America Borders ---
                // US - Canada border
                moveTo(w * 0.08f, h * 0.23f)
                lineTo(w * 0.31f, h * 0.23f)
                // US - Mexico border
                moveTo(w * 0.12f, h * 0.38f)
                lineTo(w * 0.23f, h * 0.38f)

                // --- South America Borders ---
                // Colombia / Peru / Brazil border division
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.24f, h * 0.58f)
                lineTo(w * 0.22f, h * 0.65f)
                lineTo(w * 0.25f, h * 0.72f)
                // Brazil - Argentina border
                moveTo(w * 0.25f, h * 0.72f)
                lineTo(w * 0.31f, h * 0.72f)

                // --- Africa Borders ---
                // North Africa divider (Sahara border)
                moveTo(w * 0.38f, h * 0.48f)
                lineTo(w * 0.55f, h * 0.48f)
                // Central - South divider
                moveTo(w * 0.44f, h * 0.65f)
                lineTo(w * 0.53f, h * 0.65f)

                // --- Eurasia Borders ---
                // Europe - Russia border
                moveTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.50f, h * 0.32f)
                // Spain - France border
                moveTo(w * 0.41f, h * 0.34f)
                lineTo(w * 0.44f, h * 0.34f)
                // France - Germany border
                moveTo(w * 0.44f, h * 0.31f)
                lineTo(w * 0.45f, h * 0.28f)
                // Germany - Poland border
                moveTo(w * 0.49f, h * 0.24f)
                lineTo(w * 0.49f, h * 0.29f)
                // Italy border
                moveTo(w * 0.46f, h * 0.33f)
                lineTo(w * 0.48f, h * 0.32f)
                // Turkey - Europe
                moveTo(w * 0.50f, h * 0.32f)
                lineTo(w * 0.51f, h * 0.34f)
                // Turkey - Middle East
                moveTo(w * 0.53f, h * 0.34f)
                lineTo(w * 0.53f, h * 0.36f)
                
                // Iran Border (Custom highlighted shape)
                moveTo(w * 0.54f, h * 0.36f)
                lineTo(w * 0.55f, h * 0.34f)
                lineTo(w * 0.59f, h * 0.34f)
                lineTo(w * 0.60f, h * 0.37f)
                lineTo(w * 0.58f, h * 0.39f)
                lineTo(w * 0.55f, h * 0.38f)
                close()

                // Saudi Arabia borders
                moveTo(w * 0.54f, h * 0.42f)
                lineTo(w * 0.54f, h * 0.45f)
                moveTo(w * 0.54f, h * 0.42f)
                lineTo(w * 0.59f, h * 0.40f)

                // Russia - China border
                moveTo(w * 0.65f, h * 0.24f)
                lineTo(w * 0.81f, h * 0.24f)
                // India borders
                moveTo(w * 0.65f, h * 0.41f)
                lineTo(w * 0.63f, h * 0.38f)
                lineTo(w * 0.70f, h * 0.41f)
                // China - Southeast Asia border
                moveTo(w * 0.74f, h * 0.46f)
                lineTo(w * 0.80f, h * 0.38f)

                // --- Australia Borders ---
                moveTo(w * 0.80f, h * 0.67f)
                lineTo(w * 0.80f, h * 0.78f)
            }

            val borderLineColor = Color(0xFF475569).copy(alpha = 0.5f)
            val borderStroke = Stroke(
                width = 0.8f / zoom,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx() / zoom, 2.dp.toPx() / zoom), 0f
                )
            )
            drawPath(bordersPath, color = borderLineColor, style = borderStroke)

            // 3. Draw active server connection flying arc
            if (connectionActive && lineProgress > 0f) {
                val x0 = 0.57f * w
                val y0 = 0.36f * h
                val x2 = coords.first * w
                val y2 = coords.second * h

                // Only draw curve if the server country is not Iran itself
                if (Math.abs(x0 - x2) > 0.01f || Math.abs(y0 - y2) > 0.01f) {
                    val x1 = (x0 + x2) / 2f
                    val y1 = minOf(y0, y2) - (h * 0.12f)

                    val steps = (30 * lineProgress).toInt().coerceAtLeast(2)
                    val points = mutableListOf<Offset>()
                    for (i in 0..steps) {
                        val t = (i.toFloat() / 30f) * lineProgress
                        val xt = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * x1 + t * t * x2
                        val yt = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * y1 + t * t * y2
                        points.add(Offset(xt, yt))
                    }
                    
                    // Draw flowing arc
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = Color(0xFF21C387).copy(alpha = 0.6f),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 1.5f / zoom,
                            cap = StrokeCap.Round
                        )
                    }

                    // Draw traveling light dot along the arc
                    val pulseT = arcPulsePhase * lineProgress
                    val pxt = (1 - pulseT) * (1 - pulseT) * x0 + 2 * (1 - pulseT) * pulseT * x1 + pulseT * pulseT * x2
                    val pyt = (1 - pulseT) * (1 - pulseT) * y0 + 2 * (1 - pulseT) * pulseT * y1 + pulseT * pulseT * y2
                    
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 4.dp.toPx() / zoom,
                        center = Offset(pxt, pyt)
                    )
                    
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        radius = 8.dp.toPx() / zoom,
                        center = Offset(pxt, pyt)
                    )
                }
            }

            // 4. Draw active server beacon marker on target country
            if (status != VpnStatus.DISCONNECTED) {
                val bx = coords.first * w
                val by = coords.second * h

                val beaconColor = if (status == VpnStatus.CONNECTED) {
                    Color(0xFF21C387) // Minty Emerald Green
                } else {
                    Color(0xFFFFB300) // Amber
                }

                // Outer pulsing glow
                drawCircle(
                    color = beaconColor.copy(alpha = glowAlpha),
                    radius = (24.dp.toPx() * glowScale) / zoom,
                    center = Offset(bx, by)
                )

                // Dynamic core glow rings
                drawCircle(
                    color = beaconColor.copy(alpha = 0.3f),
                    radius = 12.dp.toPx() / zoom,
                    center = Offset(bx, by)
                )

                // Inner solid white-core dot
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx() / zoom,
                    center = Offset(bx, by)
                )

                drawCircle(
                    color = beaconColor,
                    radius = 5.dp.toPx() / zoom,
                    center = Offset(bx, by),
                    style = Stroke(width = 2.dp.toPx() / zoom)
                )

                // 4.1 Draw tactical crosshair/scanner around the active server
                val crosshairColor = if (status == VpnStatus.CONNECTED) Color(0xFF21C387) else Color(0xFFFFB300)
                val radarRadius = 20.dp.toPx() / zoom
                
                // Draw dotted/dashed circle
                drawCircle(
                    color = crosshairColor.copy(alpha = 0.4f),
                    radius = radarRadius,
                    center = Offset(bx, by),
                    style = Stroke(
                        width = 1.dp.toPx() / zoom,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx() / zoom, 4.dp.toPx() / zoom), 0f
                        )
                    )
                )

                // Draw crosshair ticks
                val tickLength = 6.dp.toPx() / zoom
                val tickOffset = radarRadius + 2.dp.toPx() / zoom
                // Top tick
                drawLine(
                    color = crosshairColor.copy(alpha = 0.8f),
                    start = Offset(bx, by - tickOffset),
                    end = Offset(bx, by - tickOffset - tickLength),
                    strokeWidth = 1.5f / zoom
                )
                // Bottom tick
                drawLine(
                    color = crosshairColor.copy(alpha = 0.8f),
                    start = Offset(bx, by + tickOffset),
                    end = Offset(bx, by + tickOffset + tickLength),
                    strokeWidth = 1.5f / zoom
                )
                // Left tick
                drawLine(
                    color = crosshairColor.copy(alpha = 0.8f),
                    start = Offset(bx - tickOffset, by),
                    end = Offset(bx - tickOffset - tickLength, by),
                    strokeWidth = 1.5f / zoom
                )
                // Right tick
                drawLine(
                    color = crosshairColor.copy(alpha = 0.8f),
                    start = Offset(bx + tickOffset, by),
                    end = Offset(bx + tickOffset + tickLength, by),
                    strokeWidth = 1.5f / zoom
                )

                // Draw a beautiful label/badge with country name in Persian
                drawContext.canvas.nativeCanvas.apply {
                    val labelText = if (status == VpnStatus.CONNECTED) {
                        "متصل به: $countryStr"
                    } else {
                        "در حال اتصال به: $countryStr"
                    }
                    
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10.dp.toPx() / zoom
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                    
                    val textBounds = android.graphics.Rect()
                    textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
                    
                    val bgPaddingX = 8.dp.toPx() / zoom
                    val bgPaddingY = 5.dp.toPx() / zoom
                    val bgWidth = textBounds.width() + bgPaddingX * 2
                    val bgHeight = textBounds.height() + bgPaddingY * 2
                    val bgX = bx - bgWidth / 2
                    val bgY = by - (30.dp.toPx() / zoom) - bgHeight
                    
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#E01A1F2C")
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    val strokePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor(if (status == VpnStatus.CONNECTED) "#21C387" else "#FFB300")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.dp.toPx() / zoom
                        isAntiAlias = true
                    }

                    val rectF = android.graphics.RectF(bgX, bgY, bgX + bgWidth, bgY + bgHeight)
                    val cornerRad = 4.dp.toPx() / zoom
                    this.drawRoundRect(rectF, cornerRad, cornerRad, bgPaint)
                    this.drawRoundRect(rectF, cornerRad, cornerRad, strokePaint)

                    val textY = bgY + bgPaddingY + textBounds.height()
                    this.drawText(labelText, bx, textY, textPaint)
                }
            }
        }
    }
}

fun getCoordinatesForCountry(country: String): Pair<Float, Float> {
    return when {
        country.contains("ایالات متحده") || country.contains("آمریکا") || country.contains("🇺🇸") -> Pair(0.20f, 0.33f)
        country.contains("کانادا") || country.contains("🇨🇦") -> Pair(0.18f, 0.22f)
        country.contains("آلمان") || country.contains("🇩🇪") -> Pair(0.48f, 0.28f)
        country.contains("انگلستان") || country.contains("🇬🇧") -> Pair(0.44f, 0.26f)
        country.contains("فرانسه") || country.contains("🇫🇷") -> Pair(0.45f, 0.31f)
        country.contains("هلند") || country.contains("🇳🇱") -> Pair(0.46f, 0.27f)
        country.contains("ترکیه") || country.contains("🇹🇷") -> Pair(0.53f, 0.34f)
        country.contains("فنلاند") || country.contains("🇫🇮") -> Pair(0.51f, 0.19f)
        country.contains("سنگاپور") || country.contains("🇸🇬") -> Pair(0.78f, 0.54f)
        country.contains("سوئد") || country.contains("🇸🇪") -> Pair(0.49f, 0.21f)
        country.contains("نروژ") || country.contains("🇳🇴") -> Pair(0.47f, 0.20f)
        country.contains("لهستان") || country.contains("🇵🇱") -> Pair(0.50f, 0.28f)
        country.contains("ایران") || country.contains("🇮🇷") -> Pair(0.57f, 0.36f)
        country.contains("ژاپن") || country.contains("🇯🇵") -> Pair(0.86f, 0.31f)
        country.contains("روسیه") || country.contains("🇷🇺") -> Pair(0.65f, 0.21f)
        country.contains("امارات") || country.contains("🇦🇪") -> Pair(0.58f, 0.40f)
        country.contains("سوئیس") || country.contains("🇨🇭") -> Pair(0.46f, 0.30f)
        country.contains("ایتالیا") || country.contains("🇮🇹") -> Pair(0.47f, 0.33f)
        country.contains("اسپانیا") || country.contains("🇪🇸") -> Pair(0.42f, 0.34f)
        country.contains("استرالیا") || country.contains("🇦🇺") -> Pair(0.81f, 0.73f)
        country.contains("هند") || country.contains("🇮🇳") -> Pair(0.67f, 0.43f)
        country.contains("هنگ کنگ") || country.contains("🇭🇰") -> Pair(0.79f, 0.42f)
        country.contains("کره") || country.contains("🇰🇷") -> Pair(0.84f, 0.32f)
        country.contains("چین") || country.contains("🇨🇳") -> Pair(0.75f, 0.33f)
        country.contains("ایرلند") || country.contains("🇮🇪") -> Pair(0.42f, 0.26f)
        country.contains("اتریش") || country.contains("🇦🇹") -> Pair(0.48f, 0.30f)
        country.contains("بلژیک") || country.contains("🇧🇪") -> Pair(0.45f, 0.28f)
        country.contains("دانمارک") || country.contains("🇩🇰") -> Pair(0.47f, 0.24f)
        else -> Pair(0.46f, 0.30f)
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
    val subscriptionInfo by viewModel.subscriptionInfo.collectAsState()

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

    // Dynamic NetShield stats that increment gracefully in real-time when connected
    var adsBlocked by remember { mutableStateOf(21) }
    var trackersStopped by remember { mutableStateOf(14) }

    LaunchedEffect(vpnStatus) {
        if (vpnStatus == VpnStatus.CONNECTED) {
            while (true) {
                kotlinx.coroutines.delay(6000)
                adsBlocked += (1..3).random()
                trackersStopped += (1..2).random()
            }
        }
    }

    val themeBackground = Color(0xFF0B0E14)
    val protonCardColor = Color(0xFF181D26)
    val protonGreen = Color(0xFF21C387)
    val protonAmber = Color(0xFFFFA000)
    val protonGrey = Color(0xFF9EA3AE)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = themeBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.logout(context) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(protonCardColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White.copy(alpha = 0.8f)
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
                            .background(protonCardColor, CircleShape)
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = protonGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = when (vpnStatus) {
                                VpnStatus.CONNECTED -> protonGreen.copy(alpha = 0.15f)
                                VpnStatus.CONNECTING -> protonAmber.copy(alpha = 0.15f)
                                VpnStatus.DISCONNECTED -> Color.White.copy(alpha = 0.05f)
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (vpnStatus) {
                                VpnStatus.CONNECTED -> Icons.Filled.CheckCircle
                                VpnStatus.CONNECTING -> Icons.Filled.Refresh
                                VpnStatus.DISCONNECTED -> Icons.Filled.Lock
                            },
                            contentDescription = "Status Icon",
                            tint = when (vpnStatus) {
                                VpnStatus.CONNECTED -> protonGreen
                                VpnStatus.CONNECTING -> protonAmber
                                VpnStatus.DISCONNECTED -> protonGrey
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (vpnStatus) {
                                VpnStatus.CONNECTED -> "Protected"
                                VpnStatus.CONNECTING -> "Connecting..."
                                VpnStatus.DISCONNECTED -> "Unprotected"
                            },
                            color = when (vpnStatus) {
                                VpnStatus.CONNECTED -> protonGreen
                                VpnStatus.CONNECTING -> protonAmber
                                VpnStatus.DISCONNECTED -> Color.White.copy(alpha = 0.7f)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(protonCardColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${configs.size} سرور",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = "Server Count",
                            tint = Color.White.copy(alpha = 0.8f),
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
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = protonCardColor),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF2C323E), Color(0xFF1E232A))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Subscription Info",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اطلاعات اشتراک شما",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    Toast.makeText(context, "جزئیات اشتراک", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "فعال",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = protonGreen
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Go",
                                    tint = protonGrey,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val info = subscriptionInfo
                        if (info != null) {
                            val totalBytes = info.total
                            val usedBytes = info.upload + info.download
                            val remainingBytes = (totalBytes - usedBytes).coerceAtLeast(0L)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = getDaysRemaining(info.expire),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "زمان باقی‌مانده",
                                        fontSize = 10.sp,
                                        color = protonGrey,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .align(Alignment.CenterVertically)
                                )

                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatBytes(remainingBytes),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = protonGreen
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "حجم باقی‌مانده",
                                        fontSize = 10.sp,
                                        color = protonGrey,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .align(Alignment.CenterVertically)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatBytes(totalBytes),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "حجم کل",
                                        fontSize = 10.sp,
                                        color = protonGrey,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "در حال دریافت اطلاعات اشتراک...",
                                    fontSize = 11.sp,
                                    color = protonGrey,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StylizedWorldMap(
                        status = vpnStatus,
                        selectedServer = selectedServer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = protonCardColor),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF2C323E), Color(0xFF1E232A))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (vpnStatus) {
                                    VpnStatus.CONNECTED -> "Browsing safely from"
                                    VpnStatus.CONNECTING -> "Searching best server..."
                                    VpnStatus.DISCONNECTED -> "Ready to connect"
                                },
                                fontSize = 11.sp,
                                color = protonGrey,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    Toast.makeText(context, "بخش راهنما", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "Help",
                                    fontSize = 11.sp,
                                    color = protonGrey,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Help",
                                    tint = protonGrey,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .clickable {
                                    Toast.makeText(context, "سرور انتخابی هوشمند: ${selectedServer?.remarks ?: "اتصال خودکار"}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val country = getCountryForServer(selectedServer)
                                val emoji = country.takeLast(2)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.06f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (vpnStatus != VpnStatus.DISCONNECTED && emoji.any { it.isSurrogate() }) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Public,
                                            contentDescription = "Globe",
                                            tint = protonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = when (vpnStatus) {
                                            VpnStatus.DISCONNECTED -> "بهترین مکان (انتخاب خودکار)"
                                            else -> country.dropLast(2).trim()
                                        },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (vpnStatus) {
                                            VpnStatus.DISCONNECTED -> "با کمترین پینگ متصل شوید"
                                            else -> selectedServer?.remarks ?: "سرور کمکی"
                                        },
                                        color = protonGrey,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Select Server",
                                tint = protonGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("connection_toggle_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (vpnStatus) {
                                    VpnStatus.CONNECTED -> Color(0xFF2C323E)
                                    VpnStatus.CONNECTING -> protonAmber
                                    VpnStatus.DISCONNECTED -> protonGreen
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when (vpnStatus) {
                                    VpnStatus.CONNECTED -> "Disconnect"
                                    VpnStatus.CONNECTING -> "Connecting..."
                                    VpnStatus.DISCONNECTED -> "Quick Connect"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
