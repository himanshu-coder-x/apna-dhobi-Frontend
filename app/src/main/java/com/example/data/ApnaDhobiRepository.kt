package com.example.data

import com.example.data.api.*
import com.example.data.dto.*
import com.example.data.network.RetrofitClient
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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
    val isOpen: Boolean = true,
    val imageUrl: String? = null,
    val address: String = "1012 Ocean Avenue, Connaught Place, New Delhi",
    val categoryTag: String = "Laundry",
    val ratingCount: Int = 365,
    val providerName: String = "Jenny Wilson",
    val providerRole: String = "Service Provider & Specialist",
    val providerPhone: String = "+91 98765 43210",
    val services: List<String> = listOf("Wash & Fold", "Dry Cleaning", "Carpet Washing", "Wash & Iron", "Free Pickup", "24h Delivery"),
    val galleryImages: List<String> = listOf(
        "https://images.unsplash.com/photo-1545173168-9f1947eebb7f?w=600&q=80",
        "https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?w=600&q=80",
        "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?w=600&q=80",
        "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&q=80"
    )
)

data class VendorReview(
    val id: String,
    val vendorId: String,
    val author: String,
    val rating: Double,
    val comment: String,
    val date: String = "Today",
    val verified: Boolean = true
)

data class ServiceCategory(
    val id: String,
    val name: String,
    val colorHex: String = "#2563EB",
    val iconName: String = "LocalLaundryService",
    val imageUrl: String? = null,
    val description: String? = null,
    val turnaroundTime: String? = null,
    val startingPrice: String? = null,
    val popularTag: String? = null,
    val displayOrder: Int = 0
)

data class LaundryProduct(
    val id: String,
    val name: String,
    val categoryId: String, // e.g., "laundry", "dry_cleaning"
    val originalPrice: Double,
    val discountPrice: Double,
    val deliveryEstimate: String,
    val popularBadge: String? = null,
    val imageUrl: String? = null
)

class ApnaDhobiRepository(private val dao: ApnaDhobiDao) {

    private val authApi = RetrofitClient.authApi
    private val catalogApi = RetrofitClient.catalogApi
    private val ordersApi = RetrofitClient.ordersApi
    private val walletApi = RetrofitClient.walletApi
    private val vendorsApi = RetrofitClient.vendorsApi
    private val staffApi = RetrofitClient.staffApi
    private val uploadsApi = RetrofitClient.uploadsApi
    private val usersApi = RetrofitClient.usersApi
    private val addressesApi = RetrofitClient.addressesApi
    private val supportApi = RetrofitClient.supportApi
    private val couponsApi = RetrofitClient.couponsApi

    // Auth Actions
    suspend fun sendOtp(phone: String): SendOtpResponse? {
        return try {
            val response = authApi.sendOtp(SendOtpRequest(phone.trim()))
            if (!response.isSuccessful) {
                Log.e("ApnaDhobiRepository", "sendOtp failed: HTTP ${response.code()} - ${response.message()}")
                null
            } else {
                response.body()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "sendOtp network exception for $phone: ${e.message}", e)
            null
        }
    }

