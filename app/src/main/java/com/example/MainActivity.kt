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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.text.style.TextDecoration
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
import androidx.compose.ui.layout.ContentScale
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
// MODERN CATEGORY VISUAL GRAPHICS (MATCHING IMAGE 2)
// ==========================================
private fun drawSparkle(scope: DrawScope, center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticBezierTo(center.x, center.y, center.x + size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + size)
        quadraticBezierTo(center.x, center.y, center.x - size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - size)
        close()
    }
    scope.drawPath(path, color)
}

@Composable
fun WashingMachineIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Washing machine solid outer body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.06f),
            size = Size(w * 0.80f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.18f, h * 0.18f)
        )

        // Top control panel separator line
        drawLine(
            color = Color.White.copy(alpha = 0.90f),
            start = Offset(w * 0.18f, h * 0.26f),
            end = Offset(w * 0.82f, h * 0.26f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )

        // Top right dial button
        drawCircle(
            color = Color.White,
            radius = w * 0.055f,
            center = Offset(w * 0.72f, h * 0.16f)
        )

        // Top left detergent drawer indicator
        drawLine(
            color = Color.White,
            start = Offset(w * 0.22f, h * 0.16f),
            end = Offset(w * 0.44f, h * 0.16f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )

        // Outer glass door circle (thick white ring)
        drawCircle(
            color = Color.White,
            radius = w * 0.24f,
            center = Offset(w * 0.50f, h * 0.59f),
            style = Stroke(width = w * 0.065f)
        )

        // Inner drum water wave/swirl
        val wavePath = Path().apply {
            moveTo(w * 0.35f, h * 0.60f)
            quadraticBezierTo(w * 0.42f, h * 0.52f, w * 0.50f, h * 0.60f)
            quadraticBezierTo(w * 0.58f, h * 0.68f, w * 0.65f, h * 0.60f)
        }
        drawPath(
            path = wavePath,
            color = Color.White,
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun IronIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Iron solid body with pointy nose
        val ironBody = Path().apply {
            moveTo(w * 0.88f, h * 0.68f)
            lineTo(w * 0.14f, h * 0.68f)
            quadraticBezierTo(w * 0.08f, h * 0.68f, w * 0.08f, h * 0.60f)
            lineTo(w * 0.16f, h * 0.34f)
            quadraticBezierTo(w * 0.22f, h * 0.26f, w * 0.36f, h * 0.26f)
            lineTo(w * 0.74f, h * 0.26f)
            quadraticBezierTo(w * 0.86f, h * 0.28f, w * 0.82f, h * 0.44f)
            lineTo(w * 0.68f, h * 0.48f)
            lineTo(w * 0.36f, h * 0.48f)
            quadraticBezierTo(w * 0.28f, h * 0.48f, w * 0.26f, h * 0.54f)
            lineTo(w * 0.80f, h * 0.56f)
            close()
        }
        drawPath(path = ironBody, color = tint)

        // Handle inner cut-out window
        val handleHole = Path().apply {
            moveTo(w * 0.34f, h * 0.36f)
            lineTo(w * 0.68f, h * 0.36f)
            lineTo(w * 0.58f, h * 0.46f)
            lineTo(w * 0.30f, h * 0.46f)
            close()
        }
        drawPath(path = handleHole, color = Color.White)

        // Bottom Soleplate line
        drawLine(
            color = tint,
            start = Offset(w * 0.10f, h * 0.78f),
            end = Offset(w * 0.90f, h * 0.78f),
            strokeWidth = w * 0.065f,
            cap = StrokeCap.Round
        )

        // Heat dial knob
        drawCircle(
            color = Color.White,
            radius = w * 0.055f,
            center = Offset(w * 0.46f, h * 0.58f)
        )
    }
}

@Composable
fun DryCleanIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Solid Shirt / Garment Body
        val shirtPath = Path().apply {
            moveTo(w * 0.34f, h * 0.14f)
            lineTo(w * 0.08f, h * 0.28f)
            lineTo(w * 0.16f, h * 0.46f)
            lineTo(w * 0.26f, h * 0.40f)
            lineTo(w * 0.26f, h * 0.88f)
            lineTo(w * 0.74f, h * 0.88f)
            lineTo(w * 0.74f, h * 0.40f)
            lineTo(w * 0.84f, h * 0.46f)
            lineTo(w * 0.92f, h * 0.28f)
            lineTo(w * 0.66f, h * 0.14f)
            quadraticBezierTo(w * 0.50f, h * 0.28f, w * 0.34f, h * 0.14f)
            close()
        }
        drawPath(path = shirtPath, color = tint)

        // Sparkle Star 1 (Top Right)
        drawSparkle(this, Offset(w * 0.52f, h * 0.42f), w * 0.09f, Color.White)

        // Sparkle Star 2 (Bottom Left)
        drawSparkle(this, Offset(w * 0.38f, h * 0.68f), w * 0.065f, Color.White)

        // Sparkle Star 3 (Bottom Right)
        drawSparkle(this, Offset(w * 0.62f, h * 0.64f), w * 0.055f, Color.White)
    }
}

@Composable
fun CarpetShoeIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Folded Rug / Carpet body with roll
        val rugPath = Path().apply {
            moveTo(w * 0.18f, h * 0.16f)
            lineTo(w * 0.72f, h * 0.16f)
            quadraticBezierTo(w * 0.90f, h * 0.16f, w * 0.90f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.74f)
            lineTo(w * 0.38f, h * 0.74f)
            quadraticBezierTo(w * 0.18f, h * 0.74f, w * 0.18f, h * 0.48f)
            close()
        }
        drawPath(path = rugPath, color = tint)

        // Rug fold / roll highlight
        drawCircle(
            color = Color.White,
            radius = w * 0.10f,
            center = Offset(w * 0.38f, h * 0.34f)
        )
        drawCircle(
            color = tint,
            radius = w * 0.055f,
            center = Offset(w * 0.38f, h * 0.34f)
        )

        // Fringe Tassels at bottom
        for (i in 0..4) {
            val startX = w * (0.44f + i * 0.095f)
            drawLine(
                color = tint,
                start = Offset(startX, h * 0.76f),
                end = Offset(startX, h * 0.90f),
                strokeWidth = w * 0.045f,
                cap = StrokeCap.Round
            )
        }

        // Clean sparkle on carpet
        drawSparkle(this, Offset(w * 0.66f, h * 0.46f), w * 0.08f, Color.White)
    }
}

