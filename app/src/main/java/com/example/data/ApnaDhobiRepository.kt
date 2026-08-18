package com.example.data

import com.example.data.api.*
import com.example.data.dto.*
import com.example.data.network.RetrofitClient
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// Hardcoded model types for robust UI reference
data class Vendor(
    val id: String,
    val name: String,
    val description: String,
    val rating: Double,
    val distanceKm: Double,
    val deliveryTimeMins: Int,
    val startingPrice: Int,
    val bannerColorHex: String,
    val logoText: String,
    val isOpen: Boolean = true
)

data class ServiceCategory(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconName: String
)

data class LaundryProduct(
    val id: String,
    val name: String,
    val categoryId: String, // e.g., "laundry", "dry_cleaning"
    val originalPrice: Double,
    val discountPrice: Double,
    val deliveryEstimate: String,
    val popularBadge: String? = null
)

class ApnaDhobiRepository(private val dao: ApnaDhobiDao) {

    private val authApi = RetrofitClient.authApi
    private val catalogApi = RetrofitClient.catalogApi
    private val ordersApi = RetrofitClient.ordersApi
    private val walletApi = RetrofitClient.walletApi
    private val vendorsApi = RetrofitClient.vendorsApi
    private val staffApi = RetrofitClient.staffApi
    private val uploadsApi = RetrofitClient.uploadsApi

