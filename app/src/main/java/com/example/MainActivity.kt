package com.example

import android.os.Bundle
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.location.Geocoder
import android.Manifest
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
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
// REVERSE GEOCODING & LOCATION SEARCH HELPERS
// ==========================================
suspend fun fetchAddressFromCoordinates(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
    if (lat == 0.0 && lon == 0.0) return@withContext "Detecting location..."
    try {
        @Suppress("DEPRECATION")
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val line = addr.getAddressLine(0)
            if (!line.isNullOrBlank()) return@withContext line
            val fallback = listOfNotNull(addr.featureName, addr.subLocality, addr.locality, addr.adminArea, addr.postalCode).joinToString(", ")
            if (fallback.isNotBlank()) return@withContext fallback
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ApnaDhobiAndroidApp/2.0")
        conn.connectTimeout = 3500
        conn.readTimeout = 3500
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().readText()
            val jsonObj = JSONObject(json)
            val displayName = jsonObj.optString("display_name")
            if (displayName.isNotBlank()) return@withContext displayName
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    "Location: ${String.format(Locale.US, "%.4f", lat)}°N, ${String.format(Locale.US, "%.4f", lon)}°E"
}

suspend fun searchLocationsFromQuery(context: Context, query: String): List<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
    val results = mutableListOf<Triple<String, Double, Double>>()
    if (query.isBlank()) return@withContext results
    try {
        @Suppress("DEPRECATION")
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocationName(query, 5)
        if (!addresses.isNullOrEmpty()) {
            for (addr in addresses) {
                val name = addr.getAddressLine(0) ?: "${addr.featureName ?: ""}, ${addr.locality ?: ""}"
                results.add(Triple(name, addr.latitude, addr.longitude))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (results.isEmpty()) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "ApnaDhobiAndroidApp/1.0")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val jsonArray = org.json.JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val disp = obj.optString("display_name")
                    val lat = obj.optDouble("lat")
                    val lon = obj.optDouble("lon")
                    results.add(Triple(disp, lat, lon))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    results
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
                        LoginSuccessWelcomeDialog(vm = vm)
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

fun resolveImageUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    if (rawUrl.startsWith("data:image")) return rawUrl
    var url = rawUrl.trim()
    if (url.startsWith("http://10.0.2.2:3000")) {
        url = url.replace("http://10.0.2.2:3000", "https://apna-dhobi-backend.onrender.com")
    }
    if (url.startsWith("http://localhost:3000")) {
        url = url.replace("http://localhost:3000", "https://apna-dhobi-backend.onrender.com")
    }
    if (url.startsWith("/api/v1/")) {
        url = "https://apna-dhobi-backend.onrender.com$url"
    } else if (url.startsWith("/")) {
        url = "https://apna-dhobi-backend.onrender.com$url"
    } else if (url.startsWith("uploads/")) {
        url = "https://apna-dhobi-backend.onrender.com/$url"
    }
    return url
}

// ==========================================
// PROFESSIONAL LOGIN SUCCESS WELCOME POPUP MODAL (ZOMATO / BLINKIT STANDARD)
// ==========================================
@Composable
fun LoginSuccessWelcomeDialog(vm: ApnaDhobiViewModel) {
    val showDialog by vm.showLoginSuccessDialog.collectAsState()
    val userName by vm.userName.collectAsState()
    val userPhone by vm.userPhone.collectAsState()

    if (showDialog) {
        Dialog(onDismissRequest = { vm.showLoginSuccessDialog.value = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close 'X' Button at top-right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { vm.showLoginSuccessDialog.value = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Celebration Glowing Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFDCFCE7), Color(0xFFF0FDF4))
                                ),
                                shape = CircleShape
                            )
                            .border(3.dp, Color(0xFF22C55E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = "🎉 Login Successful!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Welcome Text with User details
                    val displayName = if (userName.isNotBlank() && !userName.startsWith("usr_")) userName else if (userPhone.isNotBlank()) "+91 $userPhone" else "Valued Member"
                    Text(
                        text = "Welcome to Apna Dhobi, $displayName! Your account is verified and ready for doorstep laundry.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Feature Perks Cards
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFFFF7ED), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🚚", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Free Doorstep Pickup & Return", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF0F172A))
                                    Text("Zero contact sanitized transit", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0).copy(alpha = 0.6f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("24-Hour Express Superfast Wash", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF0F172A))
                                    Text("Next-day doorstep delivery guarantee", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0).copy(alpha = 0.6f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎁", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("50% OFF Applied on First Order", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF0F172A))
                                    Text("Use promo code WASH50 at checkout", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Action Button: Start Exploring 🚀
                    Button(
                        onClick = { vm.showLoginSuccessDialog.value = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                    ) {
                        Text(
                            text = "Start Exploring 🚀",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// UNIVERSAL IMAGE COMPOSABLE (SUPPORTS BASE64 DATA URIS, RELATIVE & NETWORK URLS)
// ==========================================
@Composable
fun UniversalAppImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    fallback: @Composable () -> Unit
) {
    val resolvedModel = resolveImageUrl(model)
    if (resolvedModel.isNullOrBlank()) {
        fallback()
        return
    }

    if (resolvedModel.startsWith("data:image") && resolvedModel.contains("base64,")) {
        val base64Data = resolvedModel.substringAfter("base64,")
        val bitmap = remember(base64Data) {
            try {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            fallback()
        }
    } else {
        coil.compose.SubcomposeAsyncImage(
            model = resolvedModel,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            error = { fallback() }
        )
    }
}

// ==========================================
// BRAND LOGO MOCKUP (MATCHING SCREENSHOT & DYNAMIC ADMIN CUSTOMIZATION)
// ==========================================
@Composable
fun ApnaDhobiBrandLogo(modifier: Modifier = Modifier, customLogoUrl: String? = null) {
    UniversalAppImage(
        model = customLogoUrl,
        contentDescription = "Apna Dhobi Brand Logo",
        modifier = modifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ) {
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
        delay(2600)
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
                val isBrandLogoVisible by vm.adminIsBrandLogoVisible.collectAsState()
                val customLogoUrl by vm.adminBrandLogoUrl.collectAsState()

                if (isBrandLogoVisible) {
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
                                .padding(16.dp),
                                customLogoUrl = customLogoUrl.ifBlank { null }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // App Title
                val brandNamePrimary by vm.adminBrandNamePrimary.collectAsState()
                val brandNameSecondary by vm.adminBrandNameSecondary.collectAsState()
                val brandTagline by vm.adminBrandTagline.collectAsState()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = brandNamePrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = brandNameSecondary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaffronOrange,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tagline
                Text(
                    text = brandTagline.ifBlank { "साफ़ कपड़े, खुशहाल ज़िंदगी" },
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
                        vm.navigateTo(ApnaDhobiScreen.Login)
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
fun DeliveryTruckGraphic(
    modifier: Modifier = Modifier,
    primaryName: String = "Apna",
    secondaryName: String = "Dhobi"
) {
    Box(
        modifier = modifier
            .size(width = 88.dp, height = 58.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Ground Shadow
            drawOval(
                color = Color(0x220F172A),
                topLeft = Offset(w * 0.04f, h * 0.82f),
                size = Size(w * 0.92f, h * 0.14f)
            )

            // Cab Front (Pure White Body with Navy outline)
            val cabPath = Path().apply {
                moveTo(w * 0.05f, h * 0.74f)
                lineTo(w * 0.05f, h * 0.38f)
                lineTo(w * 0.24f, h * 0.22f)
                lineTo(w * 0.36f, h * 0.22f)
                lineTo(w * 0.36f, h * 0.74f)
                close()
            }
            drawPath(cabPath, color = Color(0xFFF8FAFC))
            drawPath(cabPath, color = Color(0xFF94A3B8), style = Stroke(width = 2.5f))

            // Cab Windshield Glass (Cyan Blue)
            val windowPath = Path().apply {
                moveTo(w * 0.12f, h * 0.40f)
                lineTo(w * 0.23f, h * 0.28f)
                lineTo(w * 0.32f, h * 0.28f)
                lineTo(w * 0.32f, h * 0.42f)
                close()
            }
            drawPath(windowPath, color = Color(0xFF38BDF8))

            // Front Headlight (Warm Yellow)
            drawRoundRect(
                color = Color(0xFFFBBF24),
                topLeft = Offset(w * 0.04f, h * 0.52f),
                size = Size(w * 0.04f, h * 0.14f),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // Blue Cargo Box (Navy Royal Blue)
            drawRoundRect(
                color = Color(0xFF0F3E88),
                topLeft = Offset(w * 0.34f, h * 0.12f),
                size = Size(w * 0.61f, h * 0.62f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Orange Cargo Accent Stripe
            drawRect(
                color = Color(0xFFEA580C),
                topLeft = Offset(w * 0.34f, h * 0.62f),
                size = Size(w * 0.61f, h * 0.08f)
            )

            // Front Wheel (Black tire + Silver rim)
            drawCircle(Color(0xFF1E293B), radius = h * 0.14f, center = Offset(w * 0.22f, h * 0.78f))
            drawCircle(Color(0xFFE2E8F0), radius = h * 0.07f, center = Offset(w * 0.22f, h * 0.78f))
            drawCircle(Color(0xFF0F172A), radius = h * 0.03f, center = Offset(w * 0.22f, h * 0.78f))

            // Rear Wheel (Black tire + Silver rim)
            drawCircle(Color(0xFF1E293B), radius = h * 0.14f, center = Offset(w * 0.78f, h * 0.78f))
            drawCircle(Color(0xFFE2E8F0), radius = h * 0.07f, center = Offset(w * 0.78f, h * 0.78f))
            drawCircle(Color(0xFF0F172A), radius = h * 0.03f, center = Offset(w * 0.78f, h * 0.78f))
        }

        Column(
            modifier = Modifier.padding(start = 30.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(primaryName, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(secondaryName, color = Color(0xFFFDBA74), fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
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
    val isRegistrationRequired by vm.isRegistrationRequired.collectAsState()
    val showOtpPopup by vm.showOtpPopup.collectAsState()
    val receivedOtpCode by vm.receivedOtpCode.collectAsState()
    val otpCountdown by vm.otpCountdown.collectAsState()

    var authTab by remember { mutableStateOf("login") } // "login", "profile", "admin"
    
    // Automatically switch to profile tab if registration is required
    LaunchedEffect(isRegistrationRequired) {
        if (isRegistrationRequired) {
            authTab = "profile"
        }
    }

    // Form fields
    var regFullName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regMobile by remember { mutableStateOf("") }
    var regReferral by remember { mutableStateOf("") }

    var adminEmail by remember { mutableStateOf("admin@apnadhobi.com") }
    var adminPasscode by remember { mutableStateOf("492011") }

    var loginReferral by remember { mutableStateOf("") }

    var showGoogleDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var isLangMenuExpanded by remember { mutableStateOf(false) }

    val languages = listOf("English", "Hindi (हिंदी)", "Punjabi (ਪੰਜਾਬੀ)", "Bengali (বাংলা)")

    val brandNamePrimary by vm.adminBrandNamePrimary.collectAsState()
    val brandNameSecondary by vm.adminBrandNameSecondary.collectAsState()
    val brandTagline by vm.adminBrandTagline.collectAsState()
    val brandLogoUrl by vm.adminBrandLogoUrl.collectAsState()
    val isBrandLogoVisible by vm.adminIsBrandLogoVisible.collectAsState()
    val defaultVehicleUrl by vm.adminDefaultVehicleGraphicUrl.collectAsState()
    val promoBanners by vm.loginPromoBanners.collectAsState()

    val bannerPagerState = rememberPagerState(pageCount = { promoBanners.size.coerceAtLeast(1) })

    LaunchedEffect(promoBanners.size) {
        if (promoBanners.size > 1) {
            while (true) {
                delay(3800)
                val nextPage = (bannerPagerState.currentPage + 1) % promoBanners.size
                bannerPagerState.animateScrollToPage(nextPage)
            }
        }
    }

    BackHandler {
        if (vm.isLoggedIn.value) {
            if (vm.postAuthDestination != null) {
                vm.postAuthDestination = null
            }
            vm.navigateBack()
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }

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
            // Top Row: Logo & Language Selector Badge (Matching Image 1 & Dynamic Admin Brand)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBrandLogoVisible) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    UniversalAppImage(
                                        model = brandLogoUrl,
                                        contentDescription = "Brand Logo",
                                        modifier = Modifier.size(26.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DryCleaning,
                                            contentDescription = null,
                                            tint = Color(0xFF0F3E88),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = brandNamePrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F3E88)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = brandNameSecondary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = SaffronOrange
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = brandTagline,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(start = 44.dp)
                    )
                }

                Box {
                    Surface(
                        onClick = { isLangMenuExpanded = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF0F3E88),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$selectedLanguage ▾",
                                color = Color(0xFF0F3E88),
                                fontSize = 12.5.sp,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Promo Banner Carousel Card (Matching Image 1 & Customizable from Admin Panel, Max 4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, Color(0xFFFFE0B2))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HorizontalPager(
                        state = bannerPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val currentBanner = promoBanners.getOrNull(page)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentBanner?.title ?: "Free Pickup &",
                                    color = Color(0xFF0F172A),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentBanner?.subtitle ?: "Return Delivery",
                                    color = SaffronOrange,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentBanner?.badge ?: "Premium laundry & dry cleaning\nat your doorstep",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            UniversalAppImage(
                                model = currentBanner?.imageUrl?.takeIf { it.isNotBlank() } ?: defaultVehicleUrl.takeIf { it.isNotBlank() },
                                contentDescription = "Banner Visual",
                                modifier = Modifier
                                    .size(width = 88.dp, height = 58.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            ) {
                                DeliveryTruckGraphic(
                                    primaryName = brandNamePrimary,
                                    secondaryName = brandNameSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic Dots Pagination Indicator (Matches 1 to 4 banners)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(promoBanners.size.coerceAtLeast(1)) { index ->
                            val isSelected = bannerPagerState.currentPage == index
                            Surface(
                                modifier = Modifier.size(if (isSelected) 7.dp else 5.dp),
                                shape = CircleShape,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                            ) {}
                            if (index < promoBanners.size - 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2-PART STRIP: Secure Login & Create Profile (ADMIN REMOVED AS REQUESTED)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Option 1: Secure Login
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { authTab = "login" }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = if (authTab == "login") Color(0xFFEFF6FF) else Color(0xFFF1F5F9)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (authTab == "login") Color(0xFF2563EB) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Secure Login",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (authTab == "login") Color(0xFF2563EB) else Color(0xFF1E293B)
                            )
                            Text(
                                text = "Your data is safe",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    // Option 2: Create Profile
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { authTab = "profile" }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = if (authTab == "profile") Color(0xFFEFF6FF) else Color(0xFFF1F5F9)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = if (authTab == "profile") Color(0xFF2563EB) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Create Profile",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (authTab == "profile") Color(0xFF2563EB) else Color(0xFF1E293B)
                            )
                            Text(
                                text = "For faster booking",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main White Auth Card Container (Matching Image 1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    when (authTab) {
                        "profile" -> {
                            // CREATE PROFILE TAB
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF0F3E88),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Complete Your Profile",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F3E88)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Field 1: Full Name (Required)
                            OutlinedTextField(
                                value = regFullName,
                                onValueChange = { regFullName = it },
                                placeholder = { Text("Full Name (Required)", color = Color(0xFF94A3B8), fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF0F3E88),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Field 2: Email ID (Required)
                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                placeholder = { Text("Email ID (Required)", color = Color(0xFF94A3B8), fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF0F3E88),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Field 3: Mobile Contact
                            OutlinedTextField(
                                value = regMobile,
                                onValueChange = { regMobile = it },
                                placeholder = { Text("Mobile Contact", color = Color(0xFF94A3B8), fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF0F3E88),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Field 4: Referral Bonus Code (Optional) + Apply
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = regReferral,
                                    onValueChange = { regReferral = it },
                                    placeholder = { Text("Referral Code (Optional)", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF64748B)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = Color(0xFF0F3E88),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (regReferral.isNotBlank()) {
                                            vm.userReferralCode.value = regReferral
                                            vm.applyReferral()
                                        }
                                    },
                                    modifier = Modifier.height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                                ) {
                                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Main Action Button: Create Profile & Sign Up
                            Button(
                                onClick = {
                                    vm.registerUserProfile(
                                        name = regFullName,
                                        email = regEmail,
                                        phone = regMobile.ifBlank { mobileNumber },
                                        referralCode = regReferral
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                            ) {
                                Text(
                                    text = "Create Profile & Continue 🚀",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        else -> {
                            // SECURE LOGIN TAB
                            Text(
                                text = "Enter Mobile Number",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isOtpSent) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("OTP sent to +91 ${mobileNumber.ifBlank { "9876543210" }}", color = Color(0xFF0F3E88), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            Text("Code: ${receivedOtpCode ?: "1234"}", color = Color(0xFF059669), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = { vm.isOtpSent.value = false }) {
                                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = otp,
                                    onValueChange = { vm.loginOtp.value = it },
                                    placeholder = { Text("Enter 4-digit verification code", color = Color(0xFF94A3B8)) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF0F3E88)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = Color(0xFF0F3E88),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // 60-Second (1 Minute) Live Countdown Timer & Resend Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (otpCountdown > 0) {
                                        val secFormatted = if (otpCountdown < 10) "0$otpCountdown" else "$otpCountdown"
                                        Text(
                                            text = "⏳ OTP valid for 00:${secFormatted}s",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF64748B)
                                        )
                                    } else {
                                        Text(
                                            text = "OTP expired",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Red
                                        )
                                    }

                                    Text(
                                        text = if (otpCountdown > 0) "Resend in ${otpCountdown}s" else "Resend OTP",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (otpCountdown > 0) Color.Gray else SaffronOrange,
                                        modifier = Modifier.clickable(enabled = otpCountdown == 0) {
                                            vm.sendOtp(mobileNumber)
                                        }
                                    )
                                }
                            } else {
                                // Mobile Number Input Row with Flag
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🇮🇳", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "+91 ▾", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(24.dp)
                                                .background(Color(0xFFCBD5E1))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        BasicTextField(
                                            value = mobileNumber,
                                            onValueChange = { vm.loginMobileNumber.value = it },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 14.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            decorationBox = { innerTextField ->
                                                if (mobileNumber.isEmpty()) {
                                                    Text("Enter 10-digit mobile number", color = Color(0xFF94A3B8), fontSize = 13.5.sp)
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Main Action Button: Send OTP Code / Verify & Continue
                            Button(
                                onClick = {
                                    if (!isOtpSent) {
                                        vm.sendOtp(mobileNumber)
                                    } else {
                                        coroutineScope.launch {
                                            if (vm.verifyOtp(mobileNumber, otp)) {
                                                // Redirected inside verifyOtp
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (!isOtpSent) Icons.Default.Send else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!isOtpSent) "Send OTP Code" else "Verify & Continue 🚀",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider: OR CONTINUE WITH (Matching Image 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                )
                Text(
                    text = "OR CONTINUE WITH",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Social Row: Continue with Google & Continue as Guest (Matching Image 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Continue with Google Button
                Surface(
                    onClick = { showGoogleDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleColoredLogo(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color(0xFF0F172A),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Continue as Guest Button (Forwards to LocationSelection)
                Surface(
                    onClick = {
                        vm.isLoggedIn.value = true
                        vm.userName.value = "Guest User"
                        if (vm.postAuthDestination != null) {
                            val dest = vm.postAuthDestination!!
                            vm.postAuthDestination = null
                            vm.navigateTo(dest)
                        } else {
                            vm.navigateTo(ApnaDhobiScreen.LocationSelection)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF0F3E88),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Continue as Guest",
                            color = Color(0xFF0F172A),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Trust Badges Strip (100% Secure | Quick & Easy | 24/7 Support) (Matching Image 1)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: 100% Secure
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "100% Secure",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Your data is safe",
                                fontSize = 10.sp,
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
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Color(0xFFFEF9C3)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Quick & Easy",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Login in seconds",
                                fontSize = 10.sp,
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
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "24/7 Support",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "We're here to help",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Legal Footer (Terms & Conditions and Privacy Policy) (Matching Image 1)
            Text(
                text = "By continuing, you agree to our",
                fontSize = 11.5.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terms & Conditions",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3E88),
                    modifier = Modifier.clickable { showTermsDialog = true }
                )
                Text(
                    text = " and ",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Privacy Policy",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3E88),
                    modifier = Modifier.clickable { showPrivacyDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Google Sign In Pop-up Dialog (Professional & Real-Time)
        if (showGoogleDialog) {
            var googleEmailInput by remember { mutableStateOf("") }
            var googleNameInput by remember { mutableStateOf("") }
            var isSubmittingGoogle by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { if (!isSubmittingGoogle) showGoogleDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(12.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GoogleColoredLogo(modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Sign In with Google", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("Enter your Google Account email to continue", fontSize = 12.sp, color = Color(0xFF64748B), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = googleEmailInput,
                            onValueChange = { googleEmailInput = it },
                            placeholder = { Text("your.email@gmail.com", fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF4285F4)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = googleNameInput,
                            onValueChange = { googleNameInput = it },
                            placeholder = { Text("Your Full Name (Optional)", fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (googleEmailInput.isNotBlank() && googleEmailInput.contains("@")) {
                                    isSubmittingGoogle = true
                                    coroutineScope.launch {
                                        val success = vm.attemptGoogleLogin(googleEmailInput.trim(), googleNameInput.trim())
                                        isSubmittingGoogle = false
                                        if (success) {
                                            showGoogleDialog = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a valid Google email address", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                            enabled = !isSubmittingGoogle
                        ) {
                            if (isSubmittingGoogle) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Continue with Google", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { showGoogleDialog = false }, enabled = !isSubmittingGoogle) {
                            Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Top In-App Dynamic OTP Delivery Pop-up / Notification Banner
        AnimatedVisibility(
            visible = showOtpPopup && !receivedOtpCode.isNullOrBlank(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3E88)),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SaffronOrange,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📲 Apna Dhobi Verification Code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Your one-time login OTP is: ${receivedOtpCode ?: ""}", color = Color(0xFFFFD54F), fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            receivedOtpCode?.let { vm.loginOtp.value = it }
                            vm.showOtpPopup.value = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Auto-Fill", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { vm.showOtpPopup.value = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // Terms & Conditions Real-time Dialog
        if (showTermsDialog) {
            TermsAndConditionsDialog(onDismiss = { showTermsDialog = false })
        }

        // Privacy Policy Real-time Dialog
        if (showPrivacyDialog) {
            PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
        }
    }
}

// ==========================================
// TERMS & CONDITIONS DIALOG
// ==========================================
@Composable
fun TermsAndConditionsDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📜", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Terms & Conditions",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F3E88)
                            )
                            Text(
                                text = "Apna Dhobi Fabric Care Service",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegalSectionItem(
                        number = "1",
                        title = "Service Acceptance & Doorstep Pickup",
                        content = "By booking through Apna Dhobi, the customer authorizes our verified delivery partner to pick up, inspect, count, and transport garments to certified processing hubs."
                    )
                    LegalSectionItem(
                        number = "2",
                        title = "Turnaround Time & Express Washing",
                        content = "Standard laundry orders are delivered within 24-48 hours. Express deliveries are delivered within 12-24 hours subject to local weather and drying conditions."
                    )
                    LegalSectionItem(
                        number = "3",
                        title = "Fabric Care, Color Bleed & Damage Policy",
                        content = "Every garment is processed according to manufacturer wash care labels. In the rare event of damage or loss verified during intake, compensation up to 5x of the washing charge or max ₹2,500 will be credited."
                    )
                    LegalSectionItem(
                        number = "4",
                        title = "Instant Cancellation & 100% Refund",
                        content = "Orders can be canceled anytime before the pickup partner arrives. If an order is canceled after pickup or vendor acceptance, a full refund is automatically credited back to your Apna Dhobi wallet within 60 seconds."
                    )
                    LegalSectionItem(
                        number = "5",
                        title = "Pricing, Weight & Payment Settlement",
                        content = "Final invoice is calculated based on digital weight verification or piece-count verified at the intake terminal. Payments can be settled via UPI, Cards, NetBanking, COD, or In-App Wallet."
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                ) {
                    Text("I Understand & Agree", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// PRIVACY POLICY DIALOG
// ==========================================
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🛡️", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Privacy & Data Policy",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F3E88)
                            )
                            Text(
                                text = "100% Encrypted & Safe Platform",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegalSectionItem(
                        number = "1",
                        title = "Information We Collect",
                        content = "We collect your name, contact phone number, saved delivery address, and order transaction history solely to provide fast laundry pickup and delivery services."
                    )
                    LegalSectionItem(
                        number = "2",
                        title = "Real-time Location & GPS Usage",
                        content = "Device GPS telemetry is used exclusively during active order tracking to calculate estimated delivery time (ETA) and route our logistics partner to your doorstep."
                    )
                    LegalSectionItem(
                        number = "3",
                        title = "Payment Security & Zero Card Storage",
                        content = "All transactions are processed through RBI-compliant, bank-grade encrypted payment gateways. Apna Dhobi never stores your CVV, PIN, or netbanking passwords."
                    )
                    LegalSectionItem(
                        number = "4",
                        title = "Zero Spam & Third-Party Sharing",
                        content = "Your phone number and private details are never sold, rented, or shared with third-party advertisers. Notifications are restricted to order updates and exclusive deals."
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                ) {
                    Text("Close & Acknowledge", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun LegalSectionItem(number: String, title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = Color(0xFF0F3E88)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = Color(0xFF0F172A)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            fontSize = 12.sp,
            color = Color(0xFF475569),
            lineHeight = 17.sp
        )
    }
}

// ==========================================
// MODERN CATEGORY VISUAL GRAPHICS (MATCHING IMAGE 2)
// ==========================================
private fun drawSparkle(scope: DrawScope, center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + size)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - size)
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
            quadraticTo(w * 0.42f, h * 0.52f, w * 0.50f, h * 0.60f)
            quadraticTo(w * 0.58f, h * 0.68f, w * 0.65f, h * 0.60f)
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
            quadraticTo(w * 0.08f, h * 0.68f, w * 0.08f, h * 0.60f)
            lineTo(w * 0.16f, h * 0.34f)
            quadraticTo(w * 0.22f, h * 0.26f, w * 0.36f, h * 0.26f)
            lineTo(w * 0.74f, h * 0.26f)
            quadraticTo(w * 0.86f, h * 0.28f, w * 0.82f, h * 0.44f)
            lineTo(w * 0.68f, h * 0.48f)
            lineTo(w * 0.36f, h * 0.48f)
            quadraticTo(w * 0.28f, h * 0.48f, w * 0.26f, h * 0.54f)
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
            quadraticTo(w * 0.50f, h * 0.28f, w * 0.34f, h * 0.14f)
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
            quadraticTo(w * 0.90f, h * 0.16f, w * 0.90f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.74f)
            lineTo(w * 0.38f, h * 0.74f)
            quadraticTo(w * 0.18f, h * 0.74f, w * 0.18f, h * 0.48f)
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
            quadraticTo(w * 0.92f, h * 0.55f, w * 0.80f, h * 0.50f)
            lineTo(w * 0.58f, h * 0.48f)
            lineTo(w * 0.48f, h * 0.24f)
            quadraticTo(w * 0.38f, h * 0.20f, w * 0.30f, h * 0.28f)
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
            url.startsWith("/") -> "https://apna-dhobi-backend.onrender.com$url"
            else -> "https://apna-dhobi-backend.onrender.com/$url"
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
                            url.startsWith("/") -> "https://apna-dhobi-backend.onrender.com$url"
                            else -> "https://apna-dhobi-backend.onrender.com/$url"
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
// LOCATION SELECTION SCREEN (FULLY INTERACTIVE & REAL-TIME GPS)
// ==========================================
@Composable
fun LocationSelectionScreen(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    
    val customerLat by vm.customerLat.collectAsState()
    val customerLng by vm.customerLng.collectAsState()
    val currentAddress by vm.currentFullAddress.collectAsState()
    val savedAddressesList by vm.savedAddresses.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var addressDetailText by remember { mutableStateOf(if (currentAddress.startsWith("📍")) "" else currentAddress) }
    var selectedLabel by remember { mutableStateOf("Home") }
    var houseFlatNo by remember { mutableStateOf("Flat 101, 1st Floor") }
    var isDetecting by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    var isSearchingLocations by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(true) }
    var isManualEditing by remember { mutableStateOf(false) }
    var manualAddressInput by remember { mutableStateOf("") }

    fun handleBackToHome() {
        val finalFormatted = if (houseFlatNo.isNotBlank() && addressDetailText.isNotBlank()) {
            "$houseFlatNo, $addressDetailText"
        } else {
            addressDetailText.ifBlank { "Rohtak, Haryana" }
        }
        if (addressDetailText.isNotBlank() && !addressDetailText.startsWith("📡")) {
            vm.currentFullAddress.value = finalFormatted
        }
        vm.navigateTo(ApnaDhobiScreen.HomeFrame)
    }

    fun handleSaveAndProceed() {
        val finalFormatted = if (houseFlatNo.isNotBlank() && addressDetailText.isNotBlank()) {
            "$houseFlatNo, $addressDetailText"
        } else {
            addressDetailText.ifBlank { "Rohtak, Haryana" }
        }
        vm.saveNewAddress(selectedLabel, finalFormatted)
        vm.currentFullAddress.value = finalFormatted
        Toast.makeText(context, "Location Saved Successfully!", Toast.LENGTH_SHORT).show()
        vm.navigateTo(ApnaDhobiScreen.HomeFrame)
    }

    BackHandler {
        handleBackToHome()
    }

    var hasInitialGpsLockDone by remember { mutableStateOf(false) }

    val initialPos = if (customerLat != 0.0 && customerLng != 0.0) LatLng(customerLat, customerLng) else LatLng(28.8955, 76.6066)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 17.5f)
    }
    val markerState = rememberMarkerState(position = initialPos)

    // Android Location Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            isDetecting = true
            addressDetailText = "📡 Detecting your current live location..."
            vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                isDetecting = false
                hasInitialGpsLockDone = true
                addressDetailText = addr
                markerState.position = LatLng(lat, lng)
                coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
                }
            }
        } else {
            Toast.makeText(context, "Location permission required to detect GPS.", Toast.LENGTH_SHORT).show()
        }
    }

    val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Immediate Hardware GPS detection on screen opening
    LaunchedEffect(Unit) {
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isGpsEnabled = locManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                locManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        if (!isGpsEnabled) {
            Toast.makeText(context, "Please turn on Location / GPS for live pickup accuracy", Toast.LENGTH_LONG).show()
        }

        if (hasLocationPermission) {
            isDetecting = true
            addressDetailText = "📡 Detecting your current live location..."
            vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                isDetecting = false
                hasInitialGpsLockDone = true
                addressDetailText = addr
                markerState.position = LatLng(lat, lng)
                coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Dynamic Search Autocomplete Debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            isSearchingLocations = true
            delay(300)
            val results = searchLocationsFromQuery(context, searchQuery.trim())
            searchResults = results
            isSearchingLocations = false
        } else {
            searchResults = emptyList()
            isSearchingLocations = false
        }
    }

    // Update marker and reverse geocode when user manually drags/moves map
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving && hasInitialGpsLockDone && !isDetecting) {
            val target = cameraPositionState.position.target
            markerState.position = target
            vm.customerLat.value = target.latitude
            vm.customerLng.value = target.longitude
            val resolvedAddr = fetchAddressFromCoordinates(context, target.latitude, target.longitude)
            if (resolvedAddr.isNotBlank()) {
                addressDetailText = resolvedAddr
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Interactive Google Map (Satellite Hybrid / Normal Vector Mode)
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = if (isSatelliteMode) MapType.HYBRID else MapType.NORMAL,
                isBuildingEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            // Live GPS Blue Accuracy Ring
            Circle(
                center = markerState.position,
                radius = 28.0,
                fillColor = Color(0x332563EB),
                strokeColor = Color(0x992563EB),
                strokeWidth = 2.5f
            )

            // Red Location Teardrop Marker
            Marker(
                state = markerState,
                title = "Pickup Location",
                snippet = addressDetailText
            )
        }

        // Top Floating Header: Back Button + Search Bar
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
                    onClick = { handleBackToHome() },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SaffronOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                            }),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search area, apartment, street...", color = Color(0xFF94A3B8), fontSize = 13.5.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = { /* Voice Search */ }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
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
                        // Option 1: Use Real Current Device Location (GPS)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = ""
                                    keyboardController?.hide()
                                    isDetecting = true
                                    addressDetailText = "📡 Detecting your current live location..."
                                    vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                                        isDetecting = false
                                        hasInitialGpsLockDone = true
                                        addressDetailText = addr
                                        markerState.position = LatLng(lat, lng)
                                        coroutineScope.launch {
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SaffronOrange.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("📍 Use Current Location", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SaffronOrange)
                                Text("Using device GPS live precision", fontSize = 11.5.sp, color = Color.Gray)
                            }
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))

                        if (isSearchingLocations) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SaffronOrange, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching places...", fontSize = 13.sp, color = Color.Gray)
                            }
                        } else {
                            val itemsToShow = if (searchResults.isNotEmpty()) searchResults else listOf(
                                Triple("Rohtak City Center, Model Town, Haryana", 28.8955, 76.6066),
                                Triple("Sector 14, Rohtak, Haryana", 28.8845, 76.6189),
                                Triple("Cyber City, Gurugram, Haryana", 28.4950, 77.0895),
                                Triple("Indiranagar, Bengaluru, Karnataka 560038", 12.9784, 77.6408)
                            ).filter { it.first.contains(searchQuery, ignoreCase = true) || searchQuery.length > 1 }

                            itemsToShow.forEach { (name, lat, lng) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = ""
                                            keyboardController?.hide()
                                            addressDetailText = name
                                            vm.customerLat.value = lat
                                            vm.customerLng.value = lng
                                            markerState.position = LatLng(lat, lng)
                                            coroutineScope.launch {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17.5f))
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        // Floating Satellite View Toggle Pill Button (Top Right)
        Surface(
            onClick = { isSatelliteMode = !isSatelliteMode },
            shape = RoundedCornerShape(20.dp),
            color = if (isSatelliteMode) Color(0xFF1D4ED8) else Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 70.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSatelliteMode) "🛰️ Satellite ON" else "🗺️ Satellite View",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSatelliteMode) Color.White else Color(0xFF0F172A)
                )
            }
        }

        // Floating Circular GPS Locate Button (Crosshair)
        Surface(
            onClick = {
                isDetecting = true
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    addressDetailText = "📡 Detecting your current live location..."
                    vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                        isDetecting = false
                        hasInitialGpsLockDone = true
                        addressDetailText = addr
                        markerState.position = LatLng(lat, lng)
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f)
                            )
                        }
                    }
                } else {
                    isDetecting = false
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 118.dp, end = 16.dp)
                .size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SaffronOrange, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Locate Me",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Bottom Sheet Location Details (Matching Image 2 media_1787302122651.png)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.58f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Handle Bar
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 1. Current Location Detected Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current location detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = addressDetailText.ifBlank { "Detecting live GPS location..." },
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                isDetecting = true
                                addressDetailText = "📡 Detecting your current live location..."
                                vm.fetchRealGpsLocation(context) { lat, lng, addr ->
                                    isDetecting = false
                                    hasInitialGpsLockDone = true
                                    addressDetailText = addr
                                    markerState.position = LatLng(lat, lng)
                                    coroutineScope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-detect",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Re-detect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Search or Enter Address Manually Box
                Surface(
                    onClick = { isManualEditing = !isManualEditing },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (manualAddressInput.isNotBlank()) manualAddressInput else "Search or enter address manually",
                                fontSize = 13.5.sp,
                                color = if (manualAddressInput.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (isManualEditing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualAddressInput,
                                onValueChange = {
                                    manualAddressInput = it
                                    addressDetailText = it
                                },
                                placeholder = { Text("Type complete area, landmark, colony...", fontSize = 12.5.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = false,
                                maxLines = 3
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. House / Flat / Floor No. (Optional) Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "House / Flat / Floor No. (Optional)",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                            BasicTextField(
                                value = houseFlatNo,
                                onValueChange = { houseFlatNo = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = SaffronOrange,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (houseFlatNo.isEmpty()) {
                                        Text("e.g. Flat 101, 1st Floor", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. SAVE AS Tag Chips
                Text(
                    text = "SAVE AS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Home Chip
                    val isHome = selectedLabel == "Home"
                    Surface(
                        onClick = { selectedLabel = "Home" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isHome) SaffronOrange else Color.White,
                        border = BorderStroke(1.dp, if (isHome) SaffronOrange else Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = if (isHome) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Home",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isHome) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }

                    // Office Chip
                    val isOffice = selectedLabel == "Office"
                    Surface(
                        onClick = { selectedLabel = "Office" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOffice) SaffronOrange else Color.White,
                        border = BorderStroke(1.dp, if (isOffice) SaffronOrange else Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Work,
                                contentDescription = null,
                                tint = if (isOffice) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Office",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isOffice) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }

                    // Other Chip
                    val isOther = selectedLabel == "Other"
                    Surface(
                        onClick = { selectedLabel = "Other" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOther) SaffronOrange else Color.White,
                        border = BorderStroke(1.dp, if (isOther) SaffronOrange else Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isOther) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Other",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isOther) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. SAVED ADDRESSES List
                Text(
                    text = "SAVED ADDRESSES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val addressesList = if (savedAddressesList.isNotEmpty()) savedAddressesList else listOf(
                    SavedAddress(id = 1, label = "Home", addressLine = "Flat 101, 1st Floor, 12th Main Rd, Indiranagar, Bengaluru, Karnataka 560038"),
                    SavedAddress(id = 2, label = "Office", addressLine = "Unit 45, 3rd Floor, Prestige Tech Park, Marathahalli, Bengaluru, Karnataka 560037")
                )

                addressesList.forEach { addr ->
                    Surface(
                        onClick = {
                            addressDetailText = addr.addressLine
                            selectedLabel = addr.label
                            Toast.makeText(context, "Selected ${addr.label} address", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isHomeItem = addr.label.equals("Home", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isHomeItem) Color(0xFFFFF7ED) else Color(0xFFEFF6FF),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isHomeItem) Icons.Default.Home else Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = if (isHomeItem) SaffronOrange else Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = addr.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = addr.addressLine,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { /* More options */ }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. CONFIRM & PROCEED Button
                Button(
                    onClick = { handleSaveAndProceed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text(
                        text = "CONFIRM & PROCEED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// ORDER TRACKING SCREEN (LIVE MAP TELEMETRY)
// ==========================================
// ORDER TRACKING SCREEN (MATCHING SCREENSHOTS)
// ==========================================
@Composable
fun OrderTrackingScreen(vm: ApnaDhobiViewModel, orderId: Int) {
    val context = LocalContext.current
    val customerLat by vm.customerLat.collectAsState()
    val customerLng by vm.customerLng.collectAsState()
    val agentLat by vm.activeDeliveryBoyLat.collectAsState()
    val agentLng by vm.activeDeliveryBoyLng.collectAsState()
    val etaText by vm.trackingEtaText.collectAsState()
    val orders by vm.ordersList.collectAsState()
    val currentOrder = orders.find { it.id == orderId } ?: orders.firstOrNull()

    val currentStatus = currentOrder?.status ?: "Confirmed"
    var verificationCode by remember { mutableStateOf("") }
    var isCodeVerified by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val customerPos = LatLng(
        if (customerLat != 0.0) customerLat else 28.6280,
        if (customerLng != 0.0) customerLng else 77.2155
    )
    val agentPos = LatLng(
        if (agentLat != 0.0) agentLat else 28.6185,
        if (agentLng != 0.0) agentLng else 77.2085
    )
    val centerPos = LatLng(
        (customerPos.latitude + agentPos.latitude) / 2.0,
        (customerPos.longitude + agentPos.longitude) / 2.0
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerPos, 13.6f)
    }

    LaunchedEffect(agentLat, agentLng, customerLat, customerLng) {
        val updatedCenter = LatLng(
            (customerPos.latitude + agentPos.latitude) / 2.0,
            (customerPos.longitude + agentPos.longitude) / 2.0
        )
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(updatedCenter, 13.6f),
            durationMs = 800
        )
    }

    val routePoints = remember(agentPos, customerPos) {
        val mid1 = LatLng(
            agentPos.latitude + (customerPos.latitude - agentPos.latitude) * 0.35,
            agentPos.longitude + (customerPos.longitude - agentPos.longitude) * 0.15
        )
        val mid2 = LatLng(
            agentPos.latitude + (customerPos.latitude - agentPos.latitude) * 0.70,
            agentPos.longitude + (customerPos.longitude - agentPos.longitude) * 0.85
        )
        listOf(agentPos, mid1, mid2, customerPos)
    }

    BackHandler {
        vm.navigateTo(ApnaDhobiScreen.HomeFrame)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // TOP APP BAR: ← Order Tracking + Order ID + Help Button (Matching Screenshot 2)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { vm.navigateTo(ApnaDhobiScreen.HomeFrame) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F3E88)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Order Tracking",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F3E88)
                        )
                        Text(
                            text = "Order ID: #AD${(currentOrder?.id ?: orderId).toString().padStart(8, '0')}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // 🎧 Help Button (Matching Screenshot 2)
                Surface(
                    onClick = { showHelpDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Help",
                            tint = Color(0xFF0F3E88),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Help",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F3E88)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. TOP INTERACTIVE GOOGLE MAP CARD (Matching Screenshot 2)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9ECEF)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                compassEnabled = false,
                                myLocationButtonEnabled = false,
                                mapToolbarEnabled = false
                            ),
                            properties = MapProperties(
                                isMyLocationEnabled = false,
                                mapType = MapType.NORMAL
                            )
                        ) {
                            val customerMarkerState = rememberMarkerState(position = customerPos)
                            LaunchedEffect(customerPos) { customerMarkerState.position = customerPos }
                            Marker(
                                state = customerMarkerState,
                                title = "Your Location 🏠",
                                snippet = "Delivery Address",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                            )
                            val agentMarkerState = rememberMarkerState(position = agentPos)
                            LaunchedEffect(agentPos) { agentMarkerState.position = agentPos }
                            Marker(
                                state = agentMarkerState,
                                title = "Rohan Sharma 🛵",
                                snippet = "Live Delivery Partner",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                            Polyline(
                                points = routePoints,
                                color = Color(0xFF2563EB),
                                width = 10f
                            )
                        }

                        // Floating ETA Badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                            border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = etaText.ifBlank { "8 mins away" },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // Floating Map Quick Controls (Zoom In, Zoom Out, Recenter)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = {
                                    cameraPositionState.move(CameraUpdateFactory.zoomIn())
                                },
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFF0F3E88), modifier = Modifier.size(16.dp))
                                }
                            }

                            Surface(
                                onClick = {
                                    cameraPositionState.move(CameraUpdateFactory.zoomOut())
                                },
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFF0F3E88), modifier = Modifier.size(16.dp))
                                }
                            }

                            Surface(
                                onClick = {
                                    cameraPositionState.move(
                                        CameraUpdateFactory.newLatLngZoom(centerPos, 13.6f)
                                    )
                                },
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = SaffronOrange, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        // Floating Bottom Partner Banner on Map (Matching Screenshot 2)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(34.dp),
                                        shape = CircleShape,
                                        color = Color(0xFFEFF6FF)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.TwoWheeler,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Rohan Sharma is on the way",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "to pick up your order",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876543210"))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color(0xFF0F3E88),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Call Partner",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F3E88)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. ORDER STATUS CARD (Matching Screenshot 2 Stepper)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Order Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F3E88)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val statusSteps = listOf(
                            Triple("Confirmed", "Dec 03, 09:00 AM", null),
                            Triple("Picked Up", "Dec 03, 09:30 AM", "Rohan Sharma"),
                            Triple("In Process", "Washing & Sanitizing", null),
                            Triple("Out for Delivery", "Express Delivery", null),
                            Triple("Delivered", "Garments Handed Over", null)
                        )

                        val isCancelled = currentStatus.equals("Cancelled", ignoreCase = true)
                        val activeIndex = if (isCancelled) -1 else when (currentStatus.lowercase()) {
                            "placed", "confirmed" -> 0
                            "picked up", "picked_up", "picked" -> 1
                            "washing", "in process", "in_process", "ironing", "processing" -> 2
                            "out for delivery", "on the way", "out_for_delivery" -> 3
                            "delivered", "completed" -> 4
                            else -> 1
                        }

                        if (isCancelled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Order Cancelled", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFDC2626))
                                        Text("100% refund has been credited to your payment method/wallet.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                    }
                                }
                            }
                        } else {
                            statusSteps.forEachIndexed { index, (title, time, subtitle) ->
                                val isPassed = index < activeIndex
                                val isCurrent = index == activeIndex

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = when {
                                                isCurrent || isPassed -> SaffronOrange
                                                else -> Color(0xFFE2E8F0)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isPassed) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                } else if (isCurrent) {
                                                    Surface(
                                                        modifier = Modifier.size(6.dp),
                                                        shape = CircleShape,
                                                        color = Color.White
                                                    ) {}
                                                }
                                            }
                                        }

                                        if (index < statusSteps.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(30.dp)
                                                    .background(if (index < activeIndex) SaffronOrange else Color(0xFFE2E8F0))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = title,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isCurrent || isPassed) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent || isPassed) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                                )
                                                if (isCurrent) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = SaffronOrange
                                                    ) {
                                                        Text(
                                                            text = "Live",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (subtitle != null && isCurrent) {
                                                Text(
                                                    text = subtitle,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        Text(
                                            text = time,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. DELIVERY PARTNER CARD (Matching Screenshot 2)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Rohan Sharma",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delivery Partner",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("4.8 (128)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Call Action Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876543210"))
                                            context.startActivity(intent)
                                        },
                                    shape = CircleShape,
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("Call", fontSize = 10.5.sp, color = Color(0xFF475569))
                            }

                            // Message Action Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+919876543210"))
                                            context.startActivity(intent)
                                        },
                                    shape = CircleShape,
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Chat, contentDescription = "Message", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("Message", fontSize = 10.5.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }

            // 4. HANDOVER VERIFICATION CARD (Matching Screenshot 2)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = SaffronOrange
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Handover Verification",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = SaffronOrange
                                )
                                Text(
                                    text = "Enter the 4-digit code provided for your delivery",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = verificationCode,
                                        onValueChange = { if (it.length <= 4) verificationCode = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (verificationCode.isEmpty()) {
                                                Text("Enter 4-digit code", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (verificationCode.isNotBlank()) {
                                        isCodeVerified = true
                                        Toast.makeText(context, "Verification Successful! Garments Handed Over.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Please enter 4-digit code (e.g. 4821)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                            ) {
                                Text(
                                    text = if (isCodeVerified) "Verified ✓" else "Verify",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 5. ORDER SUMMARY CARD (Matching Screenshot 2)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Order Summary",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F3E88)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentOrder?.itemsSummary?.ifBlank { "Heavy Wedding Saree Dry Clean (x1)" } ?: "Heavy Wedding Saree Dry Clean (x1)",
                                fontSize = 12.5.sp,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "₹${currentOrder?.totalPrice?.toInt() ?: 299}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Bill", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(
                                text = "₹${currentOrder?.totalPrice?.toInt() ?: 299}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = SaffronOrange
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Payment Method", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(
                                text = currentOrder?.paymentMethod?.ifBlank { "UPI / PhonePe" } ?: "UPI / PhonePe",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            // 6. CANCEL ORDER BUTTON (Customer Cancellation Option)
            if (currentStatus.lowercase() != "delivered" && currentStatus.lowercase() != "cancelled") {
                item {
                    Surface(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancel Order",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }

        // STICKY BOTTOM BUTTON: Return to Dashboard (Matching Screenshot 2 Pill)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { vm.navigateTo(ApnaDhobiScreen.HomeFrame) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Return to Dashboard",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(28.dp))
                    }
                }
            }
        }
    }

    // HELP SUPPORT DIALOG (Triggered from Top Right 🎧 Help Button)
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF0F3E88), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Apna Dhobi Support 🎧", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F3E88))
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("How can we assist you with Order #AD${(currentOrder?.id ?: orderId).toString().padStart(8, '0')}?", fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Surface(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876543210"))
                            context.startActivity(intent)
                            showHelpDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Call Customer Care", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                Text("+91 98765 43210 • 24x7 Dedicated Support", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919876543210?text=Hi%20Apna%20Dhobi%20Support,%20I%20need%20help%20with%20Order%20%23AD${(currentOrder?.id ?: orderId).toString().padStart(8, '0')}"))
                            context.startActivity(intent)
                            showHelpDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("WhatsApp Live Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                Text("Instant reply within 2 minutes", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close", color = Color(0xFF0F3E88), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // CANCEL ORDER CONFIRMATION DIALOG
    if (showCancelDialog) {
        var cancelReason by remember { mutableStateOf("Change of schedule / Placed by mistake") }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Order?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Are you sure you want to cancel Order #AD${(currentOrder?.id ?: orderId).toString().padStart(8, '0')}?", fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text("Select Cancellation Reason:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(6.dp))

                    val reasons = listOf(
                        "Change of schedule / Placed by mistake",
                        "Selected wrong items/vendor",
                        "Pickup delayed / Not needed anymore"
                    )

                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cancelReason = reason }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = cancelReason == reason,
                                onClick = { cancelReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = SaffronOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reason, fontSize = 12.sp, color = Color(0xFF334155))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Text(
                            text = "✓ 100% Instant Refund will be credited to your Apna Dhobi Wallet / Bank Account.",
                            fontSize = 11.sp,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.cancelOrder(currentOrder?.id ?: orderId, cancelReason)
                        showCancelDialog = false
                        Toast.makeText(context, "Order Cancelled. Refund initiated.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Cancel", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Order", color = Color(0xFF64748B))
                }
            }
        )
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

    BackHandler {
        vm.navigateBack()
    }

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
                                        url.startsWith("/") -> "https://apna-dhobi-backend.onrender.com$url"
                                        else -> "https://apna-dhobi-backend.onrender.com/$url"
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
                                            text = prod.popularBadge?.uppercase() ?: "",
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
 * Customize Garment Treatment Dialog Modal (Exact Design from Uploaded Image 1)
 */
@Composable
fun CustomizeGarmentTreatmentDialog(
    product: LaundryProduct,
    vendor: Vendor,
    onDismiss: () -> Unit,
    onAddToBasket: (treatment: String, notes: String, quantity: Int) -> Unit
) {
    var selectedTreatment by remember { mutableStateOf("Standard Wash") }
    var userNotes by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var showBreakdownDropdown by remember { mutableStateOf(false) }

    data class TreatmentOption(
        val name: String,
        val subtitle: String,
        val extraPrice: Int,
        val iconType: String,
        val isRecommended: Boolean = false
    )

    val treatments = listOf(
        TreatmentOption("Standard Wash", "Inbuilt deep clean for everyday fabrics", 0, "tshirt", isRecommended = true),
        TreatmentOption("Delicate Silk", "Delicately washed for fabrics like silk", 50, "silk", isRecommended = false),
        TreatmentOption("Heavy Bead", "No damage wash for beaded/bridal wear", 100, "diamond", isRecommended = false)
    )

    val currentOption = treatments.find { it.name == selectedTreatment } ?: treatments.first()
    val extraCost = currentOption.extraPrice
    val basePrice = product.discountPrice.toInt()
    val unitPrice = basePrice + extraCost
    val totalPrice = unitPrice * quantity

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Row: Title & Subtitle + Close Button (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Customize Garment Treatment",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F3E88) // Royal Navy Blue (Image 1)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configure the best care for your garment",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Top-right Close Button (X in light blue circle)
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDismiss() },
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Select Fabric Treatment Type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddBox,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Select Fabric Treatment Type",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F3E88)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 Options List
                treatments.forEach { opt ->
                    val isSelected = selectedTreatment == opt.name

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedTreatment = opt.name },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFF8FAFC) else Color.White,
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF3B82F6)) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = if (isSelected) 0.5.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Radio Button
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 5.dp else 1.5.dp,
                                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                        shape = CircleShape
                                    )
                                    .background(Color.White)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            // Icon Circle
                            val iconBg = when (opt.iconType) {
                                "silk" -> Color(0xFFFDE8E8)
                                "diamond" -> Color(0xFFE0F2FE)
                                else -> Color(0xFFE0E7FF)
                            }
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = iconBg
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val emoji = when (opt.iconType) {
                                        "silk" -> "🌸"
                                        "diamond" -> "💎"
                                        else -> "👕"
                                    }
                                    Text(emoji, fontSize = 18.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Name, Recommended Badge, and Subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = opt.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    if (opt.isRecommended) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE0E7FF)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("👑", fontSize = 8.sp)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Recommended",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2563EB)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = opt.subtitle,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Price on right
                            Text(
                                text = "+₹${opt.extraPrice}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F3E88)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Special Laundering Instructions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Special Laundering Instructions",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F3E88)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = userNotes,
                            onValueChange = { if (it.length <= 100) userNotes = it },
                            modifier = Modifier.fillMaxSize(),
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
                        Text(
                            text = "${userNotes.length}/100",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Select Quantity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Select Quantity",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F3E88)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { if (quantity > 1) quantity-- },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("—", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        }
                        Text(
                            text = "$quantity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { quantity++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expandable Breakdown Dropdown (View Details)
                if (showBreakdownDropdown) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Base Price:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("₹$basePrice", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Treatment ($selectedTreatment):", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("+₹$extraCost", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quantity:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("x $quantity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Calculation:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("₹$totalPrice", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F3E88))
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Row: Total Amount & Add to Basket Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Amount",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "₹$totalPrice",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F3E88)
                        )
                        Row(
                            modifier = Modifier.clickable { showBreakdownDropdown = !showBreakdownDropdown },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "View Details",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showBreakdownDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Add to Basket Button
                    Button(
                        onClick = {
                            val treatmentLabel = when (selectedTreatment) {
                                "Delicate Silk" -> "Delicate Silk 🌸"
                                "Heavy Bead" -> "Heavy Bead 💎"
                                else -> "Standard Wash 🧺"
                            }
                            onAddToBasket(treatmentLabel, userNotes, quantity)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88)), // Deep Royal Navy Blue (Image 1)
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Add to Basket • ₹$totalPrice",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Assurance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981), // Green Shield
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Your clothes are in safe hands",
                        fontSize = 11.5.sp,
                        color = Color(0xFF475569)
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

    BackHandler {
        vm.navigateBack()
    }

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
                mainImg.startsWith("/") -> "https://apna-dhobi-backend.onrender.com$mainImg"
                else -> "https://apna-dhobi-backend.onrender.com/$mainImg"
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
// SLOT SELECTION SCREEN ("Scheduling Checkout")
// ==========================================
@Composable
fun SlotSelectionScreen(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val currentAddress by vm.currentFullAddress.collectAsState()
    val savedAddresses by vm.savedAddresses.collectAsState(initial = emptyList())
    val selectedPickupDate by vm.selectedPickupDate.collectAsState()
    val selectedPickupSlot by vm.selectedPickupSlot.collectAsState()
    val selectedDeliveryDate by vm.selectedDeliveryDate.collectAsState()
    val selectedDeliverySlot by vm.selectedDeliverySlot.collectAsState()
    val isExpress by vm.isExpressDelivery.collectAsState()
    val comments by vm.deliveryInstructions.collectAsState()
    val userName by vm.userName.collectAsState()

    var showMapDialog by remember { mutableStateOf(false) }

    BackHandler {
        vm.navigateBack()
    }

    // Days for Pickup Date Selection
    val today = remember { LocalDate.now() }
    val pickupDays = remember { (0..4).map { today.plusDays(it.toLong()) } }
    val deliveryDays = remember { (2..6).map { today.plusDays(it.toLong()) } }

    val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val dayNumFormat = DateTimeFormatter.ofPattern("dd", Locale.ENGLISH)
    val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // TOP APP BAR: ← Scheduling Checkout with statusBarsPadding
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.navigateBack() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F3E88)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scheduling Checkout",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3E88)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 4-STEP PROGRESS STEPPER
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Step 1: Address (Done)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFF16A34A)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Address 📍", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 2: Schedule (Active)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = SaffronOrange
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("2", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Schedule 📅", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F3E88))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 3: Payment
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFFE2E8F0)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("3", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Payment 💳", fontSize = 10.sp, color = Color(0xFF64748B))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 4: Confirm
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFFE2E8F0)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("4", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Confirm 🎉", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }

            // SECTION 1: 1. SELECT PICKUP ADDRESS
            item {
                Text(
                    text = "1. SELECT PICKUP ADDRESS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Active Selected Location Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Selected Location",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF0F3E88)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentAddress.ifBlank { "Shanti Kutir, Block 4-B, Connaught Place, New Delhi" },
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Saved Address Book
                        Text(
                            text = "Saved Address Book",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sample Saved Address 1 (Office)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.currentFullAddress.value = "Tech Park Tower A, Sector 62, Noida"
                                    Toast.makeText(context, "Location set to Office!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GoldPremium,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Office", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF1E293B))
                                    Text("Tech Park Tower A, Sector 62, Noida", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            val isOfficeSelected = currentAddress.contains("Sector 62", ignoreCase = true) || currentAddress.contains("Noida", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isOfficeSelected) 5.dp else 1.5.dp,
                                        color = if (isOfficeSelected) SaffronOrange else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    )
                                    .background(Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Choose Realtime Location from Map Button
                        Button(
                            onClick = { showMapDialog = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, SaffronOrange),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Choose Realtime Location from Map",
                                    color = SaffronOrange,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: 2. SCHEDULE PICKUP DATE & TIME
            item {
                Text(
                    text = "2. SCHEDULE PICKUP DATE & TIME",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pickupDays.forEach { date ->
                        val isSelected = selectedPickupDate.contains(date.format(dayNumFormat))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    vm.selectPickupDate(date)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SaffronOrange else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.format(dayFormat).uppercase(),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date.format(dayNumFormat),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date.format(monthFormat),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Calendar button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                Toast.makeText(context, "Calendar picker: dates synchronized with vendor hours!", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Timing Slot Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Pickup Timing Slot",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Timings are in 12h format",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Slots by Partitions: MORNING, AFTERNOON, EVENING
                val morningSlots = listOf("10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM", "12:00 PM", "12:30 PM")
                val afternoonSlots = listOf("01:00 PM", "01:30 PM", "02:00 PM", "02:30 PM", "03:00 PM", "03:30 PM")
                val eveningSlots = listOf("05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM", "07:00 PM", "07:30 PM")

                SlotPartitionRow(title = "MORNING", slots = morningSlots, selectedSlot = selectedPickupSlot, onSelect = { vm.selectPickupSlot(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SlotPartitionRow(title = "AFTERNOON", slots = afternoonSlots, selectedSlot = selectedPickupSlot, onSelect = { vm.selectPickupSlot(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SlotPartitionRow(title = "EVENING", slots = eveningSlots, selectedSlot = selectedPickupSlot, onSelect = { vm.selectPickupSlot(it) })
            }

            // SECTION 3: 3. SCHEDULE RETURN DELIVERY
            item {
                Text(
                    text = "3. SCHEDULE RETURN DELIVERY",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronOrange,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Delivery Date Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    deliveryDays.forEach { date ->
                        val isSelected = selectedDeliveryDate.contains(date.format(dayNumFormat))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    vm.selectDeliveryDate(date)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SaffronOrange else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.format(dayFormat).uppercase(),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date.format(dayNumFormat),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date.format(monthFormat),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Calendar icon button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                Toast.makeText(context, "Delivery dates synchronized with vendor turnaround!", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Delivery Timing Slot",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Timings are in 12h format",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val morningSlots = listOf("10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM", "12:00 PM", "12:30 PM")
                val afternoonSlots = listOf("01:00 PM", "01:30 PM", "02:00 PM", "02:30 PM", "03:00 PM", "03:30 PM")
                val eveningSlots = listOf("05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM", "07:00 PM", "07:30 PM")
                val expressSlots = listOf("08:00 PM", "08:30 PM", "09:00 PM", "09:30 PM", "10:00 PM")

                SlotPartitionRow(title = "MORNING", slots = morningSlots, selectedSlot = selectedDeliverySlot, onSelect = { vm.selectDeliverySlot(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SlotPartitionRow(title = "AFTERNOON", slots = afternoonSlots, selectedSlot = selectedDeliverySlot, onSelect = { vm.selectDeliverySlot(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SlotPartitionRow(title = "EVENING", slots = eveningSlots, selectedSlot = selectedDeliverySlot, onSelect = { vm.selectDeliverySlot(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SlotPartitionRow(title = "EXPRESS (18 hrs) ⚡", slots = expressSlots, selectedSlot = selectedDeliverySlot, onSelect = { vm.selectDeliverySlot(it) }, isExpressHeader = true)
            }

            // SECTION 4: EXPRESS TURNAROUND TOGGLE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = Color(0xFF0F3E88),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Express Turnaround ⚡",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Guaranteed wash + return under 18 hours (₹80 charge)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Switch(
                            checked = isExpress,
                            onCheckedChange = { vm.setExpressDelivery(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SaffronOrange,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }

            // SECTION 5: ADDITIONAL COORDINATION COMMENTS (Optional)
            item {
                Text(
                    text = "ADDITIONAL COORDINATION COMMENTS (Optional)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        BasicTextField(
                            value = comments,
                            onValueChange = { vm.deliveryInstructions.value = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = Color(0xFF1E293B)
                            ),
                            decorationBox = { innerTextField ->
                                if (comments.isEmpty()) {
                                    Text(
                                        text = "e.g. Leave with gate keeper, call before picking up garments",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // STICKY BOTTOM BUTTON: Proceed to Payments
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        if (!vm.isLoggedIn.value) {
                            vm.postAuthDestination = ApnaDhobiScreen.Payment
                            vm.navigateTo(ApnaDhobiScreen.Login)
                        } else {
                            vm.navigateTo(ApnaDhobiScreen.Payment)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text(
                        text = "Proceed to Payments",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // REALTIME MAP LOCATION PICKER DIALOG
    if (showMapDialog) {
        Dialog(onDismissRequest = { showMapDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 10.dp
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = SaffronOrange)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Realtime Location", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F3E88))
                        }
                        IconButton(onClick = { showMapDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Map Simulator View
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE2E8F0),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationSearching, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Interactive GPS Map Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text("28.6315° N, 77.2167° E (Connaught Place)", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Detect Current GPS Location
                    Button(
                        onClick = {
                            vm.currentFullAddress.value = "Shanti Kutir, Block 4-B, Connaught Place, New Delhi"
                            Toast.makeText(context, "Current GPS location updated!", Toast.LENGTH_SHORT).show()
                            showMapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Current GPS Location", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Select Custom Address 2
                    OutlinedButton(
                        onClick = {
                            vm.currentFullAddress.value = "Tech Park Tower A, Sector 62, Noida"
                            Toast.makeText(context, "Location set to Noida Tech Park!", Toast.LENGTH_SHORT).show()
                            showMapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text("Select: Tech Park Sector 62, Noida", fontSize = 12.sp, color = Color(0xFF1E293B))
                    }
                }
            }
        }
    }
}

/**
 * Helper row for slot partitions (MORNING, AFTERNOON, EVENING, EXPRESS)
 */
@Composable
fun SlotPartitionRow(
    title: String,
    slots: List<String>,
    selectedSlot: String,
    onSelect: (String) -> Unit,
    isExpressHeader: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExpressHeader) Color(0xFF0284C7) else Color(0xFF2563EB),
                letterSpacing = 0.5.sp
            )
            if (isExpressHeader) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Slots grid / row
        val chunkedSlots = slots.chunked(4)
        chunkedSlots.forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowSlots.forEach { slot ->
                    val isSelected = selectedSlot == slot
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(slot) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) SaffronOrange else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slot,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF1E293B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                // Fill remaining spaces in row
                repeat(4 - rowSlots.size) {
                    Spacer(modifier = Modifier.weight(1f))
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
    val subTotal by vm.cartSubTotal.collectAsState()
    val discount by vm.cartDiscount.collectAsState()
    val logisticsFee by vm.deliveryFee.collectAsState()
    val gstAndTaxes by vm.gstAndTaxes.collectAsState()
    val appliedCoupon by vm.appliedCoupon.collectAsState()

    val firstVendorName = cartItems.firstOrNull()?.vendorName?.ifBlank { "Apna Dhobi Express" } ?: "Apna Dhobi Express"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // TOP APP BAR / HEADER ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { vm.selectBottomTab("home") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Charcoal
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Your Laundry Bucket",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${cartItems.size} item${if (cartItems.size > 1) "s" else ""} added from ",
                            fontSize = 11.5.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = firstVendorName,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronOrange
                        )
                    }
                }
            }

            if (cartItems.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { vm.clearCart() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

        val ordersList by vm.ordersList.collectAsState()
        val latestActiveOrder = ordersList.firstOrNull { it.status.uppercase() != "DELIVERED" && it.status.uppercase() != "CANCELLED" } ?: ordersList.firstOrNull()

        if (cartItems.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // If there's an active or past placed order, show it prominently here so user can check & track their order!
                if (latestActiveOrder != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(32.dp),
                                            shape = CircleShape,
                                            color = Color(0xFFEFF6FF)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalShipping,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0F3E88),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Your Placed Order 📦",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF0F3E88)
                                            )
                                            Text(
                                                text = "Order #AD${latestActiveOrder.id.toString().padStart(8, '0')}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SaffronOrange
                                    ) {
                                        Text(
                                            text = latestActiveOrder.status.uppercase(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = latestActiveOrder.itemsSummary.ifBlank { "Laundry Garments" },
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF1E293B)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Paid: ₹${latestActiveOrder.totalPrice.toInt()}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaffronOrange
                                    )
                                    Text(
                                        text = latestActiveOrder.pickupSlot.ifBlank { "Delivery In Progress" },
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { vm.navigateTo(ApnaDhobiScreen.OrderTracking(latestActiveOrder.id)) },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Track Live Order 📍", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { vm.selectBottomTab("orders") },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF0F3E88))
                                    ) {
                                        Text("All Orders History 📋", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F3E88))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bucket Empty Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = Color.LightGray.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your laundry bucket is empty!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Charcoal,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add products from nearby local professional laundry providers.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { vm.selectBottomTab("home") },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Explore Services 🧺",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { vm.selectBottomTab("orders") },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, SaffronOrange)
                                ) {
                                    Text(
                                        text = "View Orders 📦",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaffronOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                // CART ITEMS LIST
                items(cartItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Image + Item info + Stepper + Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Garment Image
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🥻", fontSize = 34.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Item Title, Service, Base Rate
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Store,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Service: ${item.vendorName.ifBlank { "Apna Dhobi Express" }}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Base Rate: ₹${item.discountPrice.toInt()}/kg",
                                        color = SaffronOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Stepper and Delete Icon
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable {
                                                        if (item.quantity > 1) {
                                                            vm.updateCartQuantity(item.id, item.quantity - 1)
                                                        } else {
                                                            vm.removeCartItemCompletely(item.id)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("—", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                            }
                                            Text(
                                                text = "${item.quantity}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B),
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable {
                                                        vm.updateCartQuantity(item.id, item.quantity + 1)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = { vm.removeCartItemCompletely(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // SELECT FABRIC TREATMENT TYPE:
                            Text(
                                text = "SELECT FABRIC TREATMENT TYPE:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                letterSpacing = 0.3.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val treatmentPills = listOf(
                                "Standard Wash 🧺" to "Standard",
                                "Delicate Silk 🌸" to "Silk",
                                "Heavy Bead 💎" to "Bead"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                treatmentPills.forEach { (label, key) ->
                                    val isSelected = item.dryCleaningType.contains(key, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFFFF7ED) else Color.White,
                                        border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                vm.updateCartItemDryCleaningType(item.id, label)
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) SaffronOrange else Color(0xFF475569),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // CUSTOM INSTRUCTIONS / NOTES FOR DHOBI:
                            Text(
                                text = "CUSTOM INSTRUCTIONS / NOTES FOR DHOBI:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                letterSpacing = 0.3.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    BasicTextField(
                                        value = item.userNotes,
                                        onValueChange = {
                                            if (it.length <= 100) vm.updateCartItemNotes(item.id, it)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF1E293B)
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (item.userNotes.isEmpty()) {
                                                Text(
                                                    text = "e.g. starch heavily, remove cuff stain...",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    Text(
                                        text = "${item.userNotes.length}/100",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // PRIORITY SERVICE & QUALITY RATING:
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PRIORITY SERVICE & QUALITY RATING:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )

                                Row {
                                    repeat(5) { starIndex ->
                                        val isFilled = starIndex < item.reviewRating
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (isFilled) SaffronOrange else Color(0xFFCBD5E1),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable {
                                                    vm.updateCartItemRating(item.id, starIndex + 1)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // AVAILABLE COUPONS (Apna Dhobi Express)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF9)),
                        border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AVAILABLE COUPONS ($firstVendorName)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SaffronOrange
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Coupon 1: WELCOME20
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFFF7ED),
                                            border = BorderStroke(1.dp, SaffronOrange)
                                        ) {
                                            Text(
                                                text = "20%\nOFF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = SaffronOrange,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("WELCOME20", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF1E293B))
                                            Text("Get 20% off on your first order", fontSize = 10.5.sp, color = Color(0xFF64748B))
                                            Text("Min. order ₹199 • Valid till 30 May 2025", fontSize = 9.5.sp, color = Color(0xFF94A3B8))
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            vm.appliedCoupon.value = if (appliedCoupon == "WELCOME20") "" else "WELCOME20"
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (appliedCoupon == "WELCOME20") GreenSuccess else SaffronOrange
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (appliedCoupon == "WELCOME20") "Applied ✓" else "Apply",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Coupon 2: EXPRESS15
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFFF7ED),
                                            border = BorderStroke(1.dp, SaffronOrange)
                                        ) {
                                            Text(
                                                text = "15%\nOFF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = SaffronOrange,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("EXPRESS15", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF1E293B))
                                            Text("15% off on express orders", fontSize = 10.5.sp, color = Color(0xFF64748B))
                                            Text("Min. order ₹299 • Valid till 15 May 2025", fontSize = 9.5.sp, color = Color(0xFF94A3B8))
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            vm.appliedCoupon.value = if (appliedCoupon == "EXPRESS15") "" else "EXPRESS15"
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (appliedCoupon == "EXPRESS15") GreenSuccess else SaffronOrange
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (appliedCoupon == "EXPRESS15") "Applied ✓" else "Apply",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { /* View coupons */ },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "View all 5 coupons",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaffronOrange
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // BILL BREAKDOWN
                item {
                    Text(
                        text = "BILL BREAKDOWN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Items Price Total", fontSize = 12.5.sp, color = Color(0xFF64748B))
                                Text("₹${subTotal.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }

                            if (discount > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Promo Coupon Discount", fontSize = 12.5.sp, color = Color(0xFF10B981))
                                    Text("-₹${discount.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Express Logistics Fee", fontSize = 12.5.sp, color = Color(0xFF64748B))
                                Text("₹${logisticsFee.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Govt Service GST (18%)", fontSize = 12.5.sp, color = Color(0xFF64748B))
                                Text("₹${gstAndTaxes.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            // STICKY BOTTOM BAR (Proceed to Checkout)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Amount",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "₹${finalTotal.toInt()}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = SaffronOrange
                        )
                        if (discount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "You save ₹${discount.toInt()}",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!vm.isLoggedIn.value) {
                                vm.postAuthDestination = ApnaDhobiScreen.SlotSelection
                                vm.navigateTo(ApnaDhobiScreen.Login)
                            } else {
                                vm.navigateTo(ApnaDhobiScreen.SlotSelection)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Proceed to Checkout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
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
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
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
// PAYMENT SCREEN ("Select Payment Gateway")
// ==========================================
@Composable
fun PaymentScreen(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val cartItems by vm.cartItems.collectAsState()
    val finalTotal by vm.cartFinalTotal.collectAsState()
    val walletBalance by vm.walletBalance.collectAsState()
    val firstVendorName = cartItems.firstOrNull()?.vendorName?.ifBlank { "Apna Dhobi Express" } ?: "Apna Dhobi Express"

    var selectedMethod by remember { mutableStateOf("UPI") } // "UPI", "CARDS", "APNA_WALLET", "PAYTM_WALLET", "PHONEPE_WALLET", "NET_BANKING", "COD"
    var showRazorpayDialog by remember { mutableStateOf(false) }
    var razorpayState by remember { mutableStateOf("PROCESSING") } // "PROCESSING", "SUCCESS", "FAILED"

    BackHandler {
        vm.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // Top App Bar with statusBarsPadding
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.navigateBack() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F3E88)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select Payment Gateway",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3E88)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 4-STEP PROGRESS STEPPER
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Step 1: Address (Done)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFF16A34A)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Address 📍", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 2: Schedule (Done)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFF16A34A)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Schedule 📅", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 3: Payment (Active)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = SaffronOrange
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("3", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Payment 💳", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F3E88))
                        }

                        Box(modifier = Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFCBD5E1)))

                        // Step 4: Confirm
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color(0xFFE2E8F0)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("4", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Confirm 🎉", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }

            // PAYABLE AMOUNT BLUE CARD HEADER (Exact from Image)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3E88)), // Royal Deep Navy Blue
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "PAYABLE AMOUNT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF93C5FD),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${String.format(Locale.ENGLISH, "%.2f", finalTotal)}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Order ID: #LAU-210525-0012 • $firstVendorName",
                                fontSize = 11.5.sp,
                                color = Color(0xFFBFDBFE)
                            )
                        }

                        // Bottom White Assurance Banner inside card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "100% Secure Payment",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "Your payment details are safe with us.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Text(
                                    text = "⚡Razorpay",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0C2340)
                                )
                            }
                        }
                    }
                }
            }

            // CHOOSE A PAYMENT METHOD Section
            item {
                Text(
                    text = "CHOOSE A PAYMENT METHOD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Option 1: UPI / PhonePe / Google Pay / Paytm
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("UPI", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                                }
                            }
                        },
                        title = "UPI / PhonePe / Google Pay / Paytm",
                        subtitle = "Pay securely using any UPI app",
                        isSelected = selectedMethod == "UPI",
                        onClick = { selectedMethod = "UPI" }
                    )

                    // Option 2: Credit / Debit Cards
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        title = "Credit / Debit Cards",
                        subtitle = "Visa, Mastercard, RuPay & more",
                        isSelected = selectedMethod == "CARDS",
                        onClick = { selectedMethod = "CARDS" }
                    )

                    // Option 3: Apna Dhobi App Wallet (Requested by user)
                    val isWalletSufficient = walletBalance >= finalTotal
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFF7ED),
                                border = BorderStroke(1.dp, SaffronOrange),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("👛", fontSize = 18.sp)
                                }
                            }
                        },
                        title = "Apna Dhobi Wallet (₹${walletBalance.toInt()})",
                        subtitle = if (isWalletSufficient) "Instant 1-click checkout from wallet cash" else "Low balance: Top up wallet or select other options",
                        extraBadge = if (isWalletSufficient) "Sufficient 🟢" else "Add Money ⚠️",
                        isSelected = selectedMethod == "APNA_WALLET",
                        onClick = { selectedMethod = "APNA_WALLET" }
                    )

                    // Option 4: Paytm Wallet
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE0F2FE),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Paytm", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF0284C7))
                                }
                            }
                        },
                        title = "Paytm Wallet",
                        subtitle = "Pay using your Paytm wallet balance",
                        isSelected = selectedMethod == "PAYTM_WALLET",
                        onClick = { selectedMethod = "PAYTM_WALLET" }
                    )

                    // Option 5: PhonePe Wallet
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF3E8FF),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("पे", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF7E22CE))
                                }
                            }
                        },
                        title = "PhonePe Wallet",
                        subtitle = "Pay using your PhonePe wallet balance",
                        isSelected = selectedMethod == "PHONEPE_WALLET",
                        onClick = { selectedMethod = "PHONEPE_WALLET" }
                    )

                    // Option 6: Net Banking
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        title = "Net Banking",
                        subtitle = "All major banks supported",
                        isSelected = selectedMethod == "NET_BANKING",
                        onClick = { selectedMethod = "NET_BANKING" }
                    )

                    // Option 7: Cash on Delivery
                    PaymentMethodCard(
                        iconBadge = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFECFDF5),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        title = "Cash on Delivery",
                        subtitle = "Pay cash when we deliver",
                        isSelected = selectedMethod == "COD",
                        onClick = { selectedMethod = "COD" }
                    )
                }
            }

            // Security note footer
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Secured by Razorpay • Your data is encrypted & safe.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // STICKY BOTTOM BAR (Pay ₹323.02 & Place Order)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (selectedMethod == "APNA_WALLET") {
                            if (walletBalance >= finalTotal) {
                                vm.processCheckout("Apna Wallet", true)
                                Toast.makeText(context, "Payment Successful via Apna Dhobi Wallet!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Insufficient Wallet Balance! Please top up or select UPI/Razorpay.", Toast.LENGTH_LONG).show()
                            }
                        } else if (selectedMethod == "COD") {
                            vm.processCheckout("Cash on Delivery", false)
                            Toast.makeText(context, "Order Placed with Cash on Delivery!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Launch Real-Time Razorpay Dialog Flow
                            razorpayState = "PROCESSING"
                            showRazorpayDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pay ₹${String.format(Locale.ENGLISH, "%.2f", finalTotal)} & Place Order",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "By proceeding, you agree to our Terms & Conditions and Privacy Policy.",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // REAL-TIME RAZORPAY PAYMENT GATEWAY POPUP
    if (showRazorpayDialog) {
        Dialog(onDismissRequest = { if (razorpayState != "PROCESSING") showRazorpayDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Razorpay Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF0C2340), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Razorpay Gateway", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0C2340))
                        }
                        if (razorpayState != "PROCESSING") {
                            IconButton(
                                onClick = {
                                    showRazorpayDialog = false
                                    vm.processCheckout(selectedMethod, false)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (razorpayState == "PROCESSING") {
                        CircularProgressIndicator(
                            color = Color(0xFF0F3E88),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Securing Real-Time Transaction...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connecting to Razorpay & $selectedMethod gateway",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "Amount: ₹${String.format(Locale.ENGLISH, "%.2f", finalTotal)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronOrange,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        LaunchedEffect(Unit) {
                            delay(1800)
                            razorpayState = "SUCCESS"
                            delay(1400)
                            showRazorpayDialog = false
                            vm.processCheckout(selectedMethod, false)
                            Toast.makeText(context, "Payment Verified by Razorpay! Order Scheduled.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Payment Verified & Authenticated!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Razorpay Ref: pay_${System.currentTimeMillis() % 100000000}x",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                showRazorpayDialog = false
                                vm.processCheckout(selectedMethod, false)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Track Order 📍", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Payment Method Card with selection state
 */
@Composable
fun PaymentMethodCard(
    iconBadge: @Composable () -> Unit,
    title: String,
    subtitle: String,
    extraBadge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFFFFF7ED) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) SaffronOrange else Color(0xFFE2E8F0)),
        shadowElevation = if (isSelected) 1.dp else 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                iconBadge()
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF1E293B)
                        )
                        if (extraBadge != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = extraBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (isSelected) {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = CircleShape,
                    color = SaffronOrange
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(18.dp)
                )
            }
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

