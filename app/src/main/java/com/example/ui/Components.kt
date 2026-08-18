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
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin

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

    val activeBanner = banners[activePage]
    
    // Helper to parse hex strings to Color
    fun parseHex(hex: String): Color {
        return try {
            Color(hex.trim().removePrefix("0x").removePrefix("0X").removePrefix("#").toLong(16))
        } catch (e: Exception) {
            RoyalBlue
        }
    }

    val bannerColors = if (!activeBanner.colors.isNullOrEmpty()) {
        activeBanner.colors.map { parseHex(it) }
    } else {
        listOf(RoyalBlue, SaffronOrange)
    }

    val brandTag = activeBanner.brandName?.takeIf { it.isNotBlank() }
        ?: activeBanner.badge?.takeIf { it.isNotBlank() }
        ?: "APNA DHOBI"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(bannerColors),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onPromoClick(activeBanner.redirectUrl ?: activeBanner.code ?: "") }
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Bubble watermark visuals
        AnimatedWashingBubbles(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = brandTag.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Text(
                text = activeBanner.title ?: "",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 23.sp
            )

            if (!activeBanner.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeBanner.subtitle,
                    color = LightCream,
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }
        }

        // Slide Indicator Dots (● ○ ○)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            banners.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == activePage) 8.dp else 6.dp)
                        .background(
                            color = if (index == activePage) Color.White else Color.White.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
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


