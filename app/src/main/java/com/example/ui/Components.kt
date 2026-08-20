package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.example.ui.theme.*
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

// In Memory LRU Image Cache for ultra-fast native image performance
private val imageMemoryCache = android.util.LruCache<String, ImageBitmap>(50)

@Composable
fun ApnaNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {}
) {
    if (url.isNullOrBlank()) {
        placeholder()
        return
    }

    val resolvedUrl = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "http://10.0.2.2:3000$url"
        else -> "http://10.0.2.2:3000/$url"
    }

    var bitmap by remember(resolvedUrl) {
        mutableStateOf(imageMemoryCache.get(resolvedUrl))
    }

    LaunchedEffect(resolvedUrl) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val connection = java.net.URL(resolvedUrl).openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connectTimeout = 6000
                    connection.readTimeout = 6000
                    connection.connect()
                    if (connection.responseCode == 200) {
                        val inputStream = connection.inputStream
                        val decoded = android.graphics.BitmapFactory.decodeStream(inputStream)
                        decoded?.let {
                            val imgBitmap = it.asImageBitmap()
                            imageMemoryCache.put(resolvedUrl, imgBitmap)
                            bitmap = imgBitmap
                        }
                    }
                } catch (e: Throwable) {
                    // Fallback gracefully
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        placeholder()
    }
}

/**
 * Animated Washing Bubble Loader or Splash Background
 * Draws premium animated bubbles using Canvas
 */
@Composable
fun AnimatedWashingBubbles(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubbles")
    
    val bubbleY1 by infiniteTransition.animateFloat(
        initialValue = 400f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "b1"
    )

    val bubbleY2 by infiniteTransition.animateFloat(
        initialValue = 450f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "b2"
    )

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f || width.isInfinite() || height.isInfinite() || width.isNaN() || height.isNaN()) return@Canvas

        // Limit loop maximum width to prevent thread-blocking cycles (ANRs) in case size is unconstrained
        val maxLoopWidth = minOf(width, 3000f).toInt()

        // Draw animated watermark background laundry wave
        val wavePath = Path()
        wavePath.moveTo(0f, height * 0.8f)
        for (x in 0..maxLoopWidth step 5) {
            val y = height * 0.82f + sin(x * 0.02f + waveOffset) * 15f
            wavePath.lineTo(x.toFloat(), y)
        }
        wavePath.lineTo(width, height)
        wavePath.lineTo(0f, height)
        wavePath.close()

        drawPath(
            path = wavePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    RoyalBlue.copy(alpha = 0.15f),
                    RoyalBlue.copy(alpha = 0.35f)
                )
            )
        )

        // Floating Bubbles
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = 30f,
            center = Offset(width * 0.25f, bubbleY1)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = 20f,
            center = Offset(width * 0.7f, bubbleY2)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = 15f,
            center = Offset(width * 0.45f, (bubbleY1 + 150f) % height)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = 25f,
            center = Offset(width * 0.85f, (bubbleY2 + 200f) % height)
        )
    }
}

/**
 * Beautiful vector Canvas representation of "Apna Dhobi" brand mascot washing scene.
 * This is detailed, beautiful, and completely scales.
 */
@Composable
fun IndianLaundryVectorMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f

        // Draw water tub
        drawArc(
            color = RoyalBlue.copy(alpha = 0.85f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.4f),
            topLeft = Offset(w * 0.15f, cy)
        )

        // Draw soapy foam bubbles inside tub
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 25f, center = Offset(cx - 50f, cy + 20f))
        drawCircle(Color.White.copy(alpha = 0.95f), radius = 35f, center = Offset(cx, cy + 10f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 30f, center = Offset(cx + 60f, cy + 15f))

        // Draw Dhobi hanging rope decoration lines
        drawLine(
            color = SaffronOrange,
            start = Offset(0f, h * 0.2f),
            end = Offset(w, h * 0.2f),
            strokeWidth = 6f
        )

        // Draw shirt hanging on rope
        val shirtPath = Path().apply {
            moveTo(cx - 80f, h * 0.2f)
            lineTo(cx - 100f, h * 0.28f)
            lineTo(cx - 70f, h * 0.32f)
            lineTo(cx - 70f, h * 0.48f)
            lineTo(cx - 30f, h * 0.48f)
            lineTo(cx - 30f, h * 0.32f)
            lineTo(cx, h * 0.28f)
            lineTo(cx - 20f, h * 0.2f)
            close()
        }
        drawPath(shirtPath, color = Color.White)
        drawPath(shirtPath, color = SaffronOrange.copy(alpha = 0.8f), style = Stroke(4f))

        // Draw pegs hanging shirt
        drawCircle(SaffronOrange, radius = 5f, center = Offset(cx - 80f, h * 0.2f))
        drawCircle(SaffronOrange, radius = 5f, center = Offset(cx - 20f, h * 0.2f))
    }
}