@Composable
fun ShoeIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Sneaker body path
        val shoePath = Path().apply {
            moveTo(w * 0.12f, h * 0.65f)
            lineTo(w * 0.88f, h * 0.65f)
            quadraticBezierTo(w * 0.92f, h * 0.55f, w * 0.80f, h * 0.50f)
            lineTo(w * 0.58f, h * 0.48f)
            lineTo(w * 0.48f, h * 0.24f)
            quadraticBezierTo(w * 0.38f, h * 0.20f, w * 0.30f, h * 0.28f)
            lineTo(w * 0.22f, h * 0.42f)
            lineTo(w * 0.12f, h * 0.50f)
            close()
        }
        drawPath(path = shoePath, color = tint)

        // Thick Soleplate
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.76f),
            end = Offset(w * 0.92f, h * 0.76f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )

        // Lace eyelet stripes
        drawLine(
            color = Color.White,
            start = Offset(w * 0.50f, h * 0.36f),
            end = Offset(w * 0.60f, h * 0.40f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.45f, h * 0.44f),
            end = Offset(w * 0.56f, h * 0.48f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )

        // Sparkle above sneaker
        drawSparkle(this, Offset(w * 0.76f, h * 0.30f), w * 0.08f, tint)
    }
}

@Composable
fun WeddingWearIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Royal hanger / sherwani coat outline
        val suitPath = Path().apply {
            moveTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.16f, h * 0.32f)
            lineTo(w * 0.22f, h * 0.88f)
            lineTo(w * 0.78f, h * 0.88f)
            lineTo(w * 0.84f, h * 0.32f)
            close()
        }
        drawPath(path = suitPath, color = tint)

        // Golden / White Lapel
        val lapelPath = Path().apply {
            moveTo(w * 0.50f, h * 0.30f)
            lineTo(w * 0.40f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.80f)
            lineTo(w * 0.60f, h * 0.55f)
            close()
        }
        drawPath(path = lapelPath, color = Color.White)

        // Sparkle
        drawSparkle(this, Offset(w * 0.78f, h * 0.24f), w * 0.08f, tint)
    }
}

@Composable
fun PremiumCareIconGraphic(tint: Color = SaffronOrange, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Crown / Diamond Premium Shape
        val crownPath = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.30f, h * 0.72f)
            lineTo(w * 0.70f, h * 0.72f)
            lineTo(w * 0.85f, h * 0.35f)
            lineTo(w * 0.65f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.24f)
            lineTo(w * 0.35f, h * 0.48f)
            close()
        }
        drawPath(path = crownPath, color = tint)

        // Crown baseline
        drawLine(
            color = tint,
            start = Offset(w * 0.25f, h * 0.80f),
            end = Offset(w * 0.75f, h * 0.80f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )

        // Crown Jewels / Circles
        drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.48f))
        drawCircle(color = Color.White, radius = w * 0.035f, center = Offset(w * 0.35f, h * 0.56f))
        drawCircle(color = Color.White, radius = w * 0.035f, center = Offset(w * 0.65f, h * 0.56f))
    }
}

