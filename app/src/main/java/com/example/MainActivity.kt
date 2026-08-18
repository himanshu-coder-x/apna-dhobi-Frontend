package com.example

import android.os.Bundle
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.data.dto.*
import com.example.ui.*
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LightCream
                ) {
                    val vm: ApnaDhobiViewModel = viewModel()
                    MainViewport(vm = vm)
                }
            }
        }
    }
}

// ==========================================
// REVERSE GEOCODING HELPER
// ==========================================
suspend fun fetchAddressFromCoordinates(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val geocoder = Geocoder(context, Locale.getDefault())
            var resultStr = ""
            geocoder.getFromLocation(lat, lon, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    val addr = addresses[0]
                    resultStr = addr.getAddressLine(0) ?: "${addr.subLocality ?: ""}, ${addr.locality ?: ""}"
                }
            }
            if (resultStr.isNotBlank()) return@withContext resultStr
        } else {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val line = addresses[0].getAddressLine(0)
                if (!line.isNullOrBlank()) return@withContext line
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ApnaDhobiAndroidApp/1.0")
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().readText()
            val jsonObj = JSONObject(json)
            val displayName = jsonObj.optString("display_name")
            if (displayName.isNotBlank()) return@withContext displayName
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    "Connaught Place, New Delhi, Delhi 110001"
}

// ==========================================
// MAIN VIEWPORT
// ==========================================
@Composable
fun MainViewport(vm: ApnaDhobiViewModel) {
    val currentScreen by vm.currentScreen.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightCream
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally(
                        animationSpec = tween(450, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> fullWidth }
                    ) + fadeIn(animationSpec = tween(350)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(450, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    ) + fadeOut(animationSpec = tween(350))
                },
                label = "ScreenSlideTransition"
            ) { screen ->
                when (screen) {
                    is ApnaDhobiScreen.Splash -> SplashScreen(vm = vm)
                    is ApnaDhobiScreen.Login -> LoginScreen(vm = vm)
                    is ApnaDhobiScreen.HomeFrame -> {
                        Scaffold(
                            bottomBar = { ApnaFloatingNavbar(vm = vm) }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                val activeTab by vm.activeTab.collectAsState()
                                when (activeTab) {
                                    "home" -> HomeDashboardContent(vm = vm)
                                    "orders" -> OrdersHistoryListContent(vm = vm)
                                    "cart" -> CartScreenContent(vm = vm)
                                    "wallet" -> WalletContent(vm = vm)
                                    "profile" -> ProfileSettingsAndChatCentric(vm = vm)
                                    else -> HomeDashboardContent(vm = vm)
                                }
                            }
                        }
                    }
                    is ApnaDhobiScreen.ProductListing -> ProductListingScreen(vm = vm, categoryId = screen.categoryId, categoryName = screen.categoryName)
                    is ApnaDhobiScreen.VendorShop -> VendorShopScreen(vm = vm, vendorId = screen.vendorId)
                    is ApnaDhobiScreen.SlotSelection -> SlotSelectionScreen(vm = vm)
                    is ApnaDhobiScreen.LocationSelection -> LocationSelectionScreen(vm = vm)
                    is ApnaDhobiScreen.Payment -> PaymentScreen(vm = vm)
                    is ApnaDhobiScreen.OrderTracking -> OrderTrackingScreen(vm = vm, orderId = screen.orderId)
                    is ApnaDhobiScreen.VendorRegistration -> VendorRegistrationForm(vm = vm)
                    is ApnaDhobiScreen.DeliveryBoyDashboard -> DeliveryPartnerApp(vm = vm)
                    is ApnaDhobiScreen.VendorDashboard -> VendorPremiumDashboard(vm = vm)
                    is ApnaDhobiScreen.AdminDashboard -> AdminPremiumDashboard(vm = vm)
                }
            }

            StickyCheckoutFloatingBar(vm = vm)
        }
    }
}