/**
 * Promotional Banners Auto-Slider layout.
 * Displays gorgeous high-contrast premium festival discount metrics.
 */
@Composable
fun PromoBannerSlider(
    vm: ApnaDhobiViewModel,
    onPromoClick: (String) -> Unit
) {
    val banners by vm.bannersState.collectAsState()
    var activePage by remember { mutableStateOf(0) }
    
    // Auto increment pages to simulate a real banner slider
    LaunchedEffect(banners) {
        if (banners.isEmpty()) return@LaunchedEffect
        while(true) {
            delay(4000)
            activePage = (activePage + 1) % banners.size
        }
    }

    if (banners.isEmpty()) return

    // Safe page index check
    val safeIndex = activePage.coerceIn(0, (banners.size - 1).coerceAtLeast(0))
    val activeBanner = banners[safeIndex]
    
    // Helper to parse hex strings to Color with proper alpha
    fun parseHex(hex: String): Color {
        return try {
            val clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            val fullHex = when (clean.length) {
                6 -> "FF$clean"
                8 -> clean
                else -> "FF1E6BFF"
            }
            Color(fullHex.toLong(16))
        } catch (e: Exception) {
            RoyalBlue
        }
    }

    val bannerColors = if (!activeBanner.colors.isNullOrEmpty()) {
        activeBanner.colors.map { parseHex(it) }
    } else {
        listOf(RoyalBlue, SaffronOrange)
    }

    val rawImg = activeBanner.imageUrl?.takeIf { it.isNotBlank() }
        ?: activeBanner.mediaUrl?.takeIf { it.isNotBlank() }

    val resolvedImgUrl = rawImg?.let { url ->
        when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "http://10.0.2.2:3000$url"
            else -> "http://10.0.2.2:3000/$url"
        }
    }

    val brandTag = activeBanner.brandName?.takeIf { it.isNotBlank() }
        ?: activeBanner.badge?.takeIf { it.isNotBlank() }
        ?: "APNA DHOBI"

    val hasTitle = !activeBanner.title.isNullOrBlank() && !activeBanner.title.equals("Untitled Banner", ignoreCase = true)
    val shouldShowText = (activeBanner.showTextOverlay != false) && hasTitle

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(bannerColors),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onPromoClick(activeBanner.redirectUrl ?: activeBanner.code ?: "") },
        contentAlignment = Alignment.CenterStart
    ) {
        if (resolvedImgUrl != null) {
            // Render actual uploaded/preset media image from Admin Panel
            ApnaNetworkImage(
                url = resolvedImgUrl,
                contentDescription = activeBanner.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // If text overlay is enabled, show subtle dark gradient for high contrast
            if (shouldShowText) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                )
            }
        } else {
            // Bubble watermark visuals for gradient banners
            AnimatedWashingBubbles(modifier = Modifier.fillMaxSize())
        }

        // Text & Branding Content Overlay
        if (shouldShowText || resolvedImgUrl == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .padding(bottom = 12.dp, end = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = brandTag.uppercase(),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (hasTitle) {
                    Text(
                        text = activeBanner.title ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 19.sp
                    )
                }

                if (!activeBanner.subtitle.isNullOrBlank() && !activeBanner.subtitle.equals("No subtitle provided", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeBanner.subtitle,
                        color = LightCream,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }
        }

        // Slide Indicator Dots (● ○ ○)
        if (banners.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                banners.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == safeIndex) 8.dp else 6.dp)
                            .background(
                                color = if (index == safeIndex) Color.White else Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

/**
 * Modern Washing Appliance Graphic (Stylized 3D Metallic Washing Machine for Promo Banners)
 */
@Composable
fun StylizedWashingApplianceGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer Machine Body in Metallic White/Silver with soft perspective
        val bodyPath = Path().apply {
            moveTo(w * 0.12f, h * 0.10f)
            lineTo(w * 0.88f, h * 0.06f)
            lineTo(w * 0.94f, h * 0.88f)
            lineTo(w * 0.18f, h * 0.94f)
            close()
        }
        drawPath(
            path = bodyPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0xFFCBD5E1)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Top Control Dark Dashboard
        val panelPath = Path().apply {
            moveTo(w * 0.14f, h * 0.13f)
            lineTo(w * 0.87f, h * 0.09f)
            lineTo(w * 0.88f, h * 0.25f)
            lineTo(w * 0.15f, h * 0.28f)
            close()
        }
        drawPath(
            path = panelPath,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A))
            )
        )

        // Control Panel Knob
        drawCircle(
            color = Color(0xFF64748B),
            radius = w * 0.045f,
            center = Offset(w * 0.48f, h * 0.18f)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = w * 0.02f,
            center = Offset(w * 0.48f, h * 0.18f)
        )

        // Door Outer Chrome Ring
        val doorCenter = Offset(w * 0.54f, h * 0.60f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF475569)),
                center = doorCenter,
                radius = w * 0.32f
            ),
            radius = w * 0.29f,
            center = doorCenter
        )

        // Door Glass (Deep Blue Tint with reflection)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF3B82F6)),
                start = Offset(doorCenter.x - w * 0.22f, doorCenter.y - h * 0.22f),
                end = Offset(doorCenter.x + w * 0.22f, doorCenter.y + h * 0.22f)
            ),
            radius = w * 0.22f,
            center = doorCenter
        )

        // Glass Highlights
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = w * 0.08f,
            center = Offset(doorCenter.x - w * 0.08f, doorCenter.y - h * 0.08f)
        )

        // Bottom Pedestal Line
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(w * 0.20f, h * 0.88f),
            end = Offset(w * 0.92f, h * 0.83f),
            strokeWidth = w * 0.03f
        )
    }
}