@Composable
fun CategoryModernVisual(
    category: ServiceCategory,
    modifier: Modifier = Modifier,
    tint: Color = SaffronOrange
) {
    val rawCatImg = category.imageUrl?.takeIf { it.isNotBlank() }
    val resolvedCatImg = rawCatImg?.let { url ->
        when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "http://10.0.2.2:3000$url"
            else -> "http://10.0.2.2:3000/$url"
        }
    }

    if (resolvedCatImg != null) {
        com.example.ui.ApnaNetworkImage(
            url = resolvedCatImg,
            contentDescription = category.name,
            modifier = modifier
                .clip(CircleShape)
                .padding(3.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        when {
            category.id == "laundry" || category.iconName.contains("Wash", ignoreCase = true) || category.name.contains("Wash", ignoreCase = true) || category.name.contains("Laundry", ignoreCase = true) -> {
                WashingMachineIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "ironing" || category.iconName.contains("Iron", ignoreCase = true) || category.name.contains("Iron", ignoreCase = true) -> {
                IronIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "dry_cleaning" || category.iconName.contains("Dry", ignoreCase = true) || category.name.contains("Dry", ignoreCase = true) -> {
                DryCleanIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "shoe_cleaning" || category.name.contains("Shoe", ignoreCase = true) -> {
                ShoeIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "carpet_cleaning" || category.name.contains("Carpet", ignoreCase = true) -> {
                CarpetShoeIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "blanket_wash" || category.name.contains("Blanket", ignoreCase = true) -> {
                CarpetShoeIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "wedding_wear" || category.name.contains("Wedding", ignoreCase = true) -> {
                WeddingWearIconGraphic(tint = tint, modifier = modifier)
            }
            category.id == "premium_care" || category.name.contains("Premium", ignoreCase = true) -> {
                PremiumCareIconGraphic(tint = tint, modifier = modifier)
            }
            else -> {
                WashingMachineIconGraphic(tint = tint, modifier = modifier)
            }
        }
    }
}

// ==========================================
// HOME DASHBOARD CONTENT (REDESIGNED TO TARGET LAYOUT)
// ==========================================
@Composable
fun HomeDashboardContent(vm: ApnaDhobiViewModel) {
    val fullAddress by vm.currentFullAddress.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val vendors by vm.vendorsState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val cartItems by vm.cartItems.collectAsState()
    val context = LocalContext.current

    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Automatically sync latest banners and categories from backend database on Home screen load
    LaunchedEffect(Unit) {
        vm.refreshCatalog()
    }

    if (showNotificationsDialog) {
        val notificationList by vm.notifications.collectAsState()
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifications 🔔", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal)
                    if (notificationList.isNotEmpty()) {
                        Text(
                            text = "Clear all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronOrange,
                            modifier = Modifier
                                .clickable { vm.clearNotifications() }
                                .padding(4.dp)
                        )
                    }
                }
            },
            text = {
                if (notificationList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No new notifications today! 🎉", fontSize = 13.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notificationList) { msg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LightCream,
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(msg, fontSize = 12.5.sp, color = Charcoal, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Close", color = SaffronOrange, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // 1. TOP COMPACT HEADER BAR
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Brand & Location details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Brand Logo + Name
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = SaffronOrange.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocalLaundryService,
                                        contentDescription = null,
                                        tint = SaffronOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apna Dhobi",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal,
                                letterSpacing = (-0.3).sp
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Location Pin + Address Text + Dropdown
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { vm.navigateTo(ApnaDhobiScreen.LocationSelection) }
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = SaffronOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = fullAddress.ifBlank { "Connaught Place, New Delhi..." },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Charcoal.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right Notification & Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    showNotificationsDialog = true
                                },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = "Notifications",
                                    tint = Charcoal,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    vm.setActiveTab("cart")
                                },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = "Cart / Bucket",
                                    tint = if (cartItems.isNotEmpty()) SaffronOrange else Charcoal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. CLEAN COMPACT ROUNDED SEARCH BAR
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = SaffronOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search services, dry cleaning, iron...",
                                fontSize = 12.5.sp,
                                color = Color.Gray
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { vm.searchQuery.value = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.5.sp,
                                color = Charcoal,
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = SaffronOrange.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 3. SPECIAL FOR YOU PROMO BANNERS SLIDER
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                com.example.ui.PromoBannerSlider(vm = vm, onPromoClick = { code ->
                    Toast.makeText(context, "Promo code applied: $code 🎉", Toast.LENGTH_SHORT).show()
                })
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4. WHAT CARE DO YOUR GARMENTS NEED? (Services / Categories)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text(
                    text = "What care do your garments need?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Choose from our specialized fabric care services",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories) { cat ->
                    val catThemeColor = SaffronOrange

                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .clickable {
                                vm.navigateTo(ApnaDhobiScreen.ProductListing(cat.id, cat.name))
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = Color(0xFFF3F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryModernVisual(
                                    category = cat,
                                    modifier = Modifier.size(42.dp),
                                    tint = catThemeColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cat.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Charcoal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 5. AI VOICE-BASED BOOKING BANNER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(SaffronOrange, Color(0xFFFF8C00))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Booking",
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Voice-Based Booking",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Just tap & speak your order in English/Hindi!",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "Listening... Speak your order! 🎤", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Speak 🎤", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 5.1 MID PROMO SUB-BANNER SLIDER (IMAGE 2 TARGET LAYOUT)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                com.example.ui.MidBannerSlider(
                    vm = vm,
                    onPromoClick = { codeOrUrl ->
                        if (codeOrUrl.startsWith("/")) {
                            vm.navigateTo(ApnaDhobiScreen.ProductListing("laundry", "Wash & Fold"))
                        } else {
                            Toast.makeText(context, "Promo code applied: $codeOrUrl 🎉", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 6. EXPRESS PICKUP & DELIVERY CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { vm.navigateTo(ApnaDhobiScreen.SlotSelection) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                elevation = CardDefaults.cardElevation(1.5.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = GoldPremium.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = GoldPremium,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Express Pickup & Delivery",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal
                        )
                        Text(
                            text = "We pick up and deliver at your doorstep",
                            fontSize = 10.5.sp,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 7. POPULAR LAUNDRY NEARBY
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Popular Laundry Nearby",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )
                Text(
                    text = "See all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    modifier = Modifier
                        .clickable { vm.navigateTo(ApnaDhobiScreen.ProductListing("laundry", "All Laundries")) }
                        .padding(2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vendors) { vendor ->
                    val rawVendorImg = vendor.imageUrl?.takeIf { it.isNotBlank() }
                    val resolvedVendorImg = rawVendorImg?.let { url ->
                        when {
                            url.startsWith("http://") || url.startsWith("https://") -> url
                            url.startsWith("/") -> "http://10.0.2.2:3000$url"
                            else -> "http://10.0.2.2:3000/$url"
                        }
                    }

                    Card(
                        modifier = Modifier
                            .width(205.dp)
                            .clickable { vm.navigateTo(ApnaDhobiScreen.VendorShop(vendor.id)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                    ) {
                        Column {
                            // Top Provider Visual Banner (Dynamic media with fallback)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (resolvedVendorImg != null) {
                                    com.example.ui.ApnaNetworkImage(
                                        url = resolvedVendorImg,
                                        contentDescription = vendor.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        RoyalBlue.copy(alpha = 0.85f),
                                                        SaffronOrange.copy(alpha = 0.85f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(38.dp),
                                                shape = CircleShape,
                                                color = Color.White.copy(alpha = 0.92f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = vendor.logoText.ifBlank { vendor.name.take(2).uppercase() },
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 14.sp,
                                                        color = RoyalBlue
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "★ Verified Partner",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Provider details (Matching Image 2 Specs)
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = vendor.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Charcoal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${vendor.rating}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Charcoal
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = GoldPremium,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "(${vendor.ratingCount})",
                                            fontSize = 9.5.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Service tags (Row 1: Wash & Fold, Dry Cleaning)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Text(
                                            text = "Wash & Fold",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4B5563),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Text(
                                            text = "Dry Cleaning",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4B5563),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // Service tags (Row 2: Carpet Wash, Wash & Iron)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Text(
                                            text = "Carpet Wash",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4B5563),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Text(
                                            text = "Wash & Iron",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4B5563),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                // Highlighted Service badges (Row 3: Free Pickup, 24h Delivery)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = RoyalBlue.copy(alpha = 0.09f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🚚 Free Pickup",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RoyalBlue
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SaffronOrange.copy(alpha = 0.09f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "⚡ 24h Delivery",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SaffronOrange
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Bottom Row: Open status on Left, Distance on Right
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (vendor.isOpen) GreenSuccess else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (vendor.isOpen) "Open Now" else "Closed",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (vendor.isOpen) GreenSuccess else Color.Gray
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = SaffronOrange,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${vendor.distanceKm} km",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Charcoal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 9. FOOTER PROMO BANNER SLIDER
        item {
            com.example.ui.FooterBannerSlider(
                vm = vm,
                onPromoClick = { codeOrUrl ->
                    if (codeOrUrl.startsWith("/")) {
                        vm.navigateTo(ApnaDhobiScreen.ProductListing("ironing", "Steam Ironing"))
                    } else {
                        Toast.makeText(context, "Offer Claimed: $codeOrUrl 🎉", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
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
// PRODUCT LISTING SCREEN (MATCHING IMAGE 1)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListingScreen(vm: ApnaDhobiViewModel, categoryId: String, categoryName: String) {
    val products by vm.productsState.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val cartItems by vm.cartItems.collectAsState()
    val vendors by vm.vendorsState.collectAsState()
    val defaultVendor = vendors.firstOrNull() ?: Vendor("v1", "Apna Dhobi Hub", "Central Laundromat", 4.8, 1.2, 30, 49, "#1E88E5", "AD", true)

    // Current category selection state
    var selectedCatId by remember(categoryId) { 
        mutableStateOf(if (categoryId.isNotBlank()) categoryId else (categories.firstOrNull()?.id ?: "dry_cleaning")) 
    }
    var searchQuery by remember { mutableStateOf("") }

    // Find the currently selected category object
    val selectedCategoryObj = categories.find { it.id.equals(selectedCatId, ignoreCase = true) }
        ?: categories.find { it.name.equals(selectedCatId, ignoreCase = true) }

    val displayTitle = selectedCategoryObj?.name ?: categoryName.ifBlank { "Dry Cleaning" }
    val displayDescription = selectedCategoryObj?.description?.takeIf { it.isNotBlank() }
        ?: "Laundering at: Apna Dhobi Express"

    val totalCartCount = cartItems.sumOf { it.quantity }

    // Filter products by selected category and search query
    val filteredProducts = remember(products, selectedCatId, searchQuery) {
        products.filter { prod ->
            val matchesCategory = if (selectedCatId.isBlank() || selectedCatId.equals("all", ignoreCase = true)) {
                true
            } else {
                prod.categoryId.equals(selectedCatId, ignoreCase = true) ||
                (selectedCatId == "dry_cleaning" && prod.categoryId == "dry_cleaning") ||
                (selectedCatId == "laundry" && prod.categoryId == "laundry") ||
                (selectedCatId == "ironing" && prod.categoryId == "ironing") ||
                (selectedCatId == "shoe_cleaning" && prod.categoryId == "shoe_cleaning")
            }
            val matchesSearch = searchQuery.isBlank() || 
                prod.name.contains(searchQuery, ignoreCase = true) || 
                (prod.popularBadge?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesSearch
        }
    }

    // State for Customize Garment Treatment Dialog (Image Modal)
    var customizingProduct by remember { mutableStateOf<LaundryProduct?>(null) }

    // Render Modal Dialog when user clicks ADD on a product
    customizingProduct?.let { prodToCustomize ->
        CustomizeGarmentTreatmentDialog(
            product = prodToCustomize,
            vendor = defaultVendor,
            onDismiss = { customizingProduct = null },
            onAddToBasket = { treatment, notes, qty ->
                vm.addProductToCartCustomized(prodToCustomize, defaultVendor, treatment, notes, qty)
                customizingProduct = null
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFFFDFBF7), // Matching soft off-white cream background in Image 1
        topBar = {
            Surface(
                color = Color(0xFFFDFBF7),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Row: Back button, Title & Category Description, Cart Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { vm.navigateBack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1E3A8A), // Navy Blue
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E3A8A) // Deep Navy Blue (Image 1)
                            )
                            Text(
                                text = displayDescription,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Top right Cart button with badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { 
                                    vm.setActiveTab("cart")
                                    vm.navigateTo(ApnaDhobiScreen.HomeFrame)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Cart",
                                tint = Color(0xFF1E3A8A),
                                modifier = Modifier.size(24.dp)
                            )
                            if (totalCartCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = SaffronOrange,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(17.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$totalCartCount",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Rounded Search Bar (Image 1: "Search clothes (e.g., shirt, saree, shoe...)")
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF1E3A8A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Charcoal
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search clothes (e.g., shirt, saree, shoe...)",
                                            fontSize = 13.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Horizontal Category Filter Pills (Image 1: Laundry, Dry Cleaning (Selected), Ironing, Shoe Cleaning)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat.id.equals(selectedCatId, ignoreCase = true) ||
                                            (selectedCatId == "dry_cleaning" && cat.id == "dry_cleaning") ||
                                            (selectedCatId == "laundry" && cat.id == "laundry") ||
                                            (selectedCatId == "ironing" && cat.id == "ironing") ||
                                            (selectedCatId == "shoe_cleaning" && cat.id == "shoe_cleaning")

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) SaffronOrange else Color.White,
                                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shadowElevation = if (isSelected) 2.dp else 0.5.dp,
                                modifier = Modifier.clickable {
                                    selectedCatId = cat.id
                                }
                            ) {
                                Text(
                                    text = cat.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧺", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No clothes found in this category",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal
                    )
                    Text(
                        text = "Try switching category or clear search query",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // 2-Column Product Grid (Image 1 layout)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredProducts) { prod ->
                    val inCart = cartItems.find { it.productId == prod.id }
                    val hasBadge = !prod.popularBadge.isNullOrBlank()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customizingProduct = prod },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column {
                            // Top Image Container with Badge Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(Color(0xFFF8FAFC)),
                                contentAlignment = Alignment.Center
                            ) {
                                val rawImg = prod.imageUrl?.takeIf { it.isNotBlank() }
                                val resolvedImg = rawImg?.let { url ->
                                    when {
                                        url.startsWith("http://") || url.startsWith("https://") -> url
                                        url.startsWith("/") -> "http://10.0.2.2:3000$url"
                                        else -> "http://10.0.2.2:3000/$url"
                                    }
                                }

                                if (resolvedImg != null) {
                                    com.example.ui.ApnaNetworkImage(
                                        url = resolvedImg,
                                        contentDescription = prod.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // High quality fallback vector visual
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("👔", fontSize = 42.sp)
                                    }
                                }

                                // Premium Badge on top-left of image (Image 1: "PREMIUM CHOICE" / "POPULAR")
                                if (hasBadge) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = SaffronOrange
                                    ) {
                                        Text(
                                            text = prod.popularBadge!!.uppercase(),
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                        )
                                    }
                                }
                            }

                            // Product Details Section (Title, Delivery, Price & ADD Button)
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = prod.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3A8A), // Navy Blue (Image 1)
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (prod.deliveryEstimate.startsWith("Delivery:", ignoreCase = true)) prod.deliveryEstimate else "Delivery: ${prod.deliveryEstimate}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Bottom Row: Pricing & ADD Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        // Strikethrough Original Price
                                        if (prod.originalPrice > prod.discountPrice) {
                                            Text(
                                                text = "₹${prod.originalPrice.toInt()}",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF94A3B8),
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        }
                                        // Bold Orange Discount Price
                                        Text(
                                            text = "₹${prod.discountPrice.toInt()}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = SaffronOrange
                                        )
                                    }

                                    // ADD / Quantity Button
                                    if (inCart == null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E3A8A), // Deep Navy Blue (Image 1)
                                            shadowElevation = 1.5.dp,
                                            modifier = Modifier.clickable {
                                                customizingProduct = prod
                                            }
                                        ) {
                                            Text(
                                                text = "ADD",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E3A8A).copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, Color(0xFF1E3A8A))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { vm.removeProductFromCart(prod, defaultVendor) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                                }
                                                Text(
                                                    text = "${inCart.quantity}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1E3A8A),
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable {
                                                            customizingProduct = prod
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
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
    }
}

/**
 * Customize Garment Treatment Dialog Modal (Exact Design from Uploaded Image)
 */
@Composable
fun CustomizeGarmentTreatmentDialog(
    product: LaundryProduct,
    vendor: Vendor,
    onDismiss: () -> Unit,
    onAddToBasket: (treatment: String, notes: String, quantity: Int) -> Unit
) {
    var selectedTreatment by remember { mutableStateOf("Standard Wash 🧺") }
    var userNotes by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }

    val treatments = listOf(
        Triple("Standard Wash 🧺", "Inbuilt deep clean", 0),
        Triple("Delicate Silk 🌸", "Delicately washed for fabrics like silk", 50),
        Triple("Heavy Bead 💎", "No damage wash for beaded/bridal wear", 100)
    )

    val extraCost = treatments.find { it.first == selectedTreatment }?.third ?: 0
    val unitPrice = product.discountPrice.toInt() + extraCost
    val totalPrice = unitPrice * quantity

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF232733), // Dark charcoal-slate container from Image
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Customize Garment Treatment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8) // Bright Blue header from Image
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Configure options for ${product.name}",
                    fontSize = 12.5.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: SELECT FABRIC TREATMENT TYPE
                Text(
                    text = "SELECT FABRIC TREATMENT TYPE:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                treatments.forEach { (name, desc, extra) ->
                    val isSelected = selectedTreatment == name
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedTreatment = name },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color.White,
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF2563EB)) else null,
                        shadowElevation = if (isSelected) 0.dp else 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF0F172A)
                                )
                                Text(
                                    text = desc,
                                    fontSize = 10.5.sp,
                                    color = if (isSelected) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                            Text(
                                text = "+₹$extra",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaffronOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: SPECIAL LAUNDERING INSTRUC
                Text(
                    text = "SPECIAL LAUNDERING INSTRUC:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = userNotes,
                            onValueChange = { userNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.5.sp,
                                color = Color(0xFF0F172A)
                            ),
                            decorationBox = { innerTextField ->
                                if (userNotes.isEmpty()) {
                                    Text(
                                        text = "e.g. starch heavily, remove cuff stain...",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: SELECT QTY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT QTY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronOrange,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { if (quantity > 1) quantity-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            }
                            Text(
                                text = "$quantity",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { quantity++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary CTA Button: Add to Basket • ₹totalPrice
                Button(
                    onClick = {
                        onAddToBasket(selectedTreatment, userNotes, quantity)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text(
                        text = "Add to Basket • ₹$totalPrice",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cancel Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// ==========================================
// VENDOR SHOP SCREEN (MATCHING IMAGE 3 + REAL-TIME CONTROLS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorShopScreen(vm: ApnaDhobiViewModel, vendorId: String) {
    val vendors by vm.vendorsState.collectAsState()
    val products by vm.productsState.collectAsState()
    val cartItems by vm.cartItems.collectAsState()
    val favVendors by vm.favoriteVendorIds.collectAsState()
    val reviewsMap by vm.vendorReviewsState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val vendor = vendors.find { it.id == vendorId } ?: vendors.firstOrNull() ?: Vendor(
        id = "vendor_1",
        name = "Apna Dhobi Express",
        description = "Express laundry, ironing & fabric care. We use eco-friendly detergents and advanced German washing technology for supreme hygiene and wrinkle-free finishing.",
        rating = 4.8,
        distanceKm = 1.2,
        deliveryTimeMins = 45,
        startingPrice = 49,
        bannerColorHex = "0xFF0D47A1",
        logoText = "ADE",
        isOpen = true
    )

    val isFav = favVendors.contains(vendor.id)

    // Build the dynamic list of store images
    val allStoreImages = remember(vendor) {
        val list = mutableListOf<String>()
        val mainImg = vendor.imageUrl?.takeIf { it.isNotBlank() }
        if (mainImg != null) {
            val resolved = when {
                mainImg.startsWith("http://") || mainImg.startsWith("https://") -> mainImg
                mainImg.startsWith("/") -> "http://10.0.2.2:3000$mainImg"
                else -> "http://10.0.2.2:3000/$mainImg"
            }
            list.add(resolved)
        }
        if (vendor.galleryImages.isNotEmpty()) {
            list.addAll(vendor.galleryImages)
        } else {
            list.addAll(listOf(
                "https://images.unsplash.com/photo-1545173168-9f1947eebb7f?w=800&q=80",
                "https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?w=800&q=80",
                "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?w=800&q=80",
                "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&q=80"
            ))
        }
        list.distinct()
    }

    val pagerState = rememberPagerState(pageCount = { allStoreImages.size.coerceAtLeast(1) })

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReadMore by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Dialog state for Customizing Weight / Pairs & Fabric Inquiry
    var customizingProduct by remember { mutableStateOf<LaundryProduct?>(null) }
    var itemQuantity by remember { mutableIntStateOf(1) }
    var itemWeightKg by remember { mutableStateOf("1") }
    var unitMode by remember { mutableStateOf("Pairs / Pieces") }
    var fabricInquiryText by remember { mutableStateOf("") }

    // Dialog state for Writing a Review
    var showWriteReviewDialog by remember { mutableStateOf(false) }
    var reviewAuthorName by remember { mutableStateOf("") }
    var reviewRatingScore by remember { mutableDoubleStateOf(5.0) }
    var reviewCommentText by remember { mutableStateOf("") }

    val tabTitles = listOf("About", "Services", "Gallery", "Reviews")

    // --- ITEM CUSTOMIZATION & INQUIRY MODAL DIALOG ---
    customizingProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { customizingProduct = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = SaffronOrange.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LocalLaundryService, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal)
                        Text("Base: ₹${prod.discountPrice.toInt()} • ${prod.deliveryEstimate}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Unit selector (Pieces/Pairs vs Weight in Kg)
                    Text("SELECT QUANTITY TYPE:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Pairs / Pieces", "Weight (Kg)").forEach { mode ->
                            val isSelected = unitMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { unitMode = mode },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SaffronOrange.copy(alpha = 0.12f) else Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color.Transparent)
                            ) {
                                Text(
                                    text = mode,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SaffronOrange else Charcoal,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (unitMode == "Pairs / Pieces") {
                        Text("NUMBER OF PAIRS / PIECES:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 2, 3, 5).forEach { count ->
                                    Surface(
                                        modifier = Modifier.clickable { itemQuantity = count },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (itemQuantity == count) SaffronOrange else Color(0xFFF0F0F0)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (itemQuantity == count) Color.White else Charcoal,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (itemQuantity > 1) itemQuantity-- },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Minus", tint = SaffronOrange)
                                }
                                Text("$itemQuantity", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp))
                                IconButton(
                                    onClick = { itemQuantity++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Plus", tint = SaffronOrange)
                                }
                            }
                        }
                    } else {
                        Text("ESTIMATED WEIGHT (KG):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1", "2", "3", "5", "8+").forEach { kg ->
                                val isSelected = itemWeightKg == kg
                                Surface(
                                    modifier = Modifier.clickable { itemWeightKg = kg },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) SaffronOrange else Color(0xFFF0F0F0)
                                ) {
                                    Text(
                                        text = "$kg Kg",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Charcoal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Special Care Inquiry & Notes
                    Text("SPECIAL INQUIRY / FABRIC CARE NOTES:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fabricInquiryText,
                        onValueChange = { fabricInquiryText = it },
                        placeholder = { Text("e.g. Starch collar, separate whites, gentle fabric wash...", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = false,
                        maxLines = 2,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Charcoal)
                    )
                }
            },
            confirmButton = {
                val multiplier = if (unitMode == "Pairs / Pieces") itemQuantity else (itemWeightKg.toIntOrNull() ?: 1)
                val finalPrice = (prod.discountPrice * multiplier).toInt()
                Button(
                    onClick = {
                        for (i in 1..multiplier) {
                            vm.addProductToCart(prod, vendor)
                        }
                        if (fabricInquiryText.isNotBlank()) {
                            vm.pushSimulatedNotification("Note saved for ${prod.name}: '$fabricInquiryText'")
                        }
                        customizingProduct = null
                        Toast.makeText(context, "Added $multiplier ${prod.name} (₹$finalPrice) to cart! 🧺", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("Add to Cart • ₹$finalPrice", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { customizingProduct = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    // --- WRITE REAL-TIME REVIEW DIALOG ---
    if (showWriteReviewDialog) {
        AlertDialog(
            onDismissRequest = { showWriteReviewDialog = false },
            title = {
                Text("Write a Review for ${vendor.name} ⭐", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("YOUR RATING:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= reviewRatingScore) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = GoldPremium,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable { reviewRatingScore = star.toDouble() }
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${reviewRatingScore.toInt()}.0 / 5.0", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Charcoal)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("YOUR NAME (OPTIONAL):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = reviewAuthorName,
                        onValueChange = { reviewAuthorName = it },
                        placeholder = { Text("Your Name", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = Charcoal)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("YOUR EXPERIENCE & FEEDBACK:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = reviewCommentText,
                        onValueChange = { reviewCommentText = it },
                        placeholder = { Text("How was the washing quality, pickup speed, and packaging?", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Charcoal)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reviewCommentText.isNotBlank()) {
                            vm.addVendorReview(
                                vendorId = vendor.id,
                                author = reviewAuthorName.ifBlank { "You" },
                                rating = reviewRatingScore,
                                comment = reviewCommentText.trim()
                            )
                            showWriteReviewDialog = false
                            reviewCommentText = ""
                            Toast.makeText(context, "Review posted successfully! ⭐", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please write a comment", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("Submit Review ⭐", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteReviewDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0xFFEEEEEE))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val totalCartCount = cartItems.sumOf { it.quantity }
                    val totalCartPrice = cartItems.sumOf { it.discountPrice * it.quantity }.toInt()

                    if (totalCartCount > 0) {
                        Button(
                            onClick = {
                                vm.navigateTo(ApnaDhobiScreen.SlotSelection)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧺 $totalCartCount Items • ₹$totalCartPrice",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Proceed to Pickup ➔",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (selectedTabIndex != 1) {
                                    selectedTabIndex = 1
                                    Toast.makeText(context, "Please select services or clothes to book! 🧺", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Tap 'ADD +' on any item below to add into your laundry bucket!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Select Services to Book ➔",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            // 1. TOP HERO MEDIA HEADER (Interactive Swipeable Image Slider + Real Actions + Thumbnails)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    // Main Swipeable Store Photo Slider
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val currentImg = allStoreImages.getOrNull(page)
                        if (currentImg != null) {
                            com.example.ui.ApnaNetworkImage(
                                url = currentImg,
                                contentDescription = "${vendor.name} photo $page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(listOf(RoyalBlue, SaffronOrange))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = vendor.logoText.ifBlank { vendor.name.take(3).uppercase() },
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // Scrim gradient for contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.50f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f)
                                    )
                                )
                            )
                    )

                    // Top Action Bar placed high with statusBarsPadding (Back, Share, Heart Favorite)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { vm.navigateBack() },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Charcoal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Check out ${vendor.name} on Apna Dhobi! ⭐ ${vendor.rating} ★ - Top-rated laundry & fabric care with free doorstep pickup: https://apnadhobi.app/store/${vendor.id}"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share ${vendor.name}"))
                                    },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Charcoal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        vm.toggleFavoriteVendor(vendor.id)
                                    },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) Color.Red else Charcoal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Gallery Thumbnails Overlay (Clicking selects the active slide!)
                    LazyRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(allStoreImages.take(5)) { index, thumbUrl ->
                            val isCurrentPage = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isCurrentPage) 2.5.dp else 1.dp,
                                        color = if (isCurrentPage) SaffronOrange else Color.White,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                com.example.ui.ApnaNetworkImage(
                                    url = thumbUrl,
                                    contentDescription = "Store photo thumb $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (index == 4 && allStoreImages.size > 5) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${allStoreImages.size - 4}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. VENDOR INFO & METADATA (Matching Image 3 Layout)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Category Badge & Star Rating Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SaffronOrange.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = vendor.categoryTag.ifBlank { "Laundry" },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaffronOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedTabIndex = 3 }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = GoldPremium,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${vendor.rating}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "(${vendor.ratingCount} reviews)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Store / Vendor Name
                    Text(
                        text = vendor.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal,
                        letterSpacing = (-0.2).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Address Text
                    Text(
                        text = vendor.address.ifBlank { "1012 Ocean Avenue, Sector 4, New Delhi, India" },
                        fontSize = 12.5.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }

            // 3. TABS ROW (About | Services | Gallery | Reviews)
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.White,
                    contentColor = SaffronOrange,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = SaffronOrange,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) SaffronOrange else Color.Gray
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
            }

            // 4. TAB CONTENTS
            when (selectedTabIndex) {
                // TAB 0: ABOUT
                0 -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "About",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val fullDesc = vendor.description.ifBlank {
                                "Apna Dhobi Express provides top-quality laundry, dry cleaning, steam ironing, and fabric restoration services. We use eco-friendly detergents, German automated washing machines, and customized fabric care cycles to ensure complete cleanliness, germ protection, and fresh fragrance."
                            }
                            Text(
                                text = if (isReadMore) fullDesc else fullDesc.take(130) + (if (fullDesc.length > 130) "..." else ""),
                                fontSize = 12.5.sp,
                                color = Charcoal.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                            if (fullDesc.length > 130) {
                                Text(
                                    text = if (isReadMore) "Read less" else "Read more",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaffronOrange,
                                    modifier = Modifier
                                        .clickable { isReadMore = !isReadMore }
                                        .padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // "Service Provider" Section (Matching Image 3)
                            Text(
                                text = "Service Provider",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(46.dp),
                                        shape = CircleShape,
                                        color = SaffronOrange.copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Provider",
                                                tint = SaffronOrange,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = vendor.providerName.ifBlank { "Jenny Wilson" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Charcoal
                                        )
                                        Text(
                                            text = vendor.providerRole.ifBlank { "Service Provider & Fabric Lead" },
                                            fontSize = 11.5.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // Quick Chat & Call Buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable {
                                                    Toast.makeText(context, "Opening direct support chat with ${vendor.providerName} 💬", Toast.LENGTH_SHORT).show()
                                                },
                                            shape = CircleShape,
                                            color = RoyalBlue.copy(alpha = 0.12f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ChatBubbleOutline,
                                                    contentDescription = "Chat",
                                                    tint = RoyalBlue,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable {
                                                    try {
                                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${vendor.providerPhone.ifBlank { "+919871122334" }}"))
                                                        context.startActivity(dialIntent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Calling ${vendor.providerPhone} 📞", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            shape = CircleShape,
                                            color = SaffronOrange.copy(alpha = 0.12f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Phone,
                                                    contentDescription = "Call",
                                                    tint = SaffronOrange,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Key Store Guarantees
                            Text(
                                text = "Service Guarantees",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val guarantees = listOf(
                                "🌱 100% Eco-Friendly Detergents",
                                "🛡️ Antimicrobial & Germ-Free Sanitization",
                                "⚡ Express Doorstep Pickup & Delivery",
                                "👔 Individual Hydro-Carbon Gentle Fabric Care"
                            )
                            guarantees.forEach { itemText ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = itemText,
                                        fontSize = 12.5.sp,
                                        color = Charcoal.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: SERVICES (Products from ViewModel + Category Filtering + Customizer)
                1 -> {
                    item {
                        // Category Filter Chips
                        val filterCategories = listOf("All", "laundry", "dry_cleaning", "ironing", "shoe_cleaning", "blanket_wash", "wedding_wear", "premium_care")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filterCategories) { filterKey ->
                                val label = when (filterKey) {
                                    "All" -> "All Services"
                                    "laundry" -> "Wash & Fold"
                                    "dry_cleaning" -> "Dry Clean"
                                    "ironing" -> "Steam Press"
                                    "shoe_cleaning" -> "Shoe Spa"
                                    "blanket_wash" -> "Blankets"
                                    "wedding_wear" -> "Wedding Wear"
                                    "premium_care" -> "Delicates"
                                    else -> filterKey
                                }
                                val isSelected = selectedCategoryFilter == filterKey
                                Surface(
                                    modifier = Modifier.clickable { selectedCategoryFilter = filterKey },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) SaffronOrange else Color(0xFFF3F4F6),
                                    border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Charcoal,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    val filteredProds = if (selectedCategoryFilter == "All") {
                        products
                    } else {
                        products.filter { it.categoryId == selectedCategoryFilter }
                    }

                    items(filteredProds) { prod ->
                        val inCart = cartItems.find { it.productId == prod.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    customizingProduct = prod
                                    itemQuantity = inCart?.quantity ?: 1
                                    fabricInquiryText = ""
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = SaffronOrange.copy(alpha = 0.10f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LocalLaundryService,
                                            contentDescription = null,
                                            tint = SaffronOrange,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Charcoal
                                    )
                                    Text(
                                        text = "⚡ ${prod.deliveryEstimate}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "₹${prod.discountPrice.toInt()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = SaffronOrange
                                        )
                                        if (prod.originalPrice > prod.discountPrice) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "₹${prod.originalPrice.toInt()}",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                            )
                                        }
                                    }
                                }

                                if (inCart == null) {
                                    Button(
                                        onClick = {
                                            customizingProduct = prod
                                            itemQuantity = 1
                                            fabricInquiryText = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text("ADD +", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { vm.removeProductFromCart(prod, vendor) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveCircleOutline,
                                                contentDescription = "Remove",
                                                tint = SaffronOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Text(
                                            text = "${inCart.quantity}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = { vm.addProductToCart(prod, vendor) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddCircle,
                                                contentDescription = "Add",
                                                tint = SaffronOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: GALLERY
                2 -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Store & Equipment Gallery",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            allStoreImages.forEachIndexed { idx, imgUrl ->
                                val caption = when (idx) {
                                    0 -> "Main Store Front & Counter"
                                    1 -> "Advanced German Washer Extractors"
                                    2 -> "Steam Ironing & Wrinkle-Free Press Station"
                                    3 -> "Clean Garment Packaging & Wardrobe Hangers"
                                    else -> "Laundry Hygiene Facility"
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                        ) {
                                            com.example.ui.ApnaNetworkImage(
                                                url = imgUrl,
                                                contentDescription = caption,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Text(
                                            text = caption,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Charcoal,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: REVIEWS (Real-Time Live Reviews & Write Review)
                3 -> {
                    item {
                        val vendorReviews = reviewsMap[vendor.id] ?: reviewsMap["vendor_1"] ?: emptyList()
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
                                Text(
                                    text = "Customer Reviews & Ratings",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Charcoal
                                )
                                Button(
                                    onClick = { showWriteReviewDialog = true },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Write Review ✍️", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Overall Score Summary Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = LightCream,
                                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${vendor.rating}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Charcoal)
                                        Row {
                                            repeat(5) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${vendor.ratingCount} Ratings", fontSize = 11.sp, color = Color.Gray)
                                    }

                                    Spacer(modifier = Modifier.width(20.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        listOf(
                                            "5 ★" to 0.85f,
                                            "4 ★" to 0.12f,
                                            "3 ★" to 0.03f
                                        ).forEach { (starLabel, progress) ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            ) {
                                                Text(starLabel, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.width(22.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = SaffronOrange,
                                                    trackColor = Color(0xFFE5E7EB)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Customer reviews list
                            vendorReviews.forEach { review ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    modifier = Modifier.size(24.dp),
                                                    shape = CircleShape,
                                                    color = SaffronOrange.copy(alpha = 0.15f)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(review.author.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SaffronOrange)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = review.author, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Charcoal)
                                                if (review.verified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("✓ Verified", fontSize = 10.sp, color = GreenSuccess, fontWeight = FontWeight.Medium)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${review.rating.toInt()} ★", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(review.date, fontSize = 10.5.sp, color = Color.Gray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = review.comment, fontSize = 12.sp, color = Charcoal.copy(alpha = 0.85f), lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
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
                                if (item.dryCleaningType.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = RoyalBlue.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = item.dryCleaningType,
                                            color = RoyalBlue,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (!item.userNotes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Note: ${item.userNotes}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${item.discountPrice.toInt()} / item",
                                    color = SaffronOrange,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
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
                        Text(b.title ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(b.subtitle ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
