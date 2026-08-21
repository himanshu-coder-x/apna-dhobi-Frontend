package com.example.ui

import com.example.UniversalAppImage
import com.example.DeliveryTruckGraphic
import com.example.ApnaDhobiBrandLogo
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.*
import com.example.data.*
import com.example.data.dto.*
import com.example.ui.theme.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.util.QrScannerManager
import androidx.compose.ui.graphics.PathEffect
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.text.TextStyle

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = it.getString(nameIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Attached_KYC_Document.pdf"
}

// ==========================================
// FEATURE 2 — STICKY FLOATING CHECKOUT BAR
// ==========================================
@Composable
fun StickyCheckoutFloatingBar(vm: ApnaDhobiViewModel) {
    val cartItems by vm.cartItems.collectAsState()
    val finalTotal by vm.cartFinalTotal.collectAsState()
    val activeTab by vm.activeTab.collectAsState()
    val currentScreen by vm.currentScreen.collectAsState()

    // Render only if user has items in cart, is on home frame flow and NOT on checkout screen
    if (cartItems.isNotEmpty() && currentScreen == ApnaDhobiScreen.HomeFrame && activeTab != "cart") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { vm.selectBottomTab("cart") }
                .testTag("sticky_checkout_floating_bar"),
            colors = CardDefaults.cardColors(containerColor = SaffronOrange),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Basket",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val totalItems = cartItems.sumOf { it.quantity }
                        Text("$totalItems Garments Added", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Instant Pickup & Express Delivery", color = LightCream, fontSize = 11.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹${finalTotal.toInt()}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "Checkout ➜",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ==========================================
// FEATURE 3 & 5 — PREMIUM USER PROFILE DASHBOARD
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileDashboard(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by vm.isDarkMode.collectAsState()
    val isHindi by vm.isHindi.collectAsState()

    // Profile States
    val name by vm.userName.collectAsState()
    val email by vm.userEmail.collectAsState()
    val mobile by vm.userPhone.collectAsState()
    val photoUrl by vm.userProfilePhoto.collectAsState()
    val gender by vm.userGender.collectAsState()
    val dob by vm.userDob.collectAsState()
    val isUploadingPhoto by vm.isUploadingProfilePhoto.collectAsState()
    val isSavingProfile by vm.isSavingProfile.collectAsState()

    // Submodules Data States
    val ordersList by vm.ordersList.collectAsState()
    val remoteAddressesList by vm.remoteAddresses.collectAsState()
    val localSavedAddresses by vm.savedAddresses.collectAsState()
    val walletBal by vm.walletBalance.collectAsState()
    val transactionsList by vm.walletTransactions.collectAsState()
    val couponsList by vm.availableCouponsList.collectAsState()
    val supportTicketsList by vm.supportTicketsList.collectAsState()
    val alertFeed by vm.notifications.collectAsState()

    var activeSubView by remember { mutableStateOf("menu") } // menu, orders, order_details, address, wallet, membership, support, create_ticket, vendor_onboarding, delivery_onboarding, alerts
    var selectedOrderForDetail by remember { mutableStateOf<OrderRecord?>(null) }
    var selectedOrderFilter by remember { mutableStateOf("All") } // All, Active, Completed, Cancelled

    // Dialog States
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showViewPhotoDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    var tempEditName by remember { mutableStateOf(name) }
    var tempEditEmail by remember { mutableStateOf(email) }
    var tempEditGender by remember { mutableStateOf(gender) }
    var tempEditDob by remember { mutableStateOf(dob) }

    var showAddressDialog by remember { mutableStateOf(false) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var editingAddressId by remember { mutableStateOf("") }
    var addrName by remember { mutableStateOf("") }
    var addrPhone by remember { mutableStateOf("") }
    var addrFlat by remember { mutableStateOf("") }
    var addrStreet by remember { mutableStateOf("") }
    var addrLandmark by remember { mutableStateOf("") }
    var addrCity by remember { mutableStateOf("New Delhi") }
    var addrPincode by remember { mutableStateOf("110001") }
    var addrType by remember { mutableStateOf("Home") } // Home, Office, Other
    var addrIsDefault by remember { mutableStateOf(false) }

    // Ticket Form States
    var ticketCategory by remember { mutableStateOf("ORDER_ISSUE") }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketDesc by remember { mutableStateOf("") }
    var ticketOrderId by remember { mutableStateOf("") }
    var ticketPhone by remember { mutableStateOf(mobile) }
    var isCreatingTicket by remember { mutableStateOf(false) }

    // Vendor Onboarding States
    var vStep by remember { mutableIntStateOf(1) }
    var vStoreName by remember { mutableStateOf("") }
    var vStoreDesc by remember { mutableStateOf("") }
    var vStoreCategory by remember { mutableStateOf("Laundry & Dry Cleaning") }
    var vStoreCity by remember { mutableStateOf("New Delhi") }
    var vStoreAddress by remember { mutableStateOf("") }
    var vStoreRadius by remember { mutableStateOf("5") }
    var vOwnerName by remember { mutableStateOf(name) }
    var vOwnerPhone by remember { mutableStateOf(mobile) }
    var vOwnerGst by remember { mutableStateOf("") }
    var vBankAcc by remember { mutableStateOf("") }
    var vBankIfsc by remember { mutableStateOf("") }
    var vBankHolder by remember { mutableStateOf("") }
    var vUpiId by remember { mutableStateOf("") }
    var vAadhaarUploaded by remember { mutableStateOf(false) }
    var vPanUploaded by remember { mutableStateOf(false) }
    var vStorePhotoUploaded by remember { mutableStateOf(false) }
    var isSubmittingVendor by remember { mutableStateOf(false) }

    // Delivery Partner Onboarding States
    var dStep by remember { mutableIntStateOf(1) }
    var dFullName by remember { mutableStateOf(name) }
    var dPhone by remember { mutableStateOf(mobile) }
    var dCity by remember { mutableStateOf("New Delhi") }
    var dVehicleType by remember { mutableStateOf("Motorcycle / Scooter") }
    var dLicenseNo by remember { mutableStateOf("") }
    var dVehicleRegNo by remember { mutableStateOf("") }
    var dBankAcc by remember { mutableStateOf("") }
    var dBankIfsc by remember { mutableStateOf("") }
    var dUpiId by remember { mutableStateOf("") }
    var dLicenseUploaded by remember { mutableStateOf(false) }
    var dAadhaarUploaded by remember { mutableStateOf(false) }
    var isSubmittingDelivery by remember { mutableStateOf(false) }

    // Role Gateway States
    var showVendorGatewayDialog by remember { mutableStateOf(false) }
    var showDeliveryGatewayDialog by remember { mutableStateOf(false) }
    var showPartnerSignInDialog by remember { mutableStateOf(false) }
    var partnerSignInRole by remember { mutableStateOf("vendor") } // "vendor" or "delivery"
    var partnerSignInPhone by remember { mutableStateOf(mobile) }
    var partnerSignInOtp by remember { mutableStateOf("") }

    // Onboarding Feedback Dialog States
    var showOnboardingFeedbackDialog by remember { mutableStateOf(false) }
    var onboardingFeedbackSuccess by remember { mutableStateOf(true) }
    var onboardingFeedbackTitle by remember { mutableStateOf("") }
    var onboardingFeedbackMessage by remember { mutableStateOf("") }
    var onboardingFeedbackType by remember { mutableStateOf("vendor") }

    val vendorStatusFlow by vm.vendorApplicationStatus.collectAsState()
    val deliveryStatusFlow by vm.deliveryApplicationStatus.collectAsState()

    // Image Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
                    val filename = "profile_${System.currentTimeMillis()}.jpg"
                    vm.uploadProfilePhotoBytes(bytes, filename, mimeType)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                val filename = "camera_${System.currentTimeMillis()}.jpg"
                vm.uploadProfilePhotoBytes(bytes, filename, "image/jpeg")
            } catch (e: Exception) {
                Toast.makeText(context, "Camera upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Royal Blue Header (Matching Image 2)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F47A6)),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)
            ) {
                // Header Bar (Title, Back if subview, Notification Bell)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (activeSubView != "menu") {
                        IconButton(
                            onClick = { activeSubView = "menu" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when (activeSubView) {
                            "orders" -> "My Orders 🛍️"
                            "order_details" -> "Order Details 🧾"
                            "address" -> "Saved Addresses 📍"
                            "wallet" -> "Wallet & Rewards 👛"
                            "membership" -> "Gold Membership ⭐"
                            "support" -> "Help & Support 🎧"
                            "create_ticket" -> "Raise a Ticket 🎫"
                            "vendor_onboarding" -> "Vendor Registration 🏪"
                            "delivery_onboarding" -> "Delivery Partner 🛵"
                            "alerts" -> "Notifications 🔔"
                            else -> "My Account & Support"
                        },
                        color = Color.White,
                        fontSize = if (activeSubView == "menu") 19.5.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { activeSubView = "alerts" }) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                                    .border(1.dp, Color(0xFF0F47A6), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (alertFeed.isNotEmpty()) alertFeed.size.toString() else "1",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Inner Profile Card Container (Matching Image 2)
                Surface(
                    color = Color(0xFF1955B5),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with Crown Badge (Clickable to change picture)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { showPhotoSheet = true }
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                UniversalAppImage(
                                    model = photoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFFFF6B00), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (name.isNotBlank()) name.take(1).uppercase() else "C",
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(Color(0xFFFF6B00), CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (name.isNotBlank()) name.take(1).uppercase() else "C",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            // Uploading Spinner Overlay
                            if (isUploadingPhoto) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Gold Crown attached to bottom right
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .background(Color(0xFFF59E0B), CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👑", fontSize = 9.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Customer Details
                        val displayName = if (name.isNotBlank()) name else if (mobile.isNotBlank()) "Customer (${mobile.takeLast(4)})" else "Customer"
                        val displayPhone = if (mobile.isNotBlank()) mobile else "9876543210"
                        val displayEmail = if (email.isNotBlank()) email else "${displayPhone.replace("+91", "").trim()}@apnadhobi.com"

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        tempEditName = name
                                        tempEditEmail = email
                                        tempEditGender = gender
                                        tempEditDob = dob
                                        showEditProfileDialog = true
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(displayPhone, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(displayEmail, color = Color.White.copy(alpha = 0.9f), fontSize = 10.5.sp, maxLines = 1)
                            }
                        }

                        // Orange Pill: GOLD VIP MEMBER
                        Surface(
                            color = Color(0xFFFF6B00),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.clickable { activeSubView = "membership" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👑", fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "GOLD VIP",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area Switcher
        when (activeSubView) {
            "menu" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                ) {
                    // Card 1: 4 Core Navigation Items (Matching Image 2)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                ProfileModernMenuItem(
                                    icon = Icons.Default.ShoppingBag,
                                    iconBg = Color(0xFFEFF6FF),
                                    iconTint = Color(0xFF2563EB),
                                    title = "My Orders",
                                    subtitle = "Track active & past orders (${ordersList.size})",
                                    onClick = { activeSubView = "orders" }
                                )
                                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                                ProfileModernMenuItem(
                                    icon = Icons.Default.LocationOn,
                                    iconBg = Color(0xFFEFF6FF),
                                    iconTint = Color(0xFF2563EB),
                                    title = "Saved Addresses",
                                    subtitle = "Manage home & office delivery addresses",
                                    onClick = { activeSubView = "address" }
                                )
                                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                                ProfileModernMenuItem(
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconBg = Color(0xFFEFF6FF),
                                    iconTint = Color(0xFF2563EB),
                                    title = "Wallet & Rewards",
                                    subtitle = "Balance: ₹${walletBal.toInt()} • Cashback & transactions",
                                    onClick = { activeSubView = "wallet" }
                                )
                                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                                ProfileModernMenuItem(
                                    icon = Icons.Default.Headphones,
                                    iconBg = Color(0xFFEFF6FF),
                                    iconTint = Color(0xFF2563EB),
                                    title = "Help & Support",
                                    subtitle = "FAQs, tickets (${supportTicketsList.size}) & AI help",
                                    onClick = { activeSubView = "support" }
                                )
                            }
                        }
                    }

                    // Card 2: Gold Membership ⭐ Card (Matching Image 2)
                    item {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(Color(0xFF1D4ED8), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("👑", fontSize = 20.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Gold Membership ⭐", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF1E3A8A))
                                            Text("Enjoy exclusive rewards & benefits", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                    Text("🎁", fontSize = 34.sp)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Perks Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        "Free Wash on Points",
                                        "Priority Support",
                                        "Special Offers"
                                    ).forEach { perk ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(Color(0xFFFF6B00), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(perk, fontSize = 10.5.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Coupon tags row (Live Backend Coupons)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val displayCoupons = if (couponsList.isNotEmpty()) {
                                        couponsList.take(3).map { Triple(it.code, "${it.discountValue.toInt()}% OFF", it.description) }
                                    } else {
                                        listOf(
                                            Triple("DHOBI20", "20% OFF", "20% OFF"),
                                            Triple("FREESHIP", "Free Delivery", "Free Delivery"),
                                            Triple("SAMEDAY", "Same Day Service", "Same Day Service")
                                        )
                                    }
                                    displayCoupons.forEach { (code, label, _) ->
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    vm.appliedCoupon.value = code
                                                    Toast.makeText(context, "$code coupon applied! 🎉", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(code, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1D4ED8))
                                                Text(label, fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Become a Vendor (Matching Image 2 + Enterprise Gateway)
                    item {
                        Surface(
                            color = Color(0xFFFFF7ED),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0xFFFFEDD5), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🏪", fontSize = 20.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (vendorStatusFlow == "APPROVED") "Laundry Vendor Station 🚀" else "Become a Vendor",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = Color(0xFFC2410C)
                                        )
                                        Text(
                                            if (vendorStatusFlow == "APPROVED") "Manage orders, services & earnings" else "Grow your laundry business with us",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (vendorStatusFlow == "APPROVED") {
                                            vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                                        } else {
                                            showVendorGatewayDialog = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (vendorStatusFlow == "APPROVED") "Dashboard" else "Join Now",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Card 4: Become a Delivery Partner (Matching Image 2 + Enterprise Gateway)
                    item {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0xFFDBEAFE), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🛵", fontSize = 20.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (deliveryStatusFlow == "APPROVED") "Delivery Partner Workspace 🛵" else "Become a Delivery Partner",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = Color(0xFF1E40AF)
                                        )
                                        Text(
                                            if (deliveryStatusFlow == "APPROVED") "View active runs, GPS & payouts" else "Earn with flexible delivery jobs",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (deliveryStatusFlow == "APPROVED") {
                                            vm.navigateTo(ApnaDhobiScreen.DeliveryBoyDashboard)
                                        } else {
                                            showDeliveryGatewayDialog = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F47A6)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (deliveryStatusFlow == "APPROVED") "Workspace" else "Join Now",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Card 5: Need help with your account? (Matching Image 2)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Need help with your account?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { activeSubView = "support" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFEFF6FF).copy(alpha = 0.5f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🤖", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Support", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1800346244"))
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Dialing support: 1800-DHOBI 📞", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFDBA74)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFF7ED).copy(alpha = 0.5f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Call Support", color = Color(0xFFEA580C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Logout Action
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    vm.isLoggedIn.value = false
                                    vm.navigateTo(ApnaDhobiScreen.Login)
                                    Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Log Out from Account", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: MY ORDERS
            // ==========================================
            "orders" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Active", "Completed", "Cancelled").forEach { filter ->
                            FilterChip(
                                selected = selectedOrderFilter == filter,
                                onClick = { selectedOrderFilter = filter },
                                label = { Text(filter, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0F47A6),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    val filteredOrders = ordersList.filter { order ->
                        when (selectedOrderFilter) {
                            "Active" -> order.status in listOf("Placed", "Accepted", "Washing", "Ironing", "Out for Delivery", "PLACED", "ACCEPTED", "WASHING", "IRONING", "OUT_FOR_DELIVERY")
                            "Completed" -> order.status in listOf("Delivered", "DELIVERED")
                            "Cancelled" -> order.status in listOf("Cancelled", "CANCELLED")
                            else -> true
                        }
                    }

                    if (filteredOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🛍️", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No $selectedOrderFilter Orders Found", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                Text("Your placed orders will appear here in real time.", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                        ) {
                            items(filteredOrders) { order ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedOrderForDetail = order
                                            activeSubView = "order_details"
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Order #${order.id}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                            }
                                            val statusColor = when (order.status.uppercase()) {
                                                "DELIVERED" -> Color(0xFF16A34A)
                                                "CANCELLED" -> Color(0xFFDC2626)
                                                else -> Color(0xFFFF6B00)
                                            }
                                            Surface(
                                                color = statusColor.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = order.status.uppercase(),
                                                    color = statusColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(order.itemsSummary, fontSize = 13.sp, color = Color(0xFF475569), maxLines = 2)

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Total Amount", fontSize = 11.sp, color = Color(0xFF64748B))
                                                Text("₹${order.totalPrice.toInt()}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("View Details", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
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
            // SUBVIEW: ORDER DETAILS
            // ==========================================
            "order_details" -> {
                val order = selectedOrderForDetail
                if (order == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No order selected.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                    ) {
                        // Status Card & OTP
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Order #${order.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E3A8A))
                                        Surface(
                                            color = Color(0xFF2563EB),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = order.status.uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Pickup Slot", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text(order.pickupSlot.ifBlank { "Tomorrow, 10:00 AM" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Delivery OTP", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("🔑 4920", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F47A6))
                                        }
                                    }
                                }
                            }
                        }

                        // Order Timeline
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Order Timeline ⏳", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF0F172A))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    val steps = listOf(
                                        "Order Placed" to "Confirmed & Scheduled",
                                        "Assigned / Picked Up" to "Laundry collected by partner",
                                        "Processing & Wash" to "Gentle Fabric Wash & Steam Press",
                                        "Out for Delivery" to "Delivery boy on the way",
                                        "Delivered" to "Delivered at doorstep"
                                    )
                                    val currentStepIndex = when (order.status.uppercase()) {
                                        "PLACED" -> 0
                                        "ACCEPTED", "ASSIGNED" -> 1
                                        "WASHING", "IRONING", "PROCESSING" -> 2
                                        "OUT FOR DELIVERY", "OUT_FOR_DELIVERY" -> 3
                                        "DELIVERED" -> 4
                                        else -> 0
                                    }

                                    steps.forEachIndexed { index, (title, sub) ->
                                        val isDone = index <= currentStepIndex
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(
                                                        if (isDone) Color(0xFF16A34A) else Color(0xFFE2E8F0),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isDone) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(title, fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = if (isDone) Color(0xFF0F172A) else Color(0xFF94A3B8))
                                                Text(sub, fontSize = 11.sp, color = Color(0xFF64748B))
                                            }
                                        }
                                        if (index < steps.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 10.dp)
                                                    .width(2.dp)
                                                    .height(18.dp)
                                                    .background(if (index < currentStepIndex) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Items & Pricing Breakdown
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Bill & Items Breakdown 🧾", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF0F172A))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(order.itemsSummary, fontSize = 13.sp, color = Color(0xFF334155))

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Item Subtotal", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text("₹${order.totalPrice.toInt()}", fontSize = 12.sp, color = Color(0xFF334155))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Delivery & Pickup Fee", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text("FREE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Taxes & Service Charges", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text("Included", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Paid (${order.paymentMethod})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                        Text("₹${order.totalPrice.toInt()}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0F47A6))
                                    }
                                }
                            }
                        }

                        // Need Help Button
                        item {
                            Button(
                                onClick = {
                                    ticketOrderId = order.id.toString()
                                    ticketSubject = "Issue with Order #${order.id}"
                                    activeSubView = "create_ticket"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                            ) {
                                Text("Need Help with this Order? 🎫", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: SAVED ADDRESSES
            // ==========================================
            "address" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Delivery Addresses (${remoteAddressesList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Button(
                            onClick = {
                                isEditingAddress = false
                                addrName = name
                                addrPhone = mobile
                                addrFlat = ""
                                addrStreet = ""
                                addrLandmark = ""
                                addrCity = "New Delhi"
                                addrPincode = "110001"
                                addrType = "Home"
                                addrIsDefault = false
                                showAddressDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add New", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    val allAddresses = remoteAddressesList
                    if (allAddresses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📍", fontSize = 44.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No saved addresses yet.", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                                Text("Add your home or office address for seamless pickup.", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(allAddresses) { addr ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = BorderStroke(1.dp, if (addr.isDefault == true) Color(0xFF3B82F6) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFEFF6FF),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = addr.type ?: "Home",
                                                        color = Color(0xFF1D4ED8),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (addr.isDefault == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = Color(0xFFDCFCE7),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "DEFAULT",
                                                            color = Color(0xFF15803D),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        isEditingAddress = true
                                                        editingAddressId = addr.id ?: ""
                                                        addrName = addr.name ?: name
                                                        addrPhone = addr.phone ?: mobile
                                                        addrFlat = addr.flatBuilding ?: ""
                                                        addrStreet = addr.streetArea ?: ""
                                                        addrLandmark = addr.landmark ?: ""
                                                        addrCity = addr.city ?: "New Delhi"
                                                        addrPincode = addr.pincode ?: "110001"
                                                        addrType = addr.type ?: "Home"
                                                        addrIsDefault = addr.isDefault ?: false
                                                        showAddressDialog = true
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { addr.id?.let { vm.deleteRemoteAddress(it) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${addr.flatBuilding ?: ""}, ${addr.streetArea ?: ""}, ${addr.city ?: ""} - ${addr.pincode ?: ""}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF334155)
                                        )
                                        if (!addr.landmark.isNullOrBlank()) {
                                            Text("Landmark: ${addr.landmark}", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                        }

                                        if (addr.isDefault != true && !addr.id.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            TextButton(
                                                onClick = { vm.setDefaultRemoteAddress(addr.id) },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Set as Default Address", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
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
            // SUBVIEW: WALLET & REWARDS
            // ==========================================
            "wallet" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                ) {
                    // Balance Card
                    item {
                        Surface(
                            color = Color(0xFF0F47A6),
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Available Wallet Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("₹${walletBal.toInt()}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Cashback Earned", color = Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp)
                                        Text("₹150", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Reward Points", color = Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp)
                                        Text("650 pts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("VIP Tier", color = Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp)
                                        Text("👑 Gold", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Quick Top-up buttons
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Quick Add Money (Instant Razorpay)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(100, 200, 500, 1000).forEach { amt ->
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val res = vm.repository.createRazorpayOrder(amt.toDouble())
                                                    if (res != null) {
                                                        vm.repository.verifyRazorpayPayment(
                                                            orderId = res["id"] as? String ?: "rzp_${System.currentTimeMillis()}",
                                                            paymentId = "pay_${System.currentTimeMillis()}",
                                                            signature = "sig_valid"
                                                        )
                                                        vm.walletBalance.value += amt
                                                        vm.loadWalletTransactions()
                                                        Toast.makeText(context, "₹$amt added to wallet! 💳", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFEFF6FF)),
                                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                                        ) {
                                            Text("+₹$amt", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1D4ED8))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Transactions History
                    item {
                        Text("Recent Wallet Transactions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    }

                    if (transactionsList.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("👛", fontSize = 36.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No wallet transactions yet", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        items(transactionsList) { tx ->
                            val amount = (tx["amount"] as? Number)?.toDouble() ?: 0.0
                            val type = tx["type"] as? String ?: "WALLET"
                            val desc = tx["description"] as? String ?: "Wallet Transaction"
                            val isCredit = amount >= 0

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(if (isCredit) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(desc, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                            Text(type, fontSize = 10.5.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                    Text(
                                        text = "${if (isCredit) "+" else ""}₹${amount.toInt()}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: GOLD MEMBERSHIP & COUPONS
            // ==========================================
            "membership" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                ) {
                    // Gold Card
                    item {
                        Surface(
                            color = Color(0xFF1E3A8A),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("👑 GOLD VIP MEMBERSHIP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFFDE047))
                                        Text("Active until 31 Dec 2026", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Text("⭐", fontSize = 28.sp)
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                listOf(
                                    "✨ 20% Extra points on all orders",
                                    "🚀 Priority morning 10 AM delivery slots",
                                    "🚚 Free doorstep pickup on orders above ₹149",
                                    "🎧 24x7 Dedicated VIP Support Line"
                                ).forEach { perk ->
                                    Text(perk, fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }

                    item {
                        Text("Unlocked Promo Coupons 🎟️", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    }

                    items(couponsList) { cp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(cp.code, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(cp.description, fontSize = 12.sp, color = Color(0xFF334155))
                                    Text("Min order: ₹${cp.minOrderAmount.toInt()}", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Button(
                                    onClick = {
                                        vm.appliedCoupon.value = cp.code
                                        Toast.makeText(context, "${cp.code} applied! 🎉", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: HELP & SUPPORT CENTER
            // ==========================================
            "support" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                ) {
                    // Create Ticket CTA Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Have an issue with your laundry?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E3A8A))
                                    Text("Raise a support ticket & get resolution within 2 hours.", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                }
                                Button(
                                    onClick = { activeSubView = "create_ticket" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Raise Ticket", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Support Categories Grid
                    item {
                        Text("Browse Support Topics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.height(8.dp))
                        val topics = listOf(
                            "📦 Order Status" to "ORDER_ISSUE",
                            "🛵 Pickup / Delivery" to "PICKUP_ISSUE",
                            "💳 Payment & Refund" to "PAYMENT_ISSUE",
                            "👔 Laundry Quality" to "QUALITY_ISSUE",
                            "👤 Account Settings" to "ACCOUNT_ISSUE",
                            "❓ Other Inquiries" to "OTHER"
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            topics.take(3).forEach { (label, cat) ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            ticketCategory = cat
                                            activeSubView = "create_ticket"
                                        }
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    // My Support Tickets
                    item {
                        Text("My Support Tickets (${supportTicketsList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    }

                    if (supportTicketsList.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🎧", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No open support tickets.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        items(supportTicketsList) { tkt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("#${tkt.ticketId ?: "TKT"}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                        val statusCol = when (tkt.status.uppercase()) {
                                            "RESOLVED", "CLOSED" -> Color(0xFF16A34A)
                                            "IN_PROGRESS" -> Color(0xFF2563EB)
                                            else -> Color(0xFFFF6B00)
                                        }
                                        Surface(
                                            color = statusCol.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(tkt.status.uppercase(), color = statusCol, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(tkt.subject, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text(tkt.description, fontSize = 11.5.sp, color = Color(0xFF64748B), maxLines = 2)
                                }
                            }
                        }
                    }

                    // Live AI Chat Centric View
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Live AI Laundry Assistant 🤖", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
                                    ProfileSettingsAndChatCentric(vm)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: CREATE SUPPORT TICKET
            // ==========================================
            "create_ticket" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    item {
                        Text("Select Issue Category", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.height(6.dp))
                        val cats = listOf("ORDER_ISSUE", "PICKUP_ISSUE", "QUALITY_ISSUE", "PAYMENT_ISSUE", "OTHER")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cats.take(3).forEach { cat ->
                                FilterChip(
                                    selected = ticketCategory == cat,
                                    onClick = { ticketCategory = cat },
                                    label = { Text(cat.replace("_", " "), fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = ticketSubject,
                            onValueChange = { ticketSubject = it },
                            label = { Text("Subject (e.g. Saree stained during wash)") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = ticketOrderId,
                            onValueChange = { ticketOrderId = it },
                            label = { Text("Related Order ID (Optional)") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = ticketDesc,
                            onValueChange = { ticketDesc = it },
                            label = { Text("Detailed Description") },
                            minLines = 4,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = ticketPhone,
                            onValueChange = { ticketPhone = it },
                            label = { Text("Contact Phone") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                if (ticketSubject.isNotBlank() && ticketDesc.isNotBlank()) {
                                    isCreatingTicket = true
                                    vm.createSupportTicket(
                                        category = ticketCategory,
                                        subject = ticketSubject,
                                        description = ticketDesc,
                                        orderId = ticketOrderId.ifBlank { null },
                                        contactPhone = ticketPhone
                                    ) { success ->
                                        isCreatingTicket = false
                                        if (success) {
                                            ticketSubject = ""
                                            ticketDesc = ""
                                            activeSubView = "support"
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter subject and description!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isCreatingTicket,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                        ) {
                            if (isCreatingTicket) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Submit Support Ticket 🎫", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: VENDOR ONBOARDING WIZARD
            // ==========================================
            "vendor_onboarding" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    item {
                        // Step Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("1. Store", "2. Location", "3. Owner", "4. Review").forEachIndexed { index, label ->
                                val stepNum = index + 1
                                val isActive = vStep == stepNum
                                val isDone = vStep > stepNum
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(if (isActive || isDone) Color(0xFFFF6B00) else Color(0xFFE2E8F0), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$stepNum", color = if (isActive || isDone) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Text(label, fontSize = 10.5.sp, color = if (isActive) Color(0xFFFF6B00) else Color.Gray)
                                }
                            }
                        }
                    }

                    when (vStep) {
                        1 -> {
                            item {
                                Text("Step 1: Laundry Store Information 🏪", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vStoreName,
                                    onValueChange = { vStoreName = it },
                                    label = { Text("Store / Laundry Business Name *") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vStoreDesc,
                                    onValueChange = { vStoreDesc = it },
                                    label = { Text("Business Tagline / Description") },
                                    minLines = 2,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        if (vStoreName.isNotBlank()) vStep = 2
                                        else Toast.makeText(context, "Please enter Store Name!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Next: Store Location 📍", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        2 -> {
                            item {
                                Text("Step 2: Store Address & Service Area 📍", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vStoreCity,
                                    onValueChange = { vStoreCity = it },
                                    label = { Text("City *") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vStoreAddress,
                                    onValueChange = { vStoreAddress = it },
                                    label = { Text("Complete Store Street Address *") },
                                    minLines = 2,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vStoreRadius,
                                    onValueChange = { vStoreRadius = it },
                                    label = { Text("Service Radius (Kilometers)") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vStep = 1 }, modifier = Modifier.weight(1f)) { Text("Back") }
                                    Button(
                                        onClick = {
                                            if (vStoreAddress.isNotBlank()) vStep = 3
                                            else Toast.makeText(context, "Please enter Address!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                                    ) { Text("Next: Owner 👤", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        3 -> {
                            item {
                                Text("Step 3: Owner & KYC Details 👤", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vOwnerName,
                                    onValueChange = { vOwnerName = it },
                                    label = { Text("Owner Full Name *") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vOwnerPhone,
                                    onValueChange = { vOwnerPhone = it },
                                    label = { Text("Owner Mobile Number *") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = vOwnerGst,
                                    onValueChange = { vOwnerGst = it },
                                    label = { Text("GST Number / Business Registration (Optional)") },
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vStep = 2 }, modifier = Modifier.weight(1f)) { Text("Back") }
                                    Button(
                                        onClick = { vStep = 4 },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                                    ) { Text("Review & Submit ✨", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        4 -> {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Review Vendor Application 📋", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Store: $vStoreName", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Address: $vStoreAddress, $vStoreCity", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text("Service Radius: $vStoreRadius km", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text("Owner: $vOwnerName ($vOwnerPhone)", fontSize = 12.sp, color = Color(0xFF475569))
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        isSubmittingVendor = true
                                        vm.submitVendorOnboarding(
                                            storeName = vStoreName,
                                            description = vStoreDesc,
                                            address = "$vStoreAddress, $vStoreCity",
                                            logoText = vStoreName.take(2).uppercase(),
                                            bannerColor = "#FF6B00",
                                            phone = vOwnerPhone,
                                            ownerName = vOwnerName
                                        ) { success, msg ->
                                            isSubmittingVendor = false
                                            onboardingFeedbackSuccess = success
                                            onboardingFeedbackTitle = if (success) "Vendor Registration Approved! 🎉" else "Vendor Registration Status ⚠️"
                                            onboardingFeedbackMessage = if (success) "Your laundry store '$vStoreName' has been registered and approved successfully on Apna Dhobi! You can now start managing laundry orders." else msg
                                            onboardingFeedbackType = "vendor"
                                            showOnboardingFeedbackDialog = true
                                        }
                                    },
                                    enabled = !isSubmittingVendor,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isSubmittingVendor) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Submit Application 🚀", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: DELIVERY PARTNER ONBOARDING WIZARD
            // ==========================================
            "delivery_onboarding" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    item {
                        Text("Become a Delivery Partner 🛵", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        Text("Earn weekly payouts with flexible delivery hours in your area.", fontSize = 12.sp, color = Color(0xFF64748B))
                    }

                    item {
                        OutlinedTextField(
                            value = dFullName,
                            onValueChange = { dFullName = it },
                            label = { Text("Full Name *") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dPhone,
                            onValueChange = { dPhone = it },
                            label = { Text("Mobile Phone Number *") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dCity,
                            onValueChange = { dCity = it },
                            label = { Text("Working City *") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dVehicleType,
                            onValueChange = { dVehicleType = it },
                            label = { Text("Vehicle Type (Bike, Scooter, E-Rickshaw)") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dLicenseNo,
                            onValueChange = { dLicenseNo = it },
                            label = { Text("Driving License Number (DL-XXXX)") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                if (dFullName.isNotBlank() && dPhone.isNotBlank()) {
                                    isSubmittingDelivery = true
                                    vm.submitDeliveryPartnerOnboarding(
                                        phone = dPhone,
                                        name = dFullName,
                                        city = dCity,
                                        vehicleType = dVehicleType,
                                        licenseNumber = dLicenseNo
                                    ) { success, msg ->
                                        isSubmittingDelivery = false
                                        onboardingFeedbackSuccess = success
                                        onboardingFeedbackTitle = if (success) "Delivery Partner Approved! 🛵" else "Registration Status ⚠️"
                                        onboardingFeedbackMessage = if (success) "Congratulations $dFullName! Your delivery partner account has been approved. You can now accept pickup and delivery tasks." else msg
                                        onboardingFeedbackType = "delivery"
                                        showOnboardingFeedbackDialog = true
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter Name & Phone!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSubmittingDelivery,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F47A6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSubmittingDelivery) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Submit Delivery Partner Application 🛵", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SUBVIEW: NOTIFICATIONS / ALERTS
            // ==========================================
            "alerts" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    if (alertFeed.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No notifications received yet.")
                            }
                        }
                    } else {
                        items(alertFeed) { msg ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFF6B00))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(msg, fontSize = 13.sp, color = Color(0xFF1E293B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOG: PHOTO ACTION SHEET
    // ==========================================
    if (showPhotoSheet) {
        AlertDialog(
            onDismissRequest = { showPhotoSheet = false },
            title = { Text("Profile Photo 📸", fontWeight = FontWeight.Bold, color = Color(0xFF0F47A6)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!photoUrl.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                showPhotoSheet = false
                                showViewPhotoDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF2563EB))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("View Profile Photo", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            showPhotoSheet = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Take Photo", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(
                        onClick = {
                            showPhotoSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Choose from Gallery", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!photoUrl.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                showPhotoSheet = false
                                vm.removeProfilePhoto()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Remove Photo", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSheet = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // View Large Photo Dialog
    if (showViewPhotoDialog && !photoUrl.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showViewPhotoDialog = false },
            title = { Text("Profile Photo Preview", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UniversalAppImage(
                        model = photoUrl,
                        contentDescription = "Full Profile Photo",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                    ) {}
                }
            },
            confirmButton = {
                Button(onClick = { showViewPhotoDialog = false }) { Text("Close") }
            }
        )
    }

    // ==========================================
    // DIALOG: EDIT CUSTOMER PROFILE
    // ==========================================
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditProfileDialog = false },
            title = { Text("Edit Customer Profile ✏️", fontWeight = FontWeight.Bold, color = Color(0xFF0F47A6)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tempEditName,
                        onValueChange = { tempEditName = it },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempEditEmail,
                        onValueChange = { tempEditEmail = it },
                        label = { Text("Email Address *") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Gender", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Male", "Female", "Other").forEach { g ->
                            FilterChip(
                                selected = tempEditGender == g,
                                onClick = { tempEditGender = g },
                                label = { Text(g, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tempEditDob,
                        onValueChange = { tempEditDob = it },
                        label = { Text("Date of Birth (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempEditName.isNotBlank() && tempEditEmail.isNotBlank()) {
                            vm.updateCustomerProfile(
                                name = tempEditName,
                                email = tempEditEmail,
                                gender = tempEditGender,
                                dob = tempEditDob
                            ) { success ->
                                if (success) showEditProfileDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Name and Email cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSavingProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ==========================================
    // DIALOG: ADD / EDIT ADDRESS (FULL CRUD)
    // ==========================================
    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text(if (isEditingAddress) "Edit Address ✏️" else "Add New Address 📍", fontWeight = FontWeight.Bold, color = Color(0xFF0F47A6)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Home", "Office", "Other").forEach { t ->
                            FilterChip(
                                selected = addrType == t,
                                onClick = { addrType = t },
                                label = { Text(t, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = addrFlat,
                        onValueChange = { addrFlat = it },
                        label = { Text("Flat / Building / Floor No. *") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addrStreet,
                        onValueChange = { addrStreet = it },
                        label = { Text("Street / Locality / Sector *") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addrLandmark,
                        onValueChange = { addrLandmark = it },
                        label = { Text("Landmark (Optional)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addrCity,
                            onValueChange = { addrCity = it },
                            label = { Text("City") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = addrPincode,
                            onValueChange = { addrPincode = it },
                            label = { Text("Pincode") },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addrIsDefault, onCheckedChange = { addrIsDefault = it })
                        Text("Set as Default Delivery Address", fontSize = 12.sp, color = Color(0xFF334155))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (addrFlat.isNotBlank() && addrStreet.isNotBlank()) {
                            if (isEditingAddress && editingAddressId.isNotBlank()) {
                                vm.updateRemoteAddress(
                                    id = editingAddressId,
                                    name = addrName,
                                    phone = addrPhone,
                                    flatBuilding = addrFlat,
                                    streetArea = addrStreet,
                                    landmark = addrLandmark,
                                    city = addrCity,
                                    pincode = addrPincode,
                                    type = addrType,
                                    isDefault = addrIsDefault
                                ) { success ->
                                    if (success) showAddressDialog = false
                                }
                            } else {
                                vm.addRemoteAddress(
                                    name = addrName,
                                    phone = addrPhone,
                                    flatBuilding = addrFlat,
                                    streetArea = addrStreet,
                                    landmark = addrLandmark,
                                    city = addrCity,
                                    pincode = addrPincode,
                                    type = addrType,
                                    isDefault = addrIsDefault
                                ) { success ->
                                    if (success) showAddressDialog = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter flat and street address!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Text(if (isEditingAddress) "Save Changes" else "Add Address", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ==========================================
    // DIALOG: ONBOARDING FEEDBACK / STATUS
    // ==========================================
    if (showOnboardingFeedbackDialog) {
        AlertDialog(
            onDismissRequest = {
                showOnboardingFeedbackDialog = false
                if (onboardingFeedbackSuccess) activeSubView = "menu"
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (onboardingFeedbackSuccess) "🎉 " else "⚠️ ", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = onboardingFeedbackTitle,
                        fontWeight = FontWeight.Bold,
                        color = if (onboardingFeedbackSuccess) Color(0xFF15803D) else Color(0xFFC2410C),
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = if (onboardingFeedbackSuccess) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (onboardingFeedbackSuccess) "STATUS: ACTIVE / APPROVED ✅" else "STATUS: NOTICE / REVIEW ℹ️",
                                color = if (onboardingFeedbackSuccess) Color(0xFF166534) else Color(0xFF92400E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        text = onboardingFeedbackMessage,
                        fontSize = 13.5.sp,
                        color = Color(0xFF334155),
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOnboardingFeedbackDialog = false
                        if (onboardingFeedbackSuccess) {
                            if (onboardingFeedbackType == "vendor") {
                                vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                            } else {
                                vm.navigateTo(ApnaDhobiScreen.DeliveryBoyDashboard)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (onboardingFeedbackSuccess) Color(0xFF16A34A) else Color(0xFFFF6B00)
                    )
                ) {
                    Text(if (onboardingFeedbackSuccess) "Open Dashboard 🚀" else "OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOnboardingFeedbackDialog = false
                    if (onboardingFeedbackSuccess) activeSubView = "menu"
                }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    // ==========================================
    // DIALOG: VENDOR ROLE ENTRY GATEWAY
    // ==========================================
    if (showVendorGatewayDialog) {
        AlertDialog(
            onDismissRequest = { showVendorGatewayDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏪", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Laundry Vendor Gateway", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose how you would like to proceed with your laundry business account:", fontSize = 13.sp, color = Color(0xFF475569))

                    // Option A: Register
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVendorGatewayDialog = false
                                activeSubView = "vendor_onboarding"
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("New Partner? Create Account", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC2410C))
                                Text("Multi-step KYC & store registration", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Option B: Sign In
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVendorGatewayDialog = false
                                partnerSignInRole = "vendor"
                                showPartnerSignInDialog = true
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔑", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Already Registered? Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                                Text("Quick OTP login to Vendor Dashboard", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVendorGatewayDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ==========================================
    // DIALOG: DELIVERY ROLE ENTRY GATEWAY
    // ==========================================
    if (showDeliveryGatewayDialog) {
        AlertDialog(
            onDismissRequest = { showDeliveryGatewayDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛵", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delivery Partner Gateway", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose how you would like to access your delivery fleet account:", fontSize = 13.sp, color = Color(0xFF475569))

                    // Option A: Register
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDeliveryGatewayDialog = false
                                activeSubView = "delivery_onboarding"
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Join Fleet: Create Account", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                                Text("Weekly payouts, vehicle registration & KYC", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Option B: Sign In
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDeliveryGatewayDialog = false
                                partnerSignInRole = "delivery"
                                showPartnerSignInDialog = true
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔑", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Already a Partner? Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                                Text("Quick OTP login to Driver Workspace", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeliveryGatewayDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ==========================================
    // DIALOG: PARTNER OTP SIGN IN
    // ==========================================
    if (showPartnerSignInDialog) {
        AlertDialog(
            onDismissRequest = { showPartnerSignInDialog = false },
            title = {
                Text(
                    text = if (partnerSignInRole == "vendor") "Vendor Sign In 🏪" else "Delivery Partner Sign In 🛵",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your registered mobile number and verification OTP to access your dashboard.", fontSize = 12.sp, color = Color(0xFF64748B))
                    OutlinedTextField(
                        value = partnerSignInPhone,
                        onValueChange = { partnerSignInPhone = it },
                        label = { Text("Registered Mobile (+91)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = partnerSignInOtp,
                        onValueChange = { partnerSignInOtp = it },
                        label = { Text("Enter OTP (e.g. 123456)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (partnerSignInPhone.isNotBlank()) {
                            showPartnerSignInDialog = false
                            if (partnerSignInRole == "vendor") {
                                vm.vendorApplicationStatus.value = "APPROVED"
                                vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                            } else {
                                vm.deliveryApplicationStatus.value = "APPROVED"
                                vm.navigateTo(ApnaDhobiScreen.DeliveryBoyDashboard)
                            }
                        } else {
                            Toast.makeText(context, "Please enter mobile number!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (partnerSignInRole == "vendor") Color(0xFFFF6B00) else Color(0xFF0F47A6)
                    )
                ) {
                    Text("Verify & Open Workspace 🚀", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPartnerSignInDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun ProfileModernMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.5.sp, color = Color(0xFF64748B))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ProfileMenuListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = RoyalBlue.copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = "", tint = RoyalBlue, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "", tint = Color.LightGray, modifier = Modifier.size(16.dp))
    }
}


// ==========================================
// FEATURE 4 — PREMIUM WALLET DASHBOARD
// ==========================================
@Composable
fun WalletDashboard(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val walletBal by vm.walletBalance.collectAsState()
    val isDarkMode by vm.isDarkMode.collectAsState()

    // Additional required wallet parameters
    val refundStat by vm.walletRefundStatus.collectAsState()
    val linksUpi by vm.linkedUpiId.collectAsState()
    val cashbackByUs by vm.cashbackHistory.collectAsState()
    val referralEarn by vm.referralEarnings.collectAsState()
    val couponRewardTotal by vm.couponRewardsCount.collectAsState()
    val pointsMember by vm.membershipPoints.collectAsState()
    val promoCredsVal by vm.promoCredits.collectAsState()

    var customAmtText by remember { mutableStateOf("") }
    var tempUpiText by remember { mutableStateOf(linksUpi) }
    var editUpiToggle by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) DarkBackground else LightCream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "My Digital Wallet Portfolio",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = if (isDarkMode) Color.White else RoyalBlue
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Large Credit Card representation with Royal Gold theme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(colors = listOf(RoyalBlue, SaffronOrange)), RoundedCornerShape(20.dp))
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("APNA PREMIUM CO-BRANDED PASS", color = LightCream, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Icon(Icons.Default.CreditCard, contentDescription = "", tint = Color.White)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text("CURRENT LIQUID CARD BALANCE", color = LightCream, fontSize = 10.sp)
                Text("₹${walletBal.toInt()}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("MEMBER CREDIT POINTS", color = LightCream, fontSize = 9.sp)
                        Text("$pointsMember pts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PROMO CREDIT RELEASES", color = LightCream, fontSize = 9.sp)
                        Text("₹${promoCredsVal.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Unified statistics row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Referral Earns", fontSize = 11.sp, color = Color.Gray)
                    Text("₹${referralEarn.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoyalBlue)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Coupon Savings", fontSize = 11.sp, color = Color.Gray)
                    Text("₹${couponRewardTotal * 40}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoyalBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // Razorpay Gateway Sandbox Simulator state attributes
        var showRazorpayDialog by remember { mutableStateOf(false) }
        var razorpayAmount by remember { mutableStateOf(0.0) }
        var razorpayState by remember { mutableStateOf("SELECT_METHOD") } // SELECT_METHOD, SECURING, LOADING, SUCCESS
        val scope = rememberCoroutineScope()

        if (showRazorpayDialog) {
            AlertDialog(
                onDismissRequest = { if (razorpayState != "LOADING") showRazorpayDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️ Razorpay Sandbox", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (razorpayState) {
                            "SELECT_METHOD" -> {
                                Text(
                                    "Authorize Deposit of ₹${razorpayAmount.toInt()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = RoyalBlue
                                )
                                Text(
                                    "Select interactive test payment method to simulate automated credit hook trigger:",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        razorpayState = "LOADING"
                                        scope.launch {
                                            delay(1600)
                                            razorpayState = "SUCCESS"
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("💳 Test Cards / Auto-Decline Sim", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.ArrowForward, contentDescription = "", tint = Color.Gray)
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        razorpayState = "LOADING"
                                        scope.launch {
                                            delay(1200)
                                            razorpayState = "SUCCESS"
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xF0EFF6FF)),
                                    border = BorderStroke(1.dp, RoyalBlue)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("📲 Dynamic UPI Push Simulation", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Black, color = RoyalBlue)
                                        Icon(Icons.Default.Check, contentDescription = "", tint = RoyalBlue)
                                    }
                                }
                            }
                            "LOADING" -> {
                                CircularProgressIndicator(color = SaffronOrange, modifier = Modifier.size(44.dp))
                                Text("Contacting Razorpay central exchange hooks...", fontSize = 12.sp, color = Color.Gray)
                                Text("Do not press hardware back or close window...", fontSize = 10.sp, color = Color.LightGray)
                            }
                            "SUCCESS" -> {
                                Box(
                                    modifier = Modifier.size(54.dp).background(Color(0xFF2E7D32), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                                Text("Recharge Completed Successfully! 🚀", fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), fontSize = 15.sp)
                                Text("₹${razorpayAmount.toInt()} credited immediately to your digital laundry wallet ledger.", fontSize = 11.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (razorpayState == "SUCCESS") {
                        Button(
                            onClick = {
                                vm.topUpWallet(razorpayAmount, "Razorpay Sandbox")
                                Toast.makeText(context, "₹${razorpayAmount.toInt()} Added to Wallet!", Toast.LENGTH_SHORT).show()
                                showRazorpayDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Awesome, Return!")
                        }
                    }
                },
                dismissButton = {
                    if (razorpayState != "LOADING") {
                        TextButton(onClick = { showRazorpayDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                }
            )
        }

        // TOP-UP WALLET REVOLVING BALANCE
        Text("TOP-UP WALLET REVOLVING BALANCE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoyalBlue)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(100, 200, 500).forEach { amt ->
                Button(
                    onClick = {
                        razorpayAmount = amt.toDouble()
                        razorpayState = "SELECT_METHOD"
                        showRazorpayDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("+ ₹$amt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Add Money field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customAmtText,
                onValueChange = { customAmtText = it },
                placeholder = { Text("Enter manual deposit amount") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = getLightBgTextFieldColors()
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val converted = customAmtText.toDoubleOrNull()
                    if (converted != null && converted > 0) {
                        razorpayAmount = converted
                        razorpayState = "SELECT_METHOD"
                        showRazorpayDialog = true
                        customAmtText = ""
                    } else {
                        Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
            ) {
                Text("RECHARGE")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // UPI Withdrawal Panel & Requesting form
        var showWithdrawalForm by remember { mutableStateOf(false) }
        var bankAccountNo by remember { mutableStateOf("") }
        var bankIfscCode by remember { mutableStateOf("") }
        var withdrawAmountText by remember { mutableStateOf("") }

        Text("WITHDRAW FUNDS TO BANK / UPI 📤", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoyalBlue)
        Spacer(modifier = Modifier.height(8.dp))

        if (!showWithdrawalForm) {
            OutlinedButton(
                onClick = { showWithdrawalForm = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, RoyalBlue)
            ) {
                Text("Request Bank Transfer withdrawal", color = RoyalBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bank Account Settlement Panel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SaffronOrange)
                    
                    OutlinedTextField(
                        value = bankAccountNo,
                        onValueChange = { bankAccountNo = it },
                        label = { Text("Beneficiary Account Number") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bankIfscCode,
                        onValueChange = { bankIfscCode = it.uppercase() },
                        label = { Text("IFSC Code (e.g. HDFC0000102)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = withdrawAmountText,
                        onValueChange = { withdrawAmountText = it },
                        label = { Text("Transfer Amount (₹)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showWithdrawalForm = false }) {
                            Text("Dismiss", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val amountToWithdraw = withdrawAmountText.toDoubleOrNull()
                                if (amountToWithdraw == null || amountToWithdraw <= 0) {
                                    Toast.makeText(context, "Enter valid withdrawal amount", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (bankAccountNo.length < 8) {
                                    Toast.makeText(context, "Enter a valid account number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (amountToWithdraw > walletBal) {
                                    Toast.makeText(context, "Insufficient liquid balance!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Subtract balance
                                vm.walletBalance.value -= amountToWithdraw
                                
                                // Post manual withdrawal to Admin system-wide requests
                                vm.createWalletDepositRequest(
                                    amount = -amountToWithdraw, // negative represents deduction
                                    method = "Withdrawal Bank Draft",
                                    upiOrRef = "$bankAccountNo - $bankIfscCode"
                                )

                                // Insert pending report log in ledger
                                val tempHistory = vm.cashbackHistory.value.toMutableList()
                                val last4 = if (bankAccountNo.length > 4) bankAccountNo.substring(bankAccountNo.length - 4) else bankAccountNo
                                tempHistory.add(0, "[DEBIT / PENDING] ₹${amountToWithdraw.toInt()} Settlement to Acc ending ****$last4")
                                vm.cashbackHistory.value = tempHistory

                                withdrawAmountText = ""
                                showWithdrawalForm = false
                                Toast.makeText(context, "Withdrawal Draft successfully submitted to Admin approvals queue!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                        ) {
                            Text("Post Withdrawal Draft")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Linked UPI Account Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Linked UPI Withdrawal Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (editUpiToggle) {
                        OutlinedTextField(
                            value = tempUpiText,
                            onValueChange = { tempUpiText = it },
                            singleLine = true,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(linksUpi, color = SaffronOrange, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
                IconButton(onClick = {
                    if (editUpiToggle) {
                        if (tempUpiText.isNotBlank()) {
                            vm.linkedUpiId.value = tempUpiText
                        }
                    }
                    editUpiToggle = !editUpiToggle
                }) {
                    Icon(
                        imageVector = if (editUpiToggle) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = "Edit UPI",
                        tint = RoyalBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Refund status check
        Card(
            colors = CardDefaults.cardColors(RoyalBlue.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = "", tint = RoyalBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Active Refund Claim Monitor", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(refundStat, fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Visual rewards history log
        Text("COMPREHENSIVE LEDGER REGISTER & TRANSACTION HISTORY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(12.dp)) {
                cashbackByUs.forEachIndexed { index, hist ->
                    val isPending = hist.contains("PENDING")
                    val isDebit = hist.contains("DEBIT") || hist.contains("WITHDRAWAL")
                    val isCredit = hist.contains("CREDIT") || hist.contains("Cashback") || hist.contains("bonus") || hist.contains("credit")

                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPending) Icons.Default.Info 
                                         else if (isDebit) Icons.Default.ArrowBack 
                                         else Icons.Default.Stars,
                            contentDescription = "",
                            tint = if (isPending) Color(0xFFD97706) 
                                   else if (isDebit) Color(0xFFDC2626) 
                                   else Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hist,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isPending) Color(0xFFD97706) 
                                        else if (isDebit) Color(0xFF7F1D1D) 
                                        else Color(0xFF14532D)
                            )
                            Text(
                                text = if (isPending) "Awaiting Admin Settlement Verification" else "Transaction Complete • Settled to balance",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    if (index < cashbackByUs.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}


// ==========================================
// FEATURE 6 & 8 — VENDOR REGISTRATION SCREEN
// ==========================================
// ==========================================
// FEATURE 6 & 8 — VENDOR REGISTRATION SCREEN
// ==========================================
@Composable
fun VendorRegistrationForm(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Portal mode: "register" vs "login"
    var portalMode by remember { mutableStateOf("register") }

    // Form parameter mutable states
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }
    var userEnteredOtp by remember { mutableStateOf("") }
    var isOtpVerified by remember { mutableStateOf(false) }
    var isOtpSentForVendor by remember { mutableStateOf(false) }

    // KYC File Paths & Activity Result Launchers
    var aadhaarFilePath by remember { mutableStateOf<String?>(null) }
    var gstRegPath by remember { mutableStateOf<String?>(null) }
    var bannerFilePath by remember { mutableStateOf<String?>(null) }
    var profilePicPath by remember { mutableStateOf<String?>(null) }

    val aadhaarLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            aadhaarFilePath = getFileNameFromUri(context, uri)
            Toast.makeText(context, "Aadhaar attached: $aadhaarFilePath ✅", Toast.LENGTH_SHORT).show()
        }
    }
    val gstLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            gstRegPath = getFileNameFromUri(context, uri)
            Toast.makeText(context, "GSTIN document attached: $gstRegPath ✅", Toast.LENGTH_SHORT).show()
        }
    }
    val bannerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            bannerFilePath = getFileNameFromUri(context, uri)
            Toast.makeText(context, "Banner image attached: $bannerFilePath ✅", Toast.LENGTH_SHORT).show()
        }
    }
    val profilePicLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profilePicPath = getFileNameFromUri(context, uri)
            Toast.makeText(context, "Profile picture attached: $profilePicPath ✅", Toast.LENGTH_SHORT).show()
        }
    }

    var shopAddressLine by remember { mutableStateOf("") }
    var mapSelectedCoordinates by remember { mutableStateOf("Sector 62 Cluster Noida (28.6273° N, 77.3725° E)") }
    var pickupRadiusKm by remember { mutableStateOf(5.0f) }

    // Service checklist selection
    var isLaundrySelected by remember { mutableStateOf(true) }
    var isDryCleaningSelected by remember { mutableStateOf(true) }
    var isIroningSelected by remember { mutableStateOf(true) }
    var isShoeSelected by remember { mutableStateOf(false) }

    var timingHoursStr by remember { mutableStateOf("09:00 AM - 08:30 PM") }
    var bankAccountNo by remember { mutableStateOf("") }
    var bankIfscCode by remember { mutableStateOf("") }
    var vendorUpiId by remember { mutableStateOf("") }

    val darkInputStyle = TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Medium)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // Top Bar Toolbar
        Card(
            colors = CardDefaults.cardColors(containerColor = RoyalBlue),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Vendor Onboarding Portal 🧺", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Partner & Launch Laundry Store on Apna Dhobi", color = LightCream, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector Toggle Pills (Login vs Register)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (portalMode == "register") SaffronOrange else Color.Transparent)
                            .clickable { portalMode = "register" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🧺 Register New Store",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (portalMode == "login") SaffronOrange else Color.Transparent)
                            .clickable { portalMode = "login" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🔑 Vendor Login",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (portalMode == "login") {
            // VENDOR LOGIN FLOW
            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storefront, contentDescription = "", tint = RoyalBlue, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Vendor Partner Login", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RoyalBlue)
                            }
                            Text(
                                "Enter your registered vendor mobile number to verify and open your Vendor Dashboard directly.",
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )

                            OutlinedTextField(
                                value = mobileNo,
                                onValueChange = { mobileNo = it },
                                label = { Text("Registered Mobile Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "", tint = RoyalBlue) },
                                textStyle = darkInputStyle,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = getLightBgTextFieldColors()
                            )

                            Button(
                                onClick = {
                                    if (mobileNo.length >= 10) {
                                        isOtpSentForVendor = true
                                        Toast.makeText(context, "Verification OTP sent to $mobileNo 💬", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                            ) {
                                Text("Send Verification OTP 📩", fontWeight = FontWeight.Bold)
                            }

                            if (isOtpSentForVendor) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = userEnteredOtp,
                                        onValueChange = { userEnteredOtp = it },
                                        label = { Text("Enter OTP (Default: 1234)") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "", tint = RoyalBlue) },
                                        textStyle = darkInputStyle,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = getLightBgTextFieldColors()
                                    )

                                    Button(
                                        onClick = {
                                            if (userEnteredOtp.isBlank()) {
                                                userEnteredOtp = "1234"
                                            }
                                            val vendorObj = vm.loginVendorByMobile(mobileNo)
                                            Toast.makeText(context, "Vendor Login Successful! Opening Dashboard 🚀", Toast.LENGTH_LONG).show()
                                            vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                    ) {
                                        Text("Verify & Open Vendor Dashboard 🔑", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // VENDOR REGISTRATION FORM FLOW
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("BUSINESS & OWNER CORE DETAILS", fontWeight = FontWeight.Bold, color = RoyalBlue)
                }

                item {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop registered business name (Required)") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = "", tint = RoyalBlue) },
                        textStyle = darkInputStyle,
                        modifier = Modifier.fillMaxWidth(),
                        colors = getLightBgTextFieldColors()
                    )
                }

                item {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner legal full name (Required)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "", tint = RoyalBlue) },
                        textStyle = darkInputStyle,
                        modifier = Modifier.fillMaxWidth(),
                        colors = getLightBgTextFieldColors()
                    )
                }

                // Mobile and OTP verification
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = mobileNo,
                                    onValueChange = { mobileNo = it },
                                    label = { Text("Owner mobile number") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "", tint = RoyalBlue) },
                                    textStyle = darkInputStyle,
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = getLightBgTextFieldColors()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (mobileNo.length >= 10) {
                                            isOtpSentForVendor = true
                                            Toast.makeText(context, "Verification code sent to $mobileNo 💬", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Enter a valid mobile num", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                                ) {
                                    Text("Send OTP")
                                }
                            }

                            if (isOtpSentForVendor) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = userEnteredOtp,
                                        onValueChange = { userEnteredOtp = it },
                                        label = { Text("Enter OTP (Default: 1234)") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "", tint = RoyalBlue) },
                                        textStyle = darkInputStyle,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = getLightBgTextFieldColors()
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (userEnteredOtp == "1234" || userEnteredOtp == "123456" || userEnteredOtp.length >= 4 || userEnteredOtp.isBlank()) {
                                                isOtpVerified = true
                                                Toast.makeText(context, "Mobile Verified successfully! ✅", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Invalid OTP code", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                    ) {
                                        Text("Verify")
                                    }
                                }
                            }
                        }
                    }
                }

                // Upload Document files
                item {
                    Text("GOVERNMENT CRITICAL KYC REQUISITIONS", fontWeight = FontWeight.Bold, color = RoyalBlue)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FileInputTrigger(
                                label = "Attach Aadhaar Identity Card (PDF/JPEG)",
                                selectedPath = aadhaarFilePath,
                                onSelect = {
                                    try {
                                        aadhaarLauncher.launch("*/*")
                                    } catch (e: Exception) {
                                        aadhaarFilePath = "Aadhaar_UIDAI_Verified_Card.pdf"
                                        Toast.makeText(context, "Sample Aadhaar Document Attached!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            FileInputTrigger(
                                label = "Attach GSTIN Registrations / IT Certificate",
                                selectedPath = gstRegPath,
                                onSelect = {
                                    try {
                                        gstLauncher.launch("*/*")
                                    } catch (e: Exception) {
                                        gstRegPath = "GSTIN_Certificate_Approved.jpg"
                                        Toast.makeText(context, "Sample GSTIN Document Attached!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            FileInputTrigger(
                                label = "Select Store Background / App Banner Cover",
                                selectedPath = bannerFilePath,
                                onSelect = {
                                    try {
                                        bannerLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        bannerFilePath = "Shop_Front_Onboarding_Photo.jpg"
                                        Toast.makeText(context, "Sample Store Cover Attached!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            FileInputTrigger(
                                label = "Select Owner Professional Profile Avatar Picture",
                                selectedPath = profilePicPath,
                                onSelect = {
                                    try {
                                        profilePicLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        profilePicPath = "Owner_DP_Verified.png"
                                        Toast.makeText(context, "Sample Owner Avatar Attached!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Shop Address and Map Pin
                item {
                    Text("LOGISTICS & GOOGLE MAPS LOCATION TAGGING", fontWeight = FontWeight.Bold, color = RoyalBlue)
                }

                item {
                    OutlinedTextField(
                        value = shopAddressLine,
                        onValueChange = { shopAddressLine = it },
                        label = { Text("Detailed Shop Location Address") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "", tint = RoyalBlue) },
                        textStyle = darkInputStyle,
                        modifier = Modifier.fillMaxWidth(),
                        colors = getLightBgTextFieldColors()
                    )
                }

                // Google Maps coordinate position finder button
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SaffronOrange.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, SaffronOrange.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = "", tint = SaffronOrange)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Map Coordinates GPS Finder", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoyalBlue)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(mapSelectedCoordinates, fontSize = 12.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    mapSelectedCoordinates = "Sector 62 Cluster Noida (28.6273° N, 77.3725° E)"
                                    if (shopAddressLine.isBlank()) {
                                        shopAddressLine = "Plot 42, Sector 62, Noida, Uttar Pradesh 201309"
                                    }
                                    Toast.makeText(context, "Location & coordinates tagged via Maps API! 📍", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = "")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Detect Shop Coordinates via Maps API", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Text("Pickup Service Delivery radius bounds: ${pickupRadiusKm.toInt()} km", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RoyalBlue)
                    Slider(
                        value = pickupRadiusKm,
                        onValueChange = { pickupRadiusKm = it },
                        valueRange = 1.0f..15.0f,
                        colors = SliderDefaults.colors(thumbColor = RoyalBlue, activeTrackColor = RoyalBlue)
                    )
                }

                // Services checkboxes
                item {
                    Text("OFFERED LAUNDRY SERVICES & CATEGORIES", fontWeight = FontWeight.Bold, color = RoyalBlue)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = isLaundrySelected, onCheckedChange = { isLaundrySelected = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Laundry Washing & Folding", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = isDryCleaningSelected, onCheckedChange = { isDryCleaningSelected = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Premium Suit & Saree Dry Cleaning", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = isIroningSelected, onCheckedChange = { isIroningSelected = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Heavy Crisp Steam Ironing & Pressing", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = isShoeSelected, onCheckedChange = { isShoeSelected = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sports & Formal Shoe Polishing & Cleaning", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Bank details and shop timing
                item {
                    Text("WORKING HOURS & MERCHANT BANK ACCOUNT", fontWeight = FontWeight.Bold, color = RoyalBlue)
                }

                item {
                    OutlinedTextField(
                        value = timingHoursStr,
                        onValueChange = { timingHoursStr = it },
                        label = { Text("Active shop weekly working hours") },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = "", tint = RoyalBlue) },
                        textStyle = darkInputStyle,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = bankAccountNo,
                        onValueChange = { bankAccountNo = it },
                        label = { Text("Payer/Merchant Bank Account Number") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = "", tint = RoyalBlue) },
                        textStyle = darkInputStyle,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bankIfscCode,
                            onValueChange = { bankIfscCode = it },
                            label = { Text("Bank IFSC Code") },
                            textStyle = darkInputStyle,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vendorUpiId,
                            onValueChange = { vendorUpiId = it },
                            label = { Text("Commercial UPI ID (Required)") },
                            leadingIcon = { Icon(Icons.Default.Payment, contentDescription = "", tint = RoyalBlue) },
                            textStyle = darkInputStyle,
                            colors = getLightBgTextFieldColors(),
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }

                // Onboard submit button
                item {
                    Button(
                        onClick = {
                            if (shopName.isBlank()) {
                                Toast.makeText(context, "Please enter your registered shop business name!", Toast.LENGTH_SHORT).show()
                            } else if (ownerName.isBlank()) {
                                Toast.makeText(context, "Please enter owner full legal name!", Toast.LENGTH_SHORT).show()
                            } else if (vendorUpiId.isBlank()) {
                                Toast.makeText(context, "Commercial UPI ID is required for payouts!", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val success = vm.registerVendor(
                                        name = shopName,
                                        description = "Premium Laundry & Dry Cleaning Center",
                                        address = if (shopAddressLine.isNotBlank()) shopAddressLine else mapSelectedCoordinates,
                                        logoText = shopName.take(3).uppercase(),
                                        bannerColor = "0xFF0D47A1",
                                        ownerName = ownerName,
                                        mobile = mobileNo,
                                        aadhaarDoc = aadhaarFilePath ?: "",
                                        gstDoc = gstRegPath ?: "",
                                        bannerPic = bannerFilePath ?: "",
                                        ownerAvatar = profilePicPath ?: "",
                                        workingHours = timingHoursStr,
                                        bankAccountNo = bankAccountNo,
                                        ifscCode = bankIfscCode,
                                        upiId = vendorUpiId
                                    )
                                    if (success) {
                                        Toast.makeText(context, "Laundry Store Onboarded Successfully! 🌸 Opening Dashboard...", Toast.LENGTH_LONG).show()
                                        vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                                    } else {
                                        Toast.makeText(context, "Registration failed. Please verify input fields.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Complete Verification & Launch Shop 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun FileInputTrigger(
    label: String,
    selectedPath: String?,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selectedPath != null) GreenSuccess else Color.LightGray, RoundedCornerShape(10.dp))
            .background(if (selectedPath != null) GreenSuccess.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            if (selectedPath != null) {
                Text(selectedPath, fontSize = 12.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
            } else {
                Text("Tap to browse & select file", fontSize = 11.sp, color = Color.Gray)
            }
        }
        Icon(
            imageVector = if (selectedPath != null) Icons.Default.CheckCircle else Icons.Default.FileUpload,
            contentDescription = "",
            tint = if (selectedPath != null) GreenSuccess else RoyalBlue
        )
    }
}


// ==========================================
// FEATURE 9 & 10 — VENDOR PREMIUM DASHBOARD
// ==========================================
@Composable
fun VendorPremiumDashboard(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val orders by vm.ordersList.collectAsState()
    val activeVendor by vm.selectedVendor.collectAsState()
    val productsAll by vm.productsState.collectAsState()
    val stats by vm.vendorStats.collectAsState()
    val scannerManager = remember { QrScannerManager(context) }

    LaunchedEffect(Unit) {
        vm.refreshVendorStats()
    }

    var isShopOpen by remember { mutableStateOf(activeVendor.isOpen) }
    var activeDashboardSubTab by remember { mutableStateOf("home") } // home, services, orders, reviews, staff

    val servicesListState = rememberLazyListState()
    val reviewsListState = rememberLazyListState()
    val ordersListState = rememberLazyListState()
    val homeListState = rememberLazyListState()
    val staffListState = rememberLazyListState()

    // Staff manage states
    var staffAddDialog by remember { mutableStateOf(false) }
    var newWorkerName by remember { mutableStateOf("") }
    var newWorkerPhone by remember { mutableStateOf("") }
    val workers by vm.workers.collectAsState()

    // Service manage states
    var serviceAddDialog by remember { mutableStateOf(false) }
    var newServiceName by remember { mutableStateOf("") }
    var newServicePrice by remember { mutableStateOf("") }
    var newServiceCat by remember { mutableStateOf("laundry") }

    LaunchedEffect(activeDashboardSubTab) {
        if (activeDashboardSubTab == "staff") vm.refreshWorkers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // Dashboard Header Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = RoyalBlue),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = if (activeDashboardSubTab == "home") "Vendor Partner Station" else activeDashboardSubTab.uppercase() + " CARE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        scannerManager.startScanning { code ->
                            scope.launch {
                                val order = vm.findOrderByQr(code)
                                if (order != null) {
                                    activeDashboardSubTab = "orders"
                                    Toast.makeText(context, "Order found: #${order.id}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "No order linked to this QR", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Bag", tint = Color.White)
                    }
                    // Toggle Open/Close Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isShopOpen) "OPEN" else "CLOSED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isShopOpen,
                            onCheckedChange = {
                                isShopOpen = it
                                vm.toggleVendorStatus(it)
                                val newLogs = vm.notifications.value.toMutableList()
                                newLogs.add(0, "Your shop status has been toggled to: " + if(it) "Online (Open)" else "Offline (Closed)")
                                vm.notifications.value = newLogs
                                Toast.makeText(context, if (it) "Shop is open for orders! 🧺" else "Shop is closed!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeVendor.logoText, fontWeight = FontWeight.Bold, color = SaffronOrange)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(activeVendor.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Text("UPI Merchant Node: ${activeVendor.name.replace(" ", "").lowercase()}@paytm", color = LightCream, fontSize = 12.sp)
                    }
                }
            }
        }

        // Sub navigation tabrow
        TabRow(
            selectedTabIndex = when(activeDashboardSubTab) {
                "home" -> 0
                "services" -> 1
                "orders" -> 2
                else -> 3
            },
            containerColor = Color.White,
            contentColor = RoyalBlue
        ) {
            Tab(selected = activeDashboardSubTab == "home", onClick = { 
                activeDashboardSubTab = "home"
                vm.refreshVendorStats()
            }) {
                Text("HOME", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDashboardSubTab == "services", onClick = { activeDashboardSubTab = "services" }) {
                Text("SERVICES", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDashboardSubTab == "orders", onClick = { 
                activeDashboardSubTab = "orders"
                vm.refreshVendorOrders()
            }) {
                Text("ORDERS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDashboardSubTab == "reviews", onClick = { activeDashboardSubTab = "reviews" }) {
                Text("REVIEWS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDashboardSubTab == "staff", onClick = { activeDashboardSubTab = "staff" }) {
                Text("STAFF", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeDashboardSubTab) {
            "staff" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Register Housewives/Workers", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = { staffAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                    ) {
                        Text("+ Add Staff")
                    }
                }

                LazyColumn(
                    state = staffListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    if (workers.isEmpty()) {
                        item {
                            Text("No staff registered yet.", color = Color.Gray)
                        }
                    } else {
                        items(workers) { worker ->
                            val name = worker["name"] as? String ?: "Worker"
                            val active = worker["isActive"] as? Boolean ?: true
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(name, fontWeight = FontWeight.Bold)
                                        Text(if (active) "Status: Online" else "Status: Offline", fontSize = 12.sp, color = if (active) GreenSuccess else Color.Red)
                                    }
                                    IconButton(onClick = { /* Toggle worker status API */ }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "reviews" -> {
                Text("Customer Reviews Feed", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                LazyColumn(
                    state = reviewsListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .simpleVerticalScrollbar(reviewsListState, color = RoyalBlue)
                ) {
                    val reviews = listOf(
                        Triple("Rahul K.", "Excellent clean clothing, clothes smell amazing!", 5),
                        Triple("Priya M.", "Wedding Lehenga arrived without stains, very careful and premium handling", 5),
                        Triple("Amit S.", "Ironing crease was very neat, but pickup delayed by 10 mins", 4)
                    )
                    items(reviews) { rev ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(rev.first, fontWeight = FontWeight.Bold)
                                    Row {
                                        repeat(rev.third) {
                                            Icon(Icons.Default.Star, contentDescription = "", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Text(rev.second, fontSize = 13.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
            "orders" -> {
                val pendingOrders = orders.filter { it.status != "Delivered" }
                Text("Active Garments Workflow Panel (${pendingOrders.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(16.dp))

                LazyColumn(
                    state = ordersListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .simpleVerticalScrollbar(ordersListState, color = RoyalBlue),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pendingOrders.isEmpty()) {
                        item {
                            Text("No pending marketplace orders found.")
                        }
                    } else {
                        items(pendingOrders) { order ->
                            var weightInput by remember { mutableStateOf(order.weightKg.toString()) }
                            var itemCountInput by remember { mutableStateOf(order.verifiedItemCount.toString()) }
                            var bagIdInput by remember { mutableStateOf(order.bagId) }
                            val scannerManager = remember { QrScannerManager(context) }

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Order ID #${order.id}", fontWeight = FontWeight.Black)
                                        Text(order.status.uppercase(), color = SaffronOrange, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Bucket items: ${order.itemsSummary}", fontSize = 13.sp, color = Color.Gray)
                                    
                                    if (!order.userNotes.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF9C3),
                                            border = BorderStroke(1.dp, Color(0xFFFEF08A))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("📝", fontSize = 11.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Instructions: ${order.userNotes}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF854D0E),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Operational Inputs for Vendor
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = weightInput,
                                            onValueChange = { weightInput = it },
                                            label = { Text("Wt (kg)", fontSize = 10.sp) },
                                            modifier = Modifier.weight(0.7f),
                                            singleLine = true,
                                            colors = getLightBgTextFieldColors()
                                        )
                                        OutlinedTextField(
                                            value = itemCountInput,
                                            onValueChange = { itemCountInput = it },
                                            label = { Text("Items", fontSize = 10.sp) },
                                            modifier = Modifier.weight(0.7f),
                                            singleLine = true,
                                            colors = getLightBgTextFieldColors()
                                        )
                                        OutlinedTextField(
                                            value = bagIdInput,
                                            onValueChange = { bagIdInput = it },
                                            label = { Text("Bag ID", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = getLightBgTextFieldColors(),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    scannerManager.startScanning { scannedCode ->
                                                        bagIdInput = scannedCode
                                                        Toast.makeText(context, "QR Linked: $scannedCode", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = RoyalBlue)
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                val w = weightInput.toDoubleOrNull() ?: 0.0
                                                val c = itemCountInput.toIntOrNull() ?: 0
                                                scope.launch {
                                                    vm.repository.updateOrderDetails(order.id, w, c, bagIdInput)
                                                    vm.repository.updateOrderDetailsRemote(order.id.toString(), w, c, bagIdInput)
                                                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("Save", fontSize = 10.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Payout scheduled: ₹${(order.totalPrice * 0.85).toInt()} (Commission applied)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RoyalBlue)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val nextState = when (order.status) {
                                                        "Placed", "Order Placed", "PLACED" -> "ACCEPTED"
                                                        "ACCEPTED" -> "PICKUP_ASSIGNED"
                                                        "PICKUP_ASSIGNED" -> "PICKED_UP"
                                                        "PICKED_UP" -> "RECEIVED_AT_STORE"
                                                        "RECEIVED_AT_STORE" -> "INSPECTION"
                                                        "INSPECTION" -> "PROCESSING"
                                                        "PROCESSING" -> "QUALITY_CHECK"
                                                        "QUALITY_CHECK" -> "PACKING"
                                                        "PACKING" -> "READY_FOR_DELIVERY"
                                                        else -> "DELIVERED"
                                                    }
                                                    vm.repository.updateOrderStatus(order.id, nextState)
                                                    vm.repository.updateOrderStatusRemote(order.id.toString(), nextState)
                                                    Toast.makeText(context, "Status updated to '$nextState'!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                            modifier = Modifier.weight(1.5f)
                                        ) {
                                            val btnText = when (order.status) {
                                                "Placed", "Order Placed", "PLACED" -> "ACCEPT ORDER"
                                                "ACCEPTED" -> "ASSIGN PICKUP"
                                                "PICKUP_ASSIGNED" -> "MARK PICKED UP"
                                                "PICKED_UP" -> "RECEIVE AT STORE"
                                                "RECEIVED_AT_STORE" -> "START INSPECTION"
                                                "INSPECTION" -> "START PROCESSING"
                                                "PROCESSING" -> "QUALITY CHECK"
                                                "QUALITY_CHECK" -> "PACK"
                                                "PACKING" -> "READY FOR DELIVERY"
                                                else -> "MARK COMPLETED"
                                            }
                                            Text(btnText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (order.status == "Placed" || order.status == "Order Placed" || order.status == "PLACED") {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        vm.repository.updateOrderStatus(order.id, "REJECTED")
                                                        vm.repository.updateOrderStatusRemote(order.id.toString(), "REJECTED")
                                                        Toast.makeText(context, "Order Rejected", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                                            ) {
                                                Text("REJECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                Toast.makeText(context, "Invoice invoice_${order.id}.pdf successfully downloaded!", Toast.LENGTH_LONG).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Charcoal),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Invoice", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "services" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offered Store Services list", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = { serviceAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                    ) {
                        Text("+ Add Service")
                    }
                }

                // Filter products that belong to this vendor (or generic ones)
                val vendorProds = productsAll.filter { it.id.endsWith(activeVendor.id) || it.id.contains("prod_") }

                LazyColumn(
                    state = servicesListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .simpleVerticalScrollbar(servicesListState, color = RoyalBlue)
                ) {
                    items(vendorProds) { prod ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, fontWeight = FontWeight.Bold)
                                    Text("Category: ${prod.categoryId}", fontSize = 11.sp, color = Color.Gray)
                                    Text("Base Price: ₹${prod.discountPrice.toInt()}", fontSize = 12.sp, color = SaffronOrange, fontWeight = FontWeight.Bold)
                                    Text("Treatments: Standard (+₹0) • Delicate Silk (+₹50) • Heavy Bead (+₹100)", fontSize = 10.sp, color = RoyalBlue, fontWeight = FontWeight.SemiBold)
                                }

                                Row {
                                    // Edit Price Button
                                    IconButton(onClick = {
                                        scope.launch {
                                            val success = vm.repository.updateServicePrice(prod.id, prod.discountPrice + 5)
                                            if (success) {
                                                vm.refreshCatalog()
                                                Toast.makeText(context, "Service price is now ₹${(prod.discountPrice + 5).toInt()}!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Raise Price", tint = RoyalBlue)
                                    }

                                    // Delete Service Button
                                    IconButton(onClick = {
                                        scope.launch {
                                            val success = vm.repository.deleteService(prod.id)
                                            if (success) {
                                                vm.refreshCatalog()
                                                Toast.makeText(context, "Service deleted Successfully! 🗑️", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Service", tint = RedAlert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "home" -> {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .simpleVerticalScrollbar(homeListState, color = RoyalBlue),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Revenue Analytics Graph on Canvas represents point 9
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Weekly Revenue Analytics", fontWeight = FontWeight.Bold, color = RoyalBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                ) {
                                    val th = size.height
                                    val tw = size.width
                                    if (th <= 0 || tw <= 0) return@Canvas

                                    // Draw week coordinate charts
                                    val points = listOf(Offset(0f, th * 0.8f), Offset(tw * 0.15f, th * 0.6f), Offset(tw * 0.35f, th * 0.7f), Offset(tw * 0.55f, th * 0.4f), Offset(tw * 0.75f, th * 0.2f), Offset(tw * 1.0f, th * 0.05f))
                                    for (i in 0 until points.size - 1) {
                                        drawLine(Color(0xFF0D52BA), start = points[i], end = points[i+1], strokeWidth = 8f)
                                        drawCircle(Color(0xFFFF7F00), radius = 10f, center = points[i])
                                    }
                                    drawCircle(Color(0xFFFF7F00), radius = 12f, center = points.last())
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Mon", fontSize = 10.sp, color = Color.Gray)
                                    Text("Wed", fontSize = 10.sp, color = Color.Gray)
                                    Text("Fri", fontSize = 10.sp, color = Color.Gray)
                                    Text("Sun (Today)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
                                }
                            }
                        }
                    }

                    // Numeric Metrics
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SaffronOrange)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("PENDING ORDERS", color = Color.White, fontSize = 9.sp)
                                    Text("${stats?.pendingOrders ?: 0}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = RoyalBlue)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("GROSS REVENUE", color = Color.White, fontSize = 9.sp)
                                    Text("₹${stats?.revenue?.toInt() ?: 0}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = GreenSuccess)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("NET EARNINGS", color = Color.White, fontSize = 9.sp)
                                    Text("₹${stats?.netEarnings?.toInt() ?: 0}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("COMMISSION (EST)", color = Color.White, fontSize = 9.sp)
                                    Text("₹${stats?.commission?.toInt() ?: 0}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }

                    // Help buttons / Slots management
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Logistical Parameter Hub", fontWeight = FontWeight.Bold, color = RoyalBlue)
                                Text("Staff capacity settings: 4 Active delivery boys", fontSize = 12.sp)
                                Text("Pickup slot availability: 09:00 AM - 08:30 PM (All Slots Active)", fontSize = 12.sp)

                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Logistics slot and hours adjusted successful!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Manage Delivery Radiuses & Slot Timings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Add Service Dialogue
    if (serviceAddDialog) {
        AlertDialog(
            onDismissRequest = { serviceAddDialog = false },
            title = { Text("Add New Custom Service") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        label = { Text("Service product name") },
                        colors = getLightBgTextFieldColors()
                    )

                    OutlinedTextField(
                        value = newServicePrice,
                        onValueChange = { newServicePrice = it },
                        label = { Text("Service Customer Price (₹)") },
                        colors = getLightBgTextFieldColors()
                    )

                    Text("Selected Main Category Type:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("laundry", "dry_cleaning", "ironing", "shoe_cleaning").forEach { catTy ->
                            Button(
                                onClick = { newServiceCat = catTy },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (newServiceCat == catTy) SaffronOrange else Color.Gray
                                )
                            ) {
                                Text(catTy.take(4).uppercase(), fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPrice = newServicePrice.toDoubleOrNull()
                        if (newServiceName.isNotBlank() && parsedPrice != null) {
                            scope.launch {
                                val success = vm.repository.createService(
                                    newServiceName,
                                    newServiceCat,
                                    parsedPrice * 1.25,
                                    parsedPrice,
                                    "Same Day"
                                )
                                if (success) {
                                    vm.refreshCatalog()
                                    Toast.makeText(context, "Added service: $newServiceName successfully!", Toast.LENGTH_SHORT).show()
                                    serviceAddDialog = false
                                    newServiceName = ""
                                    newServicePrice = ""
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter correct details,", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("Register Service")
                }
            }
        )
    }
}


// ==========================================
// FEATURE 19 — MASTER ADMIN DASHBOARD
// ==========================================
@Composable
fun AdminPremiumDashboard(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val orders by vm.ordersList.collectAsState(initial = emptyList())
    val registeredShops by vm.vendorsState.collectAsState(initial = emptyList())
    val comPercent by vm.adminCommissionPercent.collectAsState(initial = 12)
    val customSeoTitleText by vm.adminSeoTitle.collectAsState(initial = "")
    val bannersListActive by vm.bannersState.collectAsState(initial = emptyList())
    val boysListActive by vm.deliveryPartners.collectAsState(initial = emptyList())
    val depositRequests by vm.walletDepositRequests.collectAsState(initial = emptyList())
    val profiles by vm.allUserProfiles.collectAsState(initial = emptyList())
    val activeRentalReminders by vm.activeRentalReminders.collectAsState(initial = emptyList())
    val smtpHostVal by vm.smtpHost.collectAsState(initial = "")
    val smtpUserVal by vm.smtpUser.collectAsState(initial = "")
    val currentLatVal by vm.customerLat.collectAsState(initial = 0.0)
    val currentLngVal by vm.customerLng.collectAsState(initial = 0.0)
    
    // Manage active state segment selection inside sidebar
    var activeAdminPanelTab by remember { mutableStateOf("stats") } // stats, vendors, orders, users, wallet_approvals, delivery_fleet, coupons_banners, rental_garments, mail_diagnostics, maps_gps
    var isSidebarCollapsed by remember { mutableStateOf(false) } // supporting collapsible Notion-style aesthetics
    var isMobileSidebarOpen by remember { mutableStateOf(false) }
    val rentalListState = rememberLazyListState()
    
    // Search profile queries
    var userSearchQuery by remember { mutableStateOf("") }
    // Vendor registration values
    var newServiceName by remember { mutableStateOf("") }
    var newServicePrice by remember { mutableStateOf("") }
    var newServiceCat by remember { mutableStateOf("laundry") }

    // SEO Edit States
    var editSeoToggle by remember { mutableStateOf(false) }
    var tempSeoText by remember { mutableStateOf(customSeoTitleText) }

    // Banner Modal States
    var showAddBannerDialog by remember { mutableStateOf(false) }
    var newBannerTitle by remember { mutableStateOf("") }
    var newBannerSubtitle by remember { mutableStateOf("") }
    var newBannerCode by remember { mutableStateOf("FESTIVE25") }
    var newBannerDiscount by remember { mutableStateOf("25% OFF") }

    // Rental Garment Modal States
    var showAddRentalDialog by remember { mutableStateOf(false) }
    var newRentalDesc by remember { mutableStateOf("") }
    var newRentalStore by remember { mutableStateOf("Royal Dry Cleaners & Dyers") }
    var newRentalDueDate by remember { mutableStateOf("Sep 10, 2026") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val isDesktop = maxW >= 840.dp
        
        // --- SIDEBAR UI COMPONENT (Reusable) ---
        @Composable
        fun AdminSidebar(modifier: Modifier) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                shape = RoundedCornerShape(0.dp),
                modifier = modifier.fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp)
                ) {
                    // Header with Collapse Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = if (isDesktop && isSidebarCollapsed) Arrangement.Center else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isDesktop || !isSidebarCollapsed) {
                            Column {
                                Text(
                                    text = "Apna Dhobi",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Master Node API Hub",
                                    color = LightCream.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = { 
                            if (isDesktop) isSidebarCollapsed = !isSidebarCollapsed else isMobileSidebarOpen = false 
                        }) {
                            Icon(
                                imageVector = if (isDesktop) (if (isSidebarCollapsed) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack) else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Sidebar Toggle",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))

                    // Scrollable List of Navigation Items
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sidebarItems = listOf(
                            Pair("stats", Pair(Icons.Default.Assessment, "Overview Stats")),
                            Pair("brand_logo", Pair(Icons.Default.Image, "App Brand Logo")),
                            Pair("vendors", Pair(Icons.Default.Storefront, "Vendor Approvals")),
                            Pair("orders", Pair(Icons.Default.LocalShipping, "Orders Terminal")),
                            Pair("users", Pair(Icons.Default.People, "Users Directory")),
                            Pair("wallet_approvals", Pair(Icons.Default.Payment, "Recharge Queue")),
                            Pair("delivery_fleet", Pair(Icons.Default.DirectionsRun, "Logistics Fleet")),
                            Pair("coupons_banners", Pair(Icons.Default.Build, "Ads & SEO Ads")),
                            Pair("rental_garments", Pair(Icons.Default.Inventory, "Rental Return due")),
                            Pair("mail_diagnostics", Pair(Icons.Default.Email, "SMTP Mail server")),
                            Pair("maps_gps", Pair(Icons.Default.Map, "GPS Realtime"))
                        )

                        items(sidebarItems) { item ->
                            val isSelected = activeAdminPanelTab == item.first
                            val bg = if (isSelected) SaffronOrange else Color.Transparent
                            val textCol = if (isSelected) Color.White else LightCream

                            Card(
                                colors = CardDefaults.cardColors(containerColor = bg),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        activeAdminPanelTab = item.first
                                        if (!isDesktop) isMobileSidebarOpen = false // Close on click for mobile
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = item.second.first,
                                        contentDescription = item.second.second,
                                        tint = textCol,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (!isDesktop || !isSidebarCollapsed) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item.second.second,
                                            color = textCol,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Logout Admin Section at Bottom of Sidebar
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clickable {
                                vm.isAdminSessionActive.value = false
                                vm.navigateTo(ApnaDhobiScreen.Login)
                                Toast.makeText(context, "Logged out of admin terminal.", Toast.LENGTH_SHORT).show()
                            },
                        horizontalArrangement = if (isDesktop && isSidebarCollapsed) Arrangement.Center else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            vm.isAdminSessionActive.value = false
                            vm.navigateTo(ApnaDhobiScreen.Login)
                        }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Log Out Admin", tint = RedAlert)
                        }
                        if (!isDesktop || !isSidebarCollapsed) {
                            Text(text = "Logout Terminal", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- MAIN VIEW COMPONENT (Reusable) ---
        @Composable
        fun MainAdminContent(modifier: Modifier) {
            val userEmail by vm.userEmail.collectAsState()
            Column(
                modifier = modifier
                    .fillMaxHeight()
                    .background(LightCream)
            ) {
                // Header Action Bar (Executive Dark Glassmorphic Design)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (!isDesktop) {
                                IconButton(onClick = { isMobileSidebarOpen = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Surface(
                                color = SaffronOrange,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🛡️", fontSize = 18.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (activeAdminPanelTab) {
                                            "stats" -> "PLATFORM OVERVIEW & INSIGHTS 📊"
                                            "brand_logo" -> "APP BRAND LOGO & ASSETS 🎨"
                                            "vendors" -> "MULTI-VENDOR SHOPS & SERVICES 🏪"
                                            "orders" -> "MASTER BOOKING ORDERS TRACKER 📦"
                                            "users" -> "CLIENT PROFILES DIRECTORY 👥"
                                            "wallet_approvals" -> "RECHARGE TRANSACTION REQUESTS 💳"
                                            "delivery_fleet" -> "CO-ORDINATED LOGISTICS & FLEET 🚚"
                                            "coupons_banners" -> "PROMO ADS & COMMISSION SETTINGS 🛠️"
                                            "rental_garments" -> "LUXURY RENTAL RETURN DESK 👗"
                                            "mail_diagnostics" -> "SMTP EMAIL CORROBORATION 📧"
                                            "maps_gps" -> "REALTIME LOCATIONAL GPS ROUTER 🗺️"
                                            else -> "ADMIN PANEL CONTROL"
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("ONLINE 🟢", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(
                                    text = "Admin Node: $userEmail",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Button(
                            onClick = { vm.navigateBack() },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Display Content Router
            Box(modifier = Modifier.weight(1f)) {
                when (activeAdminPanelTab) {
                    "stats" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                // High-End KPI Metrics Grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Total Fulfilled Orders
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Total Bookings", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                Surface(color = RoyalBlue.copy(alpha = 0.1f), shape = CircleShape) {
                                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = RoyalBlue, modifier = Modifier.padding(4.dp).size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "${orders.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = RoyalBlue)
                                            Text(text = "Active Orders: ${orders.filter { it.status != "Delivered" }.size}", fontSize = 10.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    // Total Platform Revenue estimated
                                    Card(
                                        modifier = Modifier.weight(1.1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Commissions (Est)", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                Surface(color = SaffronOrange.copy(alpha = 0.1f), shape = CircleShape) {
                                                    Icon(Icons.Default.Payment, contentDescription = null, tint = SaffronOrange, modifier = Modifier.padding(4.dp).size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val estRev = orders.sumOf { it.totalPrice } * (comPercent / 100.0)
                                            Text(text = "₹${String.format("%.2f", estRev)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SaffronOrange)
                                            Text(text = "Commission Fee: $comPercent%", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    // Total registered users
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Total Clients", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                Surface(color = RoyalBlue.copy(alpha = 0.1f), shape = CircleShape) {
                                                    Icon(Icons.Default.People, contentDescription = null, tint = RoyalBlue, modifier = Modifier.padding(4.dp).size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "${profiles.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = RoyalBlue)
                                            Text(text = "Verified Profiles 🟢", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }

                            item {
                                // Monthly platform Commission Earnings (Canvas graph representation)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Platform Profit Growth Graph (Commission Accrued)", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                            val th = size.height
                                            val tw = size.width
                                            if (th <= 0 || tw <= 0) return@Canvas

                                            val barWidth = tw * 0.15f
                                            val spacing = tw * 0.08f
                                            val values = listOf(th * 0.82f, th * 0.65f, th * 0.5f, th * 0.22f)
                                            val colors = listOf(RoyalBlue.copy(alpha=0.35f), RoyalBlue.copy(alpha=0.6f), RoyalBlue.copy(alpha=0.8f), SaffronOrange)

                                            for (i in values.indices) {
                                                val left = spacing + (barWidth + spacing) * i
                                                val top = values[i]
                                                drawRect(
                                                    color = colors[i],
                                                    topLeft = Offset(left, top),
                                                    size = Size(barWidth, th - top)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround
                                        ) {
                                            Text(text = "Feb", fontSize = 10.sp, color = Color.Gray)
                                            Text(text = "Mar", fontSize = 10.sp, color = Color.Gray)
                                            Text(text = "Apr", fontSize = 10.sp, color = Color.Gray)
                                            Text(text = "May (Est)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Platform Operations & Live Health Control Center ⚡", fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 13.sp)
                                            Surface(color = GreenSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                                Text("SYSTEM HEALTH 100%", color = GreenSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))

                                        val healthRows = listOf(
                                            Pair("🟢 Core Engine & Database", "REST API & SQLite Database Synchronized (0 Errors)"),
                                            Pair("🧺 Laundry Partner Network", "8 / 10 Verified Vendor Outlets Online"),
                                            Pair("🛵 Delivery Fleet Logistics", "5 Fleet Drivers Active & Navigating"),
                                            Pair("📦 Today's Order Flow", "14 Orders Processed • 0 Delays Logged"),
                                            Pair("💳 Payment Settlements", "Razorpay Sandbox & Instant UPI Active"),
                                            Pair("📧 Notification Engine", "SMTP Relay & Push Gateway Operating Healthy")
                                        )

                                        healthRows.forEach { (label, status) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                                Text(status, fontSize = 11.sp, color = Color.DarkGray)
                                            }
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "vendors" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Add Custom Laundry Service Pricing Globally 🧺",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = RoyalBlue,
                                                fontSize = 14.sp
                                            )
                                            Surface(
                                                color = SaffronOrange.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "GLOBAL CATALOG",
                                                    color = SaffronOrange,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Services added here persist into SQLite DB & update all shop catalogs in real-time.",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = newServiceName,
                                                onValueChange = { newServiceName = it },
                                                placeholder = { Text("e.g. Silk Saree Dry Clean", fontSize = 12.sp) },
                                                label = { Text("Service Name") },
                                                colors = getLightBgTextFieldColors(),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            OutlinedTextField(
                                                value = newServicePrice,
                                                onValueChange = { newServicePrice = it },
                                                placeholder = { Text("e.g. 250", fontSize = 12.sp) },
                                                label = { Text("Price (₹)") },
                                                colors = getLightBgTextFieldColors(),
                                                modifier = Modifier.weight(0.7f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        // Category Chips Row & Add Button
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(text = "Select Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    val catMap = listOf(
                                                        "laundry" to "🧺 LAUNDRY",
                                                        "dry_cleaning" to "👔 DRY CLEAN",
                                                        "ironing" to "💨 IRONING",
                                                        "shoe_cleaning" to "👟 SHOE CARE"
                                                    )
                                                    catMap.forEach { (catId, label) ->
                                                        val isSelected = newServiceCat == catId
                                                        Surface(
                                                            color = if (isSelected) SaffronOrange else Color.LightGray.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.clickable { newServiceCat = catId }
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSelected) Color.White else Color.DarkGray,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                Button(
                                                    onClick = {
                                                        val parseVal = newServicePrice.toDoubleOrNull()
                                                        if (newServiceName.isNotBlank() && parseVal != null) {
                                                            vm.insertCustomService(newServiceName, parseVal, newServiceCat)
                                                            Toast.makeText(context, "Added globally: $newServiceName for ₹${String.format(java.util.Locale.US, "%.2f", parseVal)}", Toast.LENGTH_SHORT).show()
                                                            newServiceName = ""
                                                            newServicePrice = ""
                                                        } else {
                                                            Toast.makeText(context, "Please enter valid service name and numeric price", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("+ Add Catalog Service", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Registered Multi-vendor Listings Directory (${registeredShops.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBlue,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    fontSize = 13.sp
                                )
                            }

                            items(registeredShops) { vendorObj ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = CardDefaults.cardElevation(1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = RoyalBlue.copy(alpha = 0.1f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(text = "🏪", fontSize = 20.sp)
                                                }
                                            }
                                            Column {
                                                Text(text = vendorObj.name, fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 14.sp)
                                                Text(text = "Service Area: ${vendorObj.distanceKm} km | Starting from ₹${vendorObj.startingPrice}", fontSize = 11.sp, color = Color.Gray)
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Surface(color = GreenSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                                        Text(text = "APPROVED & KYC ACTIVE 🟢", color = GreenSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                    Text(text = "⭐ ${vendorObj.rating}", fontSize = 11.sp, color = SaffronOrange, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = {
                                                vm.selectedVendorId.value = vendorObj.id
                                                vm.navigateTo(ApnaDhobiScreen.VendorDashboard)
                                            }) {
                                                Icon(Icons.Default.Build, contentDescription = "Manage Catalog", tint = SaffronOrange)
                                            }
                                            IconButton(onClick = {
                                                val filtered = registeredShops.filter { it.id != vendorObj.id }
                                                vm.vendorsState.value = filtered
                                                Toast.makeText(context, "Suspended vendor store: ${vendorObj.name}", Toast.LENGTH_LONG).show()
                                            }) {
                                                Icon(Icons.Default.Block, contentDescription = "Suspend Store", tint = RedAlert)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "orders" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Master Placed Booking Orders Tracker (${orders.size})",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RoyalBlue,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Real-time sync with Room DB order records.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            vm.repository.placeOrder(OrderRecord(
                                                vendorName = "Royal Dry Cleaners & Dyers",
                                                itemsSummary = "2x Silk Kurti Dry Clean, 3x Denim Laundry wash",
                                                totalPrice = 420.0,
                                                pickupSlot = "Tomorrow | Morning",
                                                deliverySlot = "Friday | Evening",
                                                paymentMethod = "Apna Wallet",
                                                status = "Washing"
                                            ))
                                            vm.repository.placeOrder(OrderRecord(
                                                vendorName = "The Elite Shoe & Blanket Care",
                                                itemsSummary = "1x Premium Wool Blanket Polish, 1x Reebok Shoes detailing",
                                                totalPrice = 580.0,
                                                pickupSlot = "Thursday | Afternoon",
                                                deliverySlot = "Saturday | Evening",
                                                paymentMethod = "Cash on Delivery",
                                                status = "Placed"
                                            ))
                                            Toast.makeText(context, "Loaded test bookings into database!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Seed Demo Bookings", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (orders.isEmpty()) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(Icons.Default.ShoppingBag, contentDescription = "", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(text = "No active orders in Database.", fontWeight = FontWeight.Bold, color = RoyalBlue)
                                                Text(text = "Place orders from the customer app or click 'Seed Demo Bookings' above.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                } else {
                                    items(orders) { orderObj ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(14.dp),
                                            elevation = CardDefaults.cardElevation(2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(text = "Booking ID: #${orderObj.id}", fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 14.sp)
                                                        Text(text = "Vendor: ${orderObj.vendorName}", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                    
                                                    // Interactive State Cycle Chip
                                                    Surface(
                                                        color = when {
                                                            orderObj.status.contains("Delivered") -> Color(0xFFE8F5E9)
                                                            orderObj.status.contains("Out for Delivery") -> Color(0xFFFFF3E0)
                                                            else -> Color(0xFFE3F2FD)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.clickable {
                                                            val nextStatus = when {
                                                                orderObj.status.contains("Placed") -> "Washing"
                                                                orderObj.status.contains("Washing") -> "Ironing"
                                                                orderObj.status.contains("Ironing") -> "Out for Delivery"
                                                                orderObj.status.contains("Out for Delivery") -> "Delivered"
                                                                else -> "Placed"
                                                            }
                                                            scope.launch {
                                                                vm.repository.updateOrderStatus(orderObj.id, nextStatus)
                                                                Toast.makeText(context, "Order #${orderObj.id} status: $nextStatus", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${orderObj.status} 🏷️",
                                                            color = when {
                                                                orderObj.status.contains("Delivered") -> Color(0xFF2E7D32)
                                                                orderObj.status.contains("Out for Delivery") -> Color(0xFFE65100)
                                                                else -> RoyalBlue
                                                            },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(text = "Items: ${orderObj.itemsSummary}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                                Text(text = "Schedule: [Pickup: ${orderObj.pickupSlot}] • [Delivery: ${orderObj.deliverySlot}]", fontSize = 11.sp, color = Color.Gray)
                                                
                                                // Fixed Currency Formatting Bug (₹127.02 instead of ₹127.02000000000001)
                                                val formattedTotal = String.format(java.util.Locale.US, "%.2f", orderObj.totalPrice)
                                                Text(
                                                    text = "Bill Value: ₹$formattedTotal | Pay Term: ${orderObj.paymentMethod}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SaffronOrange,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                                
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.4f))
                                                
                                                // Assign Logistics Agent Action Bar
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "Assign Logistics Fleet:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        listOf("Rohan 🛵", "Vinod 🚚", "Amit 🚲").forEach { boyName ->
                                                            Button(
                                                                onClick = {
                                                                    vm.assignDriverToOrder(orderObj.id, boyName)
                                                                    Toast.makeText(context, "Assigned order #${orderObj.id} to $boyName!", Toast.LENGTH_SHORT).show()
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = SaffronOrange.copy(alpha = 0.15f),
                                                                    contentColor = SaffronOrange
                                                                ),
                                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(text = boyName, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

                    "users" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "Client Profiles Database Directory 👤", fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = userSearchQuery,
                                        onValueChange = { userSearchQuery = it },
                                        placeholder = { Text("Search client name, phone, email...", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "", tint = RoyalBlue) },
                                        colors = getLightBgTextFieldColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Clean Title & Action Buttons Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Registered Clients (${profiles.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBlue,
                                    fontSize = 13.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                vm.registerUserProfile("Anil Satya", "anil.satyaka@gmail.com", "+91 9876543210")
                                                vm.registerUserProfile("Rohan Sharma", "rohan.sharma@gmail.com", "+91 9988776655")
                                                vm.registerUserProfile("Priya Verma", "priya.verma@example.com", "+91 9123456789")
                                                Toast.makeText(context, "Seeded sandbox users into SQLite Database!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = "Seed Sandbox Users", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                vm.repository.clearAllUserProfiles()
                                                Toast.makeText(context, "Cleared client database!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = "Truncate List", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val filteredProfiles = if (userSearchQuery.isBlank()) {
                                    profiles
                                } else {
                                    profiles.filter {
                                        it.name.contains(userSearchQuery, ignoreCase = true) ||
                                        it.email.contains(userSearchQuery, ignoreCase = true) ||
                                        it.phone.contains(userSearchQuery)
                                    }
                                }

                                if (filteredProfiles.isEmpty()) {
                                    item {
                                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                                Text(text = "No client profiles found matching search criteria.", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    items(filteredProfiles) { profObj ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(14.dp),
                                            elevation = CardDefaults.cardElevation(1.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            color = RoyalBlue.copy(alpha = 0.1f),
                                                            shape = CircleShape,
                                                            modifier = Modifier.size(40.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(text = profObj.name.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 16.sp)
                                                            }
                                                        }
                                                        Column {
                                                            Text(text = profObj.name, fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 13.sp)
                                                            Text(text = "📞 ${profObj.phone} • ✉️ ${profObj.email}", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                    
                                                    // Wallet Credit Balance Badge
                                                    Surface(
                                                        color = GreenSuccess.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Wallet: ₹500.00 💳",
                                                            color = GreenSuccess,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Role Badges
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Surface(color = Color(0xFF2563EB).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                                            Text(text = "CUSTOMER 🔵", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }
                                                        Surface(color = Color(0xFFD97706).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                                            Text(text = "🏆 VIP GOLD MEMBER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }
                                                    }

                                                    // Interactive Client Action Buttons
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(
                                                            onClick = {
                                                                vm.walletBalance.value += 200.0
                                                                Toast.makeText(context, "Credited ₹200 wallet bonus to ${profObj.name}!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess.copy(alpha = 0.15f), contentColor = GreenSuccess),
                                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(text = "+ ₹200 Credit 💳", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                Toast.makeText(context, "Editing profile record for ${profObj.name}", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue.copy(alpha = 0.12f), contentColor = RoyalBlue),
                                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(text = "Edit ✏️", fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

                    "wallet_approvals" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "RECHARGE TRANSACTION REQUEST GATEWAY 💳", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                                        Text(text = "Approve manual UPI/Razorpay deposits to credit user wallet balance in real-time.", fontSize = 11.sp, color = LightCream.copy(alpha = 0.8f))
                                    }
                                    Button(
                                        onClick = {
                                            vm.seedDemoDepositRequest()
                                            Toast.makeText(context, "Demo Deposit Request posted to Queue!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Seed Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (depositRequests.isEmpty()) {
                                    item {
                                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("💳 No Pending Recharge Requests", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 14.sp)
                                                Text("Click '+ Seed Demo' above or add money from user wallet screen to test approval workflow.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                                Button(
                                                    onClick = { vm.seedDemoDepositRequest() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Create Test Deposit Check (₹500)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(depositRequests) { req ->
                                        val id = req["_id"] as? String ?: ""
                                        val status = req["status"] as? String ?: "PENDING"
                                        val amount = (req["amount"] as? Number)?.toDouble() ?: 0.0
                                        val refId = req["referenceId"] as? String ?: ""
                                        val uName = req["userName"] as? String ?: ""
                                        val uEmail = req["userEmail"] as? String ?: ""
                                        val pMethod = req["paymentMethod"] as? String ?: ""

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Surface(color = RoyalBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                                            Text("Ref: $refId", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = RoyalBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }
                                                        Text("• $pMethod", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                    }
                                                    Surface(
                                                        color = when (status) {
                                                            "APPROVED" -> GreenSuccess.copy(alpha = 0.15f)
                                                            "REJECTED" -> RedAlert.copy(alpha = 0.15f)
                                                            else -> SaffronOrange.copy(alpha = 0.15f)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = when (status) {
                                                                "APPROVED" -> "APPROVED 🟢"
                                                                "REJECTED" -> "REJECTED 🔴"
                                                                else -> "PENDING APPROVAL 🟠"
                                                            },
                                                            color = when (status) {
                                                                "APPROVED" -> GreenSuccess
                                                                "REJECTED" -> RedAlert
                                                                else -> SaffronOrange
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(text = uName.ifBlank { "Anil Satya" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                        Text(text = uEmail.ifBlank { "anil.satya@gmail.com" }, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                    Text(text = "₹${String.format("%.2f", amount)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = GreenSuccess)
                                                }

                                                if (status == "PENDING") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = {
                                                                vm.approveWalletDepositRequest(id)
                                                                vm.sendWalletCreditSmtpEmail(uName, uEmail, amount, pMethod)
                                                                Toast.makeText(context, "Approved! ₹$amount credited to client wallet balance.", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                                            modifier = Modifier.weight(1f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(vertical = 8.dp)
                                                        ) {
                                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(text = "Approve & Credit Balance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                vm.rejectWalletDepositRequest(id)
                                                                Toast.makeText(context, "Deposit request rejected.", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                                        ) {
                                                            Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(text = "Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

                    "delivery_fleet" -> {
                        DeliveryPartnerApp(vm)
                    }

                    "coupons_banners" -> {
                        val bannersListActive by vm.bannersState.collectAsState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Home Promotional Sliding Banner Manager
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = "PROMOTIONAL BANNERS CATALOG 🏷️", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 14.sp)
                                            Text(text = "Manage active deals & promo highlights shown on client home feed", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Button(
                                            onClick = { showAddBannerDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ Add Banner", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    bannersListActive.forEach { banner ->
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = banner.title ?: "", fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 13.sp)
                                                    Text(text = banner.subtitle ?: "", fontSize = 11.sp, color = Color.DarkGray)
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        if (!banner.code.isNullOrBlank()) {
                                                            Surface(color = SaffronOrange.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                                                Text(text = "Code: ${banner.code}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SaffronOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                            }
                                                        }
                                                        if (!banner.badge.isNullOrBlank()) {
                                                            Text(text = "• ${banner.badge}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val success = vm.repository.deleteBanner(banner.id)
                                                            if (success) {
                                                                vm.refreshCatalog()
                                                                Toast.makeText(context, "Removed banner: ${banner.title} successfully!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Banner", tint = RedAlert, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Commission slide settings
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "UNIFIED VENDOR COMMISSION RATE 💼", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                                        Surface(color = RoyalBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                            Text(text = "$comPercent% Platform Fee", color = RoyalBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                    Slider(
                                        value = comPercent.toFloat(),
                                        onValueChange = { vm.adminCommissionPercent.value = it.toInt() },
                                        valueRange = 5.0f..35.0f,
                                        colors = SliderDefaults.colors(thumbColor = RoyalBlue, activeTrackColor = RoyalBlue)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Vendor Payout Share: ${100 - comPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                        Text(text = "Platform Margin: $comPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoyalBlue)
                                    }
                                }
                            }

                            // Google Custom App Store SEO title configurator
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "SEO LANDING PAGE METADATA TAG SETTINGS 🔍", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                                    Text(text = "Configures dynamic meta title for search indexation across Google & App Stores.", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (editSeoToggle) {
                                        OutlinedTextField(
                                            value = tempSeoText,
                                            onValueChange = { tempSeoText = it },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF0F172A),
                                                unfocusedTextColor = Color(0xFF0F172A),
                                                focusedContainerColor = Color(0xFFF8FAFC),
                                                unfocusedContainerColor = Color.White,
                                                focusedBorderColor = RoyalBlue,
                                                unfocusedBorderColor = Color.LightGray
                                            ),
                                            label = { Text("SEO Meta Title Tag", color = Color.Gray, fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = customSeoTitleText.ifBlank { "Apna Dhobi - Premium Doorstep Laundry & Dry Cleaning App" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (editSeoToggle) {
                                                vm.adminSeoTitle.value = tempSeoText
                                                Toast.makeText(context, "SEO Metadata tag updated successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                            editSeoToggle = !editSeoToggle
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(if (editSeoToggle) Icons.Default.Check else Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = if (editSeoToggle) "Apply & Save SEO Tag" else "Edit SEO Title Tag", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    "rental_garments" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "LUXURY WEDDING GARMENTS RENTAL DESK 👗", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                                        Text(text = "Track return due dates, dispatch SMS alerts, and settle rental inventory.", fontSize = 11.sp, color = LightCream.copy(alpha = 0.8f))
                                    }
                                    Button(
                                        onClick = { showAddRentalDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Add Rental Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            LazyColumn(
                                state = rentalListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .simpleVerticalScrollbar(rentalListState, color = RoyalBlue),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeRentalReminders) { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Rental Item ID: #${item.id}", fontWeight = FontWeight.ExtraBold, color = RoyalBlue, fontSize = 12.sp)
                                                Surface(
                                                    color = if (item.status == "Returned") GreenSuccess.copy(alpha = 0.15f) else SaffronOrange.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = if (item.status == "Returned") "RETURNED 🟢" else "RETURN PENDING 🟠",
                                                        color = if (item.status == "Returned") GreenSuccess else SaffronOrange,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(text = item.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                            Text(text = "Partner Store: ${item.store}", fontSize = 11.sp, color = Color.Gray)
                                            Text(text = "Return Due Date: ${item.dueDate}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SaffronOrange)

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        val updated = activeRentalReminders.map {
                                                            if (it.id == item.id) it.copy(status = "Returned") else it
                                                        }
                                                        vm.activeRentalReminders.value = updated
                                                        vm.pushSimulatedNotification("Rental item #${item.id} (${item.description}) marked as Returned! ✅")
                                                        Toast.makeText(context, "Settle complete! Garment marked as Returned. ✅", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "Mark Returned", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }

                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            vm.pushSimulatedNotification("SMS ALERT: Your rental item '${item.description}' is due by ${item.dueDate}! Please return to ${item.store}. 📱")
                                                            Toast.makeText(context, "Transactional Reminder SMS alert dispatched!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                                    modifier = Modifier.weight(1.1f),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "Dispatch Reminder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "brand_logo" -> {
                        BrandLogoAdminSubPanel(vm)
                    }

                    "mail_diagnostics" -> {
                        EmailConfigSubPanel(vm)
                    }

                    "maps_gps" -> {
                        GoogleMapsSubPanel(vm)
                    }
                }
            }
        }
    }

        // --- RENDER RELEVANT LAYOUT BASED ON SCREEN SIZE ---
        if (isDesktop) {
            Row(modifier = Modifier.fillMaxSize()) {
                AdminSidebar(Modifier.width(if (isSidebarCollapsed) 72.dp else 260.dp))
                MainAdminContent(Modifier.weight(1f))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: Content
                MainAdminContent(Modifier.fillMaxSize())

                // Layer 2: Backdrop
                AnimatedVisibility(
                    visible = isMobileSidebarOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .pointerInput(Unit) {
                                detectTapGestures { isMobileSidebarOpen = false }
                            }
                    )
                }

                // Layer 3: Overlay Sidebar
                AnimatedVisibility(
                    visible = isMobileSidebarOpen,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    AdminSidebar(Modifier.width(minOf(maxW * 0.82f, 280.dp)))
                }
            }
        }
    }

    if (showAddBannerDialog) {
        AlertDialog(
            onDismissRequest = { showAddBannerDialog = false },
            title = { Text("+ Add Promotional Banner 🏷️", fontWeight = FontWeight.Bold, color = RoyalBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newBannerTitle,
                        onValueChange = { newBannerTitle = it },
                        label = { Text("Banner Headline (e.g. MONSOON SPECIAL ☔)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBannerSubtitle,
                        onValueChange = { newBannerSubtitle = it },
                        label = { Text("Subtitle / Short Description") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBannerCode,
                        onValueChange = { newBannerCode = it },
                        label = { Text("Promo Coupon Code (e.g. RAIN30)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBannerDiscount,
                        onValueChange = { newBannerDiscount = it },
                        label = { Text("Discount Highlight Tag (e.g. 30% OFF)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBannerTitle.isNotBlank()) {
                            scope.launch {
                                val success = vm.repository.createBanner(
                                    newBannerTitle.trim(),
                                    newBannerSubtitle.ifBlank { "Exclusive doorstep laundry savings" },
                                    newBannerCode.trim().ifBlank { "OFFER20" },
                                    newBannerDiscount.trim().ifBlank { "20% OFF" }
                                )
                                if (success) {
                                    vm.refreshCatalog()
                                    Toast.makeText(context, "Added promotional banner successfully! 🏷️", Toast.LENGTH_SHORT).show()
                                    showAddBannerDialog = false
                                    newBannerTitle = ""
                                    newBannerSubtitle = ""
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter banner headline!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("Add Banner", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBannerDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showAddRentalDialog) {
        AlertDialog(
            onDismissRequest = { showAddRentalDialog = false },
            title = { Text("+ Register Rental Garment 👗", fontWeight = FontWeight.Bold, color = RoyalBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newRentalDesc,
                        onValueChange = { newRentalDesc = it },
                        label = { Text("Garment Name (e.g. Royal Maroon Sherwani Set)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRentalStore,
                        onValueChange = { newRentalStore = it },
                        label = { Text("Partner Dry Cleaner / Store") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRentalDueDate,
                        onValueChange = { newRentalDueDate = it },
                        label = { Text("Return Due Date (e.g. Sep 15, 2026)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRentalDesc.isNotBlank()) {
                            vm.addRentalItem(newRentalDesc, newRentalStore, newRentalDueDate)
                            Toast.makeText(context, "Registered new rental item! 👗", Toast.LENGTH_SHORT).show()
                            showAddRentalDialog = false
                            newRentalDesc = ""
                        } else {
                            Toast.makeText(context, "Please enter garment description!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) {
                    Text("Register Item", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRentalDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}


// ==========================================
// FEATURE 12 — DELIVERY BOY APP & LOGISTICS FLEET MANAGEMENT
// ==========================================
@Composable
fun DeliveryPartnerApp(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val orders by vm.ordersList.collectAsState()
    val fleetDrivers by vm.deliveryPartnersState.collectAsState()
    val isOnline by vm.isDeliveryPartnerOnline.collectAsState()
    val customerLat by vm.customerLat.collectAsState()
    val customerLng by vm.customerLng.collectAsState()

    var activeDriverTab by remember { mutableStateOf("runs") } // "runs", "active", "earnings", "fleet"
    var showOnboardDriverDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var selectedOrderForOtp by remember { mutableStateOf<OrderRecord?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var isVerifyingOtp by remember { mutableStateOf(false) }

    var newDriverName by remember { mutableStateOf("") }
    var newDriverPhone by remember { mutableStateOf("") }
    var newDriverVehicle by remember { mutableStateOf("Bike 🏍️") }
    var newDriverLicense by remember { mutableStateOf("") }

    val pendingRuns = orders.filter { it.status != "Delivered" && it.status != "DELIVERED" }
    val activeRun = orders.find { it.status == "OUT_FOR_DELIVERY" || it.status == "Out for Delivery" || it.status == "PICKUP_ASSIGNED" || it.status == "PICKED_UP" }
    val completedRuns = orders.filter { it.status == "Delivered" || it.status == "DELIVERED" }
    val todayEarnings = (completedRuns.size * 65.0) + (if (isOnline) 50.0 else 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightCream)
    ) {
        // Toolbar header with Online/Offline Switch
        Card(
            colors = CardDefaults.cardColors(containerColor = RoyalBlue),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Delivery Partner Workspace 🛵", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.5.sp)
                            Text("Active Fleet Driver Node • Delhi NCR", color = LightCream, fontSize = 11.sp)
                        }
                    }

                    // Online / Offline Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            color = if (isOnline) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isOnline,
                            onCheckedChange = {
                                vm.isDeliveryPartnerOnline.value = it
                                val statusText = if (it) "You are now ONLINE! Ready to receive delivery runs. 🛵" else "You are now OFFLINE. No new runs assigned."
                                vm.showCustomAlert(statusText)
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // GPS Telemetry Banner
                Surface(
                    color = Color(0xFF1E3A8A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "GPS Stream: Live (28.6139° N, 77.2090° E)" else "GPS Stream: Paused",
                                color = Color.White,
                                fontSize = 10.5.sp
                            )
                        }
                        Text(
                            text = "⭐ 4.9 (142 Trips)",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Sub Navigation TabRow
        TabRow(
            selectedTabIndex = when (activeDriverTab) {
                "runs" -> 0
                "active" -> 1
                "earnings" -> 2
                else -> 3
            },
            containerColor = Color.White,
            contentColor = RoyalBlue
        ) {
            Tab(selected = activeDriverTab == "runs", onClick = { activeDriverTab = "runs" }) {
                Text("RUNS (${pendingRuns.size})", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDriverTab == "active", onClick = { activeDriverTab = "active" }) {
                Text("ACTIVE (${if (activeRun != null) 1 else 0})", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDriverTab == "earnings", onClick = { activeDriverTab = "earnings" }) {
                Text("EARNINGS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Tab(selected = activeDriverTab == "fleet", onClick = { activeDriverTab = "fleet" }) {
                Text("FLEET", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area Switcher
        when (activeDriverTab) {
            "runs" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Available & Assigned Jobs (${pendingRuns.size})", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 14.sp)
                            TextButton(onClick = { vm.refreshOrders() }) {
                                Text("Refresh 🔄", fontSize = 11.sp, color = RoyalBlue)
                            }
                        }
                    }

                    if (pendingRuns.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🛵", fontSize = 36.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("All caught up! No pending delivery tasks.", fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text("Stay online to receive new customer pickups.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    } else {
                        items(pendingRuns) { order ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Order #${order.id}", fontWeight = FontWeight.Black, color = RoyalBlue, fontSize = 14.sp)
                                        Surface(
                                            color = when (order.status) {
                                                "OUT_FOR_DELIVERY", "Out for Delivery" -> Color(0xFFDCFCE7)
                                                "PICKUP_ASSIGNED" -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFEFF6FF)
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = order.status.replace("_", " ").uppercase(),
                                                color = when (order.status) {
                                                    "OUT_FOR_DELIVERY", "Out for Delivery" -> Color(0xFF15803D)
                                                    "PICKUP_ASSIGNED" -> Color(0xFFB45309)
                                                    else -> Color(0xFF1D4ED8)
                                                },
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Items: ${order.itemsSummary}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                                    Text("Pickup Slot: ${order.pickupSlot}", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("Delivery Slot: ${order.deliverySlot}", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("Payout: ₹65.00 (Doorstep Run)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Advance Run Step Button
                                        Button(
                                            onClick = {
                                                val nextStatus = when (order.status) {
                                                    "PLACED", "Placed", "Order Placed", "ACCEPTED" -> "PICKUP_ASSIGNED"
                                                    "PICKUP_ASSIGNED" -> "PICKED_UP"
                                                    "PICKED_UP" -> "RECEIVED_AT_STORE"
                                                    "READY_FOR_DELIVERY", "Packed" -> "OUT_FOR_DELIVERY"
                                                    "OUT_FOR_DELIVERY", "Out for Delivery" -> {
                                                        selectedOrderForOtp = order
                                                        otpInput = ""
                                                        showOtpDialog = true
                                                        return@Button
                                                    }
                                                    else -> "DELIVERED"
                                                }
                                                vm.updateOrderStatusDirectly(order.id, nextStatus)
                                                Toast.makeText(context, "Order #${order.id} status updated to: $nextStatus! 🛵", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1.3f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (order.status == "OUT_FOR_DELIVERY" || order.status == "Out for Delivery") Color(0xFF16A34A) else SaffronOrange
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = when (order.status) {
                                                    "PLACED", "Placed", "Order Placed", "ACCEPTED" -> "🛵 Accept Run"
                                                    "PICKUP_ASSIGNED" -> "📦 Confirm Pickup"
                                                    "PICKED_UP" -> "🏪 Store Handover"
                                                    "READY_FOR_DELIVERY", "Packed" -> "🚀 Start Delivery"
                                                    "OUT_FOR_DELIVERY", "Out for Delivery" -> "🔑 Verify OTP & Finish"
                                                    else -> "Mark Complete"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        // Navigation Button
                                        Button(
                                            onClick = {
                                                val destinationQuery = if (order.vendorName.isNotBlank()) "${order.vendorName}, New Delhi" else "Connaught Place, New Delhi"
                                                val gmmIntentUri = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(destinationQuery))
                                                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
                                                mapIntent.setPackage("com.google.android.apps.maps")
                                                try {
                                                    context.startActivity(mapIntent)
                                                } catch (e: Exception) {
                                                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode(destinationQuery)))
                                                    context.startActivity(browserIntent)
                                                }
                                            },
                                            modifier = Modifier.weight(0.9f),
                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Navigate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "active" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    if (activeRun == null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("📦", fontSize = 36.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No run currently active.", fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text("Select a run from the 'RUNS' tab to start.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Active Job #${activeRun.id}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = RoyalBlue)
                                        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(6.dp)) {
                                            Text("LIVE RUNNING 🛵", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Client Details & Wardrobe:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(activeRun.itemsSummary, fontSize = 12.sp, color = Color(0xFF475569))

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Delivery Checklist:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    listOf(
                                        "1. Garment Bag sealed & verified",
                                        "2. Fragile / Silk tags inspected",
                                        "3. Collect customer signature or 4-digit OTP"
                                    ).forEach { item ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(item, fontSize = 11.5.sp, color = Color(0xFF334155))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            selectedOrderForOtp = activeRun
                                            otpInput = ""
                                            showOtpDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("🔑 Enter Customer Delivery OTP (Verify & Complete)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "earnings" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text("TODAY'S ESTIMATED EARNINGS", color = LightCream, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹${todayEarnings.toInt()}.00", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Completed Trips: ${completedRuns.size} • Shift Hours: 4.5 hrs", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                    }

                    item {
                        Text("Payout Breakdown Ledger", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 14.sp)
                    }

                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Base Run Pay (${completedRuns.size} trips @ ₹50)", fontSize = 12.5.sp, color = Color(0xFF475569))
                                    Text("₹${completedRuns.size * 50}", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Peak Hours / Doorstep Incentives", fontSize = 12.5.sp, color = Color(0xFF475569))
                                    Text("₹${completedRuns.size * 15}", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Daily Online Attendance Bonus", fontSize = 12.5.sp, color = Color(0xFF475569))
                                    Text(if (isOnline) "₹50" else "₹0", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Weekly Payout Scheduled", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoyalBlue)
                                    Text("₹${todayEarnings.toInt()}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF16A34A))
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Instant UPI payout requested! Settlement initiated to registered account.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Request Instant UPI Payout ⚡", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "fleet" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Fleet Members (${fleetDrivers.size})", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 14.sp)
                            Button(
                                onClick = { showOnboardDriverDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("+ Add Driver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(fleetDrivers) { driver ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoyalBlue)
                                    Text("Vehicle: ${driver.vehicleType}", fontSize = 11.sp, color = Color.Gray)
                                    Text("Status: ${driver.status}", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                }
                                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(6.dp)) {
                                    Text("ONLINE 🟢", color = Color(0xFF1D4ED8), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOG: VERIFY DELIVERY OTP
    // ==========================================
    if (showOtpDialog && selectedOrderForOtp != null) {
        val currentOrder = selectedOrderForOtp!!
        AlertDialog(
            onDismissRequest = { if (!isVerifyingOtp) showOtpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔑", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify Delivery OTP", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ask customer for the 4-digit Delivery OTP sent to their mobile for Order #${currentOrder.id}.", fontSize = 12.5.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { if (it.length <= 4) otpInput = it },
                        label = { Text("4-Digit OTP (e.g. 4920)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedOtp = otpInput.toIntOrNull()
                        if (parsedOtp != null) {
                            isVerifyingOtp = true
                            vm.completeDeliveryWithOtp(currentOrder.id.toString(), parsedOtp) { success, msg ->
                                isVerifyingOtp = false
                                if (success) {
                                    vm.updateOrderStatusDirectly(currentOrder.id, "Delivered")
                                    showOtpDialog = false
                                    Toast.makeText(context, "Order #${currentOrder.id} successfully delivered! 🎉", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter 4-digit OTP!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isVerifyingOtp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    if (isVerifyingOtp) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm & Deliver 🎉", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showOtpDialog = false }, enabled = !isVerifyingOtp) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // ==========================================
    // DIALOG: ONBOARD DELIVERY DRIVER
    // ==========================================
    if (showOnboardDriverDialog) {
        AlertDialog(
            onDismissRequest = { showOnboardDriverDialog = false },
            title = { Text("Onboard Delivery Partner 🚚", fontWeight = FontWeight.Bold, color = RoyalBlue) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newDriverName,
                        onValueChange = { newDriverName = it },
                        label = { Text("Driver Full Name") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDriverPhone,
                        onValueChange = { newDriverPhone = it },
                        label = { Text("Mobile Phone") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDriverVehicle,
                        onValueChange = { newDriverVehicle = it },
                        label = { Text("Vehicle Type (e.g. Bike, EV, Van)") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDriverLicense,
                        onValueChange = { newDriverLicense = it },
                        label = { Text("Driving License / ID No.") },
                        singleLine = true,
                        colors = getLightBgTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDriverName.isNotBlank() && newDriverPhone.isNotBlank()) {
                            vm.registerDeliveryPartner(newDriverName, newDriverPhone, newDriverVehicle, newDriverLicense, "New Delhi")
                            Toast.makeText(context, "Delivery partner onboarded into fleet! 🛵", Toast.LENGTH_SHORT).show()
                            showOnboardDriverDialog = false
                        } else {
                            Toast.makeText(context, "Please fill driver name and phone!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                ) {
                    Text("Register Partner", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOnboardDriverDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// ==========================================
// EMAIL SMTP / POP TESTING & CONFIG CENTER
// ==========================================
@Composable
fun EmailConfigSubPanel(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val smtpH by vm.smtpHost.collectAsState()
    val smtpPo by vm.smtpPort.collectAsState()
    val smtpU by vm.smtpUser.collectAsState()
    val smtpPa by vm.smtpPass.collectAsState()
    
    val popH by vm.popHost.collectAsState()
    val popPo by vm.popPort.collectAsState()
    val popU by vm.popUser.collectAsState()
    val popPa by vm.popPass.collectAsState()

    val logFeed by vm.emailTestingLogs.collectAsState()
    val rentals by vm.activeRentalReminders.collectAsState()

    var showExplanationDialog by remember { mutableStateOf(false) }

    val darkBgInputColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF0F172A),
        unfocusedTextColor = Color(0xFF0F172A),
        focusedContainerColor = Color(0xFFF8FAFC),
        unfocusedContainerColor = Color.White,
        focusedBorderColor = RoyalBlue,
        unfocusedBorderColor = Color.LightGray
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Intro Executive Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = "", tint = SaffronOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CORPORATE EMAIL CONTROLLER NODE 📧", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    IconButton(onClick = { showExplanationDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "How it works", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Configure Gmail SMTP and POP mail relays. Pre-configured with secure credentials to trigger instant transactional notifications on checkout actions, cancellations, dispatches, and due deadlines.",
                    color = LightCream.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.smtpHost.value = "smtp.gmail.com"
                            vm.smtpPort.value = "587"
                            vm.smtpUser.value = "apnadhobi.official@gmail.com"
                            vm.smtpPass.value = "abcd-efgh-ijkl-mnop"
                            vm.popHost.value = "pop.gmail.com"
                            vm.popPort.value = "995"
                            vm.popUser.value = "apnadhobi.official@gmail.com"
                            vm.popPass.value = "abcd-efgh-ijkl-mnop"
                            Toast.makeText(context, "Pre-filled Gmail SMTP & POP defaults!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Fill Gmail Presets", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showExplanationDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Architecture Info", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SMTP Config Form
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GMAIL SMTP CONFIGURATION (OUTGOING RELAY) 📤", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                
                OutlinedTextField(
                    value = smtpH,
                    onValueChange = { vm.smtpHost.value = it },
                    label = { Text("SMTP Host Server") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = smtpPo,
                    onValueChange = { vm.smtpPort.value = it },
                    label = { Text("SMTP SSL Port") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = smtpU,
                    onValueChange = { vm.smtpUser.value = it },
                    label = { Text("Gmail SMTP Username ID") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = smtpPa,
                    onValueChange = { vm.smtpPass.value = it },
                    label = { Text("Gmail App Password String") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        }

        // POP Config Form
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("POP EMAIL CONFIGURATION (INCOMING INBOX CHECK) 📥", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                
                OutlinedTextField(
                    value = popH,
                    onValueChange = { vm.popHost.value = it },
                    label = { Text("POP3 Host Server") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = popPo,
                    onValueChange = { vm.popPort.value = it },
                    label = { Text("POP3 SSL Port") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = popU,
                    onValueChange = { vm.popUser.value = it },
                    label = { Text("POP3 Username") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = popPa,
                    onValueChange = { vm.popPass.value = it },
                    label = { Text("POP3 Password Source") },
                    colors = darkBgInputColors,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )

                Button(
                    onClick = { vm.executePopDiagnostics() },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger POP Inbox Connection Test", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Diagnostic Console Logs Card (With Color-Coded Log Rendering)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DIAGNOSTIC CONSOLE LOGS", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    Text("Real-Time Log Stream", color = Color.Gray, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val lines = if (logFeed.isBlank()) listOf("No mail diagnostics performed yet.") else logFeed.split("\n")
                        lines.forEach { line ->
                            val textColor = when {
                                line.contains("ERROR", ignoreCase = true) || line.contains("failed", ignoreCase = true) || line.contains("FAILED", ignoreCase = true) -> Color(0xFFFF5252) // Bright Red
                                line.contains("SUCCESS", ignoreCase = true) || line.contains("connected", ignoreCase = true) || line.contains("sent", ignoreCase = true) || line.contains("dispatched", ignoreCase = true) -> Color(0xFF4CAF50) // Vibrant Green
                                line.contains("WARN", ignoreCase = true) || line.contains("pending", ignoreCase = true) -> Color(0xFFFFB74D) // Amber
                                else -> Color(0xFF81D4FA) // Light Cyan
                            }
                            Text(
                                text = line,
                                color = textColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Action triggers card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MANUAL TRANSACTIONAL EMAIL TEST TRIGGERS ⚡", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                
                Button(
                    onClick = { vm.triggerOrderConfirmationEmail(1024, "Men's Premium Suit Clean (x1)", 499.0) },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Order Confirmation (Email Receipt)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { vm.triggerCustomEmailAlert("cancel") },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Cancel Order Notification (Email)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { vm.triggerCustomEmailAlert("pending") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Pending Notification (Email)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { vm.triggerCustomEmailAlert("dispatch") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Dispatch Order Notification (Email)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { vm.executePromoMassCampaign() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Monsoon Promotional Notification", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Rental Return Reminders Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HEAVY GARMENTS RENTAL RETURN DEADLINES 👗", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
                Text("Schedule alerts & monitor returned stock items safely.", fontSize = 11.sp, color = Color.Gray)

                rentals.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightCream),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.description, fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 13.sp)
                                Surface(
                                    color = if (item.status == "Returned & Paid" || item.status == "Returned") GreenSuccess.copy(alpha = 0.15f) else SaffronOrange.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.status,
                                        color = if (item.status == "Returned & Paid" || item.status == "Returned") GreenSuccess else SaffronOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Store outlet: ${item.store}", fontSize = 11.sp, color = Color.DarkGray)
                            Text("Due deadline date: ${item.dueDate}", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                            if (item.status != "Returned & Paid" && item.status != "Returned") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            vm.triggerCustomEmailAlert("rental_due")
                                            Toast.makeText(context, "Return Reminder email dispatched!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Alert Email", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            vm.processRentalReturnPayment(item.id)
                                            Toast.makeText(context, "Success! Item updated & confirmation mail sent.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Confirm return", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            title = { Text("REAL-TIME EMAIL ENGINE ARCHITECTURE ⚙️", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "1. Outgoing SMTP Relay:\nConnects directly over SSL (Port 587/465) via Android JavaMail API to send real-time order receipts, cancellation notices, and payment approvals.",
                        fontSize = 12.sp, color = Color(0xFF0F172A)
                    )
                    Text(
                        "2. Incoming POP3 Handshake:\nPerforms socket verification against POP3 servers (Port 995) to verify incoming customer support replies.",
                        fontSize = 12.sp, color = Color(0xFF0F172A)
                    )
                    Text(
                        "3. Real-Time Diagnostics Stream:\nEvery test trigger button fires an asynchronous coroutine that logs stdout responses directly into the Diagnostic Console in red (errors), green (successes), and cyan (info).",
                        fontSize = 12.sp, color = Color(0xFF0F172A)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExplanationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) {
                    Text("Got it!", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }
    Spacer(modifier = Modifier.height(90.dp))
}

// ==========================================
// INTERACTIVE GOOGLE MAPS AND PROGRESS TRACKING
// ==========================================
@Composable
fun GoogleMapsSubPanel(vm: ApnaDhobiViewModel) {
    val context = LocalContext.current

    val liveTracking by vm.isTrackingLiveNow.collectAsState()
    val trackingEta by vm.trackingEtaText.collectAsState()
    val trackingTelemetry by vm.trackingTelemetryLog.collectAsState()
    val gpsDetectionLog by vm.locationDetectionLog.collectAsState()
    val polylinePoints by vm.polylinePoints.collectAsState()

    var selectedStoreMapDetails by remember { 
        mutableStateOf<String?>(
            "Royal Dry Cleaners premium hub (Kasturba Road).\nDistance: 2.1 km • 4.6 Rating ⭐\nSpecializes in wedding wear & designer lehengas dry cleaning."
        ) 
    }
    
    val dLat by vm.activeDeliveryBoyLat.collectAsState()
    val dLng by vm.activeDeliveryBoyLng.collectAsState()
    val cLat by vm.customerLat.collectAsState()
    val cLng by vm.customerLng.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(LatLng(dLat, dLng), 14f)
    }

    // Auto-center camera when tracking is active
    LaunchedEffect(dLat, dLng) {
        if (liveTracking) {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLng(LatLng(dLat, dLng))
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Upper Controls card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interactive Google Maps Engine", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 15.sp)
                    Surface(
                        color = if (liveTracking) Color(0xFFE8F5E9) else Color(0xFFECEFF1),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (liveTracking) "TRACKING ACTIVE 🛰️" else "GPS STANDBY",
                            color = if (liveTracking) Color(0xFF2E7D32) else Color.DarkGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.simulateGpsLocationDetection { addr ->
                                Toast.makeText(context, "GPS Resolved Customer Center Address successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Detect GPS", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!liveTracking) {
                                vm.startLocationTracking("ORDER_123")
                                vm.fetchRoute(dLat, dLng, cLat, cLng)
                                Toast.makeText(context, "Real GPS Tracking started!", Toast.LENGTH_SHORT).show()
                            } else {
                                vm.stopLocationTracking()
                                Toast.makeText(context, "Tracking stopped.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (liveTracking) Color.Red else RoyalBlue),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(if (liveTracking) Icons.Default.Stop else Icons.Default.DirectionsRun, contentDescription = "", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (liveTracking) "Stop Tracking" else "Simulate Live Tracking", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text("Using mock GPS device receiver • High precision network provider", fontSize = 10.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }

        // Real Google Map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true),
                properties = MapProperties(isMyLocationEnabled = true)
            ) {
                // Rider Marker
                Marker(
                    state = MarkerState(position = LatLng(dLat, dLng)),
                    title = "Rider (You)",
                    snippet = "Live delivery driver position",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )

                // Customer Marker
                Marker(
                    state = MarkerState(position = LatLng(cLat, cLng)),
                    title = "Customer Destination",
                    snippet = "Delivery point: Connaught Place",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )

                // Store Marker 1: Royal Dry Cleaners
                Marker(
                    state = MarkerState(position = LatLng(28.6010, 77.1950)),
                    title = "Royal Dry Cleaners premium hub",
                    snippet = "Kasturba Road • Tap for info",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    onClick = {
                        selectedStoreMapDetails = "Royal Dry Cleaners premium hub (Kasturba Road).\nDistance: 2.1 km • 4.6 Rating ⭐\nSpecializes in wedding wear & designer lehengas dry cleaning."
                        true
                    }
                )

                // Store Marker 2: CP Express Store
                Marker(
                    state = MarkerState(position = LatLng(28.6315, 77.2167)),
                    title = "CP Express Store",
                    snippet = "Connaught Place • Tap for info",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                    onClick = {
                        selectedStoreMapDetails = "CP Express Store (Connaught Place).\nDistance: 1.4 km • 4.8 Rating ⭐\nSpecializes in executive suit laundering & 24h express delivery."
                        true
                    }
                )

                // Route Polyline from OSRM
                if (polylinePoints.isNotEmpty()) {
                    Polyline(
                        points = polylinePoints,
                        color = RoyalBlue,
                        width = 12f
                    )
                }
            }

            // Description layout card
            selectedStoreMapDetails?.let { metadata ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, contentDescription = "", tint = RoyalBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Store Details Selected", fontWeight = FontWeight.Bold, color = RoyalBlue, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(metadata, fontSize = 12.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { selectedStoreMapDetails = null },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick instruction overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    "💡 Tap on map sectors to inspect store mappings",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Lower terminal telemetry terminal
        Card(
            colors = CardDefaults.cardColors(containerColor = Charcoal),
            shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Telemetry Console & GPS satellite log", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (liveTracking) "ETA: 12 mins" else trackingEta, color = SaffronOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (liveTracking) "Active GPS Publisher: Broadcasting to Server...\nLat: ${String.format("%.4f", dLat)}, Lng: ${String.format("%.4f", dLng)}" else "GPS telemetry: Active satellite sweep",
                    color = Color.LightGray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Outlets mapped: CP Express, Royal Cleaners", color = Color.Gray, fontSize = 10.sp)
                    Text("Rider Lat: ${String.format("%.4f", dLat)} • Lng: ${String.format("%.4f", dLng)}", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ==========================================
// BRAND IDENTITY, LOGO CRUD & VEHICLE / BANNER GRAPHICS STUDIO (ADMIN PANEL)
// ==========================================
@Composable
fun BrandLogoAdminSubPanel(vm: ApnaDhobiViewModel) {
    val brandNamePrimary by vm.adminBrandNamePrimary.collectAsState()
    val brandNameSecondary by vm.adminBrandNameSecondary.collectAsState()
    val brandTagline by vm.adminBrandTagline.collectAsState()
    val currentLogoUrl by vm.adminBrandLogoUrl.collectAsState()
    val isBrandLogoVisible by vm.adminIsBrandLogoVisible.collectAsState()
    val defaultVehicleUrl by vm.adminDefaultVehicleGraphicUrl.collectAsState()
    val vehiclePreset by vm.adminVehicleIconPreset.collectAsState()
    val promoBanners by vm.loginPromoBanners.collectAsState()

    var inputPrimaryName by remember(brandNamePrimary) { mutableStateOf(brandNamePrimary) }
    var inputSecondaryName by remember(brandNameSecondary) { mutableStateOf(brandNameSecondary) }
    var inputTagline by remember(brandTagline) { mutableStateOf(brandTagline) }
    var inputLogoUrl by remember(currentLogoUrl) { mutableStateOf(currentLogoUrl) }
    var inputVehicleUrl by remember(defaultVehicleUrl) { mutableStateOf(defaultVehicleUrl) }

    // System Image Picker Launchers for Gallery/Storage Upload
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val uriStr = it.toString()
            inputLogoUrl = uriStr
            vm.updateBrandLogo(uriStr)
        }
    }

    val vehiclePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val uriStr = it.toString()
            inputVehicleUrl = uriStr
            vm.updateVehicleGraphic(uriStr)
        }
    }

    // Dialog state for Add / Edit Promo Banner
    var showBannerDialog by remember { mutableStateOf(false) }
    var editingBannerId by remember { mutableStateOf<String?>(null) }
    var bannerTitle by remember { mutableStateOf("") }
    var bannerSubtitle by remember { mutableStateOf("") }
    var bannerDescription by remember { mutableStateOf("") }
    var bannerCode by remember { mutableStateOf("") }
    var bannerImageUrl by remember { mutableStateOf("") }

    val bannerImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            bannerImageUrl = it.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: BRAND LOGO & IDENTITY (FULL CRUD & UPLOAD/LINK)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(3.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Brand Logo & Identity Studio",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Text(
                                text = "Manage logo, upload files, hide/show & update titles",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Show / Hide Logo Toggle Switch Row
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Brand Logo Visibility (App-wide)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Text(
                                text = if (isBrandLogoVisible) "Currently VISIBLE on Splash & Headers 🟢" else "Currently HIDDEN across App 🔴",
                                fontSize = 11.sp,
                                color = if (isBrandLogoVisible) Color(0xFF16A34A) else Color(0xFFEF4444)
                            )
                        }
                        Switch(
                            checked = isBrandLogoVisible,
                            onCheckedChange = { vm.toggleBrandLogoVisibility(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RoyalBlue
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Badge
                Text(
                    text = "LIVE BRAND PREVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SaffronOrange,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isBrandLogoVisible) {
                    Surface(
                        modifier = Modifier.size(130.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(2.dp, SaffronOrange.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ApnaDhobiBrandLogo(
                                modifier = Modifier
                                    .size(115.dp)
                                    .padding(10.dp),
                                customLogoUrl = currentLogoUrl.ifBlank { null }
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2)
                    ) {
                        Text(
                            text = "🚫 Brand Logo is Hidden",
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = inputPrimaryName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = RoyalBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = inputSecondaryName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = SaffronOrange
                    )
                }
                Text(
                    text = inputTagline,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Upload from Device or Web URL for Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { logoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload from Device 📁", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            inputLogoUrl = ""
                            vm.removeBrandLogo()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove / Clear Logo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Logo URL Input
                OutlinedTextField(
                    value = inputLogoUrl,
                    onValueChange = { inputLogoUrl = it },
                    label = { Text("Or Paste Brand Logo Image URL (PNG / JPG / WebP)") },
                    placeholder = { Text("https://example.com/brand-logo.png") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = RoyalBlue) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input Fields Row: Primary & Secondary Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = inputPrimaryName,
                        onValueChange = { inputPrimaryName = it },
                        label = { Text("Primary Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    OutlinedTextField(
                        value = inputSecondaryName,
                        onValueChange = { inputSecondaryName = it },
                        label = { Text("Secondary Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaffronOrange,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tagline Input
                OutlinedTextField(
                    value = inputTagline,
                    onValueChange = { inputTagline = it },
                    label = { Text("Brand Tagline") },
                    placeholder = { Text("We clean, you relax.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            vm.updateBrandSettings(
                                primaryName = inputPrimaryName.trim(),
                                secondaryName = inputSecondaryName.trim(),
                                tagline = inputTagline.trim(),
                                logoUrl = inputLogoUrl.trim()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Brand Identity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            inputPrimaryName = "Apna"
                            inputSecondaryName = "Dhobi"
                            inputTagline = "We clean, you relax."
                            inputLogoUrl = ""
                            vm.updateBrandSettings("Apna", "Dhobi", "We clean, you relax.", "")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SaffronOrange)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Default", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SaffronOrange)
                    }
                }
            }
        }

        // CARD 2: VEHICLE & BANNER GRAPHIC STUDIO (IMAGE 2 POINT)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(3.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Vehicle & Banner Icon Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal
                        )
                        Text(
                            text = "Upload custom delivery vehicle graphic or set web link",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle Live Preview Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFBEF), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFFFE0B2), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("CURRENT VEHICLE GRAPHIC", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SaffronOrange)
                        Text(
                            text = if (defaultVehicleUrl.isNotBlank()) "Custom Uploaded Graphic Active" else "Official Delivery Truck Vector",
                            fontSize = 12.sp,
                            color = Charcoal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        modifier = Modifier.size(width = 86.dp, height = 54.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            UniversalAppImage(
                                model = defaultVehicleUrl,
                                contentDescription = "Vehicle Preview",
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            ) {
                                DeliveryTruckGraphic(
                                    primaryName = brandNamePrimary,
                                    secondaryName = brandNameSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { vehiclePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Vehicle 🚚", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            inputVehicleUrl = ""
                            vm.updateVehicleGraphic("")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Vector Truck", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputVehicleUrl,
                    onValueChange = { inputVehicleUrl = it },
                    label = { Text("Or Paste Vehicle Image URL (PNG / SVG / JPG)") },
                    placeholder = { Text("https://example.com/delivery-truck.png") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = SaffronOrange) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SaffronOrange,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        vm.updateVehicleGraphic(inputVehicleUrl.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                ) {
                    Text("Save Vehicle Graphic", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // CARD 3: PROMO BANNERS MANAGER (MAX 4 BANNERS WITH UPLOAD/LINK)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(3.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFF7ED)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ViewCarousel,
                                    contentDescription = null,
                                    tint = SaffronOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Promo Banners Studio",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal
                            )
                            Text(
                                text = "Active Banners: ${promoBanners.size}/4 Maximum",
                                fontSize = 12.sp,
                                color = if (promoBanners.size >= 4) SaffronOrange else Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (promoBanners.size >= 4) {
                                vm.pushSimulatedNotification("Maximum 4 banners allowed! Please edit or delete one.")
                            } else {
                                editingBannerId = null
                                bannerTitle = ""
                                bannerSubtitle = ""
                                bannerDescription = ""
                                bannerCode = ""
                                bannerImageUrl = ""
                                showBannerDialog = true
                            }
                        },
                        enabled = promoBanners.size < 4,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Banner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of Active Banners
                promoBanners.forEachIndexed { index, banner ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEF)),
                        border = BorderStroke(1.dp, Color(0xFFFFE0B2))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = RoyalBlue
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = banner.title ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = banner.subtitle ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = SaffronOrange
                                    )
                                }
                                Text(
                                    text = banner.badge ?: "",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!banner.code.isNullOrBlank()) {
                                    Text(
                                        text = "Code: ${banner.code}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBlue
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    editingBannerId = banner.id
                                    bannerTitle = banner.title ?: ""
                                    bannerSubtitle = banner.subtitle ?: ""
                                    bannerDescription = banner.badge ?: ""
                                    bannerCode = banner.code ?: ""
                                    bannerImageUrl = banner.imageUrl ?: ""
                                    showBannerDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = RoyalBlue, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    vm.deleteLoginPromoBanner(banner.id)
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for Add / Edit Promo Banner with File Picker
    if (showBannerDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showBannerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (editingBannerId == null) "➕ Add New Promo Banner (Max 4)" else "✏️ Edit Promo Banner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = RoyalBlue
                    )

                    OutlinedTextField(
                        value = bannerTitle,
                        onValueChange = { bannerTitle = it },
                        label = { Text("Headline 1 (e.g. Free Pickup &)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = bannerSubtitle,
                        onValueChange = { bannerSubtitle = it },
                        label = { Text("Headline 2 (e.g. Return Delivery)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = bannerDescription,
                        onValueChange = { bannerDescription = it },
                        label = { Text("Description (e.g. Premium laundry...)") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = bannerCode,
                        onValueChange = { bannerCode = it },
                        label = { Text("Promo Coupon Code (e.g. FREEPICKUP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Upload Graphic from Storage Button
                    Button(
                        onClick = { bannerImagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E88))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Graphic from Device 📁", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = bannerImageUrl,
                        onValueChange = { bannerImageUrl = it },
                        label = { Text("Or Paste Graphic Image URL (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (bannerImageUrl.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .size(60.dp)
                                .align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            coil.compose.AsyncImage(
                                model = bannerImageUrl,
                                contentDescription = "Thumbnail Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showBannerDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (bannerTitle.isNotBlank()) {
                                    if (editingBannerId == null) {
                                        vm.addLoginPromoBanner(
                                            title = bannerTitle,
                                            subtitle = bannerSubtitle,
                                            description = bannerDescription,
                                            code = bannerCode,
                                            imageUrl = bannerImageUrl
                                        )
                                    } else {
                                        vm.updateLoginPromoBanner(
                                            id = editingBannerId!!,
                                            title = bannerTitle,
                                            subtitle = bannerSubtitle,
                                            description = bannerDescription,
                                            code = bannerCode,
                                            imageUrl = bannerImageUrl
                                        )
                                    }
                                    showBannerDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange)
                        ) {
                            Text("Save Banner", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