    // Auth Actions
    suspend fun sendOtp(phone: String): Boolean {
        return try {
            authApi.sendOtp(SendOtpRequest(phone.trim())).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkRegistration(phone: String): Boolean {
        return try {
            val response = authApi.checkRegistration(phone.trim())
            response.isSuccessful && response.body()?.get("isRegistered") == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun validateReferral(code: String): Boolean {
        return try {
            val response = authApi.validateReferral(code.trim())
            response.isSuccessful && response.body()?.get("isValid") == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun register(name: String, email: String, phone: String, referralCode: String?): AuthResponse? {
        return try {
            val response = authApi.register(RegisterRequest(phone.trim(), name.trim(), email.trim(), referralCode?.trim()))
            if (response.isSuccessful) {
                response.body()?.also { auth ->
                    auth.accessToken?.let { RetrofitClient.setAuthToken(it) }
                    auth.user?.let { user ->
                        createUserProfile(UserProfile(
                            phone = user.phone,
                            name = user.name ?: "",
                            email = user.email ?: "",
                            roles = user.roles.joinToString(",")
                        ))
                    }
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun verifyOtp(phone: String, otp: String): AuthResponse? {
        return try {
            val response = authApi.verifyOtp(VerifyOtpRequest(phone.trim(), otp.trim()))
            Log.d("ApnaDhobiRepository", "OTP Verification Response: ${response.code()}")
            if (response.isSuccessful) {
                response.body()?.also { auth ->
                    // Set token for following requests only if we have one
                    auth.accessToken?.let { token ->
                        RetrofitClient.setAuthToken(token)
                        auth.user?.let { user ->
                            // Save user to local DB
                            createUserProfile(UserProfile(
                                phone = user.phone,
                                name = user.name ?: "",
                                email = user.email ?: ""
                            ))
                        }
                    }
                }
            } else {
                Log.e("ApnaDhobiRepository", "OTP Verification Failed: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "OTP Verification Error", e)
            null
        }
    }

    suspend fun createRazorpayOrder(amount: Double): Map<String, Any>? {
        return try {
            val response = walletApi.createRazorpayOrder(mapOf("amount" to amount))
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun verifyRazorpayPayment(orderId: String, paymentId: String, signature: String): Boolean {
        return try {
            val body = mapOf(
                "razorpayOrderId" to orderId,
                "razorpayPaymentId" to paymentId,
                "razorpaySignature" to signature
            )
            walletApi.verifyPayment(body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun attemptGoogleLogin(email: String, name: String): AuthResponse? {
        return try {
            val body = mapOf("email" to email, "name" to name)
            val response = authApi.googleLogin(body)
            if (response.isSuccessful) {
                response.body()?.also { auth ->
                    auth.accessToken?.let { RetrofitClient.setAuthToken(it) }
                    auth.user?.let { user ->
                        createUserProfile(UserProfile(
                            phone = user.phone,
                            name = user.name ?: "",
                            email = user.email ?: ""
                        ))
                    }
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchDeliveryAgents(): List<String> {
        return try {
            val response = authApi.getDeliveryAgents()
            if (response.isSuccessful) {
                response.body()?.mapNotNull { it.name } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Catalog Actions (Network)
    suspend fun fetchCategories(): List<ServiceCategory> {
        return try {
            val response = catalogApi.getCategories()
            if (response.isSuccessful) {
                response.body()?.map { 
                    ServiceCategory(it.id, it.name, it.colorHex ?: "RoyalBlue", it.iconName ?: "Check")
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVendors(): List<Vendor> {
        return try {
            val response = catalogApi.getVendors()
            if (response.isSuccessful) {
                response.body()?.map { 
                    Vendor(it.id, it.name, it.description ?: "", it.rating, 1.2, 45, 49, it.bannerColor ?: "0xFF0D47A1", it.logoText ?: "AD")
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchProducts(categoryId: String? = null, vendorId: String? = null): List<LaundryProduct> {
        return try {
            val response = catalogApi.getServices(categoryId, vendorId)
            if (response.isSuccessful) {
                response.body()?.map { 
                    LaundryProduct(it.id, it.name, it.categoryId, it.originalPrice, it.discountPrice, it.deliveryEstimate ?: "Same Day", it.popularBadge)
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createService(name: String, categoryId: String, originalPrice: Double, discountPrice: Double, deliveryEstimate: String): Boolean {
        return try {
            val body = mapOf(
                "name" to name,
                "categoryId" to categoryId,
                "originalPrice" to originalPrice,
                "discountPrice" to discountPrice,
                "deliveryEstimate" to deliveryEstimate
            )
            catalogApi.createService(body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateServicePrice(id: String, newPrice: Double): Boolean {
        return try {
            val body = mapOf("discountPrice" to newPrice)
            catalogApi.updateService(id, body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteService(id: String): Boolean {
        return try {
            catalogApi.deleteService(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchBanners(): List<BannerDto> {
        return try {
            val response = catalogApi.getBanners()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createBanner(title: String, subtitle: String, code: String, badge: String): Boolean {
        return try {
            val body = mapOf("title" to title, "subtitle" to subtitle, "code" to code, "badge" to badge)
            catalogApi.createBanner(body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteBanner(id: String): Boolean {
        return try {
            catalogApi.deleteBanner(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // Wallet Actions
    suspend fun fetchWalletBalance(): Double {
        return try {
            val response = walletApi.getBalance()
            if (response.isSuccessful) {
                (response.body()?.get("balance") as? Number)?.toDouble() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun createRechargeRequest(userName: String, userEmail: String, amount: Double, paymentMethod: String, referenceId: String): Boolean {
        return try {
            val body = mapOf(
                "userName" to userName,
                "userEmail" to userEmail,
                "amount" to amount,
                "paymentMethod" to paymentMethod,
                "referenceId" to referenceId
            )
            walletApi.createRechargeRequest(body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchAllRechargeRequests(): List<Map<String, Any>> {
        return try {
            val response = walletApi.getAllRechargeRequests()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun approveRechargeRequest(id: String): Boolean {
        return try {
            walletApi.approveRechargeRequest(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun rejectRechargeRequest(id: String): Boolean {
        return try {
            walletApi.rejectRechargeRequest(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // Vendor Actions
    suspend fun registerVendor(name: String, description: String, address: String, logoText: String, bannerColor: String): Vendor? {
        return try {
            val body = mapOf(
                "name" to name,
                "description" to description,
                "address" to address,
                "logoText" to logoText,
                "bannerColor" to bannerColor
            )
            val response = vendorsApi.register(body)
            if (response.isSuccessful) {
                response.body()?.let { 
                    Vendor(it.id, it.name, it.description ?: "", it.rating, 1.2, 45, 49, it.bannerColor ?: "0xFF0D47A1", it.logoText ?: "AD")
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toggleVendorStatus(vendorId: String, isOpen: Boolean): Boolean {
        return try {
            val body = mapOf("isOpen" to isOpen)
            vendorsApi.toggleStatus(vendorId, body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchVendorStats(vendorId: String): VendorStatsDto? {
        return try {
            val response = vendorsApi.getStats(vendorId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchOrders(): List<OrderRecord> {
        return try {
            val response = ordersApi.getOrders()
            if (response.isSuccessful) {
                response.body()?.map { dto ->
                    // Try to find vendor name from local list using ID or fallback
                    val vName = vendors.find { it.id == dto.id }?.name ?: "Laundromat Hub"
                    OrderRecord(
                        id = dto.id.hashCode(),
                        vendorName = vName,
                        itemsSummary = dto.itemsSummary,
                        totalPrice = dto.totalPrice,
                        pickupSlot = dto.pickupSlot,
                        deliverySlot = dto.deliverySlot,
                        paymentMethod = dto.paymentMethod,
                        status = dto.status
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVendorOrders(vendorId: String): List<OrderRecord> {
        return try {
            val response = ordersApi.getVendorOrders(vendorId)
            if (response.isSuccessful) {
                response.body()?.map { dto ->
                    OrderRecord(
                        id = dto.id.hashCode(),
                        vendorName = "My Shop", // Since it's vendor view
                        itemsSummary = dto.itemsSummary,
                        totalPrice = dto.totalPrice,
                        pickupSlot = dto.pickupSlot,
                        deliverySlot = dto.deliverySlot,
                        paymentMethod = dto.paymentMethod,
                        status = dto.status
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createOrder(vendorId: String, itemsSummary: String, totalPrice: Double, pickupSlot: String, deliverySlot: String, paymentMethod: String, useWallet: Boolean): OrderDto? {
        return try {
            val request = CreateOrderRequest(vendorId, itemsSummary, totalPrice, pickupSlot, deliverySlot, paymentMethod, useWallet)
            val response = ordersApi.createOrder(request)
            if (response.isSuccessful) {
                val dto = response.body()
                if (dto != null) {
                    // Sync local DB
                    dao.insertOrder(OrderRecord(
                        vendorName = vendorId, // ID used for simplicity here, real name comes from UI
                        itemsSummary = itemsSummary,
                        totalPrice = totalPrice,
                        pickupSlot = pickupSlot,
                        deliverySlot = deliverySlot,
                        paymentMethod = paymentMethod,
                        status = dto.status
                    ))
                }
                dto
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateOrderStatusRemote(orderId: String, status: String): Boolean {
        return try {
            ordersApi.updateOrderStatus(orderId, mapOf("status" to status)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun findOrderByQr(qrCode: String): OrderDto? {
        return try {
            val response = ordersApi.findByQr(qrCode)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    // Staff Actions
    suspend fun registerWorker(phone: String, name: String): Boolean {
        return try {
            staffApi.registerWorker(mapOf("phone" to phone, "name" to name)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchWorkers(): List<Map<String, Any>> {
        return try {
            val response = staffApi.getWorkers()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun assignTask(orderId: String, type: String, workerId: String?): Boolean {
        return try {
            val body = mutableMapOf("orderId" to orderId, "type" to type)
            workerId?.let { body["workerId"] = it }
            staffApi.assignTask(body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // File Upload
    suspend fun uploadFile(part: okhttp3.MultipartBody.Part): String? {
        return try {
            val response = uploadsApi.uploadFile(part)
            if (response.isSuccessful) response.body()?.get("url") as? String else null
        } catch (e: Exception) {
            null
        }
    }

    // Reactive State Flows
    val cartItems: Flow<List<CartItem>> = dao.getCartItemsFlow()
    val savedAddresses: Flow<List<SavedAddress>> = dao.getAddressesFlow()
    val orders: Flow<List<OrderRecord>> = dao.getOrdersFlow()
    val chatMessages: Flow<List<SupportMessage>> = dao.getMessagesFlow()

    // Database Actions
    suspend fun insertCartItem(item: CartItem) = dao.insertCartItem(item)
    suspend fun updateCartItem(item: CartItem) = dao.updateCartItem(item)
    suspend fun removeCartItem(item: CartItem) = dao.deleteCartItem(item)
    suspend fun removeCartItemById(itemId: String) = dao.deleteCartItemById(itemId)
    suspend fun clearCart() = dao.clearCart()

    suspend fun addAddress(address: SavedAddress) = dao.insertAddress(address)
    suspend fun removeAddressById(id: Int) = dao.deleteAddressById(id)

    suspend fun placeOrder(order: OrderRecord) {
        dao.insertOrder(order)
        dao.clearCart()
    }

    suspend fun updateOrderStatus(orderId: Int, status: String) {
        dao.updateOrderStatus(orderId, status)
    }

    suspend fun updateOrderDetails(orderId: Int, weight: Double, count: Int, bagId: String) {
        dao.updateOrderDetails(orderId, weight, count, bagId)
    }

    suspend fun updateOrderDetailsRemote(orderId: String, weight: Double, count: Int, bagId: String): Boolean {
        return try {
            val body = mapOf("weightKg" to weight, "verifiedItemCount" to count, "bagId" to bagId)
            ordersApi.updateOrderDetails(orderId, body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAllOrders() {
        dao.clearAllOrders()
    }

    suspend fun insertChatMessage(message: SupportMessage) = dao.insertMessage(message)

    // User Profile Actions
    val allUserProfiles: Flow<List<UserProfile>> = dao.getAllUserProfilesFlow()
    suspend fun getUserProfileByPhone(phone: String): UserProfile? = dao.getUserProfileByPhone(phone)
    suspend fun getUserProfileByEmail(email: String): UserProfile? = dao.getUserProfileByEmail(email)
    suspend fun createUserProfile(profile: UserProfile) = dao.insertUserProfile(profile)
    suspend fun clearAllUserProfiles() = dao.clearAllUserProfiles()

    // Visual Mock Data sources mapping Indian premium aesthetics
    val serviceCategories = listOf(
        ServiceCategory("laundry", "Laundry", "RoyalBlue", "LocalLaundryService"),
        ServiceCategory("dry_cleaning", "Dry Cleaning", "SaffronOrange", "DryCleaning"),
        ServiceCategory("ironing", "Ironing", "RoyalBlueLight", "Iron"),
        ServiceCategory("shoe_cleaning", "Shoe Cleaning", "GoldPremium", "CleaningServices"),
        ServiceCategory("carpet_cleaning", "Carpet Cleaning", "GreenSuccess", "RollerShades"),
        ServiceCategory("blanket_wash", "Blanket Wash", "SaffronOrangeLight", "AcUnit"),
        ServiceCategory("wedding_wear", "Wedding Wear", "Coral", "Star"),
        ServiceCategory("premium_care", "Premium Care", "Charcoal", "Favorite")
    )

    val vendors = listOf(
        Vendor(
            id = "vendor_1",
            name = "Apna Dhobi Express",
            description = "Express laundry, ironing & quick fabric care",
            rating = 4.8,
            distanceKm = 1.2,
            deliveryTimeMins = 45,
            startingPrice = 49,
            bannerColorHex = "0xFF0D47A1",
            logoText = "ADE",
            isOpen = true
        ),
        Vendor(
            id = "vendor_2",
            name = "Royal Dry Cleaners & Dyers",
            description = "Premium dry cleaning for wedding wear & designer wear",
            rating = 4.6,
            distanceKm = 2.5,
            deliveryTimeMins = 120,
            startingPrice = 99,
            bannerColorHex = "0xFFFF6B00",
            logoText = "RDC",
            isOpen = true
        ),
        Vendor(
            id = "vendor_3",
            name = "Smart Ironing Hub",
            description = "Crisp steam ironing with free hangers & delivery",
            rating = 4.9,
            distanceKm = 0.8,
            deliveryTimeMins = 30,
            startingPrice = 15,
            bannerColorHex = "0xFF00A86B",
            logoText = "SIH",
            isOpen = true
        ),
        Vendor(
            id = "vendor_4",
            name = "The Elite Shoe & Blanket Care",
            description = "Intricate deep clean for blankets, leather shoes & jackets",
            rating = 4.7,
            distanceKm = 3.1,
            deliveryTimeMins = 180,
            startingPrice = 199,
            bannerColorHex = "0xFFF4B400",
            logoText = "ESC",
            isOpen = true
        )
    )

    val products = listOf(
        // Laundry Service (laundry)
        LaundryProduct("prod_l1", "Men's Shirt Wash & Fold", "laundry", 60.0, 49.0, "Same Day", "Bestseller"),
        LaundryProduct("prod_l2", "Men's T-Shirt Wash", "laundry", 50.0, 39.0, "Same Day"),
        LaundryProduct("prod_l3", "Women's Kurti Wash", "laundry", 80.0, 59.0, "Same Day", "Hot Deal"),
        LaundryProduct("prod_l4", "Jeans Laundry / Clean", "laundry", 90.0, 75.0, "1 Day"),
        LaundryProduct("prod_l5", "Bed Sheet Double Laundry", "laundry", 150.0, 119.0, "1 Day"),

        // Dry Cleaning (dry_cleaning)
        LaundryProduct("prod_dc1", "Premium Designer Kurta Dry Clean", "dry_cleaning", 199.0, 149.0, "2 Days", "Essential"),
        LaundryProduct("prod_dc2", "Heavy Wedding Saree Dry Clean", "dry_cleaning", 399.0, 299.0, "3 Days", "Premium Choice"),
        LaundryProduct("prod_dc3", "Men's Suit 2-Piece Dry Clean", "dry_cleaning", 499.0, 399.0, "2 Days", "Popular"),
        LaundryProduct("prod_dc4", "Winter Leather Jacket Care", "dry_cleaning", 599.0, 449.0, "3 Days"),
        LaundryProduct("prod_dc5", "Double Bed Blanket Dry Clean", "dry_cleaning", 350.0, 279.0, "2 Days"),

        // Ironing (ironing)
        LaundryProduct("prod_i1", "Men's Casual Shirt Steam Iron", "ironing", 20.0, 14.0, "30 Mins", "Lightning Fast"),
        LaundryProduct("prod_i2", "Formal Trousers Crisp Press", "ironing", 25.0, 18.0, "45 Mins"),
        LaundryProduct("prod_i3", "Premium Silk Saree Delicate Press", "ironing", 80.0, 59.0, "2 Hours", "Handled with Care"),
        LaundryProduct("prod_i4", "Kids School Uniform Crisp Press", "ironing", 20.0, 12.0, "1 Hour"),

        // Shoe Cleaning
        LaundryProduct("prod_s1", "Sports Sneaker Deep Clean & Polish", "shoe_cleaning", 299.0, 199.0, "1 Day", "Like New"),
        LaundryProduct("prod_s2", "Formal Leather Shoe Wax Shine", "shoe_cleaning", 199.0, 149.0, "Same Day"),

        // Carpet Cleaning (carpet_cleaning)
        LaundryProduct("prod_c1", "Premium Woolen Persian Carpet Deep Clean", "carpet_cleaning", 1099.0, 899.0, "3 Days", "Deluxe"),
        LaundryProduct("prod_c2", "Office Synthetic Carpet Shampooing", "carpet_cleaning", 699.0, 499.0, "2 Days"),

        // Blanket Wash (blanket_wash)
        LaundryProduct("prod_b1", "Double Bed Mink Blanket Sanitization", "blanket_wash", 399.0, 299.0, "2 Days", "Cozy Wash"),
        LaundryProduct("prod_b2", "Single Bed Fleece Blanket Eco Wash", "blanket_wash", 250.0, 199.0, "1 Day"),

        // Wedding Wear (wedding_wear)
        LaundryProduct("prod_w1", "Heavy Designer Bridal Lehenga Dry Clean", "wedding_wear", 1999.0, 1499.0, "4 Days", "Royal Care"),
        LaundryProduct("prod_w2", "Groom Embroidered Sherwani Dry Clean", "wedding_wear", 1599.0, 1199.0, "3 Days"),

        // Premium Care (premium_care)
        LaundryProduct("prod_jc1", "Pure Silk Saree Delicate Hydro-Clean", "premium_care", 500.0, 399.0, "2 Days", "Petal Soft"),
        LaundryProduct("prod_jc2", "Premium Cashmere Sweater Hand Wash", "premium_care", 350.0, 249.0, "2 Days")
    )

    fun getProductsByCategoryId(catId: String): List<LaundryProduct> {
        return products.filter { it.categoryId == catId }
    }
}