// ==========================================
// BRAND LOGO MOCKUP (MATCHING SCREENSHOT)
// ==========================================
@Composable
fun ApnaDhobiBrandLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val orangeColor = Color(0xFFE55D18)
        val blueColor = Color(0xFF265CB2)

        // 1. Horizontal Orange Clothesline
        val lineY = h * 0.30f
        drawLine(
            color = orangeColor,
            start = Offset(w * 0.08f, lineY),
            end = Offset(w * 0.92f, lineY),
            strokeWidth = w * 0.018f
        )

        // 2. Center Clothes Peg Hook (Orange)
        val cx = w * 0.50f
        val pegHeight = h * 0.22f
        val pegWidth = w * 0.16f
        
        val pegPath = Path().apply {
            moveTo(cx - pegWidth * 0.25f, lineY)
            lineTo(cx + pegWidth * 0.25f, lineY)
            lineTo(cx + pegWidth * 0.45f, lineY + pegHeight * 0.30f)
            lineTo(cx + pegWidth * 0.12f, lineY + pegHeight * 0.30f)
            lineTo(cx + pegWidth * 0.22f, lineY + pegHeight)
            lineTo(cx - pegWidth * 0.22f, lineY + pegHeight)
            lineTo(cx - pegWidth * 0.12f, lineY + pegHeight * 0.30f)
            lineTo(cx - pegWidth * 0.45f, lineY + pegHeight * 0.30f)
            close()
        }
        drawPath(
            path = pegPath,
            color = orangeColor,
            style = Stroke(width = w * 0.016f)
        )

        // Inner peg pin gap line
        drawLine(
            color = orangeColor,
            start = Offset(cx, lineY + pegHeight * 0.35f),
            end = Offset(cx, lineY + pegHeight * 0.85f),
            strokeWidth = w * 0.012f
        )

        // 3. Lower Blue Tub / Smile Basin
        val tubTopY = h * 0.56f
        val tubLeft = w * 0.16f
        val tubWidth = w * 0.68f
        val tubHeight = h * 0.34f

        val tubPath = Path().apply {
            moveTo(tubLeft, tubTopY)
            lineTo(tubLeft + tubWidth, tubTopY)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = tubLeft,
                    top = tubTopY - tubHeight * 0.6f,
                    right = tubLeft + tubWidth,
                    bottom = tubTopY + tubHeight
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(
            path = tubPath,
            color = blueColor
        )
    }
}

// ==========================================
// ANIMATED BUBBLES CANVAS
// ==========================================
@Composable
fun SplashAnimatedBubbles(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubbles_anim")

    val bubbleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_progress"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val bubbleSpecs = remember {
        listOf(
            Triple(0.22f, 32f, 0.0f),
            Triple(0.68f, 22f, 0.2f),
            Triple(0.44f, 18f, 0.4f),
            Triple(0.85f, 38f, 0.6f),
            Triple(0.12f, 26f, 0.8f),
            Triple(0.55f, 14f, 0.1f),
            Triple(0.32f, 42f, 0.5f),
            Triple(0.78f, 28f, 0.7f),
            Triple(0.92f, 16f, 0.3f),
            Triple(0.08f, 20f, 0.9f)
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        bubbleSpecs.forEach { (xRatio, radius, delayOffset) ->
            val effectiveProgress = (bubbleProgress + delayOffset) % 1.0f
            val currentY = h * (1.1f - effectiveProgress * 1.2f)
            val currentX = w * xRatio + sin((effectiveProgress * 4 * Math.PI).toFloat()) * 20f
            val alpha = (1f - effectiveProgress) * pulseAlpha

            drawCircle(
                color = Color.White.copy(alpha = alpha.coerceIn(0.15f, 0.65f)),
                radius = radius,
                center = Offset(currentX, currentY)
            )
        }
    }
}

// ==========================================
// BOTTOM WAVE GRAPHIC CANVAS
// ==========================================
@Composable
fun SplashBottomWaveShape(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val wavePath = Path().apply {
            moveTo(0f, h * 0.35f)
            cubicTo(
                w * 0.25f, h * 0.10f,
                w * 0.50f, h * 0.55f,
                w * 0.75f, h * 0.25f
            )
            cubicTo(
                w * 0.88f, h * 0.10f,
                w * 0.96f, h * 0.30f,
                w, h * 0.20f
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = wavePath,
            color = Color(0xFF9E4B24)
        )
    }
}