/**
 * Mid Promo Banner Slider (Sub-Banner matching Image 2, above Express Delivery)
 */
@Composable
fun MidBannerSlider(
    vm: ApnaDhobiViewModel,
    onPromoClick: (String) -> Unit
) {
    val midBanners by vm.midBannersState.collectAsState()
    var activePage by remember { mutableStateOf(0) }

    LaunchedEffect(midBanners) {
        if (midBanners.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(4000)
            activePage = (activePage + 1) % midBanners.size
        }
    }

    if (midBanners.isEmpty()) return

    val safeIndex = activePage.coerceIn(0, (midBanners.size - 1).coerceAtLeast(0))
    val banner = midBanners[safeIndex]

    // Parse hex colors or default to brand orange gradient
    fun parseHex(hex: String, fallback: Color): Color {
        return try {
            val clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            val fullHex = when (clean.length) {
                6 -> "FF$clean"
                8 -> clean
                else -> "FFFF6B00"
            }
            Color(fullHex.toLong(16))
        } catch (e: Exception) {
            fallback
        }
    }

    val bannerColors = if (!banner.colors.isNullOrEmpty()) {
        banner.colors.mapIndexed { idx, col ->
            parseHex(col, if (idx == 0) SaffronOrange else Color(0xFFFF8C00))
        }
    } else {
        listOf(SaffronOrange, Color(0xFFFF8C00))
    }

    val rawImg = banner.imageUrl?.takeIf { it.isNotBlank() }
        ?: banner.mediaUrl?.takeIf { it.isNotBlank() }

    val resolvedImgUrl = rawImg?.let { url ->
        when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "http://10.0.2.2:3000$url"
            else -> "http://10.0.2.2:3000/$url"
        }
    }

    val titleText = banner.title?.takeIf { it.isNotBlank() }
        ?: "Laundry Made Easy , Get 50% OFF On Wash & Fold Today!"
    val codeText = banner.code?.takeIf { it.isNotBlank() }
        ?: banner.subtitle?.takeIf { it.isNotBlank() }
        ?: "WASH50"
    val ctaText = banner.ctaText?.takeIf { it.isNotBlank() } ?: "Book Now"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPromoClick(banner.redirectUrl ?: banner.code ?: "") },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(bannerColors)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left content section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (codeText.startsWith("Use code", ignoreCase = true)) codeText else "Use code: $codeText",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier.clickable { onPromoClick(banner.redirectUrl ?: banner.code ?: "") }
                    ) {
                        Text(
                            text = ctaText,
                            color = Charcoal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Right Image / Appliance graphic section
                Box(
                    modifier = Modifier
                        .size(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (resolvedImgUrl != null && !resolvedImgUrl.contains("photo-1626806787461")) {
                        ApnaNetworkImage(
                            url = resolvedImgUrl,
                            contentDescription = banner.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        StylizedWashingApplianceGraphic(
                            modifier = Modifier.size(82.dp)
                        )
                    }
                }
            }

            // Sub-banner slider dots
            if (midBanners.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    midBanners.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == safeIndex) 6.dp else 4.dp)
                                .background(
                                    color = if (index == safeIndex) Color.White else Color.White.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Footer Promo Banner Slider (Bottom banner matching app themes)
 */
@Composable
fun FooterBannerSlider(
    vm: ApnaDhobiViewModel,
    onPromoClick: (String) -> Unit
) {
    val footerBanners by vm.footerBannersState.collectAsState()
    var activePage by remember { mutableStateOf(0) }

    LaunchedEffect(footerBanners) {
        if (footerBanners.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5000)
            activePage = (activePage + 1) % footerBanners.size
        }
    }

    if (footerBanners.isEmpty()) return

    val safeIndex = activePage.coerceIn(0, (footerBanners.size - 1).coerceAtLeast(0))
    val banner = footerBanners[safeIndex]

    val bannerColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onPromoClick(banner.redirectUrl ?: banner.code ?: "") },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(bannerColors))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = SaffronOrange.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎁", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = banner.title ?: "Special Member Offer",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = banner.subtitle ?: "Exclusive discounts on regular laundry",
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SaffronOrange,
                    modifier = Modifier.clickable { onPromoClick(banner.redirectUrl ?: banner.code ?: "") }
                ) {
                    Text(
                        text = banner.ctaText ?: "Claim",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

data class PromoBannerData(
    val title: String,
    val subtitle: String,
    val code: String,
    val colors: List<Color>,
    val badge: String
)

/**
 * Animated Live Revenue Chart
 * Draws high quality analytical graph with custom gradients
 */
@Composable
fun LiveAnalyticalChart(
    color: Color,
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = if (points.size > 1) width / (points.size - 1) else 0f

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, point ->
            val progressVal = maxVal * (1f - (point / maxVal) * animatedProgress.value)
            val y = progressVal * (height * 0.7f) + (height * 0.15f)
            val x = idx * stepX

            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (idx == points.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        // Fill background area under chart with soft matching gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.4f),
                    color.copy(alpha = 0.05f)
                )
            )
        )

        // Draw line trace
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 6f)
        )

        // Draw node circles
        points.forEachIndexed { idx, point ->
            val y = (1f - (point / maxVal) * animatedProgress.value) * (height * 0.7f) + (height * 0.15f)
            val x = idx * stepX
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(x, y)
            )
            drawCircle(
                color = color,
                radius = 6f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Premium scrollbar modifier for LazyColumn lists
 */
@Composable
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    color: Color = Color.Gray.copy(alpha = 0.7f),
    width: Dp = 6.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 0.8f else 0.35f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbar_list_alpha"
    )

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size

        if (needDrawScrollbar && firstVisibleElementIndex != null && alpha > 0f) {
            val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}

/**
 * Premium scrollbar modifier for LazyVerticalGrid grids
 */
@Composable
fun Modifier.gridVerticalScrollbar(
    state: LazyGridState,
    color: Color = Color.Gray.copy(alpha = 0.7f),
    width: Dp = 6.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 0.8f else 0.35f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbar_grid_alpha"
    )

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size

        if (needDrawScrollbar && firstVisibleElementIndex != null && alpha > 0f) {
            val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}

/**
 * Standard Production Loading Overlay
 */
@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = RoyalBlue)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing request...", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun getLightBgTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RoyalBlue,
    unfocusedBorderColor = Color(0xFFDCDCDC),
    focusedLabelColor = RoyalBlue,
    unfocusedLabelColor = Color.Gray,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = RoyalBlue
)

@Composable
fun getDarkBgTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SaffronOrange,
    unfocusedBorderColor = Color.DarkGray,
    focusedLabelColor = SaffronOrange,
    unfocusedLabelColor = Color.LightGray,
    focusedContainerColor = Charcoal,
    unfocusedContainerColor = Charcoal,
    cursorColor = SaffronOrange
)