    suspend fun checkRegistration(phone: String): Boolean {
        return try {
            val response = authApi.checkRegistration(phone.trim())
            if (!response.isSuccessful) {
                Log.e("ApnaDhobiRepository", "checkRegistration failed: HTTP ${response.code()} - ${response.message()}")
            }
            response.isSuccessful && response.body()?.get("isRegistered") == true
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "checkRegistration network exception for $phone: ${e.message}", e)
            false
        }
    }

    suspend fun validateReferral(code: String): Boolean {
        return try {
            val response = authApi.validateReferral(code.trim())
            if (!response.isSuccessful) {
                Log.e("ApnaDhobiRepository", "validateReferral failed: HTTP ${response.code()} - ${response.message()}")
            }
            response.isSuccessful && response.body()?.get("isValid") == true
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "validateReferral network exception for $code: ${e.message}", e)
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
            } else {
                Log.e("ApnaDhobiRepository", "register failed: HTTP ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "register network exception: ${e.message}", e)
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
                response.body()?.filter { it.isActive != false }?.sortedBy { it.displayOrder ?: 0 }?.map { 
                    ServiceCategory(
                        id = it.id,
                        name = it.name,
                        colorHex = it.colorHex ?: "#2563EB",
                        iconName = it.iconName ?: "LocalLaundryService",
                        imageUrl = it.imageUrl,
                        description = it.description,
                        turnaroundTime = it.turnaroundTime,
                        startingPrice = it.startingPrice,
                        popularTag = it.popularTag,
                        displayOrder = it.displayOrder ?: 0
                    )
                } ?: emptyList()
            } else {
                Log.e("ApnaDhobiRepository", "fetchCategories response error: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchCategories network error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchVendors(): List<Vendor> {
        return try {
            val response = catalogApi.getVendors()
            if (response.isSuccessful) {
                response.body()?.map { 
                    Vendor(
                        id = it.id,
                        name = it.name,
                        description = it.description ?: "Express laundry, ironing & quick fabric care",
                        rating = it.rating,
                        distanceKm = 1.2,
                        deliveryTimeMins = 45,
                        startingPrice = 49,
                        bannerColorHex = it.bannerColor ?: "0xFF0D47A1",
                        logoText = it.logoText ?: it.name.take(3).uppercase(),
                        isOpen = it.isOpen,
                        imageUrl = it.imageUrl,
                        address = it.address.ifBlank { "1012 Ocean Avenue, Sector 4, New Delhi" },
                        categoryTag = it.categoryTag ?: "Laundry"
                    )
                } ?: emptyList()
            } else {
                Log.e("ApnaDhobiRepository", "fetchVendors failed: HTTP ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchVendors network exception: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchProducts(categoryId: String? = null, vendorId: String? = null): List<LaundryProduct> {
        return try {
            val response = catalogApi.getServices(categoryId, vendorId)
            if (response.isSuccessful) {
                response.body()?.map { 
                    LaundryProduct(it.id, it.name, it.categoryId, it.originalPrice, it.discountPrice, it.deliveryEstimate ?: "Same Day", it.popularBadge, it.imageUrl)
                } ?: emptyList()
            } else {
                Log.e("ApnaDhobiRepository", "fetchProducts failed: HTTP ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchProducts network exception: ${e.message}", e)
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

    suspend fun fetchPublicConfig(): Map<String, Any>? {
        return try {
            val response = catalogApi.getPublicConfig()
            if (response.isSuccessful) {
                response.body()
            } else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchPublicConfig error: ${e.message}")
            null
        }
    }

    suspend fun fetchBanners(): List<BannerDto> {
        return try {
            val response = catalogApi.getBanners()
            if (response.isSuccessful) {
                val list = response.body()?.filter { it.isActive } ?: emptyList()
                Log.d("ApnaDhobiRepository", "fetchBanners fetched ${list.size} active banners from backend")
                list
            } else {
                Log.e("ApnaDhobiRepository", "fetchBanners failed with code: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchBanners network error: ${e.message}")
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
            } else {
                Log.e("ApnaDhobiRepository", "fetchWalletBalance failed: HTTP ${response.code()} - ${response.message()}")
                0.0
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchWalletBalance network exception: ${e.message}", e)
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

    suspend fun registerVendorDetailed(name: String, description: String, address: String, logoText: String, bannerColor: String, phone: String = "", ownerName: String = ""): Pair<Boolean, String> {
        return try {
            val body = mutableMapOf(
                "name" to name,
                "description" to description,
                "address" to address,
                "logoText" to logoText,
                "bannerColor" to bannerColor
            )
            if (phone.isNotBlank()) body["phone"] = phone
            if (ownerName.isNotBlank()) body["ownerName"] = ownerName
            val response = vendorsApi.register(body)
            if (response.isSuccessful) {
                val v = response.body()
                Pair(true, "Vendor '${v?.name ?: name}' registered and approved successfully! 🎉")
            } else {
                val errorStr = response.errorBody()?.string() ?: "Server returned error ${response.code()}"
                val cleanMsg = try {
                    if (errorStr.contains("\"message\":")) {
                        errorStr.substringAfter("\"message\":").substringBefore(",").replace("\"", "").replace("[", "").replace("]", "").replace("}", "").trim()
                    } else errorStr
                } catch (e: Exception) { errorStr }
                Pair(false, cleanMsg)
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "registerVendorDetailed error: ${e.message}", e)
            Pair(false, e.message ?: "Network error connecting to backend server")
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
            } else {
                Log.e("ApnaDhobiRepository", "fetchOrders failed: HTTP ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchOrders network exception: ${e.message}", e)
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
            } else {
                Log.e("ApnaDhobiRepository", "fetchVendorOrders failed: HTTP ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchVendorOrders network exception: ${e.message}", e)
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
            } else {
                Log.e("ApnaDhobiRepository", "createOrder failed: HTTP ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "createOrder network exception: ${e.message}", e)
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

    suspend fun registerWorkerDetailed(phone: String, name: String, city: String = "", vehicleType: String = "", licenseNumber: String = ""): Pair<Boolean, String> {
        return try {
            val body = mutableMapOf(
                "phone" to phone,
                "name" to name
            )
            if (city.isNotBlank()) body["city"] = city
            if (vehicleType.isNotBlank()) body["vehicleType"] = vehicleType
            if (licenseNumber.isNotBlank()) body["licenseNumber"] = licenseNumber
            val response = staffApi.registerWorker(body)
            if (response.isSuccessful) {
                Pair(true, "Delivery Partner registered and approved successfully! 🛵")
            } else {
                val errorStr = response.errorBody()?.string() ?: "Server returned error ${response.code()}"
                val cleanMsg = try {
                    if (errorStr.contains("\"message\":")) {
                        errorStr.substringAfter("\"message\":").substringBefore(",").replace("\"", "").replace("[", "").replace("]", "").replace("}", "").trim()
                    } else errorStr
                } catch (e: Exception) { errorStr }
                Pair(false, cleanMsg)
            }
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "registerWorkerDetailed error: ${e.message}", e)
            Pair(false, e.message ?: "Network error connecting to backend server")
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

    suspend fun uploadMediaByteArray(bytes: ByteArray, filename: String, mimeType: String): String? {
        return try {
            val reqBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = okhttp3.MultipartBody.Part.createFormData("file", filename, reqBody)
            uploadFile(part)
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "uploadMediaByteArray error: ${e.message}", e)
            null
        }
    }

    // User Profile
    suspend fun fetchUserProfileRemote(): UserDto? {
        return try {
            val response = usersApi.getProfile()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchUserProfileRemote error: ${e.message}", e)
            null
        }
    }

    suspend fun updateUserProfileRemote(name: String?, email: String?, profilePhoto: String?, gender: String?, dob: String?): UserDto? {
        return try {
            val body = mutableMapOf<String, Any>()
            name?.let { body["name"] = it }
            email?.let { body["email"] = it }
            profilePhoto?.let { body["profilePhoto"] = it }
            gender?.let { body["gender"] = it }
            dob?.let { body["dob"] = it }
            val response = usersApi.updateProfile(body)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "updateUserProfileRemote error: ${e.message}", e)
            null
        }
    }

    // Addresses
    suspend fun fetchRemoteAddresses(): List<AddressDto> {
        return try {
            val response = addressesApi.getAddresses()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchRemoteAddresses error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createRemoteAddress(
        name: String,
        phone: String,
        flatBuilding: String,
        streetArea: String,
        landmark: String,
        city: String,
        pincode: String,
        type: String,
        isDefault: Boolean
    ): AddressDto? {
        return try {
            val body = mapOf(
                "name" to name,
                "phone" to phone,
                "flatBuilding" to flatBuilding,
                "streetArea" to streetArea,
                "landmark" to landmark,
                "city" to city,
                "pincode" to pincode,
                "type" to type,
                "isDefault" to isDefault
            )
            val response = addressesApi.createAddress(body)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "createRemoteAddress error: ${e.message}", e)
            null
        }
    }

    suspend fun updateRemoteAddress(
        id: String,
        name: String,
        phone: String,
        flatBuilding: String,
        streetArea: String,
        landmark: String,
        city: String,
        pincode: String,
        type: String,
        isDefault: Boolean
    ): AddressDto? {
        return try {
            val body = mapOf(
                "name" to name,
                "phone" to phone,
                "flatBuilding" to flatBuilding,
                "streetArea" to streetArea,
                "landmark" to landmark,
                "city" to city,
                "pincode" to pincode,
                "type" to type,
                "isDefault" to isDefault
            )
            val response = addressesApi.updateAddress(id, body)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "updateRemoteAddress error: ${e.message}", e)
            null
        }
    }

    suspend fun setDefaultRemoteAddress(id: String): Boolean {
        return try {
            addressesApi.setDefaultAddress(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteRemoteAddress(id: String): Boolean {
        return try {
            addressesApi.deleteAddress(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // Wallet Transactions
    suspend fun fetchWalletTransactions(): List<Map<String, Any>> {
        return try {
            val response = walletApi.getTransactions()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchWalletTransactions error: ${e.message}", e)
            emptyList()
        }
    }

    // Coupons
    suspend fun fetchCoupons(): List<CouponDto> {
        return try {
            val response = couponsApi.getCoupons()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchCoupons error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun verifyCouponRemote(code: String): CouponDto? {
        return try {
            val response = couponsApi.verifyCoupon(code.trim().uppercase())
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "verifyCouponRemote error: ${e.message}", e)
            null
        }
    }

    // Support Tickets
    suspend fun fetchSupportTickets(): List<SupportTicketDto> {
        return try {
            val response = supportApi.getTickets()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "fetchSupportTickets error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createSupportTicket(
        category: String,
        subject: String,
        description: String,
        orderId: String? = null,
        contactPhone: String? = null
    ): SupportTicketDto? {
        return try {
            val body = mutableMapOf<String, Any>(
                "category" to category,
                "subject" to subject,
                "description" to description
            )
            orderId?.let { body["orderId"] = it }
            contactPhone?.let { body["contactPhone"] = it }
            val response = supportApi.createTicket(body)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApnaDhobiRepository", "createSupportTicket error: ${e.message}", e)
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
        ServiceCategory("laundry", "Laundry", "SaffronOrange", "WashingMachine", description = "Express Wash & Fold • Doorstep Pickup"),
        ServiceCategory("dry_cleaning", "Dry Cleaning", "SaffronOrange", "DryCleaning", description = "Laundering at: Apna Dhobi Express"),
        ServiceCategory("ironing", "Ironing", "SaffronOrange", "Iron", description = "Crisp Wrinkle-Free Steam Pressing"),
        ServiceCategory("shoe_cleaning", "Shoe Cleaning", "SaffronOrange", "CleaningServices", description = "Deep Cleaning, Polish & Deodorization"),
        ServiceCategory("carpet_cleaning", "Carpet Clean", "SaffronOrange", "RollerShades", description = "Deep Vacuuming & Shampooing"),
        ServiceCategory("blanket_wash", "Blanket Wash", "SaffronOrange", "AcUnit", description = "Hygienic Sanitization & Soft Wash"),
        ServiceCategory("wedding_wear", "Wedding Wear", "SaffronOrange", "Star", description = "Royal Designer & Heavy Fabric Care"),
        ServiceCategory("premium_care", "Premium Care", "SaffronOrange", "Favorite", description = "Delicate Silk & Wool Hydro-Clean")
    )

    val vendors = listOf(
        Vendor(
            id = "vendor_1",
            name = "Apna Dhobi Express",
            description = "Express laundry, ironing & quick fabric care. We use eco-friendly detergents and advanced German washing technology for premium fabric care.",
            rating = 4.8,
            ratingCount = 365,
            distanceKm = 1.2,
            deliveryTimeMins = 45,
            startingPrice = 49,
            bannerColorHex = "0xFF0D47A1",
            logoText = "ADE",
            isOpen = true,
            imageUrl = "https://images.unsplash.com/photo-1545173168-9f1947eebb7f?w=600&q=80",
            address = "1012 Ocean Avenue, Sector 4, New Delhi",
            categoryTag = "Laundry",
            providerName = "Jenny Wilson",
            providerRole = "Service Provider & Specialist",
            providerPhone = "+91 98765 43210",
            services = listOf("Wash & Fold", "Dry Cleaning", "Carpet Washing", "Wash & Iron", "Free Pickup", "24h Delivery")
        ),
        Vendor(
            id = "vendor_2",
            name = "Royal Dry Cleaners & Dyers",
            description = "Premium dry cleaning for wedding wear, designer suits & heavy fabrics with eco-friendly hydro-carbon technology.",
            rating = 4.6,
            ratingCount = 210,
            distanceKm = 2.5,
            deliveryTimeMins = 120,
            startingPrice = 99,
            bannerColorHex = "0xFFFF6B00",
            logoText = "RDC",
            isOpen = true,
            imageUrl = "https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?w=600&q=80",
            address = "Shop 14, Royal Market, Connaught Place, New Delhi",
            categoryTag = "Dry Cleaning",
            providerName = "Rajesh Sharma",
            providerRole = "Master Dry Cleaner",
            providerPhone = "+91 98112 34567",
            services = listOf("Dry Cleaning", "Suit Steam Iron", "Wedding Wear Care", "Leather Spa", "Free Pickup", "24h Delivery")
        ),
        Vendor(
            id = "vendor_3",
            name = "Smart Ironing Hub",
            description = "Crisp steam ironing with vacuum suction tables, wrinkle-free packaging and doorstep hanger delivery.",
            rating = 4.9,
            ratingCount = 180,
            distanceKm = 0.8,
            deliveryTimeMins = 30,
            startingPrice = 15,
            bannerColorHex = "0xFF00A86B",
            logoText = "SIH",
            isOpen = true,
            imageUrl = "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?w=600&q=80",
            address = "Plot 22, Hauz Khas, New Delhi",
            categoryTag = "Ironing",
            providerName = "Amit Verma",
            providerRole = "Steam Press Specialist",
            providerPhone = "+91 99887 76655",
            services = listOf("Steam Ironing", "Wash & Fold", "Curtain Cleaning", "Express 2h", "Free Pickup", "24h Delivery")
        ),
        Vendor(
            id = "vendor_4",
            name = "The Elite Shoe & Blanket Care",
            description = "Intricate deep clean spa for blankets, sneakers, leather shoes, boots & jackets with antimicrobial sanitization.",
            rating = 4.7,
            ratingCount = 145,
            distanceKm = 3.1,
            deliveryTimeMins = 180,
            startingPrice = 199,
            bannerColorHex = "0xFFF4B400",
            logoText = "ESC",
            isOpen = true,
            imageUrl = "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&q=80",
            address = "B-8, DLF Galleria, Cyber City, Gurugram",
            categoryTag = "Shoe Cleaning",
            providerName = "Pooja Malhotra",
            providerRole = "Footwear & Quilt Care Lead",
            providerPhone = "+91 98711 22334",
            services = listOf("Shoe Spa", "Blanket Wash", "Quilt Sanitization", "Jacket Care", "Free Pickup", "24h Delivery")
        )
    )

    val products = listOf(
        // Laundry Service (laundry)
        LaundryProduct("prod_l1", "Men's Shirt Wash & Fold", "laundry", 60.0, 49.0, "Delivery: Same Day", "BESTSELLER", "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=500&q=80"),
        LaundryProduct("prod_l2", "Men's T-Shirt Wash", "laundry", 50.0, 39.0, "Delivery: Same Day", "POPULAR", "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=500&q=80"),
        LaundryProduct("prod_l3", "Women's Kurti Wash", "laundry", 80.0, 59.0, "Delivery: Same Day", "HOT DEAL", "https://images.unsplash.com/photo-1617627143750-d86bc21e42bb?w=500&q=80"),
        LaundryProduct("prod_l4", "Jeans Laundry / Clean", "laundry", 90.0, 75.0, "Delivery: 1 Day", null, "https://images.unsplash.com/photo-1542272604-780c96856592?w=500&q=80"),
        LaundryProduct("prod_l5", "Bed Sheet Double Laundry", "laundry", 150.0, 119.0, "Delivery: 1 Day", null, "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=500&q=80"),

        // Dry Cleaning (dry_cleaning) - MATCHING IMAGE 1 EXACTLY
        LaundryProduct("prod_dc2", "Heavy Wedding Saree Dry Clean", "dry_cleaning", 399.0, 299.0, "Delivery: 3 Days", "PREMIUM CHOICE", "https://images.unsplash.com/photo-1610030469983-98e550d6193c?w=500&q=80"),
        LaundryProduct("prod_dc3", "Men's Suit 2-Piece Dry Clean", "dry_cleaning", 499.0, 399.0, "Delivery: 2 Days", "POPULAR", "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=500&q=80"),
        LaundryProduct("prod_dc1", "Premium Designer Kurta Dry Clean", "dry_cleaning", 199.0, 149.0, "Delivery: 2 Days", "ESSENTIAL", "https://images.unsplash.com/photo-1583391733956-3750e0ff4e8b?w=500&q=80"),
        LaundryProduct("prod_dc4", "Winter Leather Jacket Care", "dry_cleaning", 599.0, 449.0, "Delivery: 3 Days", "LUXE", "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=500&q=80"),
        LaundryProduct("prod_dc5", "Double Bed Blanket Dry Clean", "dry_cleaning", 350.0, 279.0, "Delivery: 2 Days", null, "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=500&q=80"),

        // Ironing (ironing)
        LaundryProduct("prod_i1", "Men's Casual Shirt Steam Iron", "ironing", 20.0, 14.0, "Delivery: 30 Mins", "LIGHTNING FAST", "https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?w=500&q=80"),
        LaundryProduct("prod_i2", "Formal Trousers Crisp Press", "ironing", 25.0, 18.0, "Delivery: 45 Mins", null, "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=500&q=80"),
        LaundryProduct("prod_i3", "Premium Silk Saree Delicate Press", "ironing", 80.0, 59.0, "Delivery: 2 Hours", "HANDLED WITH CARE", "https://images.unsplash.com/photo-1610030469983-98e550d6193c?w=500&q=80"),
        LaundryProduct("prod_i4", "Kids School Uniform Crisp Press", "ironing", 20.0, 12.0, "Delivery: 1 Hour", null, "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=500&q=80"),

        // Shoe Cleaning (shoe_cleaning)
        LaundryProduct("prod_s1", "Sports Sneaker Deep Clean & Polish", "shoe_cleaning", 299.0, 199.0, "Delivery: 1 Day", "LIKE NEW", "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=500&q=80"),
        LaundryProduct("prod_s2", "Formal Leather Shoe Wax Shine", "shoe_cleaning", 199.0, 149.0, "Delivery: Same Day", "PREMIUM SHINE", "https://images.unsplash.com/photo-1614252369475-531eba835eb1?w=500&q=80"),

        // Carpet Cleaning (carpet_cleaning)
        LaundryProduct("prod_c1", "Premium Woolen Persian Carpet Deep Clean", "carpet_cleaning", 1099.0, 899.0, "Delivery: 3 Days", "DELUXE", "https://images.unsplash.com/photo-1600121848594-d8644e57abab?w=500&q=80"),
        LaundryProduct("prod_c2", "Office Synthetic Carpet Shampooing", "carpet_cleaning", 699.0, 499.0, "Delivery: 2 Days", null, "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?w=500&q=80"),

        // Blanket Wash (blanket_wash)
        LaundryProduct("prod_b1", "Double Bed Mink Blanket Sanitization", "blanket_wash", 399.0, 299.0, "Delivery: 2 Days", "COZY WASH", "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=500&q=80"),
        LaundryProduct("prod_b2", "Single Bed Fleece Blanket Eco Wash", "blanket_wash", 250.0, 199.0, "Delivery: 1 Day", null, "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=500&q=80"),

        // Wedding Wear (wedding_wear)
        LaundryProduct("prod_w1", "Heavy Designer Bridal Lehenga Dry Clean", "wedding_wear", 1999.0, 1499.0, "Delivery: 4 Days", "ROYAL CARE", "https://images.unsplash.com/photo-1583391733956-3750e0ff4e8b?w=500&q=80"),
        LaundryProduct("prod_w2", "Groom Embroidered Sherwani Dry Clean", "wedding_wear", 1599.0, 1199.0, "Delivery: 3 Days", "SPECIAL", "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=500&q=80"),

        // Premium Care (premium_care)
        LaundryProduct("prod_jc1", "Pure Silk Saree Delicate Hydro-Clean", "premium_care", 500.0, 399.0, "Delivery: 2 Days", "PETAL SOFT", "https://images.unsplash.com/photo-1610030469983-98e550d6193c?w=500&q=80"),
        LaundryProduct("prod_jc2", "Premium Cashmere Sweater Hand Wash", "premium_care", 350.0, 249.0, "Delivery: 2 Days", null, "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=500&q=80")
    )

    fun getProductsByCategoryId(catId: String): List<LaundryProduct> {
        return products.filter { it.categoryId == catId }
    }
}