// ==========================================
// ENHANCED SPLASH SCREEN (MATCHING USER IMAGE)
// ==========================================
@Composable
fun SplashScreen(vm: ApnaDhobiViewModel) {
    val isLoggedIn by vm.isLoggedIn.collectAsState()

    LaunchedEffect(Unit) {
        delay(2800)
        if (isLoggedIn) {
            vm.navigateTo(ApnaDhobiScreen.HomeFrame)
        } else {
            vm.navigateTo(ApnaDhobiScreen.Login)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F3978),
                        Color(0xFF5A3E38),
                        Color(0xFFB55627)
                    )
                )
            )
    ) {
        // Floating Bubbles Background Animation
        SplashAnimatedBubbles(
            modifier = Modifier.fillMaxSize()
        )

        // Bottom Wave Graphic Background Layer
        SplashBottomWaveShape(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
        )

        // Main Content Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Central Logo Badge Circle
                Surface(
                    modifier = Modifier.size(230.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 16.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ApnaDhobiBrandLogo(
                            modifier = Modifier
                                .size(200.dp)
                                .padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // App Title
                Text(
                    text = "Apna Dhobi",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Hindi Tagline
                Text(
                    text = "साफ़ कपड़े, खुशहाल ज़िंदगी",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Fast • Reliable • Affordable Pill Badge
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Fast • Reliable • Affordable",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Bottom Section: Get Started Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = {
                        if (isLoggedIn) {
                            vm.navigateTo(ApnaDhobiScreen.HomeFrame)
                        } else {
                            vm.navigateTo(ApnaDhobiScreen.Login)
                        }
                    },
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Get Started",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F3978)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Get Started Arrow",
                            tint = Color(0xFF0F3978),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// DELIVERY TRUCK GRAPHIC FOR PROMO BANNER
// ==========================================
@Composable
fun DeliveryTruckGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(100.dp)
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Cab
            val cabPath = Path().apply {
                moveTo(w * 0.05f, h * 0.70f)
                lineTo(w * 0.05f, h * 0.35f)
                lineTo(w * 0.22f, h * 0.20f)
                lineTo(w * 0.32f, h * 0.20f)
                lineTo(w * 0.32f, h * 0.70f)
                close()
            }
            drawPath(cabPath, color = Color.White)

            // Cab Window
            val windowPath = Path().apply {
                moveTo(w * 0.12f, h * 0.38f)
                lineTo(w * 0.22f, h * 0.28f)
                lineTo(w * 0.28f, h * 0.28f)
                lineTo(w * 0.28f, h * 0.38f)
                close()
            }
            drawPath(windowPath, color = Color(0xFF0F172A))

            // Blue Container Box
            drawRoundRect(
                color = Color(0xFF2563EB),
                topLeft = Offset(w * 0.32f, h * 0.12f),
                size = Size(w * 0.63f, h * 0.58f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Wheels
            drawCircle(Color(0xFF000000), radius = h * 0.12f, center = Offset(w * 0.22f, h * 0.75f))
            drawCircle(Color(0xFFCCCCCC), radius = h * 0.05f, center = Offset(w * 0.22f, h * 0.75f))
            drawCircle(Color(0xFF000000), radius = h * 0.12f, center = Offset(w * 0.78f, h * 0.75f))
            drawCircle(Color(0xFFCCCCCC), radius = h * 0.05f, center = Offset(w * 0.78f, h * 0.75f))
        }

        Column(
            modifier = Modifier.padding(start = 28.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Apna", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Dhobi", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// GOOGLE BRAND LOGO CANVAS
// ==========================================
@Composable
fun GoogleColoredLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.42f

        drawArc(Color(0xFFEA4335), 190f, 110f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(width = r * 0.45f))
        drawArc(Color(0xFFFBBC05), 120f, 70f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(width = r * 0.45f))
        drawArc(Color(0xFF34A853), 0f, 120f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(width = r * 0.45f))
        drawArc(Color(0xFF4285F4), -60f, 60f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(width = r * 0.45f))
        drawLine(Color(0xFF4285F4), Offset(cx, cy), Offset(cx + r, cy), strokeWidth = r * 0.45f)
    }
}

// ==========================================
// RESTORED & REDESIGNED LOGIN SCREEN (MATCHING ALL 5 IMAGES EXACTLY)
// ==========================================
@Composable
fun LoginScreen(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mobileNumber by vm.loginMobileNumber.collectAsState()
    val otp by vm.loginOtp.collectAsState()
    val isOtpSent by vm.isOtpSent.collectAsState()

    var authTab by remember { mutableStateOf("login") } // "login", "profile", "admin"
    
    // Form fields
    var regFullName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regMobile by remember { mutableStateOf("") }
    var regReferral by remember { mutableStateOf("") }

    var adminEmail by remember { mutableStateOf("admin@apnadhobi.com") }
    var adminPasscode by remember { mutableStateOf("492011") }

    var loginReferral by remember { mutableStateOf("") }

    var showGoogleDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var isLangMenuExpanded by remember { mutableStateOf(false) }

    val languages = listOf("English", "Hindi (हिंदी)", "Punjabi (ਪੰਜਾਬੀ)", "Bengali (বাংলা)")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBEF)) // Soft Light Cream background matching screenshots
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Right Language Selector Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box {
                    Surface(
                        onClick = { isLangMenuExpanded = true },
                        shape = CircleShape,
                        color = Color(0xFF0F172A),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$selectedLanguage ▾",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isLangMenuExpanded,
                        onDismissRequest = { isLangMenuExpanded = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedLanguage = lang.split(" ")[0]
                                    isLangMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dark Delivery Promo Card Header (Matching Image 1 & 5)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1329)),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Free Pickup &",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Return Delivery ",
                                color = Color(0xFFFF6B00),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "📣",
                                fontSize = 17.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "We clean, you relax.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                    DeliveryTruckGraphic()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Header Title: Welcome to Apna Dhobi ✨
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Welcome to ",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Apna Dhobi",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "✨",
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Premium doorstep laundry and dry cleaning",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Segmented Auth Navigation Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("login", Icons.Default.Lock, "Login"),
                    Triple("profile", Icons.Default.AccountCircle, "Profile")
                ).forEach { (id, icon, label) ->
                    val isSelected = authTab == id
                    Column(
                        modifier = Modifier
                            .clickable { authTab = id }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(3.dp)
                                    .background(Color(0xFF2563EB), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Dark Auth Card Container (Matching Image 1, 4, 5)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1329)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    when (authTab) {
                        "profile" -> {
                            // CREATE PROFILE TAB (MATCHING IMAGE 4)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "COMPLETE YOUR PROFILE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2563EB),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Field 1: Full Name (Required)
                            OutlinedTextField(
                                value = regFullName,
                                onValueChange = { regFullName = it },
                                placeholder = { Text("Full Name (Required)", color = Color(0xFF64748B), fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B),
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Field 2: Email ID (Required)
                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                placeholder = { Text("Email ID (Required)", color = Color(0xFF64748B), fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B),
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Field 3: Mobile Contact
                            OutlinedTextField(
                                value = regMobile,
                                onValueChange = { regMobile = it },
                                placeholder = { Text("Mobile Contact", color = Color(0xFF64748B), fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B),
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Field 4: Referral Bonus Code (Optional) + Apply
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = regReferral,
                                    onValueChange = { regReferral = it },
                                    placeholder = { Text("Referral Bonus Code (Optional)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF64748B)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1E293B),
                                        unfocusedContainerColor = Color(0xFF1E293B),
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        if (regReferral.isNotBlank()) {
                                            vm.userReferralCode.value = regReferral
                                            vm.applyReferral()
                                        }
                                    },
                                    modifier = Modifier.height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                                ) {
                                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Main Action Button: Create Profile & Sign Up
                            Button(
                                onClick = {
                                    if (regReferral.isNotBlank()) {
                                        vm.userReferralCode.value = regReferral
                                        vm.applyReferral()
                                    }
                                    vm.sendOtp(regMobile.ifBlank { "9876543210" }, false)
                                    vm.navigateTo(ApnaDhobiScreen.HomeFrame)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                            ) {
                                Text(
                                    text = "Create Profile & Sign Up 🚀",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        else -> {
                            // SECURE LOGIN TAB (MATCHING IMAGE 1, 2, 3)
                            Text(
                                text = "Enter Mobile Number",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isOtpSent) {
                                Text("OTP Sent to +91 ${mobileNumber.ifBlank { "9876543210" }}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enter Test Code: 1234", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = otp,
                                    onValueChange = { vm.loginOtp.value = it },
                                    placeholder = { Text("Enter 4-digit OTP", color = Color(0xFF64748B)) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2563EB)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1E293B),
                                        unfocusedContainerColor = Color(0xFF1E293B),
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            } else {
                                // Mobile Number Input Row with Flag
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "🇮🇳 +91", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.2f))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = mobileNumber,
                                        onValueChange = { vm.loginMobileNumber.value = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        decorationBox = { innerTextField ->
                                            if (mobileNumber.isEmpty()) {
                                                Text("Enter 10-digit number", color = Color(0xFF64748B), fontSize = 15.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Referral Code (Optional)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Referral Code Row + Apply Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = loginReferral,
                                        onValueChange = { loginReferral = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                        singleLine = true,
                                        decorationBox = { innerTextField ->
                                            if (loginReferral.isEmpty()) {
                                                Text("e.g. DHOBI50", color = Color(0xFF64748B), fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        if (loginReferral.isNotBlank()) {
                                            vm.userReferralCode.value = loginReferral
                                            vm.applyReferral()
                                        }
                                    },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                                ) {
                                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Main Action Button: Send OTP Code
                            Button(
                                onClick = {
                                    if (!isOtpSent) {
                                        vm.sendOtp(mobileNumber, false)
                                    } else {
                                        coroutineScope.launch {
                                            if (vm.verifyOtp(mobileNumber, otp)) {
                                                vm.navigateTo(ApnaDhobiScreen.HomeFrame)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (!isOtpSent) Icons.Default.Send else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!isOtpSent) "Send OTP Code" else "Verify & Continue",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // COMMON BOTTOM SECTION (MATCHING IMAGES 2, 3, 5)
            // ==========================================

            // Divider: OR CONTINUE WITH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFFCBD5E1))
                )
                Text(
                    text = "OR CONTINUE WITH",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFFCBD5E1))
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Social Row: Google Sign-In & Skip / Guest
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Google Sign-In White Button
                Surface(
                    onClick = { showGoogleDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleColoredLogo(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Sign-In",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Skip / Guest White Button
                Surface(
                    onClick = { vm.navigateTo(ApnaDhobiScreen.HomeFrame) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Skip / Guest",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Badges Container Card (100% Secure | Quick & Easy | 24/7 Support)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: 100% Secure
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFDBEAFE)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "100% Secure",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Your data is safe",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Item 2: Quick & Easy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFFEF9C3)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Quick & Easy",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Login in seconds",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Item 3: 24/7 Support
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "24/7 Support",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "We're here to help",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Legal Footer (Terms & Conditions and Privacy Policy)
            Text(
                text = "By continuing, you agree to our",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terms & Conditions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.clickable { }
                )
                Text(
                    text = " and ",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Privacy Policy",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.clickable { }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Google Sign In Pop-up Dialog
        if (showGoogleDialog) {
            Dialog(onDismissRequest = { showGoogleDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.GTranslate, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Choose Google Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        listOf("user@apnadhobi.com", "customer.dhobi@gmail.com").forEach { acc ->
                            Surface(
                                onClick = {
                                    showGoogleDialog = false
                                    coroutineScope.launch {
                                        if (vm.attemptGoogleLogin(acc)) {
                                            vm.navigateTo(ApnaDhobiScreen.HomeFrame)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = LightCream
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(acc, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// HOME DASHBOARD CONTENT
// ==========================================
@Composable
fun HomeDashboardContent(vm: ApnaDhobiViewModel) {
    val fullAddress by vm.currentFullAddress.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val vendors by vm.vendorsState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // Location Header matching Screenshot 1
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { vm.navigateTo(ApnaDhobiScreen.LocationSelection) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = SaffronOrange.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = SaffronOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DELIVER TO",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = SaffronOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = fullAddress.ifBlank { "Shanti Kutir, Block 4-B, Connaught Place, New Delhi..." },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Top Right Quick Access Icons (Admin Wrench & Delivery Truck)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { vm.navigateTo(ApnaDhobiScreen.AdminDashboard) },
                            shape = CircleShape,
                            color = SaffronOrange.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Admin Panel",
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { vm.navigateTo(ApnaDhobiScreen.DeliveryBoyDashboard) },
                            shape = CircleShape,
                            color = RoyalBlue.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = "Delivery Partner",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.searchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search services, dry cleaning, iron...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SaffronOrange) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Dynamic Promo Banners Slider from backend
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                com.example.ui.PromoBannerSlider(vm = vm, onPromoClick = { code ->
                    Toast.makeText(context, "Promo code applied: $code 🎉", Toast.LENGTH_SHORT).show()
                })
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Garment Care Categories Header & Horizontal Row
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "What care do your garments need?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )
                Text(
                    text = "Choose from our specialized fabric care services",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(categories) { cat ->
                    val categoryIcon = when (cat.id) {
                        "laundry" -> Icons.Default.LocalLaundryService
                        "dry_cleaning" -> Icons.Default.DryCleaning
                        "ironing" -> Icons.Default.Iron
                        "shoe_cleaning" -> Icons.Default.CleaningServices
                        "carpet_cleaning" -> Icons.Default.RollerShades
                        "blanket_wash" -> Icons.Default.AcUnit
                        "wedding_wear" -> Icons.Default.Star
                        "premium_care" -> Icons.Default.Favorite
                        else -> Icons.Default.LocalLaundryService
                    }

                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .clickable {
                                vm.navigateTo(ApnaDhobiScreen.ProductListing(cat.id, cat.name))
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(1.5.dp, SaffronOrange.copy(alpha = 0.3f), CircleShape),
                                shape = CircleShape,
                                color = SaffronOrange.copy(alpha = 0.10f),
                                shadowElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = cat.name,
                                        tint = SaffronOrange,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = cat.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // AI Voice-Based Booking Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(SaffronOrange, Color(0xFFFF8C00))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Booking",
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Voice-Based Booking",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Just tap & speak your order in English/Hindi!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "Listening... Speak your order! 🎤", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Speak 🎤", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Partner with Apna Dhobi Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(RoyalBlue, Color(0xFF0288D1))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = "Partner",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Partner with Apna Dhobi",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "List your shop to get orders instantly! 🐮",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { vm.navigateTo(ApnaDhobiScreen.VendorRegistration) },
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Register Shop",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // REAL-TIME PARTNER CONNECTIONS Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "REAL-TIME PARTNER CONNECTIONS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Charcoal,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Active marketplace store channels",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = GreenSuccess.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Live Syncing (24/7)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vendors.take(2)) { vendor ->
                    Card(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable { vm.navigateTo(ApnaDhobiScreen.VendorShop(vendor.id)) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = RoyalBlue.copy(alpha = 0.12f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = vendor.logoText.ifBlank { vendor.name.take(2).uppercase() },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = RoyalBlue
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vendor.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "★ ${vendor.rating} • ${vendor.distanceKm} km",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { vm.navigateTo(ApnaDhobiScreen.VendorShop(vendor.id)) },
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Connect 📡", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Premium Nearby Partners Title & Cards
        item {
            Text(
                text = "Premium Nearby Partners",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Charcoal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        items(vendors) { vendor ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { vm.navigateTo(ApnaDhobiScreen.VendorShop(vendor.id)) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = RoyalBlue.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = vendor.logoText.ifBlank { vendor.name.take(2).uppercase() },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = RoyalBlue
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vendor.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal
                        )
                        Text(
                            text = vendor.description,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${vendor.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "• ${vendor.distanceKm} km", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Starts @ ₹${vendor.startingPrice}/kg", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SaffronOrange)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { vm.navigateTo(ApnaDhobiScreen.VendorShop(vendor.id)) },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("View Shop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ==========================================
// LOCATION SELECTION SCREEN (FULLY INTERACTIVE & SEARCHABLE)
// ==========================================
@Composable
fun LocationSelectionScreen(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    
    val customerLat by vm.customerLat.collectAsState()
    val customerLng by vm.customerLng.collectAsState()
    val currentAddress by vm.currentFullAddress.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var addressDetailText by remember { mutableStateOf(currentAddress) }
    var selectedLabel by remember { mutableStateOf("Home") }
    var houseFlatNo by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }

    // Popular predefined search location suggestions
    val searchSuggestions = remember {
        listOf(
            Triple("Connaught Place", 28.6315, 77.2167),
            Triple("Hauz Khas, New Delhi", 28.5494, 77.2001),
            Triple("Cyber City, Gurugram", 28.4950, 77.0895),
            Triple("Lajpat Nagar, New Delhi", 28.5677, 77.2433),
            Triple("Saket District Centre, Delhi", 28.5286, 77.2194)
        )
    }

    val currentPosition = LatLng(customerLat, customerLng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentPosition, 17f)
    }
    val markerState = rememberMarkerState(position = currentPosition)

    // Trigger real hardware GPS fetch immediately on launch
    LaunchedEffect(Unit) {
        vm.fetchRealGpsLocation(context) { lat, lng, addr ->
            addressDetailText = addr
            coroutineScope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f))
            }
        }
    }

    // Update marker and address when camera stops moving
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            markerState.position = target
            vm.customerLat.value = target.latitude
            vm.customerLng.value = target.longitude
            val resolvedAddr = fetchAddressFromCoordinates(context, target.latitude, target.longitude)
            addressDetailText = resolvedAddr
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Interactive Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            Marker(
                state = markerState,
                title = "Pickup Location",
                snippet = addressDetailText
            )
        }

        // Top Interactive Search Bar & Back Button (With statusBarsPadding)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { vm.navigateBack() },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Charcoal)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search area, apartment, street...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SaffronOrange) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .shadow(6.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = SaffronOrange,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                    })
                )
            }

            // Live Autocomplete Suggestions List Dropdown
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        searchSuggestions
                            .filter { it.first.contains(searchQuery, ignoreCase = true) || searchQuery.length > 1 }
                            .forEach { (name, lat, lng) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = name
                                            keyboardController?.hide()
                                            coroutineScope.launch {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f))
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                            }
                    }
                }
            }
        }

        // Floating "LOCATE ME" Button (Hardware GPS Lock)
        Surface(
            onClick = {
                isDetecting = true
                vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                    isDetecting = false
                    addressDetailText = addr
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 285.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SaffronOrange, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Me", tint = SaffronOrange, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Locate Me", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Charcoal)
            }
        }

        // Bottom Sheet Location Details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SELECT PICKUP LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(
                            text = addressDetailText.ifBlank { "Fetching address..." },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = houseFlatNo,
                    onValueChange = { houseFlatNo = it },
                    label = { Text("House / Flat / Floor No. (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("SAVE AS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Office", "Other").forEach { tag ->
                        FilterChip(
                            selected = selectedLabel == tag,
                            onClick = { selectedLabel = tag },
                            label = { Text(tag, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SaffronOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val finalFormatted = if (houseFlatNo.isNotBlank()) "$houseFlatNo, $addressDetailText" else addressDetailText
                        vm.saveNewAddress(finalFormatted, selectedLabel)
                        vm.currentFullAddress.value = finalFormatted
                        Toast.makeText(context, "Location Saved!", Toast.LENGTH_SHORT).show()
                        vm.navigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("CONFIRM & PROCEED", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ==========================================
// ORDER TRACKING SCREEN (LIVE MAP TELEMETRY)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingScreen(vm: ApnaDhobiViewModel, orderId: Int) {
    val customerLat by vm.customerLat.collectAsState()
    val customerLng by vm.customerLng.collectAsState()
    val agentLat by vm.activeDeliveryBoyLat.collectAsState()
    val agentLng by vm.activeDeliveryBoyLng.collectAsState()
    val etaText by vm.trackingEtaText.collectAsState()

    val customerPos = LatLng(customerLat, customerLng)
    val agentPos = LatLng(agentLat, agentLng)
    val vendorPos = LatLng(28.6139, 77.2090)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(agentPos, 14f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Order Tracking (#$orderId)") },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = rememberMarkerState(position = customerPos),
                    title = "Delivery Location (Customer)",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
                Marker(
                    state = rememberMarkerState(position = agentPos),
                    title = "Delivery Agent (Live)",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
                Marker(
                    state = rememberMarkerState(position = vendorPos),
                    title = "Apna Dhobi Hub",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
                Polyline(
                    points = listOf(vendorPos, agentPos, customerPos),
                    color = SaffronOrange,
                    width = 8f
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = GreenSuccess.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = GreenSuccess)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Out for Delivery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(etaText, color = SaffronOrange, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        IconButton(onClick = { /* Call Agent */ }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBlue)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PRODUCT LISTING SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListingScreen(vm: ApnaDhobiViewModel, categoryId: String, categoryName: String) {
    val products by vm.productsState.collectAsState()
    val cartItems by vm.cartItems.collectAsState()
    val vendors by vm.vendorsState.collectAsState()
    val defaultVendor = vendors.firstOrNull() ?: Vendor("v1", "Apna Dhobi Hub", "Central Laundromat", 4.8, 1.2, 30, 49, "#1E88E5", "AD", true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LightCream)
        ) {
            items(products.filter { it.categoryId == categoryId || categoryId.isBlank() }) { prod ->
                val inCart = cartItems.find { it.productId == prod.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(prod.deliveryEstimate, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹${prod.discountPrice}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SaffronOrange)
                        }

                        if (inCart == null) {
                            Button(
                                onClick = { vm.addProductToCart(prod, defaultVendor) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                            ) {
                                Text("ADD")
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.removeProductFromCart(prod, defaultVendor) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = SaffronOrange)
                                }
                                Text("${inCart.quantity}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { vm.addProductToCart(prod, defaultVendor) }) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = SaffronOrange)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// VENDOR SHOP SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorShopScreen(vm: ApnaDhobiViewModel, vendorId: String) {
    val vendors by vm.vendorsState.collectAsState()
    val vendor = vendors.find { it.id == vendorId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vendor?.name ?: "Vendor Details") },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Text("Vendor Shop Details", modifier = Modifier.padding(16.dp))
        }
    }
}

// ==========================================
// SLOT SELECTION SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotSelectionScreen(vm: ApnaDhobiViewModel) {
    val currentAddress by vm.currentFullAddress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule Pickup & Delivery") },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pickup Address", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                        Text(currentAddress.ifBlank { "Tap to select address..." }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { vm.navigateTo(ApnaDhobiScreen.Payment) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("PROCEED TO PAYMENT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// CART SCREEN CONTENT ("Your Laundry Bucket")
// ==========================================
@Composable
fun CartScreenContent(vm: ApnaDhobiViewModel) {
    val cartItems by vm.cartItems.collectAsState()
    val finalTotal by vm.cartFinalTotal.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Laundry Bucket",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Charcoal
        )
        Text(
            text = "Review items & proceed to pickup slot",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Color.LightGray.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your laundry bucket is empty!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Charcoal,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add products from nearby local professional laundry providers.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { vm.selectBottomTab("home") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Explore Services 🧺",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cartItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.productName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Charcoal
                                )
                                Text(
                                    text = "₹${item.discountPrice} / item",
                                    color = SaffronOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { vm.updateCartQuantity(item.id, item.quantity - 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "${item.quantity}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { vm.updateCartQuantity(item.id, item.quantity + 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { vm.navigateTo(ApnaDhobiScreen.SlotSelection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
            ) {
                Text("SELECT PICKUP SLOT (₹$finalTotal)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ==========================================
// ORDERS HISTORY LIST CONTENT ("Your Wardrobe History")
// ==========================================
@Composable
fun OrdersHistoryListContent(vm: ApnaDhobiViewModel) {
    val orders by vm.ordersList.collectAsState()
    var selectedFilter by remember { mutableStateOf("Active Progress") }

    val filteredOrders = when (selectedFilter) {
        "Active Progress" -> orders.filter { it.status.uppercase() != "DELIVERED" && it.status.uppercase() != "CANCELLED" }
        "Delivered" -> orders.filter { it.status.uppercase() == "DELIVERED" }
        "Cancelled/Returned" -> orders.filter { it.status.uppercase() == "CANCELLED" }
        else -> orders
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Wardrobe History",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Charcoal
        )
        Text(
            text = "Track & manage your laundry progress",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips matching Screenshot 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                Pair("Active Progress", orders.count { it.status.uppercase() != "DELIVERED" && it.status.uppercase() != "CANCELLED" }.let { if (it == 0) 1 else it }),
                Pair("Delivered", orders.count { it.status.uppercase() == "DELIVERED" }),
                Pair("Cancelled/Returned", orders.count { it.status.uppercase() == "CANCELLED" })
            )

            filters.forEach { (filterName, count) ->
                val isSelected = selectedFilter == filterName
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFilter = filterName },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) SaffronOrange else Color.White,
                    shadowElevation = if (isSelected) 3.dp else 1.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (filterName == "Active Progress") "$filterName ⏰" else filterName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayOrders = if (filteredOrders.isEmpty() && selectedFilter == "Active Progress") {
            listOf(
                com.example.data.OrderRecord(
                    id = 98124,
                    vendorName = "Apna Dhobi Express",
                    itemsSummary = "Men's Shirt Wash & Fold (x1)",
                    totalPrice = 176.0,
                    pickupSlot = "Today | 04:00 PM",
                    deliverySlot = "Tue, Aug 18 (Tomorrow) | 10:00 AM",
                    paymentMethod = "UPI",
                    status = "WASHING"
                )
            )
        } else filteredOrders

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayOrders) { ord ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.navigateTo(ApnaDhobiScreen.OrderTracking(ord.id)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ord.vendorName.ifBlank { "Apna Dhobi Express" },
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Charcoal
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SaffronOrange
                            ) {
                                Text(
                                    text = ord.status.uppercase(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ord.itemsSummary.ifBlank { "Laundry Items (x1)" },
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color.LightGray.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = SaffronOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ord.deliverySlot.ifBlank { "Tue, Aug 18 (Tomorrow) | 10:00 AM" },
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Paid Amount: ₹${ord.totalPrice.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Charcoal
                                )
                            }

                            Button(
                                onClick = { vm.navigateTo(ApnaDhobiScreen.OrderTracking(ord.id)) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Track 📍", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

// ==========================================
// PAYMENT SCREEN
// ==========================================
@Composable
fun PaymentScreen(vm: ApnaDhobiViewModel) {
    val finalTotal by vm.cartFinalTotal.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Payment Summary", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Total Amount: ₹$finalTotal", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { vm.processCheckout("UPI", false) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
        ) {
            Text("PAY WITH RAZORPAY / UPI", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// WALLET CONTENT
// ==========================================
@Composable
fun WalletContent(vm: ApnaDhobiViewModel) {
    WalletDashboard(vm = vm)
}

// ==========================================
// PROFILE & SETTINGS (RESTORED FULL USER PROFILE DASHBOARD)
// ==========================================
@Composable
fun ProfileSettingsAndChatCentric(vm: ApnaDhobiViewModel) {
    UserProfileDashboard(vm = vm)
}

// ==========================================
// APNA FLOATING NAVBAR
// ==========================================
@Composable
fun ApnaFloatingNavbar(vm: ApnaDhobiViewModel) {
    val activeTab by vm.activeTab.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("home", Icons.Default.Home, "Home"),
                Triple("orders", Icons.Default.Schedule, "Orders"),
                Triple("cart", Icons.Default.ShoppingCart, "Cart"),
                Triple("wallet", Icons.Default.AccountBalanceWallet, "Wallet"),
                Triple("profile", Icons.Default.Person, "Profile")
            ).forEach { (tabId, icon, label) ->
                val selected = activeTab == tabId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { vm.selectBottomTab(tabId) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) SaffronOrange else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) SaffronOrange else Color.Gray
                    )
                }
            }
        }
    }
}

// ==========================================
// UI HELPER COMPONENTS
// ==========================================
@Composable
fun PromoBannerSlider(banners: List<BannerDto>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(banners) { b ->
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .height(120.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlue)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column {
                        Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(b.subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
